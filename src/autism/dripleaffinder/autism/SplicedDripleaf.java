package dripleaffinder.autism;

import autismclient.api.event.AddonEvents;
import autismclient.api.module.ActionSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.SimpleModule;
import autismclient.api.module.StringSetting;
import dripleaffinder.MenuModel;

final class SplicedDripleaf extends SimpleModule {
    private final MenuModel model;
    private final ActionSetting search;

    SplicedDripleaf(MenuModel model) {
        super("spliced", "Spliced Dripleaf Finder", "Finds small dripleaf top halves that have no bottom half.");
        this.model = model;
        add(new BoolSetting("masked", "Masked Server", false));
        add(new ActionSetting("nearest", "Nearest Predictions", model::listPredictions).buttonLabel("List"));
        add(new BoolSetting("lush", "Lush Cave Pings", true));
        add(new ActionSetting("nearestLush", "Nearest Lush Caves", model::listLush).buttonLabel("List"));
        add(new BoolSetting("azalea", "Azalea Trees", true));
        add(new StringSetting("seed", "World Seed", "").description("Wins over SeedcrackerX. Enter saves.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("version", "World Version", MenuModel.AUTO, model.versions())
            .description("Auto: the version the server reports.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("serverType", "Server Type", MenuModel.AUTO, model.serverTypes())
            .description("Auto: from the server's brand.").requiresWorld().keepOnReset());
        add(new IntSetting("radius", "Search Radius", MenuModel.MAX_RADIUS / 4, MenuModel.MIN_RADIUS, MenuModel.MAX_RADIUS, MenuModel.RADIUS_STEP)
            .unit("blocks"));
        add(new IntSetting("memory", "Search Memory", Math.min(4, model.maxMemoryGb()), 1, model.maxMemoryGb(), 1).unit("GB"));
        add(new StringSetting("status", "Status", "").readonlySummary());
        search = add(new ActionSetting("search", "Seed Search", this::searchOrStop).buttonLabel("Search").requiresWorld());
        model.onEnabledChanged(this::setEnabledSilently);
        AddonEvents.onTick(mc -> search.buttonLabel(model.searching() ? "Stop" : "Search"));
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

    private void searchOrStop() {
        if (model.searching()) model.stop();
        else model.search();
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
            case "status" -> {
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
