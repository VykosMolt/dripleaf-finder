package dripleaffinder;

import net.minecraft.client.Minecraft;

public final class SeedSearchFront {
    public static final int DEFAULT_RADIUS = SeedSearch.DEFAULT_RADIUS, MAX_RADIUS = SeedSearch.MAX_RADIUS;

    public static String key() {
        return Finder.connectionKey(Minecraft.getInstance());
    }

    public static String typedSeed(Finder finder) {
        return finder.search.typedSeed();
    }

    public static void setSeed(Finder finder, String seed) {
        finder.search.setSeed(seed);
    }

    public static String typedVersion(Finder finder) {
        return finder.search.typedVersion();
    }

    public static void setVersion(Finder finder, String version) {
        finder.search.setVersion(version);
    }

    public static void search(Finder finder, Minecraft mc, int radius) {
        finder.search.start(mc, radius);
    }

    public static void stop(Finder finder) {
        finder.search.stop();
    }

    private SeedSearchFront() {
    }
}
