package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

final class SearchCompat {
    static BiomeSource column(ServerLevel level, int qx, int qz) {
        return level.getChunkSource().getGenerator().getBiomeSource();
    }

    static boolean lushNoise(ServerLevel level, BiomeSource column, int qx, int qy, int qz) {
        return false;
    }

    static boolean lushStored(ServerLevel level, ChunkAccess chunk, int qx, int qy, int qz) {
        return false;
    }

    static void requestTerrain(ServerLevel level, int x, int z) {
    }

    static ChunkAccess terrain(ServerLevel level, int x, int z) {
        throw new UnsupportedOperationException("no lush caves before 1.18");
    }

    static ChunkAccess decorate(ServerLevel level, int x, int z) {
        throw new UnsupportedOperationException("no lush caves before 1.18");
    }

    static int minY(ChunkAccess chunk) {
        return chunk.getMinBuildHeight();
    }

    static boolean decoratedAlready(ServerLevel level, int x, int z) {
        ChunkStatus status = level.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true).getStatus();
        return status != ChunkStatus.CARVERS && status.isOrAfter(ChunkStatus.CARVERS);
    }

    static BlockPos spawn(ServerLevel level) {
        return SpawnCompat.spawn(level);
    }

    private SearchCompat() {
    }
}
