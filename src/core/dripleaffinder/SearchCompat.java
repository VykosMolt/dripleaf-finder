package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class SearchCompat {
    static boolean lushNoise(ServerLevel level, int qx, int qy, int qz) {
        ServerChunkCache cache = level.getChunkSource();
        return cache.getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, cache.randomState().sampler()).is(Biomes.LUSH_CAVES);
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

    static boolean decoratedAlready(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true).getPersistedStatus().isAfter(ChunkStatus.CARVERS);
    }

    static BlockPos spawn(ServerLevel level) {
        return level.getRespawnData().pos();
    }

    static int minY(ChunkAccess chunk) {
        return chunk.getMinY();
    }

    private SearchCompat() {
    }
}
