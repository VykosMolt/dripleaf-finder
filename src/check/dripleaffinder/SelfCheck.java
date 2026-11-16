package dripleaffinder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;

public final class SelfCheck {
    static ChunkAccess chunk;

    static void set(int x, int y, int z, BlockState state) {
        TestChunk.set(chunk, x, y, z, state);
    }

    static Map<String, String> scan(boolean masked) {
        Map<String, String> got = new TreeMap<>();
        Splice.scan(chunk, masked, SelfCheck::lush, (BlockPos pos, BlockState state) ->
            got.put(pos.getX() + " " + pos.getY() + " " + pos.getZ(), Splice.isUpper(state) ? "top" : "bottom"));
        return got;
    }

    static boolean lush(BlockPos pos) {
        return !(pos.getX() == 49 && pos.getZ() == -30);
    }

    static void expect(String what, Object got, Object want) {
        if (!got.equals(want)) throw new AssertionError(what + "\nwant " + want + "\ngot  " + got);
    }

    public static void main(String[] args) {
        chunk = TestChunk.create(3, -2);

        BlockState lower = Blocks.SMALL_DRIPLEAF.defaultBlockState();
        BlockState upper = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState stone = Blocks.STONE.defaultBlockState();

        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (x != 0 || z != 0) set(x, 310, z, stone);

        set(1, 10, 1, lower); set(1, 11, 1, upper);
        set(2, 20, 2, upper);
        set(3, 30, 3, lower);
        set(4, 47, 4, lower); set(4, 48, 4, upper);
        set(5, 44, 5, stone); set(5, 45, 5, upper);
        set(11, 36, 11, Blocks.DEEPSLATE.defaultBlockState()); set(11, 37, 11, upper);
        set(12, 52, 12, Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()); set(12, 53, 12, upper);
        set(13, 40, 13, Blocks.SMOOTH_BASALT.defaultBlockState()); set(13, 41, 13, upper);
        set(6, -64, 6, upper);
        set(14, 15, 14, Blocks.WATER.defaultBlockState()); set(14, 16, 14, upper);
        set(15, 0, 15, upper);
        set(7, 60, 7, lower.setValue(BlockStateProperties.WATERLOGGED, true)); set(7, 61, 7, upper);
        set(8, 70, 8, upper); set(8, 71, 8, upper);
        set(9, 319, 9, lower);
        set(10, 80, 10, Blocks.BIG_DRIPLEAF.defaultBlockState());
        set(0, 50, 0, upper);
        set(1, 25, 2, upper);

        Map<String, String> masked = new TreeMap<>(Map.of(
            "50 20 -30", "top", "61 41 -19", "top", "56 70 -24", "top", "56 71 -24", "top"));
        Map<String, String> plain = new TreeMap<>(masked);
        plain.putAll(Map.of("54 -64 -26", "top", "62 16 -18", "top", "63 0 -17", "top"));
        expect("spliced, masked server", scan(true), masked);
        expect("spliced, plain server", scan(false), plain);

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

        expect("settle", java.util.Arrays.asList(Splice.settle(chunk, new BlockPos(50, 20, -30), 190, SelfCheck::lush),
                Splice.settle(chunk, new BlockPos(50, 20, -30), 200, SelfCheck::lush), Splice.settle(chunk, new BlockPos(49, 11, -31), 200, SelfCheck::lush),
                Splice.settle(chunk, new BlockPos(62, 16, -18), 200, SelfCheck::lush), Splice.settle(chunk, new BlockPos(49, 25, -30), 200, SelfCheck::lush)),
            java.util.Arrays.asList(null, true, false, true, false));

        java.util.List<String[]> predictions = Data.rows(java.util.List.of("x\ty\tz\thalf\tother\torder\tsurvived\tdist",
            "1406\t-48\t-148\ttop\tair\tasc\tyes\t1413", "1\t2\t3"), 6, 3);
        java.util.List<String[]> lushRows = Data.rows(java.util.List.of("70936\t-29\t114920\t22313\t1120", "1\t2\t3\tmany"), 4, 4);
        expect("data rows", java.util.List.of(predictions.size(), predictions.isEmpty() ? "none" : predictions.get(0)[3],
            lushRows.size(), lushRows.isEmpty() ? "none" : lushRows.get(0)[3]), java.util.List.of(1, "top", 1, "22313"));

        Memory memory = new Memory();
        BlockPos p = new BlockPos(1, 2, 3);
        expect("memory", java.util.List.of(memory.enter("a"), memory.announced.add(p), memory.enter("a"), memory.announced.add(p),
            memory.enter("b"), memory.announced.add(p)), java.util.List.of(true, true, false, false, true, true));

        ServerProfile keyed = new ServerProfile() {
            public boolean matches(String host) { return true; }
            public String key() { return "example.net"; }
            public int minCanopyY() { return 32; }
        };
        expect("data keys", java.util.List.of(ServerProfile.dataKey(null, "Play.Example.org:25565", null), ServerProfile.dataKey(keyed, "a.example.net", null),
                ServerProfile.dataKey(null, null, "New World (2)"), ServerProfile.dataKey(null, null, null), ServerProfile.dataKey(null, null, ".."),
                ServerProfile.dataKey(null, "play.example.org:25601", null), ServerProfile.dataKey(null, "play.example.org", null)),
            java.util.List.of("play.example.org", "example.net", "local-new_world__2_", "local", "local", "play.example.org_25601", "play.example.org"));
        expect("canopy filter", java.util.List.of(ServerProfile.keepCanopy(-18, -14, keyed),
            ServerProfile.keepCanopy(60, 64, keyed), ServerProfile.keepCanopy(-18, -14, null)), java.util.List.of(false, true, true));

        expect("splice guidance", java.util.List.of(
            Guidance.text(3705, 62, "air", "231,3", "232,3"),
            Guidance.text(3705, 62, "cave_air", "230,2", "231,3"),
            Guidance.text(3705, 62, "smooth_basalt", "231,3", "231,3"),
            Guidance.text(3705, 62, "smooth_basalt", "-", "-"),
            Guidance.text(3705, 62, "air", "231,3", "-"),
            Guidance.text(3705, 62, "air", "231,3", "x,3"),
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
        expect("host names", java.util.List.of(ServerProfile.host("Java.HugoSMP.net:25565"), ServerProfile.host("hugosmp.net."),
                ServerProfile.host(null), ServerProfile.host(".."), ServerProfile.host("[::1]:25565")),
            java.util.List.of("java.hugosmp.net", "hugosmp.net", "local", "unknown", "unknown"));

        System.out.println("SELF-CHECK PASSED: masked server " + masked.size() + " / plain server " + plain.size()
            + " spliced top halves; whole plants, lone bottoms, big dripleaf, stone/deepslate/ore below, open sky, outside lush caves ignored;"
            + " 2 azalea trees, trunkless leaves dropped; settle, data rows, memory, data keys, canopy filter, host names, server versions, splice guidance");
    }
}
