package dripleaffinder;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

final class TestChunk {
    static ChunkAccess create(int chunkX, int chunkZ) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LevelHeightAccessor height = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, height);
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) sections[i] = new LevelChunkSection(chunk.getSectionYFromSectionIndex(i) << 4);
        return chunk;
    }

    static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.getSections()[chunk.getSectionIndex(y)].setBlockState(x, y & 15, z, state, false);
    }
}
