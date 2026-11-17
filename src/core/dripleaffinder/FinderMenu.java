package dripleaffinder;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

final class FinderMenu implements MenuModel {
    private final Finder finder;
    private final Settings settings;
    private final int maxMemoryGb;

    FinderMenu(Finder finder) {
        this.finder = finder;
        settings = finder.settings;
        long bytes = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        maxMemoryGb = Math.max(1, (int) (bytes >> 30) / 2);
    }

    public boolean enabled() {
        return finder.enabled();
    }

    public void setEnabled(boolean on) {
        finder.setEnabled(on);
    }

    public void onEnabledChanged(Consumer<Boolean> listener) {
        finder.onEnabledChanged(listener);
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
        List<String> releases = new ArrayList<>(Searchers.versions());
        List<String> order = List.copyOf(Versions.RELEASES.keySet());
        releases.sort(Comparator.comparingInt(order::indexOf).reversed());
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
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, settings.radius));
    }

    public void setRadius(int radius) {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        if (settings.radius == radius) return;
        settings.radius = radius;
        settings.save();
    }

    public int memoryGb() {
        return Math.max(1, Math.min(maxMemoryGb, settings.memoryGb));
    }

    public int maxMemoryGb() {
        return maxMemoryGb;
    }

    public void setMemoryGb(int gb) {
        gb = Math.max(1, Math.min(maxMemoryGb, gb));
        if (settings.memoryGb == gb) return;
        settings.memoryGb = gb;
        settings.save();
    }

    public String status() {
        if (searching()) return "Searching...";
        return finder.search.lastResult == null ? "No search yet" : finder.search.lastResult;
    }

    public boolean searching() {
        return finder.search.running();
    }

    public void search() {
        finder.search.start(Minecraft.getInstance(), radius());
    }

    public void stop() {
        finder.search.stop();
    }
}
