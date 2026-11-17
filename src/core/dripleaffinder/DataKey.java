package dripleaffinder;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        if (world == null || world.isBlank() || world.matches("\\.+")) return "local";
        Path file = Path.of(world).getFileName();
        String name = file == null ? "world" : file.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        name = name.substring(0, Math.min(64, name.length()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(world.getBytes(StandardCharsets.UTF_8));
            return "local-" + name + "-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String host(String address) {
        if (address == null || address.isBlank()) return "local";
        String host = parts(address)[0];
        if (host.contains(":")) {
            int scope = host.indexOf('%');
            String ip = (scope < 0 ? host : host.substring(0, scope)).toLowerCase(Locale.ROOT);
            String zone = scope < 0 ? "" : host.substring(scope + 1);
            if (!ip.matches("[0-9a-f:.]+") || scope >= 0 && !zone.matches("[A-Za-z0-9._~-]+")) return "unknown";
            return "[" + ip.replace(':', '_') + (scope < 0 ? "" : "_scope_" + HexFormat.of().formatHex(zone.getBytes(StandardCharsets.UTF_8))) + "]";
        }
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
        String text = address.trim();
        Matcher m = ADDRESS.matcher(text);
        if (!m.matches()) return new String[] {text, null};
        return m.group(1) != null ? new String[] {m.group(1), m.group(2)} : new String[] {m.group(3), m.group(4)};
    }

    private DataKey() {
    }
}
