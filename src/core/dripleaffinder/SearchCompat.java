package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction;

final class SearchCompat {
    static Climate.TargetPoint column(ServerLevel level, int qx, int qz) {
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(QuartPos.toBlock(qx), 0, QuartPos.toBlock(qz));
        return Climate.target((float) sampler.temperature().compute(at), (float) sampler.humidity().compute(at),
            (float) sampler.continentalness().compute(at), (float) sampler.erosion().compute(at), 0, (float) sampler.weirdness().compute(at));
    }

    static boolean lushNoise(ServerLevel level, Climate.TargetPoint column, int qx, int qy, int qz) {
        ServerChunkCache cache = level.getChunkSource();
        DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(QuartPos.toBlock(qx), QuartPos.toBlock(qy), QuartPos.toBlock(qz));
        long depth = Climate.quantizeCoord((float) cache.randomState().sampler().depth().compute(at));
        Climate.TargetPoint target = new Climate.TargetPoint(column.temperature(), column.humidity(), column.continentalness(), column.erosion(),
            depth, column.weirdness());
        return ((MultiNoiseBiomeSource) cache.getGenerator().getBiomeSource()).getNoiseBiome(target).is(Biomes.LUSH_CAVES);
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
        return SpawnCompat.spawn(level);
    }

    static int minY(ChunkAccess chunk) {
        return chunk.getMinY();
    }

    private SearchCompat() {
    }
}
