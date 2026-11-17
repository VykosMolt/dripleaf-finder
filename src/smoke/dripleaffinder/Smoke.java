package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class Smoke implements ClientModInitializer {
    private Path out;
    private String result;
    private Scenario scenario;
    MenuSmoke menu;
    private int shotAt = -1;
    int ticks, joinedAt = -1;

    @Override
    public void onInitializeClient() {
        String file = System.getProperty("dripleaffinder.smoke");
        if (file == null) return;
        out = Path.of(file);
        FabricLoader.getInstance().getModContainer("dripleaf-finder").ifPresent(mod -> System.out.println("SMOKE mod jar: " + mod.getOrigin().getPaths().get(0)));
        if (System.getProperty("dripleaffinder.smoke.menu") != null) menu = new MenuSmoke();
        double[] camera =Arrays.stream(System.getProperty("dripleaffinder.smoke.camera").split(",")).mapToDouble(Double::parseDouble).toArray();
        String search = System.getProperty("dripleaffinder.smoke.search"), realworld = System.getProperty("dripleaffinder.smoke.realworld");
        if (realworld != null) {
            scenario = new RealWorldScenario(this, realworld.split(","), camera, Path.of(System.getProperty("dripleaffinder.smoke.tp")));
        } else if (search != null) {
            scenario = new SearchScenario(this, search.split(","), camera);
        } else {
            scenario = new PlantedScenario(this, pos(System.getProperty("dripleaffinder.smoke.pos")),
                pos(System.getProperty("dripleaffinder.smoke.negative")), camera);
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static BlockPos pos(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] at = text.split(",");
        return new BlockPos(Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2]));
    }

    private void tick(Minecraft mc) {
        ticks++;
        if (shotAt >= 0) {
            if (ticks - shotAt >= 60) finish(mc);
            return;
        }
        if (mc.level == null || mc.player == null) {
            if (ticks > 20 * 300) shoot(mc, "FAIL never joined the test server");
            return;
        }
        Finder finder = DripleafClient.finder();
        if (joinedAt < 0) {
            joinedAt = ticks;
            if (!finder.enabled()) finder.setEnabled(true);
        }
        scenario.tick(mc, finder);
    }

    static boolean near(Minecraft mc, double x, double z, double distance) {
        return mc.player.position().distanceToSqr(x, mc.player.position().y, z) < distance * distance;
    }

    static void pushSeed(long seed) {
        new Thread(() -> FabricLoader.getInstance().getEntrypoints("seedcrackerx", SeedCrackerAPI.class)
            .forEach(api -> api.pushWorldSeed(seed)), "smoke SeedcrackerX").start();
    }

    static void startSearch(Minecraft mc, Finder finder, String seed, int radius, boolean viaSeedcracker) {
        if (viaSeedcracker) {
            pushSeed(Long.parseLong(seed));
        } else {
            finder.seeds.setSeed(seed);
            finder.search.start(mc, radius, false);
        }
    }

    boolean searchDone(Finder finder, int arrivedAt) {
        return !finder.search.running() && (finder.search.lastResult != null || ticks - arrivedAt > 20 * 60);
    }

    void shoot(Minecraft mc, String result) {
        SmokeCompat.screenshot(mc);
        end(result);
    }

    void end(String result) {
        this.result = result;
        shotAt = ticks;
    }

    private void finish(Minecraft mc) {
        try {
            Files.writeString(out, "SMOKE " + result + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        shotAt = Integer.MAX_VALUE / 2;
        mc.stop();
    }
}
