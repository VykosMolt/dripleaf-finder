package dripleaffinder;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class Memory {
    final Set<BlockPos> announced = new HashSet<>(), lushPinged = new HashSet<>(), settled = new HashSet<>();
    boolean predictionsAnnounced;
    private String key;

    boolean enter(String next) {
        if (next.equals(key)) return false;
        forget();
        key = next;
        return true;
    }

    void forget() {
        key = null;
        predictionsAnnounced = false;
        announced.clear();
        lushPinged.clear();
        settled.clear();
    }

    String key() {
        return key;
    }
}
