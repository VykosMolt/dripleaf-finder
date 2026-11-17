package dripleaffinder;

import java.util.List;
import java.util.function.Consumer;

public interface MenuModel {
    String AUTO = "Auto";
    int MIN_RADIUS = 64, MAX_RADIUS = SeedSearch.MAX_RADIUS, RADIUS_STEP = 64;
    int DEFAULT_RADIUS = Math.round(SeedSearch.DEFAULT_RADIUS / (float) RADIUS_STEP) * RADIUS_STEP;

    boolean enabled();

    void setEnabled(boolean on);

    void onEnabledChanged(Consumer<Boolean> listener);

    boolean masked();

    void setMasked(boolean on);

    boolean lush();

    void setLush(boolean on);

    boolean azalea();

    void setAzalea(boolean on);

    void listPredictions();

    void listLush();

    boolean inWorld();

    String seed();

    void setSeed(String seed);

    List<String> versions();

    String version();

    void setVersion(String version);

    List<String> serverTypes();

    String serverType();

    void setServerType(String type);

    int radius();

    void setRadius(int radius);

    int memoryGb();

    int maxMemoryGb();

    void setMemoryGb(int gb);

    String status();

    boolean searching();

    void search();

    void stop();
}
