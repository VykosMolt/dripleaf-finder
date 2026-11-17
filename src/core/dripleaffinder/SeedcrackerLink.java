package dripleaffinder;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class SeedcrackerLink implements SeedCrackerAPI {
    @Override
    public void pushWorldSeed(long seed) {
        if (!FabricLoader.getInstance().isModLoaded("autism")) return;
        Minecraft mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        if (connection == null) return;
        mc.execute(() -> {
            if (mc.getConnection() == connection) DripleafClient.finder().seeds.seedFound(String.valueOf(seed), "SeedcrackerX");
        });
    }
}
