package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean masked = false;
    public boolean lush = true;
    public boolean azalea = true;
    public int radius = MenuModel.DEFAULT_RADIUS;
    public Integer memoryGb;
    private transient Path file;

    static final long TOTAL_MEMORY = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

    static int maxMemoryGb(long totalBytes) {
        return Math.max(1, (int) (totalBytes >> 30) - 2);
    }

    static int memoryGb(Integer stored, long totalBytes) {
        int max = maxMemoryGb(totalBytes), min = Math.min(MenuModel.MIN_MEMORY_GB, max);
        return stored == null ? Math.max(min, Math.min(max, Math.max(4, (int) (totalBytes >> 31)))) : Math.max(min, Math.min(max, stored));
    }

    int memoryGb() {
        return memoryGb(memoryGb, TOTAL_MEMORY);
    }

    static Settings load(Path file) {
        Settings settings = null;
        try {
            if (Files.exists(file)) settings = GSON.fromJson(Files.readString(file), Settings.class);
        } catch (IOException | RuntimeException e) {
        }
        if (settings == null) settings = new Settings();
        settings.file = file;
        return settings;
    }

    void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
        }
    }
}
