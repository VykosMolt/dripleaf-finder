package dripleaffinder;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DataKey {
    private static final Pattern PORT = Pattern.compile("^[^:]*:(\\d+)$");

    static String of(String address, String world) {
        if (address != null && !address.isBlank()) {
            Matcher port = PORT.matcher(address.trim());
            return host(address) + (port.matches() && !port.group(1).equals("25565") ? "_" + port.group(1) : "");
        }
        String name = world == null ? "" : world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return name.isEmpty() || name.matches("\\.+") ? "local" : "local-" + name;
    }

    static String host(String address) {
        if (address == null || address.isBlank()) return "local";
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon && host.substring(colon + 1).matches("\\d+")) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.matches("[a-z0-9._\\-]+") && !host.matches("\\.+") ? host : "unknown";
    }

    private DataKey() {
    }
}
