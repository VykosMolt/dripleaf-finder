package dripleaffinder;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

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

        expect("settle", java.util.Arrays.asList(Splice.settle(chunk, new BlockPos(50, 20, -30), 190, false, SelfCheck::lush),
                Splice.settle(chunk, new BlockPos(50, 20, -30), 200, false, SelfCheck::lush), Splice.settle(chunk, new BlockPos(49, 11, -31), 200, false, SelfCheck::lush),
                Splice.settle(chunk, new BlockPos(62, 16, -18), 200, false, SelfCheck::lush), Splice.settle(chunk, new BlockPos(49, 25, -30), 200, false, SelfCheck::lush),
                Splice.settle(chunk, new BlockPos(62, 16, -18), 190, true, SelfCheck::lush), Splice.settle(chunk, new BlockPos(62, 16, -18), 200, true, SelfCheck::lush)),
            java.util.Arrays.asList(null, true, false, true, false, null, false));

        System.out.println("SELF-CHECK PASSED: masked server " + masked.size() + " / plain server " + plain.size()
            + " spliced top halves; whole plants, lone bottoms, big dripleaf, stone/deepslate/ore below, open sky, outside lush caves ignored;"
            + " settle");
    }
}
