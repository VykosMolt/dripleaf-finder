package dripleaffinder;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.minecraft.client.Minecraft;

public final class SeedcrackerLink implements SeedCrackerAPI {
    @Override
    public void pushWorldSeed(long seed) {
        Minecraft.getInstance().execute(() -> DripleafClient.finder().search.seedFound(String.valueOf(seed), "SeedcrackerX"));
    }
}
