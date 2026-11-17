package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

final class SearchCompat {
    static Climate.Sampler column(ServerLevel level, int qx, int qz) {
        return level.getChunkSource().getGenerator().climateSampler();
    }

    static boolean lushNoise(ServerLevel level, Climate.Sampler column, int qx, int qy, int qz) {
        ServerChunkCache cache = level.getChunkSource();
        return cache.getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, column).is(Biomes.LUSH_CAVES);
    }

    static boolean lushStored(ServerLevel level, ChunkAccess chunk, int qx, int qy, int qz) {
        return chunk.getNoiseBiome(qx, qy, qz).is(Biomes.LUSH_CAVES);
    }

    static void requestTerrain(ServerLevel level, int x, int z) {
        level.getChunkSource().getChunkFuture(x, z, ChunkStatus.CARVERS, true);
    }

    static ChunkAccess terrain(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.CARVERS, true);
    }

    static ChunkAccess decorate(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FEATURES, true);
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
