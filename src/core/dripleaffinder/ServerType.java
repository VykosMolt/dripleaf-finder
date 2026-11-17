package dripleaffinder;

import java.util.List;
import java.util.Locale;

final class ServerType {
    static final String PAPER = "Paper/Spigot", FOLIA = "Folia";
    static final List<String> TYPES = List.of(PAPER, FOLIA);

    static String fromBrand(String brand) {
        return brand != null && brand.toLowerCase(Locale.ROOT).contains("folia") ? FOLIA : PAPER;
    }

    static String hiddenNote(long hidden, long total) {
        if (hidden == 0) return "";
        return "Folia: " + hidden + " of " + total + " predictions " + (hidden == 1 ? "depends on chunk order and is hidden."
            : "depend on chunk order and are hidden.");
    }

    private ServerType() {
    }
}
