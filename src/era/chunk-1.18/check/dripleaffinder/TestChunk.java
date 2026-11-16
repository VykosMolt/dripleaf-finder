package dripleaffinder;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

final class TestChunk {
    static ChunkAccess create(int chunkX, int chunkZ) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        LevelChunkSection[] sections = new LevelChunkSection[height.getSectionsCount()];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new LevelChunkSection(height.getSectionYFromSectionIndex(i),
                new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES), null);
        }
        return new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, sections, null, null, height, null, null);
    }

    static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }
}
