package dripleaffinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class DripleafClient implements ClientModInitializer {
    private static Finder finder;

    public static synchronized Finder finder() {
        if (finder == null) finder = new Finder(Settings.load(FabricLoader.getInstance().getConfigDir().resolve("dripleaf-finder.json")));
        return finder;
    }

    @Override
    public void onInitializeClient() {
        Finder finder = finder();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            finder.tick(mc);
            finder.search.tick(mc);
        });
        Ui.register(finder);
        Render.register(finder);
    }
}
