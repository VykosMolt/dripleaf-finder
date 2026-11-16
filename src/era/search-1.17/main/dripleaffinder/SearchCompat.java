package dripleaffinder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

final class SearchCompat {
    static final boolean SUPPORTED = false;

    static boolean lushNoise(ServerLevel level, int qx, int qy, int qz) {
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

    private SearchCompat() {
    }
}
