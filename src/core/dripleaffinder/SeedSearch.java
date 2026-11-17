package dripleaffinder;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class SeedSearch {
    public static final int DEFAULT_RADIUS = 1000, MAX_RADIUS = 4000, STATUS_LENGTH = 40;

    private final Finder finder;
    private final List<SearchRun> runs = new CopyOnWriteArrayList<>(), killed = new CopyOnWriteArrayList<>();
    final Map<BlockPos, String[]> hits = new LinkedHashMap<>();
    private final List<String> lush = new ArrayList<>();
    private final Map<String, String> failed = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    final Map<String, String> results = new HashMap<>();
    private String leftOut;
    private int[] spawn;
    private static final Object SETUP = new Object();
    private volatile int generation;
    private volatile Setup setup;
    private boolean missedLive;
    private String key, pendingKey;
    private BlockPos center;
    private int radius, ticks;
    private boolean together;
    private long startedAt;

    private static final class Setup {
        final int generation;
        final List<SearchRun> launched = new CopyOnWriteArrayList<>();
        volatile boolean complete;
        volatile String error, unstarted, phase = "starting";

        Setup(int generation) {
            this.generation = generation;
        }
    }

    SeedSearch(Finder finder) {
        this.finder = finder;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runs.forEach(SearchRun::kill);
            Setup pending = setup;
            if (pending != null) pending.launched.forEach(SearchRun::kill);
        }));
    }

    public void start(Minecraft mc, int radius, boolean chat) {
        if (running()) {
            if (chat) Finder.message("A seed search is already running; press Stop first.");
            return;
        }
        String key = Finder.connectionKey(mc), seed = finder.seeds.seed();
        if (mc.player == null || key == null) {
            refuse(key, "Join a world first", "Join a world first.", chat);
            return;
        }
        if (finder.seeds.typedVersion() == null && finder.serverVersion.asking(key)) {
            refuse(key, "Asking the server's version; retry", "Still asking the server which version it runs; search again in a few seconds.",
                chat);
            return;
        }
        String[] gen = finder.seeds.generationVersion();
        String target = gen[0];
        if (!Searchers.versions().contains(target)) {
            refuse(key, "No search for " + target + ": set World Version",
                "No seed search for " + target + " (" + gen[1] + "): " + Searchers.supported() + ". Pick one in World Version.", chat);
            return;
        }
        if (Versions.java(target) > Runtime.version().feature()) {
            refuse(key, "Needs Java " + Versions.java(target) + " for " + target, "A " + target + " world needs Java " + Versions.java(target)
                + " to generate; this game runs on Java " + Runtime.version().feature() + ".", chat);
            return;
        }
        if (seed == null) {
            refuse(key, "No seed: set World Seed", "No seed: set World Seed first.", chat);
            return;
        }
        radius = Math.max(MenuModel.MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        long available = availableMb();
        int servers = serversThatFit(available, radius);
        if (servers == 0) {
            int fits = 0;
            for (int r = MenuModel.MIN_RADIUS; r <= MAX_RADIUS; r += MenuModel.RADIUS_STEP) if (serversThatFit(available, r) > 0) fits = r;
            refuse(key, fits > 0 ? "Not enough memory: radius ≤ " + fits : "Not enough memory: needs " + (SearchRun.serverMb(MenuModel.MIN_RADIUS) + 1024) + " MB",
                "Not enough memory for radius " + radius + ": a search server needs " + SearchRun.serverMb(radius) + " MB plus 1024 MB to spare, "
                    + "and Search Memory and free memory allow " + available + " MB. "
                    + (fits > 0 ? "A Search Radius of " + fits + " fits." : "Close other programs or raise Search Memory."), chat);
            return;
        }
        boolean both = servers == 2;
        this.key = key;
        this.center = mc.player.blockPosition();
        this.radius = radius;
        this.together = both;
        this.startedAt = System.currentTimeMillis();
        hits.clear();
        lush.clear();
        results.remove(key);
        failed.clear();
        failures.clear();
        leftOut = null;
        spawn = null;
        missedLive = false;
        Setup mine = new Setup(++generation);
        setup = mine;
        Path gameDir = FabricLoader.getInstance().getGameDir();
        BlockPos at = center;
        int r = radius;
        Thread thread = new Thread(() -> {
            synchronized (SETUP) {
                try {
                    for (SearchRun old : killed) {
                        old.awaitExit();
                        killed.remove(old);
                    }
                    SearchRun.deleteFallbacks(gameDir);
                    if (mine.generation != generation) return;
                    if (!target.equals(Versions.client())) mine.phase = "downloading " + target;
                    Searchers.Launch launch = Searchers.prepare(gameDir.resolve("dripleaf-finder").resolve("cache"), target);
                    mine.phase = "starting";
                    SearchRun previous = null;
                    for (String order : SearchRun.ORDERS) {
                        if (mine.generation != generation) return;
                        mine.unstarted = order;
                        if (previous != null && previous.process.exitValue() == SearchRun.OUT_OF_MEMORY_EXIT) {
                            mine.error = "the " + previous.order + " search ran out of memory, and the " + order + " search would get the same heap. "
                                + "Raise Search Memory or lower Search Radius.";
                            return;
                        }
                        SearchRun run = SearchRun.start(gameDir, seed, order, at, r, order.equals("asc"), launch);
                        mine.launched.add(run);
                        mine.unstarted = null;
                        if (mine.generation != generation) kill(List.of(run));
                        while (!both && mine.generation == generation && run.process.isAlive()) Thread.sleep(500);
                        previous = both ? null : run;
                    }
                } catch (Exception e) {
                    mine.error = e.toString();
                } finally {
                    mine.complete = true;
                }
            }
        }, "Dripleaf seed search setup");
        thread.setDaemon(true);
        thread.start();
        if (chat) Finder.message("Seed search started for " + key + ": seed " + seed + ", " + target + ", radius " + radius + " around "
            + center.getX() + " " + center.getZ() + (both ? "." : ", one chunk order after the other (Search Memory allows one at a time)."));
    }

    private void refuse(String key, String status, String detail, boolean chat) {
        results.put(key, brief(status, STATUS_LENGTH));
        if (chat) Finder.message(detail);
    }

    private void fail(String order, String why, String detail) {
        failed.putIfAbsent(order, why);
        failures.add(detail);
    }

    String lastResult() {
        return results.get(Finder.connectionKey(Minecraft.getInstance()));
    }

    public String status() {
        Setup pending = setup;
        if (!running()) {
            String last = lastResult();
            return last == null ? "No search yet" : last;
        }
        SearchRun current = runs.stream().filter(run -> !run.finished).findFirst().orElse(null);
        int index = current != null ? runs.indexOf(current) : runs.size(), passes = SearchRun.ORDERS.size();
        String phase = current != null ? current.progress : pending != null && index < passes ? pending.phase : "saving";
        long seconds = (System.currentTimeMillis() - startedAt) / 1000;
        String pass = together || index >= passes ? "" : " " + (index + 1) + "/" + passes;
        return "Searching" + pass + ": " + phase + ", " + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60)
            + (hits.isEmpty() ? "" : ", " + hits.size() + " found");
    }

    static String brief(String text, int max) {
        text = text.replaceFirst("(?<=^|: )(?:[a-z_$][\\w$]*\\.)+(?=[A-Z][\\w$]*(?::|$))", "");
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    static int serversThatFit(long availableMb, int radius) {
        long server = SearchRun.serverMb(radius);
        return availableMb >= 2 * server + 1024 ? 2 : availableMb >= server + 1024 ? 1 : 0;
    }

    private long availableMb() {
        long free = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getFreeMemorySize() >> 20;
        if (!Files.exists(Path.of("/proc/meminfo")) && System.getProperty("os.name", "").startsWith("Mac")) free = Settings.TOTAL_MEMORY >> 21;
        try {
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (line.startsWith("MemAvailable:")) free = Long.parseLong(line.replaceAll("\\D", "")) >> 10;
            }
        } catch (IOException | NumberFormatException e) {
        }
        return Math.min(finder.settings.memoryGb() * 1024L, free);
    }

    void startOrQueue(Minecraft mc, String key) {
        if (running()) {
            pendingKey = key;
            Finder.message("It is searched when the running seed search ends.");
        } else {
            start(mc, finder.settings.radius, true);
        }
    }

    BlockPos center() {
        return center;
    }

    boolean running() {
        return setup != null || !runs.isEmpty();
    }

    boolean searching(String key) {
        return running() && key.equals(this.key);
    }

    public void stop(boolean fromCommand) {
        if (!running()) {
            if (fromCommand) Finder.message("No seed search is running.");
            return;
        }
        generation++;
        Setup pending = setup;
        setup = null;
        kill(runs);
        if (pending != null) kill(pending.launched);
        runs.clear();
        pendingKey = null;
        String saveError = hits.isEmpty() && lush.isEmpty() ? null : save(false);
        if (saveError != null) Finder.message(saveError);
        refuse(key, "Stopped", "Seed search stopped." + (hits.isEmpty() || saveError != null ? ""
            : " The " + hits.size() + " spliced top halves found so far are saved."), fromCommand);
    }

    void stopFor(String key, String status) {
        if (!searching(key)) return;
        stop(false);
        results.put(key, status);
    }

    private void kill(List<SearchRun> stopping) {
        for (SearchRun run : stopping) {
            run.kill();
            killed.add(run);
        }
    }

    void tick(Minecraft mc) {
        if (!running() || ticks++ % 20 != 0) return;
        Setup pending = setup;
        if (pending != null) {
            boolean complete = pending.complete;
            for (SearchRun run : pending.launched) if (!runs.contains(run)) runs.add(run);
            if (complete) {
                setup = null;
                if (pending.error != null && runs.isEmpty()) {
                    refuse(key, "Could not start: " + pending.error, "Seed search could not start: " + pending.error, true);
                    startPending(mc);
                    return;
                }
                if (pending.error != null) {
                    String order = pending.unstarted == null ? "search" : pending.unstarted;
                    fail(order, order + " did not start", "The " + order + " search did not start: " + pending.error);
                }
            }
        }
        for (SearchRun run : runs) {
            for (String line : run.newLines()) handle(mc, run, line);
            if (!run.finished && !run.process.isAlive()) {
                for (String line : run.newLines()) handle(mc, run, line);
                if (!run.finished) {
                    run.finished = true;
                    boolean memory = run.process.exitValue() == SearchRun.OUT_OF_MEMORY_EXIT;
                    fail(run.order, memory ? "out of memory" : run.order + " server stopped", "The " + run.order + " search server "
                        + (memory ? "ran out of memory" : "stopped without a result") + "; its log: " + run.dir.resolve("server.log"));
                }
            }
        }
        if (setup == null && runs.stream().allMatch(run -> run.finished)) finish(mc);
    }

    private void handle(Minecraft mc, SearchRun run, String line) {
        String[] f = line.split("\t");
        switch (f[0]) {
            case "status" -> {
                String[] w = f[1].split(" ");
                if (f[1].matches("(finding|scanning|measuring) .*")) run.progress = f[1];
                else if (f[1].startsWith("decorated ")) run.progress = "decorating " + 100 * Long.parseLong(w[1]) / Math.max(1, Long.parseLong(w[3])) + "%";
                else if (f[1].contains(" candidates among ")) run.progress = "checking " + w[0] + " candidates";
            }
            case "lush" -> lush.add(String.join("\t", f[1], f[2], f[3], f[4]));
            case "spawn" -> {
                spawn = new int[] {Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])};
                leftOut = " Left out: about " + f[3] + " blocks around the world spawn at " + f[1] + " " + f[2]
                    + ", which the search server generates at startup in no fixed order.";
            }
            case "hit" -> {
                BlockPos pos = new BlockPos(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
                String[] known = hits.get(pos);
                if (known != null) {
                    known[1] = "both";
                    return;
                }
                String first = f.length > 6 ? f[5] : "-", then = f.length > 6 ? f[6] : "-";
                hits.put(pos, new String[] {f[4], run.order, first, then});
                if (mc.player == null || !key.equals(Finder.connectionKey(mc))) {
                    missedLive = true;
                    return;
                }
                String guidance = Guidance.text(pos.getX(), pos.getZ(), f[4], first, then, finder.folia);
                finder.addPrediction(new Prediction(pos, "top", f[4], null, true, first, then));
                finder.announce("Spliced small dripleaf top half from the seed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + ", " + (int) Math.sqrt(pos.distSqr(mc.player.blockPosition())) + " blocks away: " + guidance);
            }
            case "done" -> {
                run.finished = true;
                run.ok = true;
            }
            case "error" -> {
                run.finished = true;
                fail(run.order, f[1], "Seed search failed (" + run.order + "): " + f[1]);
            }
            default -> {
            }
        }
    }

    private void finish(Minecraft mc) {
        boolean complete = failed.isEmpty(), done = runs.stream().anyMatch(run -> run.ok);
        for (SearchRun run : runs) run.deleteWorld();
        runs.clear();
        String saveError = save(complete);
        long both = hits.values().stream().filter(h -> h[1].equals("both")).count(), seconds = (System.currentTimeMillis() - startedAt) / 1000;
        if (done && saveError == null) {
            Finder.message("Seed search done in " + seconds + " s" + (complete ? "" : " (" + String.join(", ", failed.keySet()) + " failed)") + ": "
                + hits.size() + " spliced top halves (" + both + " in both chunk orders), " + lush.size() + " lush cave tiles; saved for " + key + "."
                + (leftOut == null ? "" : leftOut));
        }
        failures.forEach(Finder::message);
        if (saveError != null) Finder.message(saveError);
        boolean kept = !done && saveError == null && (!hits.isEmpty() || !lush.isEmpty());
        if (kept) {
            Finder.message("Kept what it found before that: " + hits.size() + " spliced top halves, " + lush.size() + " lush cave tiles; saved for "
                + key + ".");
        }
        String halves = hits.size() + (hits.size() == 1 ? " half, " : " halves, "), suffix = !kept ? ""
            : " (" + (hits.isEmpty() ? lush.size() + " lush" : hits.size()) + " kept)";
        results.put(key, !done ? brief("Failed: " + failed.values().iterator().next(), STATUS_LENGTH - suffix.length()) + suffix
            : saveError != null ? "Could not save results"
            : !complete ? "Done, " + String.join(", ", failed.keySet()) + " failed: " + halves + lush.size() + " lush"
            : "Done: " + halves + lush.size() + " lush tiles, " + seconds + " s");
        startPending(mc);
    }

    private String save(boolean replaceArea) {
        Path folder = Finder.DATA.resolve(key);
        try {
            Files.createDirectories(folder);
            if (!lush.isEmpty()) Files.write(folder.resolve("lush.tsv"), mergeLush(Data.lines(folder.resolve("lush.tsv")), lush));
            Files.write(folder.resolve("predictions.tsv"), mergePredictions(Data.lines(folder.resolve("predictions.tsv")), hits, center, radius, spawn,
                replaceArea));
        } catch (IOException e) {
            return "Could not save the search results: " + e.getMessage();
        }
        boolean here = key.equals(finder.memory.key());
        if (here) finder.reloadData();
        if (missedLive) finder.announceSavedOnReturn(key);
        else if (here) finder.markPredictionsAnnounced();
        return null;
    }

    private void startPending(Minecraft mc) {
        String next = pendingKey;
        pendingKey = null;
        if (next == null) return;
        if (next.equals(Finder.connectionKey(mc))) {
            start(mc, finder.settings.radius, true);
        } else {
            results.put(next, "Queued search dropped: you left");
            Finder.message("The seed search queued for " + next + " was dropped: you left that server. Press Seed Search there to run it.");
        }
    }

    static List<String> mergeLush(List<String> old, List<String> fresh) {
        Map<Long, long[]> tiles = new HashMap<>();
        for (String line : Stream.concat(old.stream(), fresh.stream()).toList()) {
            long[] f = numbers(line, 4);
            if (f == null) continue;
            long tile = Searcher.key(Math.floorDiv((int) f[0], 16) >> 3, Math.floorDiv((int) f[2], 16) >> 3);
            long[] best = tiles.get(tile);
            if (best == null || f[3] > best[3]) tiles.put(tile, f);
        }
        return tiles.values().stream().sorted(Comparator.comparingLong((long[] f) -> -f[3]))
            .map(f -> f[0] + "\t" + f[1] + "\t" + f[2] + "\t" + f[3]).toList();
    }

    static List<String> mergePredictions(List<String> old, Map<BlockPos, String[]> hits, BlockPos center, int radius, int[] spawn,
            boolean replaceArea) {
        List<String> lines = new ArrayList<>();
        for (String line : old) {
            long[] f = numbers(line, 3);
            if (f == null) continue;
            boolean inside = Math.abs(f[0] - center.getX()) <= radius && Math.abs(f[2] - center.getZ()) <= radius;
            boolean leftOut = spawn != null && Math.abs(f[0] - spawn[0]) <= spawn[2] && Math.abs(f[2] - spawn[1]) <= spawn[2];
            BlockPos pos = new BlockPos((int) f[0], (int) f[1], (int) f[2]);
            if ((!inside || !replaceArea || leftOut) && !hits.containsKey(pos)) lines.add(line);
        }
        hits.forEach((pos, h) -> lines.add(pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ() + "\ttop\t" + h[0] + "\t" + h[1] + "\tyes\t"
            + (int) Math.hypot(pos.getX() - center.getX(), pos.getZ() - center.getZ()) + "\t" + h[2] + "\t" + h[3]));
        return lines;
    }

    static long[] numbers(String line, int n) {
        String[] f = line.split("\t");
        if (f.length < n) return null;
        long[] out = new long[n];
        try {
            for (int i = 0; i < n; i++) out[i] = Long.parseLong(f[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
