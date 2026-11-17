package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean masked = false;
    public boolean lush = true;
    public boolean azalea = true;
    public int radius = MenuModel.DEFAULT_RADIUS;
    public int memoryGb = 4;
    private transient Path file;

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
