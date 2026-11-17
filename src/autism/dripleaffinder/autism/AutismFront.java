package dripleaffinder.autism;

import autismclient.api.SimpleAddon;
import autismclient.api.module.ActionSetting;
import autismclient.api.module.SimpleModule;
import autismclient.api.module.StringSetting;
import dripleaffinder.DripleafClient;
import dripleaffinder.Finder;
import dripleaffinder.SeedSearch;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public final class AutismFront extends SimpleAddon {
    public AutismFront() {
        super(3, "dripleaffinder");
    }

    @Override
    protected void initialize() {
        registerModule(new Module(DripleafClient.finder()));
    }

    static final class Module extends SimpleModule {
        private final Finder finder;
        private final StringSetting seed = new StringSetting("seed", "World Seed", "");
        private final StringSetting version = new StringSetting("version", "World Version", "");
        private final Field seedField, versionField;
        private int ticks;

        Module(Finder finder) {
            super("spliced", "Spliced Dripleaf Finder", "Finds small dripleaf top halves that have no bottom half.");
            this.finder = finder;
            addBool("masked", "Masked Server", finder.settings.masked);
            add(new ActionSetting("nearest", "Nearest Predictions", finder::listNearest).buttonLabel("List"));
            addBool("lush", "Lush Cave Pings", finder.settings.lush);
            add(new ActionSetting("nearestLush", "Nearest Lush Caves", finder::listNearestLush).buttonLabel("List"));
            addBool("azalea", "Azalea Trees", finder.settings.azalea);
            add(seed.description("The seed for the server you are on; it always wins over the one SeedcrackerX cracks. Empty: SeedcrackerX's."));
            add(version.description("The Minecraft version that generated this server's world. Empty: the version the server reports."));
            addInt("radius", "Search Radius", SeedSearch.DEFAULT_RADIUS, 64, SeedSearch.MAX_RADIUS, 64);
            seedField = new Field(seed, finder.seeds::typedSeed, finder.seeds::setSeed);
            versionField = new Field(version, finder.seeds::typedVersion, finder.seeds::setVersion);
            add(new ActionSetting("search", "Seed Search", this::search).buttonLabel("Search").requiresWorld());
            add(new ActionSetting("stopSearch", "Stop Seed Search", finder.search::stop).buttonLabel("Stop"));
            finder.onEnabledChanged(on -> {
                if (isEnabled() != on) setEnabled(on);
            });
            if (finder.enabled() != isEnabled()) finder.setEnabled(isEnabled());
        }

        @Override
        public void onEnable() {
            finder.setEnabled(true);
        }

        @Override
        public void onDisable() {
            finder.setEnabled(false);
        }

        private void search() {
            seedField.flush(Finder.connectionKey(Minecraft.getInstance()));
            versionField.flush(Finder.connectionKey(Minecraft.getInstance()));
            finder.search.start(Minecraft.getInstance(), getInt("radius"));
        }

        @Override
        public void tick() {
            if (!finder.enabled()) finder.setEnabled(true);
            finder.setMasked(getBool("masked"));
            finder.setLush(getBool("lush"));
            finder.setAzalea(getBool("azalea"));
            String key = Finder.connectionKey(Minecraft.getInstance());
            boolean second = ticks++ % 20 == 0;
            seedField.tick(key, second);
            versionField.tick(key, second);
        }
    }

    static final class Field {
        private final StringSetting setting;
        private final Supplier<String> saved;
        private final Consumer<String> save;
        private String key, shown = "", last = "";
        private int stable;

        Field(StringSetting setting, Supplier<String> saved, Consumer<String> save) {
            this.setting = setting;
            this.saved = saved;
            this.save = save;
        }

        void tick(String now, boolean second) {
            String text = setting.get() == null ? "" : setting.get().trim();
            if (!Objects.equals(now, key)) {
                key = now;
                if (now != null) show(saved.get());
                return;
            }
            if (now == null) return;
            if (!text.equals(last)) {
                last = text;
                stable = 0;
                return;
            }
            if (++stable == 20 && !text.equals(shown)) {
                save.accept(text.isEmpty() ? null : text);
                shown = text;
            } else if (second && text.equals(shown)) {
                String value = saved.get();
                if (!Objects.equals(value == null ? "" : value, shown)) show(value);
            }
        }

        void flush(String now) {
            String text = setting.get() == null ? "" : setting.get().trim();
            if (now != null && Objects.equals(now, key) && !text.equals(shown)) {
                save.accept(text.isEmpty() ? null : text);
                shown = text;
            }
        }

        private void show(String value) {
            shown = value == null ? "" : value;
            last = shown;
            setting.set(shown);
        }
    }
}
