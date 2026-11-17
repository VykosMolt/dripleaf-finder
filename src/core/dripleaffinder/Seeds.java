package dripleaffinder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class Seeds {
    private final Finder finder;
    private final Map<String, String> cached = new HashMap<>();
    private String cachedKey;

    Seeds(Finder finder) {
        this.finder = finder;
    }

    private static String key() {
        return Finder.connectionKey(Minecraft.getInstance());
    }

    private static Path file(String key, String name) {
        return key == null ? null : Finder.DATA.resolve(key).resolve(name);
    }

    private String read(String key, String name) {
        if (key == null) {
            cachedKey = null;
            return null;
        }
        if (!key.equals(cachedKey)) {
            cachedKey = key;
            cached.clear();
        }
        if (!cached.containsKey(name)) cached.put(name, Data.read(file(key, name)));
        return cached.get(name);
    }

    private void write(String key, String name, String value) throws IOException {
        Path target = file(key, name);
        Files.createDirectories(target.getParent());
        if (value == null) Files.deleteIfExists(target);
        else Data.write(target, List.of(value));
        if (key.equals(cachedKey)) cached.put(name, Data.read(file(key, name)));
    }

    String seed() {
        return seed(key());
    }

    private String seed(String key) {
        String typed = read(key, "seed.txt");
        return typed != null ? typed : read(key, "seedcracker.txt");
    }

    Path resultsFolder() {
        String key = key();
        Minecraft mc = Minecraft.getInstance();
        if (key != null && typedVersion() == null && Compat.serverAddress(mc) != null && !finder.serverVersion.ready(key, mc.getConnection())) return null;
        return key == null ? null : resultsFolder(Finder.DATA.resolve(key), seed(key), generationVersion()[0]);
    }

    static Path resultsFolder(Path server, String seed, String version) {
        if (server == null || seed == null || seed.isBlank() || !Versions.known(version)) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return server.resolve("generations").resolve(HexFormat.of().formatHex(digest, 0, 16)).resolve(version);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void change(String key, String name, String value, boolean changed, String from) throws IOException {
        if (changed || name.equals("version.txt")) finder.search.stopFor(key, "Stopped: " + (name.equals("version.txt") ? "version" : "seed") + " changed");
        Path folder = Finder.DATA.resolve(key);
        String before = seed(key);
        List<Path> moved = changed && before != null ? setAside(folder, before) : List.of();
        try {
            write(key, name, value);
        } catch (IOException e) {
            restore(moved, e);
            throw e;
        }
        if (!moved.isEmpty()) Finder.message("Predictions and lush caves of " + from + " set aside in " + folder + ".");
        if (changed) {
            finder.search.metadataChanged(key);
            if (key.equals(finder.memory.key())) finder.resetPredictions();
        }
    }

    static List<Path> setAside(Path folder, String seed) throws IOException {
        String suffix = "";
        for (int n = 2; Files.exists(folder.resolve(asideName("predictions", seed) + suffix + ".tsv"))
                || Files.exists(folder.resolve(asideName("lush", seed) + suffix + ".tsv")); n++) {
            suffix = "-" + n;
        }
        List<Path> moved = new ArrayList<>();
        try {
            for (String file : List.of("predictions", "lush")) {
                Path saved = folder.resolve(file + ".tsv");
                if (Files.exists(saved)) moved.add(Files.move(saved, folder.resolve(asideName(file, seed) + suffix + ".tsv")));
            }
        } catch (IOException e) {
            restore(moved, e);
            throw e;
        }
        return moved;
    }

    private static void restore(List<Path> moved, IOException failure) {
        for (Path aside : moved) {
            String name = aside.getFileName().toString().split("-", 2)[0];
            try {
                Files.move(aside, aside.resolveSibling(name + ".tsv"));
            } catch (IOException e) {
                failure.addSuppressed(e);
            }
        }
    }

    static String asideName(String file, String seed) {
        String text = seed.replaceAll("[^A-Za-z0-9-]", "");
        text = text.substring(0, Math.min(32, text.length()));
        try {
            byte[] sha = MessageDigest.getInstance("SHA-1").digest(seed.getBytes(StandardCharsets.UTF_8));
            return file + "-" + (text.isEmpty() ? "" : text + "-") + HexFormat.of().formatHex(sha, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String typedSeed() {
        return read(key(), "seed.txt");
    }

    public void setSeed(String seed) {
        String key = key();
        if (key == null) {
            Finder.message("Join a world first: the seed is kept per server.");
            return;
        }
        if (Objects.equals(seed, read(key, "seed.txt"))) return;
        try {
            String before = seed(key);
            String after = seed != null ? seed : read(key, "seedcracker.txt");
            change(key, "seed.txt", seed, !Objects.equals(before, after), "the previous seed");
            String cracked = read(key, "seedcracker.txt");
            Finder.message(seed == null ? "World Seed cleared for " + key + (cracked != null ? "; SeedcrackerX's seed is used." : ".")
                : "World Seed saved for " + key + ".");
        } catch (IOException e) {
            Finder.message("Could not save the seed (" + e.getClass().getSimpleName() + ").");
        }
    }

    void seedFound(String seed, String source) {
        Minecraft mc = Minecraft.getInstance();
        String key = Finder.connectionKey(mc);
        if (key == null) return;
        String before = seed(), typed = typedSeed();
        try {
            change(key, "seedcracker.txt", seed, typed == null && !Objects.equals(before, seed), "the previous seed");
        } catch (IOException e) {
            Finder.message("Could not save " + source + "'s seed (" + e.getClass().getSimpleName() + ").");
            return;
        }
        if (typed != null && !typed.equals(seed)) {
            Finder.message(source + " found the world seed, which differs from the typed seed; the typed seed for " + key
                + " stays in use (clear World Seed to use " + source + "'s).");
            return;
        }
        if (seed.equals(before)) {
            Finder.message(source + " found the world seed (already the seed for " + key + ").");
            return;
        }
        Finder.message(source + " found the world seed.");
        finder.search.startOrQueue(mc, key);
    }

    void showSeed() {
        String key = key(), typed = typedSeed(), cracked = read(key, "seedcracker.txt");
        Finder.message(key == null ? "Join a world first." : typed == null && cracked == null ? "No seed for " + key + ". Set World Seed."
            : "Seed for " + key + ": " + (typed != null ? "the typed seed" + (cracked != null && !cracked.equals(typed)
                ? " (SeedcrackerX found a different one)" : "") : "SeedcrackerX's seed") + ".");
    }

    String[] generationVersion() {
        String key = key();
        return generationVersion(key, read(key, "version.txt"));
    }

    private String[] generationVersion(String key, String typed) {
        if (typed != null) return new String[] {typed, "typed"};
        String server = key == null ? null : finder.serverVersion.reported(key);
        if (server != null && !server.isEmpty()) return new String[] {server, "reported by the server"};
        return new String[] {Versions.client(), server == null ? "this client's"
            : "this client's: the server did not say which; World Version sets it"};
    }

    public String typedVersion() {
        return read(key(), "version.txt");
    }

    public void setVersion(String version) {
        String key = key();
        if (key == null) {
            Finder.message("Join a world first: the version is kept per server.");
            return;
        }
        if (version != null && version.equalsIgnoreCase("auto")) version = null;
        if (version != null && !Searchers.versions().contains(version)) {
            Finder.message("No seed search for " + version + ": " + Searchers.supported() + ".");
            return;
        }
        if (Objects.equals(version, read(key, "version.txt"))) return;
        try {
            String before = generationVersion()[0];
            change(key, "version.txt", version, !before.equals(generationVersion(key, version)[0]), "the previous World Version");
            showVersion();
        } catch (IOException e) {
            Finder.message("Could not save the version (" + e.getClass().getSimpleName() + ").");
        }
    }

    void showVersion() {
        String[] v = generationVersion();
        Finder.message("Seed searches use " + v[0] + " world generation (" + v[1] + ").");
    }

    static String brand() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.serverBrand();
    }

    String[] serverType() {
        String typed = typedServerType(), brand = brand();
        if (typed != null) return new String[] {typed, "chosen"};
        return new String[] {ServerType.fromBrand(brand), brand == null ? "no brand announced" : "brand \"" + brand + "\""};
    }

    public String typedServerType() {
        return read(key(), "server-type.txt");
    }

    public void setServerType(String type) {
        String key = key();
        if (key == null) {
            Finder.message("Join a world first: the server type is kept per server.");
            return;
        }
        if (type != null && !ServerType.TYPES.contains(type)) {
            Finder.message("No server type " + type + ": " + String.join(", ", ServerType.TYPES) + ".");
            return;
        }
        if (Objects.equals(type, read(key, "server-type.txt"))) return;
        try {
            write(key, "server-type.txt", type);
        } catch (IOException e) {
            Finder.message("Could not save the server type: " + e.getMessage());
            return;
        }
        String[] t = serverType();
        Finder.message("Server Type for " + key + ": " + t[0] + " (" + t[1] + ").");
    }
}
