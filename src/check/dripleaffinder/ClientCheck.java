package dripleaffinder;

import static dripleaffinder.SelfCheck.chunk;
import static dripleaffinder.SelfCheck.expect;
import static dripleaffinder.SelfCheck.set;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ClientCheck {
    public static void main(String[] args) throws java.io.IOException {
        SelfCheck.main(args);

        for (int x = 2; x <= 4; x++) for (int y = 100; y <= 101; y++) for (int z = 2; z <= 4; z++) {
            set(x, y, z, (x + y + z) % 2 == 0 ? Blocks.AZALEA_LEAVES.defaultBlockState() : Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        }
        set(3, 99, 3, Blocks.OAK_LOG.defaultBlockState());
        set(10, 120, 10, Blocks.AZALEA_LEAVES.defaultBlockState());
        set(11, 121, 11, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        set(10, 119, 10, Blocks.OAK_LOG.defaultBlockState());
        set(13, 130, 13, Blocks.AZALEA_LEAVES.defaultBlockState());
        set(13, 131, 13, Blocks.AZALEA_LEAVES.defaultBlockState());
        expect("azalea canopies", new HashSet<>(Azalea.canopies(chunk, chunk)),
            Set.of(new AABB(50, 100, -30, 53, 102, -27), new AABB(58, 120, -22, 60, 122, -20)));

        java.util.List<Prediction> predictions = java.util.stream.Stream.of("x\ty\tz\thalf\tother\torder\tsurvived\tdist",
            "1406\t-48\t-148\ttop\tair\tasc\tyes\t1413", "1\t2\t3").map(Prediction::parse).filter(java.util.Objects::nonNull).toList();
        java.util.List<int[]> lushRows = java.util.stream.Stream.of("70936\t-29\t114920\t22313\t1120", "1\t2\t3\tmany")
            .map(line -> Data.ints(line, 4)).filter(java.util.Objects::nonNull).toList();
        expect("data rows", java.util.List.of(predictions.size(), predictions.isEmpty() ? "none" : predictions.get(0).half(),
            lushRows.size(), lushRows.isEmpty() ? "none" : lushRows.get(0)[3]), java.util.List.of(1, "top", 1, 22313));

        java.util.List<String> vertices = new java.util.ArrayList<>();
        Boxes.edges(new AABB(0, 0, 0, 1, 2, 3), (x, y, z, n) -> vertices.add(x + " " + y + " " + z + " / " + n.x + " " + n.y + " " + n.z));
        int[] flat = {0};
        Boxes.edges(new AABB(0, 5, 0, 1, 5, 1), (x, y, z, n) -> flat[0]++);
        expect("box edges", java.util.List.of(vertices.size(), vertices.get(0), vertices.get(1), vertices.get(9), vertices.get(23), flat[0]),
            java.util.List.of(24, "0.0 0.0 0.0 / 1.0 0.0 0.0", "1.0 0.0 0.0 / 1.0 0.0 0.0", "0.0 2.0 0.0 / 0.0 1.0 0.0",
                "1.0 2.0 3.0 / 0.0 0.0 1.0", 16));

        Memory memory = new Memory();
        BlockPos p = new BlockPos(1, 2, 3);
        memory.enter("a");
        boolean first = memory.announced.add(p);
        memory.enter("a");
        boolean again = memory.announced.add(p);
        memory.enter("b");
        boolean other = memory.announced.add(p);
        expect("memory", java.util.List.of(first, again, other, memory.key()), java.util.List.of(true, false, true, "b"));

        expect("data keys", java.util.List.of(DataKey.of("Play.Example.org:25565", null),
                DataKey.of(null, "New World (2)"), DataKey.of(null, null), DataKey.of(null, ".."),
                DataKey.of("play.example.org:25601", null), DataKey.of("play.example.org", null)),
            java.util.List.of("play.example.org", "local-new_world__2_", "local", "local", "play.example.org_25601", "play.example.org"));

        expect("splice guidance", java.util.List.of(
            Guidance.text(3705, 62, "air", "231,3", "232,3", false),
            Guidance.text(3705, 62, "cave_air", "230,2", "231,3", false),
            Guidance.text(3705, 62, "smooth_basalt", "231,3", "231,3", false),
            Guidance.text(3705, 62, "smooth_basalt", "-", "-", false),
            Guidance.text(3705, 62, "air", "231,3", "-", false),
            Guidance.text(3705, 62, "air", "231,3", "x,3", false),
            String.valueOf(Guidance.approach("231,3", "-")),
            Guidance.direction(0, -1) + " " + Guidance.direction(1, 1)),
            java.util.List.of(
                "cut by a geode; real only if its own chunk generated before the chunk to its east (in unexplored land: walk in from the west)",
                "cut by a mineshaft or trial chamber corridor; real only if the chunk to its north-west generated before its own chunk"
                    + " (in unexplored land: walk in from the north-west)",
                "cut by a geode; real in any chunk order",
                "cut by a geode; the chunk order it needs is unknown",
                "cut by a geode; the chunk order it needs is unknown",
                "cut by a geode; the chunk order it needs is unknown",
                "null",
                "north south-east"));
        expect("server versions", java.util.Arrays.asList(Versions.fromStatus("Paper 1.21.4", 769), Versions.fromStatus("1.20.1", 763),
            Versions.fromStatus("Velocity 3.3.0-SNAPSHOT (git-abc) 1.7.2-1.21.11", 774), Versions.fromStatus("Waterfall", 767),
            Versions.fromStatus("1.21.3", 768), Versions.fromStatus("26.2", 776), Versions.fromStatus("Spigot 1.99.1", 9999),
            Versions.fromStatus("Paper 1.21.4", 9999), Versions.fromStatus("Velocity 1.7.2-1.21.4", 769),
            Versions.fromStatus("BungeeCord 1.8.x-1.21.x", 774)),
            java.util.Arrays.asList("1.21.4", "1.20.1", null, "1.21.1", "1.21.3", "26.2", null, "1.21.4", null, null));
        expect("server types", java.util.stream.Stream.of("Paper", "Folia", "Paper (Velocity)", "DonutFolia", "vanilla", null)
                .map(ServerType::fromBrand).toList(),
            java.util.List.of("Paper/Spigot", "Folia", "Paper/Spigot", "Folia", "Paper/Spigot", "Paper/Spigot"));
        java.util.List<Prediction> rows = java.util.stream.Stream.of("100\t40\t200\ttop\tair\tboth\tyes\t300\t6,12\t6,12",
            "116\t40\t200\ttop\tair\tasc\tyes\t300\t7,12\t8,12", "132\t40\t200\ttop\tair\tasc\tyes\t300\t-\t-",
            "148\t40\t200\ttop\tair\tasc\tyes\t300", "164\t40\t200\ttop\tcave_air\tdesc\tyes\t300\t10,12\t10,12")
            .map(Prediction::parse).toList();
        java.util.List<Integer> anyOrder = rows.stream().filter(Prediction::anyOrder).map(row -> row.pos().getX()).toList();
        String folia = "Folia: about " + Guidance.FOLIA_ODDS + " (random chunk order)";
        expect("folia guidance", java.util.List.of(rows.size(), anyOrder, rows.stream().map(row -> row.text(true)).toList(), rows.get(1).text(false)),
            java.util.List.of(5, java.util.List.of(100, 164), java.util.List.of("top half, cut by a geode; real in any chunk order",
                    "top half, cut by a geode; " + folia, "top half, cut by a geode; " + folia, "top half, asc",
                    "top half, cut by a mineshaft or trial chamber corridor; real in any chunk order"),
                "top half, cut by a geode; real only if its own chunk generated before the chunk to its east (in unexplored land: walk in from the west)"));
        expect("host names", java.util.List.of(DataKey.host("Java.HugoSMP.net:25565"), DataKey.host("hugosmp.net."),
                DataKey.host(null), DataKey.host(".."), DataKey.host("[::1]:25565")),
            java.util.List.of("java.hugosmp.net", "hugosmp.net", "local", "unknown", "unknown"));
        expect("windows device names", java.util.List.of(DataKey.of("CON", null), DataKey.of("aux.example.net:25601", null), DataKey.of("nul.", null),
                DataKey.of("com1", null), DataKey.of("lpt9.example.net", null), DataKey.of("com10.net", null), DataKey.of("console.net", null),
                DataKey.of("example.aux", null), DataKey.of(null, "con")),
            java.util.List.of("_con", "_aux.example.net_25601", "_nul", "_com1", "_lpt9.example.net", "com10.net", "console.net", "example.aux",
                "local-con"));
        long gib = 1L << 30;
        expect("search memory", java.util.Arrays.asList(Settings.memoryGb(null, 32 * gib), Settings.memoryGb(null, 15 * gib + gib / 2),
                Settings.memoryGb(null, 8 * gib), Settings.memoryGb(null, 4 * gib), Settings.memoryGb(null, 2 * gib), Settings.memoryGb(12, 8 * gib),
                Settings.memoryGb(0, 8 * gib), Settings.memoryGb(5, 16 * gib), SeedSearch.serversThatFit(Settings.memoryGb(null, 15 * gib) * 1024L,
                    MenuModel.DEFAULT_RADIUS)),
            java.util.Arrays.asList(16, 7, 4, 2, 1, 6, 1, 5, 2));
        expect("brief status", java.util.List.of(
                SeedSearch.brief("Failed: java.lang.IllegalStateException: Not enough chunks loaded", 40),
                SeedSearch.brief("Could not start: java.net.UnknownHostException: piston-meta.mojang.com", 40),
                SeedSearch.brief("Failed: a.b.Outer$Inner", 40), SeedSearch.brief("Failed: out of memory", 40)),
            java.util.List.of("Failed: IllegalStateException: Not enou…", "Could not start: UnknownHostException: …",
                "Failed: Outer$Inner", "Failed: out of memory"));
        java.util.List<String> menuRows = java.util.List.of(FinderMenu.candidates(3, ServerType.PAPER, true, 0),
            FinderMenu.candidates(3, ServerType.FOLIA, true, 3), FinderMenu.candidates(1234, ServerType.FOLIA, true, 1000),
            FinderMenu.candidates(1234, ServerType.PAPER, false, 0));
        expect("candidates row", java.util.List.of(menuRows.get(0), menuRows.get(1), menuRows.stream().allMatch(row -> row.length() <= SeedSearch.STATUS_LENGTH)),
            java.util.List.of("3 here · Paper/Spigot (auto)", "3 here · Folia (auto), 1 in 2 each", true));
        java.util.List<String> asides = java.util.stream.Stream.of("1.5", "15", "种子", "семя", "-42").map(seed -> Seeds.asideName("predictions", seed))
            .toList();
        expect("set-aside names", java.util.List.of(new HashSet<>(asides).size(), asides.get(1).startsWith("predictions-15-"),
                asides.get(2).matches("predictions-[0-9a-f]{8}"), asides.get(4).matches("predictions--42-[0-9a-f]{8}"), Seeds.asideName("predictions", "15"),
                Seeds.asideName("lush", "15")),
            java.util.List.of(5, true, true, true, asides.get(1), "lush" + asides.get(1).substring("predictions".length())));
        setAside(asides.get(1));
        java.util.Map<BlockPos, String[]> found = new java.util.LinkedHashMap<>();
        found.put(new BlockPos(500, 40, 500), new String[] {"air", "asc", "-", "-"});
        java.util.List<String> old = java.util.List.of("10\t40\t10\ttop\tair\tasc\tyes\t1", "300\t40\t300\ttop\tair\tasc\tyes\t1",
            "5000\t40\t5000\ttop\tair\tasc\tyes\t1", "500\t40\t500\ttop\tair\tdesc\tyes\t1");
        java.util.function.BiFunction<int[], Boolean, java.util.List<String>> merged = (spawn, replace) -> SeedSearch.mergePredictions(old, found,
                new BlockPos(0, 64, 0), 1000, spawn, replace).stream().map(line -> line.split("\t")[0] + line.split("\t")[5]).toList();
        expect("merged predictions", java.util.List.of(merged.apply(new int[] {0, 0, 64}, true), merged.apply(null, true), merged.apply(null, false)),
            java.util.List.of(java.util.List.of("10asc", "5000asc", "500asc"), java.util.List.of("5000asc", "500asc"),
                java.util.List.of("10asc", "300asc", "5000asc", "500asc")));
        expect("spawn chunks kept", SeedSearch.mergePredictions(java.util.List.of("70\t40\t-64\ttop\tair\tasc\tyes\t1", "-65\t40\t0\ttop\tair\tasc\tyes\t1",
                "80\t40\t0\ttop\tair\tasc\tyes\t1"), java.util.Map.of(), new BlockPos(0, 64, 0), 1000, new int[] {8, 8, 64}, true)
            .stream().map(line -> line.split("\t")[0]).toList(), java.util.List.of("70"));
        BlockPos at = new BlockPos(-100, 70, 25);
        expect("overworld centre", java.util.List.of(SeedSearch.overworld(at, net.minecraft.world.level.Level.OVERWORLD),
                SeedSearch.overworld(at, net.minecraft.world.level.Level.NETHER), SeedSearch.overworld(at, net.minecraft.world.level.Level.END)),
            java.util.List.of(at, new BlockPos(-800, 70, 200), new BlockPos(0, 70, 0)));
        commandLine();

        System.out.println("CLIENT CHECK PASSED: 2 azalea trees, trunkless leaves dropped; data rows, box edges, memory, data keys, host names,"
            + " server versions, server types, folia guidance, splice guidance, search memory, brief status, candidates row, set-aside names, set aside,"
            + " merged predictions, spawn chunks kept, overworld centre, search command line");
    }

    static void setAside(String name) throws java.io.IOException {
        java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("dripleaf-aside");
        java.nio.file.Files.writeString(folder.resolve("predictions.tsv"), "new predictions");
        java.nio.file.Files.writeString(folder.resolve("lush.tsv"), "new lush");
        java.nio.file.Files.writeString(folder.resolve(name + ".tsv"), "old predictions");
        java.util.List<String> moved = Seeds.setAside(folder, "15").stream().map(path -> path.getFileName().toString()).toList();
        java.util.List<String> left = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> all = java.nio.file.Files.list(folder)) {
            for (java.nio.file.Path p : all.sorted().toList()) {
                left.add(p.getFileName() + "=" + java.nio.file.Files.readString(p));
                java.nio.file.Files.delete(p);
            }
        }
        java.nio.file.Files.delete(folder);
        String lush = "lush" + name.substring("predictions".length());
        expect("set aside", java.util.List.of(moved, left), java.util.List.of(java.util.List.of(name + "-2.tsv", lush + "-2.tsv"),
            java.util.List.of(lush + "-2.tsv=new lush", name + "-2.tsv=new predictions", name + ".tsv=old predictions")));
    }

    static void commandLine() throws java.io.IOException {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("dripleaf-check"), user = root.resolve("Mačak");
        java.nio.file.Path game = user.resolve("mc"), dir = game.resolve("dripleaf-finder/search/asc"), cache = game.resolve("dripleaf-finder/cache");
        java.nio.file.Files.createDirectories(dir);
        java.util.List<java.nio.file.Path> jars = java.util.stream.Stream.of(cache.resolve("1.21.3/server.jar"), cache.resolve("searchers/engine.jar"),
            game.resolve("mods/dripleaf.jar"), cache.resolve("1.21.3/intermediary.jar"), user.resolve("knihovny/čtení.jar")).toList();
        for (java.nio.file.Path jar : jars) {
            java.nio.file.Files.createDirectories(jar.getParent());
            java.nio.file.Files.writeString(jar, "jar");
        }
        java.util.List<String> command = SearchRun.command(dir, 300, new Searchers.Launch(jars.get(0), jars.subList(1, 3),
            java.util.List.of("/prism/libraries/fabric-loader.jar"), jars.subList(3, 5)));
        boolean copied = java.nio.file.Files.readString(dir.resolve("jars/_ten_.jar")).equals("jar");
        try (java.util.stream.Stream<java.nio.file.Path> all = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path p : all.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(p);
        }
        expect("search command line", java.util.List.of(command.subList(1, command.size()), copied), java.util.List.of(java.util.List.of(
            "-Xmx1124M", "-XX:+ExitOnOutOfMemoryError", "-Dfabric.gameJarPath=../../cache/1.21.3/server.jar",
            "-Dfabric.addMods=../../cache/searchers/engine.jar:../../../mods/dripleaf.jar", "-Ddripleaffinder.search=search.properties", "-cp",
            "/prism/libraries/fabric-loader.jar:../../cache/1.21.3/intermediary.jar:jars/_ten_.jar", "net.fabricmc.loader.impl.launch.knot.KnotServer",
            "nogui"), true));
    }
}
