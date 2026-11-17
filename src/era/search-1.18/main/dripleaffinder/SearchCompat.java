package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
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
        return lush(level, cache.getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, column));
    }

    static boolean lushStored(ServerLevel level, ChunkAccess chunk, int qx, int qy, int qz) {
        return lush(level, chunk.getNoiseBiome(qx, qy, qz));
    }

    private static boolean lush(ServerLevel level, Biome biome) {
        return level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getResourceKey(biome).filter(Biomes.LUSH_CAVES::equals).isPresent();
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
