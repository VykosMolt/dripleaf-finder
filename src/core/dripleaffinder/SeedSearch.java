package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

final class SeedSearch {
    static final int DEFAULT_RADIUS = 1000, MAX_RADIUS = 4000;
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final Pattern LOADER_JARS = Pattern.compile(
        "(fabric-loader|sponge-mixin|asm|asm-analysis|asm-commons|asm-tree|asm-util|mixinextras-fabric|mixinextras-common)-.*\\.jar");
    static final Pattern INTERMEDIARY = Pattern.compile("intermediary-.*\\.jar");

    private final Finder finder;
    private final List<Run> runs = new CopyOnWriteArrayList<>();
    final Map<BlockPos, String[]> hits = new LinkedHashMap<>();
    private final List<String> lush = new ArrayList<>();
    private final Map<String, String> detected = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> asking = java.util.concurrent.ConcurrentHashMap.newKeySet();
    String lastResult;
    private static final Object SETUP = new Object();
    private volatile int generation;
    private volatile Setup setup;
    private boolean missedLive;
    private String key, pendingKey;
    private BlockPos center;
    private int radius, ticks, idleTicks;
    private String joinedKey;
    private long startedAt, progressAt;

    private static final class Setup {
        final int generation;
        volatile List<Run> launched;
        volatile String error;

        Setup(int generation) {
            this.generation = generation;
        }
    }

    SeedSearch(Finder finder) {
        this.finder = finder;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            kill(runs);
            Setup pending = setup;
            if (pending != null && pending.launched != null) kill(pending.launched);
        }));
    }

    static String version() {
        return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
    }

    private static String key() {
        return Finder.connectionKey(Minecraft.getInstance());
    }

    private static Path file(String key, String name) {
        return key == null ? null : Finder.DATA.resolve(key).resolve(name);
    }

    private static void write(String key, String name, String value) throws IOException {
        Files.createDirectories(file(key, name).getParent());
        if (value == null) Files.deleteIfExists(file(key, name));
        else Files.writeString(file(key, name), value + "\n");
    }

    String seed() {
        return seed(key());
    }

    private static String seed(String key) {
        String typed = Data.read(file(key, "seed.txt"));
        return typed != null ? typed : Data.read(file(key, "seedcracker.txt"));
    }

    private void seedChanged(String key, String before) {
        if (java.util.Objects.equals(before, seed(key))) return;
        if (running() && key.equals(this.key)) {
            stop();
            Finder.message("(It was for seed " + before + ".)");
        }
        Path saved = Finder.DATA.resolve(key).resolve("predictions.tsv");
        try {
            if (before != null && Files.exists(saved)) {
                Files.move(saved, saved.resolveSibling("predictions-" + before + ".tsv"), StandardCopyOption.REPLACE_EXISTING);
                Finder.message("Predictions from seed " + before + " set aside (predictions-" + before + ".tsv).");
            }
        } catch (IOException e) {
            Finder.message("Could not set aside the predictions of seed " + before + ": " + e.getMessage());
        }
        if (key.equals(finder.dataKey())) finder.reloadData();
    }

    String typedSeed() {
        return Data.read(file(key(), "seed.txt"));
    }

    void setSeed(String seed) {
        String key = key();
        if (key == null) {
            Finder.message("Join a world first: the seed is kept per server.");
            return;
        }
        try {
            String before = seed(key);
            write(key, "seed.txt", seed);
            seedChanged(key, before);
            String cracked = Data.read(file(key, "seedcracker.txt"));
            Finder.message(seed == null ? "Typed seed cleared for " + key + (cracked != null ? "; SeedcrackerX's " + cracked + " is used." : ".")
                : "Seed " + seed + " saved for " + key + ". /dripleaf search looks around you (radius " + DEFAULT_RADIUS
                    + ", or /dripleaf search <radius>).");
        } catch (IOException e) {
            Finder.message("Could not save the seed: " + e.getMessage());
        }
    }

    void seedFound(String seed, String source) {
        Minecraft mc = Minecraft.getInstance();
        String key = Finder.connectionKey(mc);
        if (key == null) return;
        String before = seed(), typed = typedSeed();
        try {
            write(key, "seedcracker.txt", seed);
        } catch (IOException e) {
            Finder.message("Could not save " + source + "'s seed: " + e.getMessage());
            return;
        }
        if (typed != null && !typed.equals(seed)) {
            Finder.message(source + " found the world seed " + seed + "; the seed typed for " + key + " (" + typed
                + ") stays in use (/dripleaf seed clear to use " + source + "'s).");
            return;
        }
        if (seed.equals(before)) {
            Finder.message(source + " found the world seed " + seed + " (already the seed for " + key + ").");
            return;
        }
        Finder.message(source + " found the world seed " + seed + ".");
        seedChanged(key, before);
        if (running()) {
            pendingKey = key;
            Finder.message("It is searched when the running seed search ends.");
        } else {
            start(mc, DEFAULT_RADIUS);
        }
    }

    void showSeed() {
        String key = key(), typed = typedSeed(), cracked = Data.read(file(key, "seedcracker.txt"));
        Finder.message(key == null ? "Join a world first." : typed == null && cracked == null ? "No seed for " + key + ". /dripleaf seed <seed>"
            : "Seed for " + key + ": " + seed() + (typed != null ? " (typed" + (cracked != null && !cracked.equals(typed)
                ? "; SeedcrackerX found " + cracked : "") + ")" : " (from SeedcrackerX)"));
    }

    String[] generationVersion() {
        String key = key(), typed = Data.read(file(key, "version.txt"));
        if (typed != null) return new String[] {typed, "typed"};
        String server = key == null ? null : detected.get(key);
        if (server != null && !server.isEmpty()) return new String[] {server, "reported by the server"};
        return new String[] {version(), server == null ? "this client's" : "this client's: the server did not say which; /dripleaf version sets it"};
    }

    String typedVersion() {
        return Data.read(file(key(), "version.txt"));
    }

    void setVersion(String version) {
        String key = key();
        if (key == null) {
            Finder.message("Join a world first: the version is kept per server.");
            return;
        }
        if (version != null && version.equalsIgnoreCase("auto")) version = null;
        if (version != null && !Versions.known(version)) {
            Finder.message("Unknown version " + version + " (1.17 to 26.2).");
            return;
        }
        try {
            write(key, "version.txt", version);
            showVersion();
        } catch (IOException e) {
            Finder.message("Could not save the version: " + e.getMessage());
        }
    }

    void showVersion() {
        String[] v = generationVersion();
        Finder.message("Seed searches use " + v[0] + " world generation (" + v[1] + "). /dripleaf version <version|auto>");
    }

    void joined(Minecraft mc, String key, String address) {
        if (address == null || key == null || detected.containsKey(key) || !asking.add(key)) return;
        Thread thread = new Thread(() -> {
            try {
                String version = ServerVersion.detect(address);
                detected.put(key, version == null ? "" : version);
            } finally {
                asking.remove(key);
            }
        }, "Dripleaf server version");
        thread.setDaemon(true);
        thread.start();
    }

    void start(Minecraft mc, int radius) {
        if (running()) {
            Finder.message("A seed search is already running (/dripleaf search stop).");
            return;
        }
        String key = Finder.connectionKey(mc), seed = seed();
        if (mc.player == null || key == null) {
            Finder.message("Join a world first.");
            return;
        }
        if (typedVersion() == null && asking.contains(key)) {
            Finder.message("Still asking the server which version it runs; search again in a few seconds.");
            return;
        }
        String[] gen = generationVersion();
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
        missedLive = false;
        Setup mine = new Setup(++generation);
        setup = mine;
        Path gameDir = FabricLoader.getInstance().getGameDir();
        BlockPos at = center;
        int r = this.radius;
        Thread thread = new Thread(() -> {
            synchronized (SETUP) {
                List<Run> started = new ArrayList<>();
                try {
                    if (mine.generation != generation) return;
                    Path cache = gameDir.resolve("dripleaf-finder").resolve("cache");
                    Searchers.Launch launch = Searchers.prepare(cache, target);
                    started.add(Run.start(gameDir, seed, "asc", at, r, true, launch));
                    started.add(Run.start(gameDir, seed, "desc", at, r, false, launch));
                } catch (Exception e) {
                    mine.error = e.toString();
                }
                if (mine.error != null || mine.generation != generation) {
                    kill(started);
                    return;
                }
                mine.launched = started;
                if (mine.generation != generation) kill(started);
            }
        }, "Dripleaf seed search setup");
        thread.setDaemon(true);
        thread.start();
        Finder.message("Seed search around " + center.getX() + " " + center.getZ() + ", radius " + this.radius + ", " + target
            + " world generation (" + gen[1] + "): starting two background servers (one per chunk order). The first search for a"
            + " version downloads its server from Mojang.");
    }

    boolean running() {
        return setup != null || !runs.isEmpty();
    }

    void stop() {
        if (!running()) {
            Finder.message("No seed search is running.");
            return;
        }
        generation++;
        Setup pending = setup;
        setup = null;
        kill(runs);
        if (pending != null && pending.launched != null) kill(pending.launched);
        runs.clear();
        pendingKey = null;
        Finder.message("Seed search stopped.");
    }

    private static void kill(List<Run> list) {
        for (Run run : list) {
            run.process.destroyForcibly();
            try {
                run.process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void tick(Minecraft mc) {
        if (idleTicks++ % 20 == 0) {
            String now = Finder.connectionKey(mc);
            if (now != null && !now.equals(joinedKey)) joined(mc, now, Compat.serverAddress(mc));
            joinedKey = now;
        }
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
        for (Run run : runs) {
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

    private void handle(Minecraft mc, Run run, String line) {
        String[] f = line.split("\t");
        switch (f[0]) {
            case "status" -> {
                boolean progress = f[1].startsWith("decorated");
                if (run.order.equals("desc") || progress && System.currentTimeMillis() - progressAt < 30_000) return;
                if (progress) progressAt = System.currentTimeMillis();
                Finder.message("Seed search: " + f[1]);
            }
            case "lush" -> lush.add(String.join("\t", f[1], f[2], f[3], f[4]));
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
        for (Run run : runs) run.deleteWorld();
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
                + " spliced top halves (" + both + " in both chunk orders), " + lush.size() + " lush cave tiles; saved for " + key + ".";
        }
        Finder.message(lastResult);
        boolean here = key.equals(finder.dataKey());
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

    static Path serverJar(Path cache, String version) throws IOException {
        Path jar = cache.resolve("server.jar"), sha = cache.resolve("server.jar.sha1");
        if (Files.exists(jar) && Files.exists(sha) && Files.readString(sha).trim().equals(sha1(jar))) return jar;
        String url = null;
        for (JsonElement entry : json(MANIFEST).getAsJsonArray("versions")) {
            if (entry.getAsJsonObject().get("id").getAsString().equals(version)) url = entry.getAsJsonObject().get("url").getAsString();
        }
        if (url == null) throw new IOException("Mojang's version list has no " + version);
        JsonObject server = json(url).getAsJsonObject("downloads").getAsJsonObject("server");
        String expected = server.get("sha1").getAsString();
        Files.createDirectories(cache);
        Path part = cache.resolve("server.jar.part");
        try (InputStream in = get(server.get("url").getAsString())) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha1(part).equals(expected)) {
            Files.delete(part);
            throw new IOException("the downloaded server jar does not match Mojang's SHA-1");
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(sha, expected + "\n");
        return jar;
    }

    static JsonObject json(String url) throws IOException {
        try (InputStream in = get(url)) {
            return new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    static InputStream get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        if (connection.getResponseCode() != 200) throw new IOException(url + ": HTTP " + connection.getResponseCode());
        return connection.getInputStream();
    }

    static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static final class Run {
        final String order;
        final Path dir;
        final Process process;
        boolean finished, ok;
        private long read;

        private Run(String order, Path dir, Process process) {
            this.order = order;
            this.dir = dir;
            this.process = process;
        }

        static Run start(Path gameDir, String seed, String order, BlockPos center, int radius, boolean floors, Searchers.Launch launch)
                throws IOException {
            Path dir = gameDir.resolve("dripleaf-finder").resolve("search").resolve(order);
            delete(dir);
            Files.createDirectories(dir);
            Properties server = new Properties();
            server.setProperty("level-seed", seed);
            for (String[] kv : new String[][] {{"online-mode", "false"}, {"server-ip", "127.0.0.1"}, {"server-port", "0"},
                    {"enable-status", "false"}, {"enable-query", "false"}, {"enable-rcon", "false"}, {"max-tick-time", "-1"},
                    {"pause-when-empty-seconds", "-1"}, {"view-distance", "2"}, {"simulation-distance", "2"}, {"spawn-protection", "0"},
                    {"sync-chunk-writes", "false"}, {"spawn-monsters", "false"}, {"spawn-animals", "false"}, {"white-list", "true"}}) {
                server.setProperty(kv[0], kv[1]);
            }
            try (Writer out = Files.newBufferedWriter(dir.resolve("server.properties"), StandardCharsets.UTF_8)) {
                server.store(out, "Dripleaf Finder seed search (throwaway world)");
            }
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
            Properties spec = new Properties();
            spec.setProperty("out", dir.resolve("results.tsv").toString());
            spec.setProperty("x", String.valueOf(center.getX()));
            spec.setProperty("z", String.valueOf(center.getZ()));
            spec.setProperty("radius", String.valueOf(radius));
            spec.setProperty("order", order);
            spec.setProperty("floors", String.valueOf(floors));
            spec.setProperty("splices", "true");
            try (Writer out = Files.newBufferedWriter(dir.resolve("search.properties"), StandardCharsets.UTF_8)) {
                spec.store(out, null);
            }
            Files.writeString(dir.resolve("results.tsv"), "");
            List<String> command = List.of(ProcessHandle.current().info().command().orElse("java"), "-Xmx" + (1536 + radius) + "M",
                "-Dfabric.gameJarPath=" + launch.serverJar(), "-Dfabric.addMods=" + String.join(File.pathSeparator, launch.mods().stream().map(Path::toString).toList()),
                "-Ddripleaffinder.search=" + dir.resolve("search.properties"), "-cp", String.join(File.pathSeparator, launch.classpath()),
                "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui");
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(dir.resolve("server.log").toFile()).start();
            return new Run(order, dir, process);
        }

        void deleteWorld() {
            try {
                process.waitFor(5, TimeUnit.SECONDS);
                delete(dir.resolve("world"));
            } catch (IOException e) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        static void delete(Path path) throws IOException {
            if (!Files.exists(path)) return;
            try (Stream<Path> all = Files.walk(path)) {
                for (Path p : all.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }

        List<String> newLines() {
            List<String> lines = new ArrayList<>();
            try (RandomAccessFile file = new RandomAccessFile(dir.resolve("results.tsv").toFile(), "r")) {
                if (file.length() <= read) return lines;
                byte[] bytes = new byte[(int) (file.length() - read)];
                file.seek(read);
                file.readFully(bytes);
                String text = new String(bytes, StandardCharsets.UTF_8);
                int end = text.lastIndexOf('\n');
                if (end < 0) return lines;
                read += text.substring(0, end + 1).getBytes(StandardCharsets.UTF_8).length;
                for (String line : text.substring(0, end).split("\n")) if (!line.isEmpty()) lines.add(line);
            } catch (IOException e) {
            }
            return lines;
        }
    }
}
