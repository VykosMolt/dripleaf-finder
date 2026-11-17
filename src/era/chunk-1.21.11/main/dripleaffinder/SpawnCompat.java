package dripleaffinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class SpawnCompat {
    static BlockPos spawn(ServerLevel level) {
        return level.getRespawnData().pos();
    }

    private SpawnCompat() {
    }
}
