package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class SeedSearch {
    public static final int DEFAULT_RADIUS = 1000, MAX_RADIUS = 4000;

    private final Finder finder;
    private final List<SearchRun> runs = new CopyOnWriteArrayList<>();
    final Map<BlockPos, String[]> hits = new LinkedHashMap<>();
    private final List<String> lush = new ArrayList<>();
    String lastResult;
    private String leftOut;
    private static final Object SETUP = new Object();
    private volatile int generation;
    private volatile Setup setup;
    private boolean missedLive;
    private String key, pendingKey;
    private BlockPos center;
    private int radius, ticks;
    private long startedAt, progressAt;

    private static final class Setup {
        final int generation;
        volatile List<SearchRun> launched;
        volatile String error;

        Setup(int generation) {
            this.generation = generation;
        }
    }

    SeedSearch(Finder finder) {
        this.finder = finder;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runs.forEach(SearchRun::kill);
            Setup pending = setup;
            if (pending != null && pending.launched != null) pending.launched.forEach(SearchRun::kill);
        }));
    }

    public void start(Minecraft mc, int radius) {
        if (running()) {
            Finder.message("A seed search is already running (/dripleaf search stop).");
            return;
        }
        String key = Finder.connectionKey(mc), seed = finder.seeds.seed();
        if (mc.player == null || key == null) {
            Finder.message("Join a world first.");
            return;
        }
        if (finder.seeds.typedVersion() == null && finder.serverVersion.asking(key)) {
            Finder.message("Still asking the server which version it runs; search again in a few seconds.");
            return;
        }
        String[] gen = finder.seeds.generationVersion();
        String target = gen[0];
        if (target.startsWith("1.17")) {
            lastResult = "Lush caves only generate from 1.18 on, so a " + target + " seed has no spliced dripleaf to find.";
            Finder.message(lastResult);
            return;
        }
        if (!Versions.known(target)) {
            Finder.message("No seed search for " + target + " (" + gen[1] + "): set the world's version with /dripleaf version <version>.");
            return;
        }
        if (Versions.java(target) > Runtime.version().feature()) {
            Finder.message("A " + target + " world needs Java " + Versions.java(target) + " to generate; this game runs on Java "
                + Runtime.version().feature() + ".");
            return;
        }
        if (seed == null) {
            Finder.message("Set the world seed first: /dripleaf seed <seed>");
            return;
        }
        this.key = key;
        this.center = mc.player.blockPosition();
        this.radius = Math.max(64, Math.min(MAX_RADIUS, radius));
        this.startedAt = System.currentTimeMillis();
        this.progressAt = startedAt;
        hits.clear();
        lush.clear();
        lastResult = null;
        leftOut = null;
        missedLive = false;
        Setup mine = new Setup(++generation);
        setup = mine;
        Path gameDir = FabricLoader.getInstance().getGameDir();
        BlockPos at = center;
        int r = this.radius;
        Thread thread = new Thread(() -> {
            synchronized (SETUP) {
                List<SearchRun> started = new ArrayList<>();
                try {
                    if (mine.generation != generation) return;
                    Path cache = gameDir.resolve("dripleaf-finder").resolve("cache");
                    Searchers.Launch launch = Searchers.prepare(cache, target);
                    started.add(SearchRun.start(gameDir, seed, "asc", at, r, true, launch));
                    started.add(SearchRun.start(gameDir, seed, "desc", at, r, false, launch));
                } catch (Exception e) {
                    mine.error = e.toString();
                }
                if (mine.error != null || mine.generation != generation) {
                    started.forEach(SearchRun::kill);
                    return;
                }
                mine.launched = started;
                if (mine.generation != generation) started.forEach(SearchRun::kill);
            }
        }, "Dripleaf seed search setup");
        thread.setDaemon(true);
        thread.start();
        Finder.message("Seed search around " + center.getX() + " " + center.getZ() + ", radius " + this.radius + ", " + target
            + " world generation (" + gen[1] + "): starting two background servers (one per chunk order)."
            + (target.equals(Versions.client()) ? "" : " The first search for " + target + " downloads its server from Mojang."));
    }

    void startOrQueue(Minecraft mc, String key) {
        if (running()) {
            pendingKey = key;
            Finder.message("It is searched when the running seed search ends.");
        } else {
            start(mc, DEFAULT_RADIUS);
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
        runs.forEach(SearchRun::kill);
        if (pending != null && pending.launched != null) pending.launched.forEach(SearchRun::kill);
        runs.clear();
        pendingKey = null;
        Finder.message("Seed search stopped.");
    }

    void tick(Minecraft mc) {
        if (!running() || ticks++ % 20 != 0) return;
        Setup pending = setup;
        if (pending != null) {
            if (pending.error != null) {
                setup = null;
                lastResult = "Seed search could not start: " + pending.error;
                Finder.message(lastResult);
                startPending(mc);
                return;
            }
            if (pending.launched == null) return;
            runs.addAll(pending.launched);
            setup = null;
        }
        for (SearchRun run : runs) {
            for (String line : run.newLines()) handle(mc, run, line);
            if (!run.finished && !run.process.isAlive()) {
                for (String line : run.newLines()) handle(mc, run, line);
                if (!run.finished) {
                    run.finished = true;
                    lastResult = "The " + run.order + " search server stopped without a result; its log: " + run.dir.resolve("server.log");
                    Finder.message(lastResult);
                }
            }
        }
        if (runs.stream().allMatch(run -> run.finished)) finish(mc);
    }

    private void handle(Minecraft mc, SearchRun run, String line) {
        String[] f = line.split("\t");
        switch (f[0]) {
            case "status" -> {
                boolean progress = f[1].startsWith("decorated");
                if (run.order.equals("desc") || progress && System.currentTimeMillis() - progressAt < 30_000) return;
                if (progress) progressAt = System.currentTimeMillis();
                Finder.message("Seed search: " + f[1]);
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
                lastResult = "Seed search failed (" + run.order + "): " + f[1];
                Finder.message(lastResult);
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
            lastResult = "Could not save the search results: " + e.getMessage();
        }
        long both = hits.values().stream().filter(h -> h[1].equals("both")).count();
        if (lastResult == null) {
            lastResult = "Seed search done in " + (System.currentTimeMillis() - startedAt) / 1000 + " s: " + hits.size()
                + " spliced top halves (" + both + " in both chunk orders), " + lush.size() + " lush cave tiles; saved for " + key + "."
                + (leftOut == null ? "" : leftOut);
        }
        Finder.message(lastResult);
        boolean here = key.equals(finder.memory.key());
        if (here) finder.reloadData();
        if (missedLive) finder.announceSavedOnReturn(key);
        else if (here) finder.markPredictionsAnnounced();
        startPending(mc);
    }

    private void startPending(Minecraft mc) {
        String next = pendingKey;
        pendingKey = null;
        if (next != null && next.equals(Finder.connectionKey(mc))) start(mc, DEFAULT_RADIUS);
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
