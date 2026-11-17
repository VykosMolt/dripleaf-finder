import json
import re
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

from PIL import Image, ImageChops

RUN, OUT = Path(sys.argv[1]), Path(sys.argv[2])
PORT_A, PORT_B, ADDON, CLIENT = sys.argv[3], sys.argv[4], sys.argv[5], int(sys.argv[6])
PROBE, DATA, SETTINGS = RUN / "uiprobe", RUN / "config" / "dripleaf", RUN / "config" / "dripleaf-finder.json"
BORDER, INK = (65, 65, 73), 110
ENTER, ESCAPE, BACKSPACE, END = 257, 256, 259, 269
TITLE = "Spliced Dripleaf"
marks = shots = 0
scale = 2.0
initial_radius = None
initial_memory = None
results = []
layouts = []


class Abort(Exception):
    pass


def alive():
    return Path(f"/proc/{CLIENT}").exists()


def send(*commands, timeout=60):
    global marks
    marks += 1
    PROBE.mkdir(parents=True, exist_ok=True)
    with open(PROBE / "cmds.txt", "a") as f:
        f.write("".join(c + "\n" for c in commands) + f"mark {marks}\n")
    end = time.time() + timeout
    while time.time() < end:
        log = (PROBE / "log.txt").read_text() if (PROBE / "log.txt").exists() else ""
        for line in log.splitlines():
            if f" mark {marks} " in line:
                return line
        if not alive():
            raise Abort("the client exited")
        time.sleep(0.2)
    raise Abort(f"no answer to {commands}")


def near(pixel, color, tolerance):
    return all(abs(a - b) <= tolerance for a, b in zip(pixel, color))


def centre_y(p):
    return (p[2] + p[4]) / 2


def shot(name):
    global shots
    shots += 1
    name = f"{shots:03d}-{name}"
    send("move 2 2", "wait 3", f"shot {name}")
    path = RUN / "screenshots" / f"{name}.png"
    for _ in range(50):
        if path.exists() and path.stat().st_size > 0:
            break
        time.sleep(0.2)
    else:
        raise Abort(f"no screenshot {name}")
    time.sleep(0.3)
    shutil.copy(path, OUT / path.name)
    return Screen(OUT / path.name)


class Screen:
    def __init__(self, path):
        self.path = path
        self.image = Image.open(path).convert("RGB")
        self.words = self.read()
        lines = []
        for w in sorted(self.words, key=centre_y):
            line = next((l for l in lines if abs(centre_y(l[0]) - centre_y(w)) < 5), None)
            if line:
                line.append(w)
            else:
                lines.append([w])
        self.phrases = []
        for line in lines:
            start = len(self.phrases)
            for w in sorted(line, key=lambda w: w[1]):
                p = self.phrases[-1] if len(self.phrases) > start else None
                if p and w[1] - p[3] < 12:
                    p[0] += " " + w[0]
                    p[3], p[2], p[4] = w[3], min(p[2], w[2]), max(p[4], w[4])
                else:
                    self.phrases.append(list(w))

    def ocr(self, box, k, threshold, psm, *options):
        r, g, b = self.image.split()
        ink = ImageChops.darker(ImageChops.darker(r, g), b).crop(box)
        big = ink.resize((ink.width * k, ink.height * k), Image.LANCZOS).point(lambda v: 0 if v > threshold else 255)
        scratch = self.path.with_suffix(".ocr.png")
        big.save(scratch)
        out = subprocess.run(["tesseract", str(scratch), "-", "--psm", psm, *options] + (["tsv"] if psm == "11" else []),
                             capture_output=True, text=True).stdout
        scratch.unlink()
        return out

    def line(self, box):
        def vote(*options):
            readings = [self.ocr(box, k, threshold, "7", *options).split() for k in (3, 4, 5) for threshold in (90, 110)]
            readings = [re.sub(r"[^\w./-]", "", r[0]) for r in readings if r]
            return max(set(readings), key=readings.count) if readings else ""

        text = vote()
        return vote("-c", "tessedit_char_whitelist=0123456789.") if re.fullmatch(r"[\d.]+", text) else text

    def ink(self, x, y):
        return min(self.pixel(x, y)) > INK

    def read(self, box=None, k=3, threshold=INK):
        box = box or (0, 0, self.image.width, self.image.height)
        x0, y0 = box[0], box[1]
        tsv = self.ocr(box, k, threshold, "11")
        words = []
        for row in tsv.splitlines()[1:]:
            f = row.split("\t")
            if len(f) == 12 and f[11].strip() and float(f[10]) > 20:
                x, y, w, h = (int(v) / k for v in f[6:10])
                words.append([f[11], x0 + x, y0 + y, x0 + x + w, y0 + y + h])
        return words

    def find(self, text, strict=True):
        return next((p for p in self.phrases if (p[0].startswith(text) if strict else text in p[0])), None)

    def label(self, text, strict=True):
        p = self.find(text, strict)
        if p is None:
            raise Abort(f"no {text!r} on screen ({self.path.name})")
        return p

    def right_of(self, label):
        _, top, x1, bottom = self.row(label)
        left = max(p[3] for p in self.phrases if abs(p[1] - label[1]) < 4 and top < centre_y(p) < bottom)
        return sorted((w for w in self.words if top < centre_y(w) < bottom and left + 4 < w[1] and w[3] <= x1 and re.search(r"\w", w[0])),
                      key=lambda w: w[1])

    def value(self, text):
        return " ".join(w[0] for w in self.right_of(self.label(text)))

    def number(self, text):
        return next((w[0] for w in self.right_of(self.label(text)) if re.fullmatch(r"\d+", w[0])), None)

    def button(self, text):
        words = [w for w in self.right_of(self.label(text)) if re.fullmatch(r"[A-Za-z]{3,}", w[0])]
        if not words:
            raise Abort(f"no button beside {text!r} ({self.path.name})")
        return words[0]

    def pixel(self, x, y):
        return self.image.getpixel((int(x), int(y)))

    def row(self, label):
        y = int(centre_y(label))
        x0 = int(label[1]) - 2
        while x0 > 0 and not near(self.pixel(x0, y), BORDER, 6):
            x0 -= 1
        top = bottom = y
        while top > 0 and not near(self.pixel(x0 + 7, top), BORDER, 6):
            top -= 1
        while bottom < self.image.height - 1 and not near(self.pixel(x0 + 7, bottom), BORDER, 6):
            bottom += 1
        def edge(x):
            return all(near(self.pixel(x, yy), BORDER, 8) for yy in range(top + 5, bottom - 4))

        x1 = int(label[3])
        while x1 < self.image.width - 7 and not edge(x1):
            x1 += 1
        while x1 < self.image.width - 7 and edge(x1 + 1):
            x1 += 1
        return x0, top, x1, bottom

    def placement(self, text):
        label, toolbar = self.find(text), self.find("Reset", False)
        if label is None or toolbar is None:
            return None
        if label[2] <= toolbar[4] + 21:
            return "above"
        return "below" if self.row(label)[3] >= self.image.height - 16 else "visible"

    def overflows(self):
        title, toolbar = self.label(TITLE, False), self.label("Reset", False)
        below = [p for p in self.phrases if p[2] > toolbar[4] + 4 and p[1] > title[1] - 40 and re.search(r"[A-Za-z]{3}", p[0])]
        column = min(p[1] for p in below)
        problems = []
        for label in [p for p in below if abs(p[1] - column) < 4]:
            x0, top, x1, bottom = self.row(label)
            if top <= toolbar[4] or bottom >= self.image.height - 8:
                continue
            if x1 >= self.image.width - 7:
                problems.append(f"{label[0]!r} row has no right border")
            elif any(min(self.pixel(x, y)) > INK for x in range(x1 + 1, x1 + 7) for y in range(top + 3, bottom - 2)):
                problems.append(f"{label[0]!r} row draws past its right border")
            for p in self.phrases:
                if top < centre_y(p) < bottom and re.search(r"\w\w", p[0]) and (p[1] < x0 or p[3] > x1 + 1):
                    problems.append(f"{p[0]!r} lies outside its row")
                if top < centre_y(p) < bottom and re.search(r"(\.\.\.|…)$", p[0]):
                    problems.append(f"{p[0]!r} is cut short")
        return sorted(set(problems))


def click_at(x, y, settle=4):
    send(f"click {x / scale:.1f} {y / scale:.1f}", f"wait {settle}")


def click(phrase, settle=4):
    click_at((phrase[1] + phrase[3]) / 2, centre_y(phrase), settle)


def scroll(s, amount):
    title = s.label(TITLE, False)
    send(f"scroll {(title[1] + title[3]) / 2 / scale:.1f} {(title[4] + 120) / scale:.1f} {amount}", "wait 5")


def view(name, rows=(), top=False, check_layout=True):
    s = opened(name)
    if rows:
        for step in range(40):
            places = [s.placement(r) for r in rows]
            if all(p == "visible" for p in places):
                break
            jump = None in places and step == 0
            scroll(s, 10 if jump else 1 if "above" in places else -1)
            last, s = s, shot(f"{name}-scroll")
            if not jump and last.image.tobytes() == s.image.tobytes():
                raise Abort(f"cannot scroll {', '.join(rows)} fully into view ({s.path.name})")
        else:
            raise Abort(f"cannot scroll {', '.join(rows)} fully into view ({s.path.name})")
    else:
        scroll(s, 10 if top else -10)
    s = shot(name)
    if check_layout:
        layouts.append((s.path.name, s.overflows()))
    return s


def opened(name):
    s = shot(name + "-pre")
    if s.find(TITLE, False) is None or s.find("Reset", False) is None:
        send("close", "wait 2", "open2 autismclient.gui.d.PacketUtil", "wait 20")
        menu = shot(name + "-menu")
        entry = menu.label("Spliced", False)
        arrows = sorted((w for w in menu.words if w[0].endswith(">") and abs(centre_y(w) - centre_y(entry)) < 6 and w[1] > entry[3]),
                        key=lambda w: w[1])
        if not arrows:
            raise Abort(f"no > button beside the module in the menu ({menu.path.name})")
        click_at(arrows[0][3] - 2, centre_y(arrows[0]), 10)
        s = shot(name + "-open")
        s.label(TITLE, False)
    return s


def check(name, ok, detail, screen=None):
    results.append(ok)
    print(f"{name}: {'PASS' if ok else 'FAIL'} {detail}" + ("" if ok or screen is None else f" [{screen.path}]"), flush=True)


def flow(name, body):
    try:
        body()
    except Exception as e:
        check(name, False, f"aborted: {e!r}")


def read(key, name):
    path = DATA / key / name
    return path.read_text().strip() if path.exists() else None


def settings():
    return json.loads(SETTINGS.read_text()) if SETTINGS.exists() else {}


KEY_A, KEY_B = f"127.0.0.1_{PORT_A}", f"127.0.0.1_{PORT_B}"
with zipfile.ZipFile(ADDON) as jar:
    HAS_TYPE = any(b"Server Type" in jar.read(n) for n in jar.namelist() if n.startswith("dripleaffinder/autism/"))


def edit_seed(text, finish):
    s = view("seed-edit", rows=("World Seed", "Status"), check_layout=False)
    box = s.right_of(s.label("World Seed"))
    if not box:
        raise Abort(f"no World Seed box ({s.path.name})")
    click(box[0])
    send(f"keyp {END}", *[f"keyp {BACKSPACE}"] * 24, f"type {text}" if text else "wait 1", "wait 4")
    if finish == "enter":
        send(f"keyp {ENTER}", "wait 4")
    elif finish == "escape":
        send(f"keyp {ESCAPE}", "wait 4")
    else:
        click(s.label("Status"))
    return view(f"seed-{finish}-{text or 'empty'}", rows=("World Seed",))


def edit_number(label, value):
    s = view(f"{label.split()[1].lower()}-edit", rows=(label,), check_layout=False)
    boxes = [w for w in s.right_of(s.label(label)) if re.fullmatch(r"\d+", w[0])]
    if not boxes:
        raise Abort(f"no number box beside {label} ({s.path.name})")
    click(boxes[0])
    send(f"keyp {END}", *[f"keyp {BACKSPACE}"] * 8, f"type {value}", "wait 2", f"keyp {ENTER}", "wait 4")


def items(closed, opened, value, right):
    x0 = int(value[1]) - 2

    def text(y):
        return any(opened.ink(x, y) and opened.pixel(x, y) != closed.pixel(x, y) for x in range(x0, x0 + 50))

    lines, y = [], int(value[4]) + 6
    while y < opened.image.height - 2 and (not lines or y - lines[-1][1] < 20):
        if text(y):
            if lines and y - lines[-1][1] <= 3:
                lines[-1][1] = y
            else:
                lines.append([y, y])
        y += 1
    return [(opened.line((x0 - 4, top - 4, right - 4, bottom + 5)), [x0, top, x0 + 20, bottom]) for top, bottom in lines if bottom - top >= 5]


def choose(label, wanted, name):
    s = view(f"{name}-closed", rows=(label,), check_layout=False)
    value = s.right_of(s.label(label))
    if not value:
        raise Abort(f"no {label} dropdown ({s.path.name})")
    click(value[0])
    opened = shot(f"{name}-open")
    options = items(s, opened, value[0], s.row(s.label(label))[2])
    names = [n for n, _ in options]
    pick = next((box for n, box in options if wanted(n)), None)
    if pick is not None:
        click([""] + pick)
    else:
        send(f"keyp {ESCAPE}", "wait 4")
    return names, opened


def flow_a():
    global initial_radius, initial_memory
    labels = ["Masked Server", "World Seed", "World Version"] + ["Server Type"] * HAS_TYPE + ["Search Radius", "Search Memory", "Status", "Candidates", "Seed Search"]
    s, seen = view("a-window", top=True), set()
    for _ in range(40):
        seen |= {l for l in labels if s.placement(l) == "visible"}
        if initial_radius is None and s.placement("Search Radius") == "visible":
            initial_radius = s.number("Search Radius")
        if initial_memory is None and s.placement("Search Memory") == "visible":
            initial_memory = s.number("Search Memory")
        if all(label in seen for label in labels):
            break
        scroll(s, -1)
        last, s = s, shot("a-window-scroll")
        if last.image.tobytes() == s.image.tobytes():
            break
        layouts.append((s.path.name, s.overflows()))
    missing = [l for l in labels if l not in seen]
    check("a. module window opens and every row scrolls fully into view", not missing,
          f"{len(seen)} of {len(labels)} rows seen" + (f"; missing {', '.join(missing)}" if missing else ""), s)


def flow_b():
    s = edit_seed("1234567890", "enter")
    files = sorted(str(p.relative_to(DATA)) for p in DATA.glob("*/seed.txt"))
    check("b1. seed + Enter saves for this server only", read(KEY_A, "seed.txt") == "1234567890" and files == [f"{KEY_A}/seed.txt"],
          f"seed.txt={read(KEY_A, 'seed.txt')!r} files={files}", s)
    s = edit_seed("-987654321", "clickaway")
    check("b2. seed + click away saves", read(KEY_A, "seed.txt") == "-987654321", f"seed.txt={read(KEY_A, 'seed.txt')!r}", s)
    s = edit_seed("555", "escape")
    check("b3. seed + Escape is discarded", read(KEY_A, "seed.txt") == "-987654321", f"seed.txt={read(KEY_A, 'seed.txt')!r}", s)
    s = edit_seed("", "enter")
    check("b4. empty + Enter clears", read(KEY_A, "seed.txt") is None, f"seed.txt={read(KEY_A, 'seed.txt')!r}", s)


def flow_f_noseed():
    s = view("f-noseed-before", rows=("Status", "Seed Search"))
    click(s.button("Seed Search"))
    s = view("f-noseed-after", rows=("Status", "Seed Search"))
    status = s.value("Status")
    check("f0. Search with no seed shows the refusal in Status", "No seed" in status, f"status={status!r}", s)


def flow_c():
    with zipfile.ZipFile(ADDON) as jar:
        bundled = jar.read("searchers/versions.tsv").decode().split("\n") if "searchers/versions.tsv" in jar.namelist() else []
    expected = {"Auto", "26.2"} | {line.split("\t")[0] for line in bundled if line.strip()}
    names, opened = choose("World Version", lambda n: n != "Auto" and n in expected, "c-version")
    picked = next((n for n in names if n != "Auto" and n in expected), None)
    check("c1. World Version lists Auto and the bundled versions", set(names) == expected and len(names) == len(expected),
          f"listed={names} expected={sorted(expected)}", opened)
    chosen = read(KEY_A, "version.txt")
    check("c2. a non-Auto version is saved", picked is not None and chosen == picked, f"picked={picked!r} version.txt={chosen!r}", opened)
    names, opened = choose("World Version", lambda n: n == "Auto", "c-auto")
    check("c3. Auto removes version.txt", chosen is not None and read(KEY_A, "version.txt") is None,
          f"version.txt {chosen!r}->{read(KEY_A, 'version.txt')!r}", opened)


def flow_d():
    if not HAS_TYPE:
        print("d. Server Type: SKIP (not in this jar)")
        return
    names, opened = choose("Server Type", lambda n: n.startswith("Folia"), "d-type")
    check("d. Server Type Folia is stored per server", read(KEY_A, "server-type.txt") == "Folia" and read(KEY_B, "server-type.txt") is None,
          f"options={names} server-type.txt={read(KEY_A, 'server-type.txt')!r}", opened)


def flow_e():
    edit_number("Search Radius", 256)
    edit_number("Search Memory", 3)
    s = view("e-numbers", rows=("Search Radius", "Search Memory"))
    saved = settings()
    check("e. Search Radius and Search Memory persist", saved.get("radius") == 256 and saved.get("memoryGb") == 3,
          f"json radius={saved.get('radius')} memoryGb={saved.get('memoryGb')}; shown {s.number('Search Radius')} / {s.number('Search Memory')}", s)


def flow_f():
    edit_seed("1234567890", "enter")
    s = view("f-before", rows=("Status", "Seed Search"))
    idle = s.value("Status")
    started = time.time()
    click(s.button("Seed Search"))
    for i in range(8):
        s = view(f"f-running-{i}", rows=("Status", "Seed Search"))
        status = s.value("Status")
        if status != idle and not re.search(r"Still asking|Asking", status):
            break
    check("f1. Search moves Status off idle to a running clock", status != idle and re.search(r"\d+:\d\d", status) is not None,
          f"idle={idle!r} now={status!r} after {time.time() - started:.0f} s", s)
    button = s.button("Seed Search")[0]
    check("f2. Stop shows while searching", button == "Stop", f"button={button!r}", s)
    click(s.button("Seed Search"))
    s = view("f-stopped", rows=("Status", "Seed Search"))
    status, button = s.value("Status"), s.button("Seed Search")[0]
    check("f3. Stop stops and Search returns", "stopped" in status.lower() and button == "Search", f"status={status!r} button={button!r}", s)


def flow_g():
    choose("World Version", lambda n: n != "Auto", "g-version")
    seed, version, kind = read(KEY_A, "seed.txt"), read(KEY_A, "version.txt"), read(KEY_A, "server-type.txt")
    for label in ("Masked Server", "Lush Cave Pings", "Azalea Trees"):
        s = view("g-toggle-" + label.split()[0].lower(), rows=(label,))
        row = s.row(s.label(label))
        click_at(row[2] - 20, centre_y(s.label(label)))
    changed = settings()
    check("g0. on/off settings changed before Reset", changed.get("masked") is True and changed.get("lush") is False and changed.get("azalea") is False,
          f"masked={changed.get('masked')} lush={changed.get('lush')} azalea={changed.get('azalea')}")
    s = view("g-before", rows=("Search Radius",))
    click(s.label("Reset", False), 10)
    s = view("g-after", rows=("Search Radius", "Search Memory"))
    saved = settings()
    radius = saved.get("radius")
    shown = s.number("Search Radius")
    ok = seed is not None and version is not None and read(KEY_A, "seed.txt") == seed and read(KEY_A, "version.txt") == version
    ok = ok and read(KEY_A, "server-type.txt") == kind and str(radius) == initial_radius and shown == initial_radius
    ok = ok and str(saved.get("memoryGb")) == initial_memory and s.number("Search Memory") == initial_memory
    ok = ok and saved.get("masked") is False and saved.get("lush") is True and saved.get("azalea") is True
    check("g. Reset keeps seed and version, restores radius, memory and on/off settings", ok,
          f"seed {seed!r}->{read(KEY_A, 'seed.txt')!r} version {version!r}->{read(KEY_A, 'version.txt')!r} "
          f"type {kind!r}->{read(KEY_A, 'server-type.txt')!r} radius json={radius} shown={shown!r} default={initial_radius!r} "
          f"memory={saved.get('memoryGb')} default={initial_memory!r} masked={saved.get('masked')} lush={saved.get('lush')} azalea={saved.get('azalea')}", s)


def flow_i():
    s = view("i-toggle-before", rows=("Candidates",))
    if s.find("Disabled"):
        click(s.label("Disabled"))
    s = view("i-enabled", rows=("Candidates",))
    enabled = s.value("Candidates")
    if "here" not in enabled:
        raise Abort(f"AUTISM did not enable the test identity: Candidates={enabled!r}")
    click(s.label("Enabled"))
    s = view("i-disabled", rows=("Candidates",))
    disabled = s.value("Candidates")
    click(s.label("Disabled"))
    s = view("i-reenabled", rows=("Candidates",))
    again = s.value("Candidates")
    check("i. Candidates follows the module toggle", "here" in enabled and disabled == "Finder off" and again == enabled,
          f"enabled={enabled!r} disabled={disabled!r} reenabled={again!r}", s)


def flow_j():
    edit_number("Search Radius", 2048)
    s = view("j-search-before-leaving", rows=("Status", "Seed Search"))
    click(s.button("Seed Search"))
    send("close", "leave", "wait 20")
    for _ in range(30):
        if " world false " in send():
            break
        time.sleep(1)
    else:
        raise Abort("never left the server during search")
    s = view("j-offline", rows=("Status", "Seed Search"))
    button = s.button("Seed Search")[0]
    check("j1. Stop remains visible after leaving the server", button == "Stop", f"button={button!r}", s)
    if button != "Stop":
        raise Abort("no offline Stop button")
    click(s.button("Seed Search"))
    s = view("j-stopped-offline", rows=("Status", "Seed Search"))
    status, button = s.value("Status"), s.button("Seed Search")[0]
    check("j2. Stop works outside a world", "stopped" in status.lower() and button == "Search", f"status={status!r} button={button!r}", s)


def flow_h():
    send("close", "leave", "wait 40")
    for _ in range(30):
        if " world false " in send():
            break
        time.sleep(1)
    send(f"join 127.0.0.1:{PORT_B}")
    for _ in range(120):
        if " world true " in send():
            break
        time.sleep(1)
    else:
        raise Abort("never joined the second server")
    send("wait 160")
    s = view("h-server-b", rows=("World Seed",) + ("Server Type",) * HAS_TYPE)
    box = s.value("World Seed")
    kind = s.value("Server Type") if HAS_TYPE else "Auto"
    check("h. a different server starts without the seed", read(KEY_B, "seed.txt") is None and "empty" in box and read(KEY_A, "seed.txt") == "1234567890"
          and kind.startswith("Auto"), f"{KEY_B}/seed.txt={read(KEY_B, 'seed.txt')!r} box={box!r} server type={kind!r} "
          f"{KEY_A}/seed.txt={read(KEY_A, 'seed.txt')!r}", s)


def main():
    global scale
    for _ in range(300):
        line = send()
        if " world true " in line:
            scale = float(line.split(" scale ")[1])
            break
        time.sleep(1)
    else:
        raise Abort("never joined the server")
    send("wait 160")
    for name, body in [("a", flow_a), ("b", flow_b), ("f0", flow_f_noseed), ("c", flow_c), ("d", flow_d), ("e", flow_e),
                       ("f1", flow_f), ("i", flow_i), ("g", flow_g), ("j", flow_j), ("h", flow_h)]:
        flow(name, body)
    bad = [(name, problems) for name, problems in layouts if problems]
    check("a. no text wider than its row", not bad, f"{len(layouts)} window screenshots; " + "; ".join(f"{n}: {', '.join(p)}" for n, p in bad))


try:
    main()
except Abort as e:
    check("setup", False, str(e))
finally:
    if alive():
        try:
            send("quit", timeout=10)
        except Abort:
            pass
print("AUTISM-UI " + ("PASS" if results and all(results) else "FAIL"))
sys.exit(0 if results and all(results) else 1)
