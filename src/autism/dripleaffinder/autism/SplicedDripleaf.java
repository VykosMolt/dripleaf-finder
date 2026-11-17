package dripleaffinder.autism;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.SimpleModule;
import autismclient.api.module.StringSetting;
import dripleaffinder.MenuModel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

final class SplicedDripleaf extends SimpleModule {
    private final MenuModel model;
    private boolean inWorld;

    SplicedDripleaf(MenuModel model) {
        super("spliced", "Spliced Dripleaf Finder", "Finds small dripleaf top halves that have no bottom half.");
        this.model = model;
        add(new BoolSetting("masked", "Masked Server", false));
        add(new ActionSetting("nearest", "Nearest Predictions", model::listPredictions).buttonLabel("List").requiresWorld());
        add(new BoolSetting("lush", "Lush Cave Pings", true));
        add(new ActionSetting("nearestLush", "Nearest Lush Caves", model::listLush).buttonLabel("List").requiresWorld());
        add(new BoolSetting("azalea", "Azalea Trees", true));
        add(new StringSetting("seed", "World Seed", "").description("Wins over SeedcrackerX. Enter saves.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("version", "World Version", MenuModel.AUTO, model.versions())
            .description("Auto: the version the server reports.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("serverType", "Server Type", MenuModel.AUTO, model.serverTypes())
            .description("Auto: from the server's brand.").requiresWorld().keepOnReset());
        add(new IntSetting("radius", "Search Radius", MenuModel.DEFAULT_RADIUS, MenuModel.MIN_RADIUS, MenuModel.MAX_RADIUS, MenuModel.RADIUS_STEP)
            .unit("blocks"));
        add(new IntSetting("memory", "Search Memory", model.defaultMemoryGb(), 1, model.maxMemoryGb(), 1).unit("GB"));
        add(new StringSetting("status", "Status", "").readonlySummary());
        add(new StringSetting("candidates", "Candidates", "").readonlySummary());
        add(new ActionSetting("search", "Seed Search", model::search).buttonLabel("Search").requiresWorld()
            .visibleWhen(() -> !model.searching()));
        add(new ActionSetting("stop", "Seed Search", model::stop).buttonLabel("Stop").visibleWhen(model::searching));
        model.onEnabledChanged(this::setEnabledSilently);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean joined = mc.player != null;
            if (joined && !inWorld) model.setEnabled(isEnabled());
            inWorld = joined;
        });
    }

    @Override
    public boolean settingsShareable() {
        return false;
    }

    @Override
    public void onEnable() {
        model.setEnabled(true);
    }

    @Override
    public void onDisable() {
        model.setEnabled(false);
    }

    @Override
    protected String externalSettingValue(String id) {
        return switch (id) {
            case "masked" -> String.valueOf(model.masked());
            case "lush" -> String.valueOf(model.lush());
            case "azalea" -> String.valueOf(model.azalea());
            case "seed" -> model.inWorld() && model.seed() != null ? model.seed() : "";
            case "version" -> model.version();
            case "serverType" -> model.serverType();
            case "radius" -> String.valueOf(model.radius());
            case "memory" -> String.valueOf(model.memoryGb());
            case "status" -> model.status();
            case "candidates" -> model.candidates();
            default -> null;
        };
    }

    @Override
    protected boolean setExternalSettingValue(String id, String value) {
        switch (id) {
            case "masked" -> model.setMasked(Boolean.parseBoolean(value));
            case "lush" -> model.setLush(Boolean.parseBoolean(value));
            case "azalea" -> model.setAzalea(Boolean.parseBoolean(value));
            case "seed" -> {
                if (model.inWorld()) model.setSeed(value.isBlank() ? null : value.trim());
            }
            case "version" -> {
                if (model.inWorld()) model.setVersion(value);
            }
            case "serverType" -> {
                if (model.inWorld()) model.setServerType(value);
            }
            case "radius" -> model.setRadius(Integer.parseInt(value));
            case "memory" -> model.setMemoryGb(Integer.parseInt(value));
            case "status", "candidates" -> {
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
