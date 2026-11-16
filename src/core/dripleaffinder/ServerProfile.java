package dripleaffinder;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public interface ServerProfile {
    List<ServerProfile> PROFILES = new CopyOnWriteArrayList<>();

    boolean matches(String host);

    default boolean maskedSections() {
        return false;
    }

    default int minCanopyY() {
        return Integer.MIN_VALUE;
    }

    default String key() {
        return null;
    }

    default String lushResource() {
        return null;
    }

    static String host(String address) {
        if (address == null || address.isBlank()) return "local";
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon && host.substring(colon + 1).matches("\\d+")) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.matches("[a-z0-9._\\-]+") && !host.matches("\\.+") ? host : "unknown";
    }

    static String dataKey(ServerProfile profile, String address, String world) {
        if (profile != null && profile.key() != null) return profile.key();
        if (address != null && !address.isBlank()) {
            java.util.regex.Matcher port = java.util.regex.Pattern.compile("^[^:]*:(\\d+)$").matcher(address.trim());
            return host(address) + (port.matches() && !port.group(1).equals("25565") ? "_" + port.group(1) : "");
        }
        String name = world == null ? "" : world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return name.isEmpty() || name.matches("\\.+") ? "local" : "local-" + name;
    }

    static boolean keepCanopy(double minY, double maxY, ServerProfile profile) {
        return profile == null || maxY > profile.minCanopyY();
    }

    static ServerProfile forHost(String host) {
        for (ServerProfile profile : PROFILES) if (profile.matches(host)) return profile;
        return null;
    }
}
