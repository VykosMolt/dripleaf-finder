package dripleaffinder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;

final class SearchRun {
    static final int EXIT_WAIT_SECONDS = 30, DELETE_ATTEMPTS = 8, OUT_OF_MEMORY_EXIT = 3;
    static final List<String> ORDERS = List.of("asc", "desc");

    final String order;
    final Path dir;
    final Process process;
    boolean finished, ok;
    String progress = "starting";
    private long read;

    private SearchRun(String order, Path dir, Process process) {
        this.order = order;
        this.dir = dir;
        this.process = process;
    }

    static int heapMb(int radius) {
        return 1024 + radius / 3;
    }

    static int serverMb(int radius) {
        return heapMb(radius) + 800;
    }

    static Path searches(Path gameDir) {
        return gameDir.resolve("dripleaf-finder").resolve("search");
    }

    static SearchRun start(Path gameDir, String seed, String order, BlockPos center, int radius, boolean floors, Searchers.Launch launch)
            throws IOException {
        Path searches = searches(gameDir), dir = searches.resolve(order);
        try {
            delete(dir);
            Files.createDirectories(dir);
        } catch (IOException stillOpen) {
            dir = Files.createTempDirectory(searches, order + "-");
        }
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
        List<String> command = List.of(ProcessHandle.current().info().command().orElse("java"), "-Xmx" + heapMb(radius) + "M",
            "-XX:+ExitOnOutOfMemoryError", "-Dfabric.gameJarPath=" + launch.gameJar(),
            "-Dfabric.addMods=" + String.join(File.pathSeparator, launch.mods().stream().map(Path::toString).toList()),
            "-Ddripleaffinder.search=" + dir.resolve("search.properties"), "-cp", String.join(File.pathSeparator, launch.classpath()),
            "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui");
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true)
            .redirectOutput(dir.resolve("server.log").toFile()).start();
        return new SearchRun(order, dir, process);
    }

    static void deleteFallbacks(Path gameDir) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(searches(gameDir), "{asc,desc}-*")) {
            for (Path dir : dirs) {
                try {
                    deleteOnce(dir);
                } catch (IOException stillOpen) {
                }
            }
        } catch (IOException e) {
        }
    }

    void kill() {
        process.destroyForcibly();
    }

    void awaitExit() {
        try {
            process.waitFor(EXIT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void deleteWorld() {
        awaitExit();
        try {
            delete(dir.resolve("world"));
        } catch (IOException e) {
        }
    }

    private static void delete(Path path) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                deleteOnce(path);
                return;
            } catch (IOException e) {
                if (attempt == DELETE_ATTEMPTS) throw e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static void deleteOnce(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> all = Files.walk(path)) {
            for (Path p : all.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (UncheckedIOException e) {
            throw e.getCause();
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
