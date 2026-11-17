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
    public static final int DEFAULT_RADIUS = 1000, MAX_RADIUS = 4000;

    private final Finder finder;
    private final List<SearchRun> runs = new CopyOnWriteArrayList<>(), killed = new CopyOnWriteArrayList<>();
    final Map<BlockPos, String[]> hits = new LinkedHashMap<>();
    private final List<String> lush = new ArrayList<>();
    String lastResult;
    private String leftOut, failure;
    private static final Object SETUP = new Object();
    private volatile int generation;
    private volatile Setup setup;
    private boolean missedLive;
    private String key, pendingKey;
    private BlockPos center;
    private int radius, ticks;
    private boolean together, fromCommand;
    private long startedAt;

    private static final class Setup {
        final int generation;
        final List<SearchRun> launched = new CopyOnWriteArrayList<>();
        volatile boolean complete;
        volatile String error, phase = "starting";

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

    public void start(Minecraft mc, int radius, boolean fromCommand) {
        if (running()) {
            if (fromCommand) Finder.message("A seed search is already running; press Stop first.");
            return;
        }
        String key = Finder.connectionKey(mc), seed = finder.seeds.seed();
        if (mc.player == null || key == null) {
            refuse("Join a world first", "Join a world first.", fromCommand);
            return;
        }
        if (finder.seeds.typedVersion() == null && finder.serverVersion.asking(key)) {
            refuse("Asking the server's version; retry", "Still asking the server which version it runs; search again in a few seconds.",
                fromCommand);
            return;
        }
        String[] gen = finder.seeds.generationVersion();
        String target = gen[0];
        if (!Searchers.versions().contains(target)) {
            refuse("No search for " + target + ": set World Version",
                "No seed search for " + target + " (" + gen[1] + "): " + Searchers.supported() + ". Pick one in World Version.", fromCommand);
            return;
        }
        if (Versions.java(target) > Runtime.version().feature()) {
            refuse("Needs Java " + Versions.java(target) + " for " + target, "A " + target + " world needs Java " + Versions.java(target)
                + " to generate; this game runs on Java " + Runtime.version().feature() + ".", fromCommand);
            return;
        }
        if (seed == null) {
            refuse("No seed: set World Seed", "No seed: set World Seed first.", fromCommand);
            return;
        }
        radius = Math.max(MenuModel.MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        long available = availableMb();
        int servers = serversThatFit(available, radius);
        if (servers == 0) {
            int fits = 0;
            for (int r = MenuModel.MIN_RADIUS; r <= MAX_RADIUS; r += MenuModel.RADIUS_STEP) if (serversThatFit(available, r) > 0) fits = r;
            refuse(fits > 0 ? "Not enough memory: radius ≤ " + fits : "Not enough memory: needs " + (SearchRun.serverMb(MenuModel.MIN_RADIUS) + 1024) + " MB",
                "Not enough memory for radius " + radius + ": a search server needs " + SearchRun.serverMb(radius) + " MB plus 1024 MB to spare, "
                    + "and Search Memory and free memory allow " + available + " MB. "
                    + (fits > 0 ? "A Search Radius of " + fits + " fits." : "Close other programs or raise Search Memory."), fromCommand);
            return;
        }
        boolean both = servers == 2;
        this.key = key;
        this.center = mc.player.blockPosition();
        this.radius = radius;
        this.together = both;
        this.fromCommand = fromCommand;
        this.startedAt = System.currentTimeMillis();
        hits.clear();
        lush.clear();
        lastResult = null;
        failure = null;
        leftOut = null;
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
                    for (String order : SearchRun.ORDERS) {
                        if (mine.generation != generation) return;
                        SearchRun run = SearchRun.start(gameDir, seed, order, at, r, order.equals("asc"), launch);
                        mine.launched.add(run);
                        if (mine.generation != generation) kill(List.of(run));
                        while (!both && mine.generation == generation && run.process.isAlive()) Thread.sleep(500);
                    }
                } catch (Exception e) {
                    mine.error = e.toString();
                    kill(mine.launched);
                } finally {
                    mine.complete = true;
                }
            }
        }, "Dripleaf seed search setup");
        thread.setDaemon(true);
        thread.start();
    }

    private void refuse(String status, String detail, boolean chat) {
        lastResult = status;
        if (chat) Finder.message(detail);
    }

    private void fail(String status, String detail) {
        if (lastResult != null) return;
        lastResult = status;
        failure = detail;
    }

    public String status() {
        Setup pending = setup;
        if (!running()) return lastResult == null ? "No search yet" : lastResult;
        SearchRun current = runs.stream().filter(run -> !run.finished).findFirst().orElse(null);
        String phase = current != null ? current.progress : pending != null ? pending.phase : "saving";
        long seconds = (System.currentTimeMillis() - startedAt) / 1000;
        String pass = together || current == null ? "" : " " + (runs.indexOf(current) + 1) + "/2";
        return "Searching" + pass + ": " + phase + ", " + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60)
            + (hits.isEmpty() ? "" : ", " + hits.size() + " found");
    }

    static int serversThatFit(long availableMb, int radius) {
        long server = SearchRun.serverMb(radius);
        return availableMb >= 2 * server + 1024 ? 2 : availableMb >= server + 1024 ? 1 : 0;
    }

    private long availableMb() {
        long free = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getFreeMemorySize() >> 20;
        return Math.min(Math.max(1, finder.settings.memoryGb) * 1024L, free);
    }

    void startOrQueue(Minecraft mc, String key) {
        if (running()) {
            pendingKey = key;
            Finder.message("It is searched when the running seed search ends.");
        } else {
            start(mc, DEFAULT_RADIUS, false);
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

    public void stop() {
        if (!running()) {
            Finder.message("No seed search is running.");
            return;
        }
        generation++;
        Setup pending = setup;
        setup = null;
        kill(runs);
        if (pending != null) kill(pending.launched);
        runs.clear();
        pendingKey = null;
        lastResult = "Stopped";
        Finder.message("Seed search stopped.");
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
            if (pending.error != null) {
                setup = null;
                kill(runs);
                runs.clear();
                refuse("Could not start: " + pending.error, "Seed search could not start: " + pending.error, fromCommand);
                startPending(mc);
                return;
            }
            boolean complete = pending.complete;
            for (SearchRun run : pending.launched) if (!runs.contains(run)) runs.add(run);
            if (complete) setup = null;
        }
        for (SearchRun run : runs) {
            for (String line : run.newLines()) handle(mc, run, line);
            if (!run.finished && !run.process.isAlive()) {
                for (String line : run.newLines()) handle(mc, run, line);
                if (!run.finished) {
                    run.finished = true;
                    boolean memory = run.process.exitValue() == SearchRun.OUT_OF_MEMORY_EXIT;
                    fail("Failed: " + (memory ? "out of memory" : run.order + " server stopped"), "The " + run.order + " search server "
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
                if (f[1].startsWith("finding")) run.progress = f[1];
                else if (f[1].startsWith("decorated ")) run.progress = "decorating " + 100 * Long.parseLong(w[1]) / Math.max(1, Long.parseLong(w[3])) + "%";
                else if (f[1].contains(" candidates among ")) run.progress = "checking " + w[0] + " candidates";
            }
            case "lush" -> lush.add(String.join("\t", f[1], f[2], f[3], f[4]));
            case "spawn" -> leftOut = " Left out: about " + f[3] + " blocks around the world spawn at " + f[1] + " " + f[2]
                + ", which the search server generates at startup in no fixed order.";
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
                String guidance = Guidance.text(pos.getX(), pos.getZ(), f[4], first, then);
                finder.addPrediction(pos, "top half, " + guidance);
                finder.announce("Spliced small dripleaf top half from the seed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + ", " + (int) Math.sqrt(pos.distSqr(mc.player.blockPosition())) + " blocks away: " + guidance);
            }
            case "done" -> {
                run.finished = true;
                run.ok = true;
            }
            case "error" -> {
                run.finished = true;
                fail("Failed: " + f[1], "Seed search failed (" + run.order + "): " + f[1]);
            }
            default -> {
            }
        }
    }

    private void finish(Minecraft mc) {
        boolean complete = runs.stream().allMatch(run -> run.ok);
        for (SearchRun run : runs) run.deleteWorld();
        runs.clear();
        Path folder = Finder.DATA.resolve(key);
        try {
            Files.createDirectories(folder);
            if (!lush.isEmpty()) Files.write(folder.resolve("lush.tsv"), mergeLush(Data.lines(folder.resolve("lush.tsv")), lush));
            Files.write(folder.resolve("predictions.tsv"), mergePredictions(Data.lines(folder.resolve("predictions.tsv")), complete));
        } catch (IOException e) {
            fail("Could not save results", "Could not save the search results: " + e.getMessage());
        }
        long both = hits.values().stream().filter(h -> h[1].equals("both")).count(), seconds = (System.currentTimeMillis() - startedAt) / 1000;
        if (lastResult == null) {
            lastResult = "Done: " + hits.size() + (hits.size() == 1 ? " half, " : " halves, ") + lush.size() + " lush tiles, " + seconds + " s";
            Finder.message("Seed search done in " + seconds + " s: " + hits.size() + " spliced top halves (" + both
                + " in both chunk orders), " + lush.size() + " lush cave tiles; saved for " + key + "." + (leftOut == null ? "" : leftOut));
        } else {
            Finder.message(failure);
        }
        boolean here = key.equals(finder.memory.key());
        if (here) finder.reloadData();
        if (missedLive) finder.announceSavedOnReturn(key);
        else if (here) finder.markPredictionsAnnounced();
        startPending(mc);
    }

    private void startPending(Minecraft mc) {
        String next = pendingKey;
        pendingKey = null;
        if (next != null && next.equals(Finder.connectionKey(mc))) start(mc, DEFAULT_RADIUS, false);
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

    List<String> mergePredictions(List<String> old, boolean replaceArea) {
        List<String> lines = new ArrayList<>();
        for (String line : old) {
            long[] f = numbers(line, 3);
            if (f == null) continue;
            boolean inside = Math.abs(f[0] - center.getX()) <= radius && Math.abs(f[2] - center.getZ()) <= radius;
            BlockPos pos = new BlockPos((int) f[0], (int) f[1], (int) f[2]);
            if ((!inside || !replaceArea) && !hits.containsKey(pos)) lines.add(line);
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
