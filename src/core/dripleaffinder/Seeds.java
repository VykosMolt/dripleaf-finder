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
        Files.createDirectories(file(key, name).getParent());
        if (value == null) Files.deleteIfExists(file(key, name));
        else Files.writeString(file(key, name), value + "\n");
        if (key.equals(cachedKey)) cached.put(name, Data.read(file(key, name)));
    }

    String seed() {
        return seed(key());
    }

    private String seed(String key) {
        String typed = read(key, "seed.txt");
        return typed != null ? typed : read(key, "seedcracker.txt");
    }

    private void seedChanged(String key, String before) {
        if (Objects.equals(before, seed(key))) return;
        finder.search.stopFor(key, "Stopped: seed changed");
        setAside(key, before, "the previous seed");
    }

    private void setAside(String key, String seed, String from) {
        Path folder = Finder.DATA.resolve(key);
        try {
            if (seed != null && !setAside(folder, seed).isEmpty()) Finder.message("Predictions and lush caves of " + from + " set aside in " + folder + ".");
        } catch (IOException e) {
            Finder.message("Could not set aside the predictions and lush caves of " + from + ": " + e.getMessage());
        }
        if (key.equals(finder.memory.key())) finder.reloadData();
    }

    static List<Path> setAside(Path folder, String seed) throws IOException {
        String suffix = "";
        for (int n = 2; Files.exists(folder.resolve(asideName("predictions", seed) + suffix + ".tsv"))
                || Files.exists(folder.resolve(asideName("lush", seed) + suffix + ".tsv")); n++) {
            suffix = "-" + n;
        }
        List<Path> moved = new ArrayList<>();
        for (String file : List.of("predictions", "lush")) {
            Path saved = folder.resolve(file + ".tsv");
            if (Files.exists(saved)) moved.add(Files.move(saved, folder.resolve(asideName(file, seed) + suffix + ".tsv")));
        }
        return moved;
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
            write(key, "seed.txt", seed);
            seedChanged(key, before);
            String cracked = read(key, "seedcracker.txt");
            Finder.message(seed == null ? "World Seed cleared for " + key + (cracked != null ? "; SeedcrackerX's " + cracked + " is used." : ".")
                : "Seed " + seed + " saved for " + key + ".");
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
                + ") stays in use (clear World Seed to use " + source + "'s).");
            return;
        }
        if (seed.equals(before)) {
            Finder.message(source + " found the world seed " + seed + " (already the seed for " + key + ").");
            return;
        }
        Finder.message(source + " found the world seed " + seed + ".");
        seedChanged(key, before);
        finder.search.startOrQueue(mc, key);
    }

    void showSeed() {
        String key = key(), typed = typedSeed(), cracked = read(key, "seedcracker.txt");
        Finder.message(key == null ? "Join a world first." : typed == null && cracked == null ? "No seed for " + key + ". Set World Seed."
            : "Seed for " + key + ": " + seed() + (typed != null ? " (typed" + (cracked != null && !cracked.equals(typed)
                ? "; SeedcrackerX found " + cracked : "") + ")" : " (from SeedcrackerX)"));
    }

    String[] generationVersion() {
        String key = key(), typed = read(key, "version.txt");
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
            write(key, "version.txt", version);
            finder.search.stopFor(key, "Stopped: version changed");
            if (!before.equals(generationVersion()[0])) setAside(key, seed(key), "the previous World Version");
            showVersion();
        } catch (IOException e) {
            Finder.message("Could not save the version: " + e.getMessage());
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
        finder.checkServerType();
        String[] t = serverType();
        Finder.message("Server Type for " + key + ": " + t[0] + " (" + t[1] + ").");
    }
}
