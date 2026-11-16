package dripleaffinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Versions {
    static final Map<String, int[]> RELEASES = new LinkedHashMap<>();

    static {
        String[] table = {"1.17 755 16", "1.17.1 756 16", "1.18 757 17", "1.18.1 757 17", "1.18.2 758 17", "1.19 759 17", "1.19.1 760 17",
            "1.19.2 760 17", "1.19.3 761 17", "1.19.4 762 17", "1.20 763 17", "1.20.1 763 17", "1.20.2 764 17", "1.20.3 765 17",
            "1.20.4 765 17", "1.20.5 766 21", "1.20.6 766 21", "1.21 767 21", "1.21.1 767 21", "1.21.2 768 21", "1.21.3 768 21",
            "1.21.4 769 21", "1.21.5 770 21", "1.21.6 771 21", "1.21.7 772 21", "1.21.8 772 21", "1.21.9 773 21", "1.21.10 773 21",
            "1.21.11 774 21", "26.1 775 25", "26.1.1 775 25", "26.1.2 775 25", "26.2 776 25"};
        for (String row : table) {
            String[] f = row.split(" ");
            RELEASES.put(f[0], new int[] {Integer.parseInt(f[1]), Integer.parseInt(f[2])});
        }
    }

    static final Pattern VERSION = Pattern.compile("(?<![\\d.])(1\\.\\d+(?:\\.\\d+)?|2\\d\\.\\d+(?:\\.\\d+)?)(?![\\d.])");

    static boolean known(String version) {
        return RELEASES.containsKey(version);
    }

    static int java(String version) {
        return RELEASES.get(version)[1];
    }

    static String fromStatus(String name, int protocol) {
        if (name != null && name.matches(".*\\d+\\.(\\d+|x)(\\.(\\d+|x))?\\s*-\\s*\\d+\\.(\\d+|x).*")) return null;
        List<String> named = new ArrayList<>();
        Matcher m = VERSION.matcher(name == null ? "" : name);
        while (m.find()) if (known(m.group(1))) named.add(m.group(1));
        for (int i = named.size() - 1; i >= 0; i--) if (RELEASES.get(named.get(i))[0] == protocol) return named.get(i);
        String byProtocol = null;
        for (Map.Entry<String, int[]> e : RELEASES.entrySet()) if (e.getValue()[0] == protocol) byProtocol = e.getKey();
        if (byProtocol != null) return byProtocol;
        return named.isEmpty() ? null : named.get(named.size() - 1);
    }

    private Versions() {
    }
}
