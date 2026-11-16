package dripleaffinder;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class WorldCompat {
    static boolean maybeHas(LevelChunkSection section, Predicate<BlockState> test) {
        return section != null && !section.hasOnlyAir() && section.maybeHas(test);
    }

    static int sectionBaseY(ChunkAccess chunk, int index) {
        return chunk.getSectionYFromSectionIndex(index) << 4;
    }

    static int maxY(BlockGetter world) {
        return world.getMaxBuildHeight();
    }

    static String blockPath(Block block) {
        return Registry.BLOCK.getKey(block).getPath();
    }

    static boolean isLush(LevelReader level, BlockPos pos) {
        return level.getBiome(pos).is(Biomes.LUSH_CAVES);
    }

    private WorldCompat() {
    }
}
