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
    public static void main(String[] args) {
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

        System.out.println("CLIENT CHECK PASSED: 2 azalea trees, trunkless leaves dropped; data rows, box edges, memory, data keys, host names,"
            + " server versions, server types, folia guidance, splice guidance");
    }
}
