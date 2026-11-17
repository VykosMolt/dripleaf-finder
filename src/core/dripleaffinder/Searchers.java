package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;

final class Searchers {
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final Pattern LOADER_JARS = Pattern.compile(
        "(fabric-loader|sponge-mixin|asm|asm-analysis|asm-commons|asm-tree|asm-util|mixinextras-fabric|mixinextras-common)-.*\\.jar");

    record Launch(Path gameJar, List<Path> mods, List<String> classpath) {
    }

    static Launch prepare(Path cache, String target) throws IOException {
        boolean client = target.equals(Versions.client());
        Path mod = client ? FabricLoader.getInstance().getModContainer("dripleaf-finder").orElseThrow().getOrigin().getPaths().get(0)
            : bundled(cache.resolve("searchers"), target);
        if (client && FabricLoader.getInstance().getObjectShare().get("fabric-loader:inputGameJars") instanceof List<?> jars
                && !jars.isEmpty() && jars.get(0) instanceof Path game) {
            return new Launch(game, List.of(mod, apiBase(cache)), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        }
        Path serverJar = serverJar(cache.resolve(target), target);
        List<String> classpath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (LOADER_JARS.matcher(Path.of(entry).getFileName().toString()).matches()) classpath.add(entry);
        }
        if (target.startsWith("1.")) classpath.add(intermediary(cache.resolve(target), target).toString());
        return new Launch(serverJar, List.of(mod, apiBase(cache)), classpath);
    }

    static Path apiBase(Path cache) throws IOException {
        Path jar = cache.resolve("fabric-api-base-standin.jar");
        if (Files.exists(jar)) return jar;
        Files.createDirectories(cache);
        Path part = cache.resolve("fabric-api-base-standin.jar.part");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(part))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\": 1, \"id\": \"fabric-api-base\", \"version\": \"0.0.0+dripleaf.search\","
                + " \"name\": \"Fabric API base stand-in (Dripleaf Finder seed search server)\", \"environment\": \"*\"}")
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    static List<String> versions() {
        Set<String> versions = new HashSet<>(Set.of(Versions.client()));
        try {
            for (String line : resource("searchers/versions.tsv")) versions.add(line.split("\t")[0]);
        } catch (IOException e) {
        }
        List<String> order = List.copyOf(Versions.RELEASES.keySet());
        return versions.stream().sorted(Comparator.comparingInt(order::indexOf).reversed()).toList();
    }

    static String supported() {
        List<String> v = versions();
        String last = v.get(v.size() - 1);
        return "this build searches " + (v.size() == 1 ? last : String.join(", ", v.subList(0, v.size() - 1)) + " and " + last) + " worlds";
    }

    static Path bundled(Path dir, String target) throws IOException {
        String jar = null, sha = null;
        for (String line : resource("searchers/versions.tsv")) {
            String[] f = line.split("\t");
            if (f.length == 3 && f[0].equals(target)) {
                jar = f[1];
                sha = f[2];
            }
        }
        if (jar == null) throw new IOException("this build carries no search for " + target);
        Path out = dir.resolve(jar);
        if (!Files.exists(out) || !sha1(out).equals(sha)) {
            Files.createDirectories(dir);
            Path part = dir.resolve(jar + ".part");
            try (InputStream in = Searchers.class.getClassLoader().getResourceAsStream("searchers/" + jar)) {
                if (in == null) throw new IOException("searchers/" + jar + " is missing from this jar");
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    static Path intermediary(Path dir, String version) throws IOException {
        Path jar = dir.resolve("intermediary.jar");
        if (Files.exists(jar)) return jar;
        String url = "https://maven.fabricmc.net/net/fabricmc/intermediary/" + version + "/intermediary-" + version + ".jar";
        String expected;
        try (InputStream in = get(url + ".sha1")) {
            expected = new String(in.readAllBytes()).trim().split("\\s+")[0];
        }
        Files.createDirectories(dir);
        Path part = dir.resolve("intermediary.jar.part");
        try (InputStream in = get(url)) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha1(part).equals(expected)) {
            Files.delete(part);
            throw new IOException("intermediary " + version + " does not match its SHA-1");
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
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
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static List<String> resource(String name) throws IOException {
        try (InputStream in = Searchers.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return List.of();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private Searchers() {
    }
}
