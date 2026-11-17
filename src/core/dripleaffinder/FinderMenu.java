package dripleaffinder;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

final class FinderMenu implements MenuModel {
    private final Finder finder;
    private final int maxMemoryGb;
    private int radius = Math.round(SeedSearch.DEFAULT_RADIUS / (float) RADIUS_STEP) * RADIUS_STEP;
    private int memoryGb;

    FinderMenu(Finder finder) {
        this.finder = finder;
        long bytes = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        maxMemoryGb = Math.max(1, (int) (bytes >> 30) / 2);
        memoryGb = Math.min(4, maxMemoryGb);
    }

    public boolean enabled() {
        return finder.enabled();
    }

    public void setEnabled(boolean on) {
        finder.setEnabled(on);
    }

    public boolean masked() {
        return finder.settings.masked;
    }

    public void setMasked(boolean on) {
        finder.setMasked(on);
    }

    public boolean lush() {
        return finder.settings.lush;
    }

    public void setLush(boolean on) {
        finder.setLush(on);
    }

    public boolean azalea() {
        return finder.settings.azalea;
    }

    public void setAzalea(boolean on) {
        finder.setAzalea(on);
    }

    public void listPredictions() {
        finder.listNearest();
    }

    public void listLush() {
        finder.listNearestLush();
    }

    public boolean inWorld() {
        return Finder.connectionKey(Minecraft.getInstance()) != null;
    }

    public String seed() {
        return finder.seeds.typedSeed();
    }

    public void setSeed(String seed) {
        finder.seeds.setSeed(seed);
    }

    public List<String> versions() {
        List<String> releases = new ArrayList<>(Versions.RELEASES.keySet());
        Collections.reverse(releases);
        releases.add(0, AUTO);
        return releases;
    }

    public String version() {
        String typed = finder.seeds.typedVersion();
        return typed == null ? AUTO : typed;
    }

    public void setVersion(String version) {
        finder.seeds.setVersion(AUTO.equals(version) ? null : version);
    }

    public int radius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    public int memoryGb() {
        return memoryGb;
    }

    public int maxMemoryGb() {
        return maxMemoryGb;
    }

    public void setMemoryGb(int gb) {
        memoryGb = Math.max(1, Math.min(maxMemoryGb, gb));
    }

    public String status() {
        if (searching()) return "Searching...";
        return finder.search.lastResult == null ? "No search yet" : finder.search.lastResult;
    }

    public boolean searching() {
        return finder.search.running();
    }

    public void search() {
        finder.search.start(Minecraft.getInstance(), radius);
    }

    public void stop() {
        finder.search.stop();
    }
}
