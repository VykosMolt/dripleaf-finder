package dripleaffinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

final class Searchers {
    record Launch(Path serverJar, List<Path> mods, List<String> classpath) {
    }

    static Launch prepare(Path cache, String target) throws IOException {
        Path serverJar = SeedSearch.serverJar(cache.resolve(target), target);
        boolean own = target.equals(SeedSearch.version());
        List<String> classpath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString();
            if (SeedSearch.LOADER_JARS.matcher(name).matches() || own && SeedSearch.INTERMEDIARY.matcher(name).matches()) classpath.add(entry);
        }
        Path modJar;
        if (own) {
            modJar = FabricLoader.getInstance().getModContainer("dripleaf-finder").orElseThrow().getOrigin().getPaths().get(0);
        } else {
            modJar = bundled(cache.resolve("searchers"), target);
            if (target.startsWith("1.")) classpath.add(intermediary(cache.resolve(target), target).toString());
        }
        return new Launch(serverJar, List.of(modJar, apiBase(cache)), classpath);
    }

    static Path apiBase(Path cache) throws IOException {
        Path jar = cache.resolve("fabric-api-base-standin.jar");
        if (Files.exists(jar)) return jar;
        Files.createDirectories(cache);
        Path part = cache.resolve("fabric-api-base-standin.jar.part");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(part))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\": 1, \"id\": \"fabric-api-base\", \"version\": \"0.0.0+dripleaf.search\","
                + " \"name\": \"Fabric API base stand-in (Dripleaf Finder seed search server)\", \"environment\": \"*\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
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
        if (!Files.exists(out) || !SeedSearch.sha1(out).equals(sha)) {
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
        try (InputStream in = SeedSearch.get(url + ".sha1")) {
            expected = new String(in.readAllBytes()).trim().split("\\s+")[0];
        }
        Files.createDirectories(dir);
        Path part = dir.resolve("intermediary.jar.part");
        try (InputStream in = SeedSearch.get(url)) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!SeedSearch.sha1(part).equals(expected)) {
            Files.delete(part);
            throw new IOException("intermediary " + version + " does not match its SHA-1");
        }
        Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
        return jar;
    }

    private static List<String> resource(String name) throws IOException {
        try (InputStream in = Searchers.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return List.of();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
    }

    private Searchers() {
    }
}
