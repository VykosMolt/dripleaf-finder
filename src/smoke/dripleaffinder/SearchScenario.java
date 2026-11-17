package dripleaffinder;

import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;

final class SearchScenario implements Scenario {
    private final Smoke smoke;
    private final String seed, flag;
    private final int radius;
    private final double[] camera;
    private boolean started;
    private int arrivedAt = -1, authorityAt = -1;
    private String notes = "";

    SearchScenario(Smoke smoke, String[] args, double[] camera) {
        this.smoke = smoke;
        this.seed = args[0];
        this.radius = Integer.parseInt(args[1]);
        this.flag = args.length > 2 ? args[2] : "";
        this.camera = camera;
    }

    @Override
    public void tick(Minecraft mc, Finder finder) {
        int ticks = smoke.ticks;
        if (arrivedAt < 0 && Smoke.near(mc, camera[0], camera[2], 2)) arrivedAt = ticks;
        if (!started) {
            if (arrivedAt < 0 || ticks - arrivedAt < 40) {
                if (ticks - smoke.joinedAt > 20 * 90) smoke.shoot(mc, "FAIL never teleported to the search centre");
                return;
            }
            start(mc, finder);
            return;
        }
        if (authorityAt >= 0) {
            if (ticks - authorityAt >= 60) checkAuthority(mc, finder);
            return;
        }
        if (!smoke.searchDone(finder, arrivedAt)) {
            if (ticks - arrivedAt > 20 * 900) smoke.shoot(mc, "FAIL search still running after 15 min");
            return;
        }
        String last = finder.search.lastResult, saved = saved();
        boolean refused = !SearchCompat.SUPPORTED && last != null && last.startsWith("Lush caves only generate");
        boolean ok = refused || last != null && last.startsWith("Seed search done") && !finder.search.hits.isEmpty()
            && finder.search.hits.keySet().stream().allMatch(pos -> saved.contains(pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ() + "\ttop"));
        smoke.shoot(mc, (ok ? "PASS " : "FAIL ") + notes + last + " Saved: " + saved.replace('\n', '|'));
    }

    private void start(Minecraft mc, Finder finder) {
        String[] before = finder.search.generationVersion();
        notes += "version before typing: " + before[0] + " (" + before[1] + "); ";
        String ping = System.getProperty("dripleaffinder.smoke.ping");
        if (ping != null) notes += "pinged " + ping + ": " + ServerVersion.detect(ping) + "; ";
        if (flag.startsWith("version=")) finder.search.setVersion(flag.substring(8));
        if (flag.equals("authority")) {
            finder.search.setSeed(seed);
            Smoke.pushSeed(Long.parseLong(seed) + 1);
            authorityAt = smoke.ticks;
        } else {
            Smoke.startSearch(mc, finder, seed, radius, flag.equals("seedcracker"));
        }
        started = true;
    }

    private void checkAuthority(Minecraft mc, Finder finder) {
        String handed = Data.read(Finder.DATA.resolve(Finder.connectionKey(mc)).resolve("seedcracker.txt"));
        boolean kept = seed.equals(finder.search.seed()) && !finder.search.running()
            && String.valueOf(Long.parseLong(seed) + 1).equals(handed);
        notes += "typed seed kept over SeedcrackerX's: " + kept + " (SeedcrackerX's stored: " + handed + "); ";
        authorityAt = -1;
        if (!kept) {
            smoke.shoot(mc, "FAIL " + notes + "seed now " + finder.search.seed() + ", search running " + finder.search.running());
            return;
        }
        finder.search.start(mc, radius);
    }

    private static String saved() {
        try {
            return Files.readString(Finder.DATA.resolve(Finder.connectionKey(Minecraft.getInstance())).resolve("predictions.tsv"));
        } catch (IOException | RuntimeException e) {
            return "no predictions.tsv (" + e.getMessage() + ")";
        }
    }
}
