package dripleaffinder;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DataKey {
    private static final Pattern ADDRESS = Pattern.compile("\\[([^\\]]*)\\](?::(\\d+))?|([^:]*)(?::(\\d+))?");

    static String of(String address, String world) {
        if (address != null && !address.isBlank()) {
            String port = parts(address)[1];
            return host(address) + (port != null && !port.equals("25565") ? "_" + port : "");
        }
        String name = world == null ? "" : world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return name.isEmpty() || name.matches("\\.+") ? "local" : "local-" + name;
    }

    static String host(String address) {
        if (address == null || address.isBlank()) return "local";
        String host = parts(address)[0];
        if (host.contains(":")) return host.matches("[0-9a-f:.]+") ? "[" + host.replace(':', '_') + "]" : "unknown";
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        try {
            host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
        if (!host.matches("[a-z0-9._\\-]+")) return "unknown";
        return host.matches("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?") ? "_" + host : host;
    }

    private static String[] parts(String address) {
        String text = address.trim().toLowerCase(Locale.ROOT);
        Matcher m = ADDRESS.matcher(text);
        if (!m.matches()) return new String[] {text, null};
        return m.group(1) != null ? new String[] {m.group(1), m.group(2)} : new String[] {m.group(3), m.group(4)};
    }

    private DataKey() {
    }
}
