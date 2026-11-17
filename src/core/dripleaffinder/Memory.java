package dripleaffinder;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class Memory {
    final Set<BlockPos> announced = new HashSet<>(), lushPinged = new HashSet<>(), settled = new HashSet<>();
    boolean predictionsAnnounced;
    private String key;

    void enter(String next) {
        if (next.equals(key)) return;
        forget();
        key = next;
    }

    void forget() {
        key = null;
        predictionsChanged();
        announced.clear();
    }

    void predictionsChanged() {
        predictionsAnnounced = false;
        lushPinged.clear();
        settled.clear();
    }

    String key() {
        return key;
    }
}
