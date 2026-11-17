package dripleaffinder;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class Splice {
    static final Set<Block> STONE = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE);

    static boolean isLeaf(BlockState state) {
        return state.is(Blocks.SMALL_DRIPLEAF);
    }

    static boolean isUpper(BlockState state) {
        return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    static boolean spliced(BlockGetter world, BlockPos pos, BlockState state, boolean masked, Predicate<BlockPos> lush) {
        if (!isLeaf(state) || !isUpper(state)) return false;
        if (masked && (pos.getY() & 15) == 0) return false;
        BlockState below = world.getBlockState(pos.below());
        if (isLeaf(below) && !isUpper(below)) return false;
        if (STONE.contains(below.getBlock()) || WorldCompat.blockPath(below.getBlock()).endsWith("_ore")) return false;
        return covered(world, pos) && lush.test(pos);
    }

    static boolean lushNear(LevelReader level, BlockPos pos) {
        for (int dx = -8; dx <= 8; dx += 8) for (int dy = -4; dy <= 4; dy += 4) for (int dz = -8; dz <= 8; dz += 8) {
            if (WorldCompat.isLush(level, pos.offset(dx, dy, dz))) return true;
        }
        return false;
    }

    static final int SETTLE_TICKS = 200, SETTLE_RANGE = 32;

    static Boolean settle(BlockGetter world, BlockPos pos, int nearTicks, Predicate<BlockPos> lush) {
        if (nearTicks < SETTLE_TICKS) return null;
        return spliced(world, pos, world.getBlockState(pos), false, lush);
    }

    static boolean covered(BlockGetter world, BlockPos pos) {
        BlockPos.MutableBlockPos at = pos.mutable();
        for (int y = pos.getY() + 1; y <= WorldCompat.maxY(world); y++) {
            if (!world.getBlockState(at.setY(y)).isAir()) return true;
        }
        return false;
    }

    static void scan(ChunkAccess chunk, boolean masked, Predicate<BlockPos> lush, BiConsumer<BlockPos, BlockState> out) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (!WorldCompat.maybeHas(section, Splice::isLeaf)) continue;
            int baseY = WorldCompat.sectionBaseY(chunk, i);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isLeaf(state)) continue;
                        BlockPos pos = chunk.getPos().getBlockAt(x, baseY + y, z);
                        if (spliced(chunk, pos, state, masked, lush)) out.accept(pos, state);
                    }
                }
            }
        }
    }
}
