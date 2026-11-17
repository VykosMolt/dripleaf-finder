package dripleaffinder;

import java.util.List;
import java.util.Locale;

final class ServerType {
    static final String PAPER = "Paper/Spigot", FOLIA = "Folia";
    static final List<String> TYPES = List.of(PAPER, FOLIA);

    static String fromBrand(String brand) {
        return brand != null && brand.toLowerCase(Locale.ROOT).contains("folia") ? FOLIA : PAPER;
    }

    private ServerType() {
    }
}
