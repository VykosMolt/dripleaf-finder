package dripleaffinder.autism;

import autismclient.api.module.ActionSetting;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.SimpleModule;
import autismclient.api.module.StringSetting;
import dripleaffinder.MenuModel;

final class SplicedDripleaf extends SimpleModule {
    private final MenuModel model;

    SplicedDripleaf(MenuModel model) {
        super("spliced", "Spliced Dripleaf Finder", "Finds spliced small dripleaf.");
        this.model = model;
        add(new BoolSetting("masked", "Masked Server", false));
        add(new ActionSetting("nearest", "Nearest Predictions", model::listPredictions).buttonLabel("List"));
        add(new BoolSetting("lush", "Lush Cave Pings", true));
        add(new ActionSetting("nearestLush", "Nearest Lush Caves", model::listLush).buttonLabel("List"));
        add(new BoolSetting("azalea", "Azalea Trees", true));
        add(new StringSetting("seed", "World Seed", "").description("Press Enter to save.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("version", "World Version", MenuModel.AUTO, model.versions())
            .description("Auto: server's version.").requiresWorld().keepOnReset());
        add(new ChoiceSetting("serverType", "Server Type", MenuModel.AUTO, model.serverTypes())
            .description("Auto: server's brand.").requiresWorld().keepOnReset());
        add(new IntSetting("radius", "Search Radius", MenuModel.DEFAULT_RADIUS, MenuModel.MIN_RADIUS, MenuModel.MAX_RADIUS, MenuModel.RADIUS_STEP)
            .unit("blocks"));
        add(new IntSetting("memory", "Search Memory", model.defaultMemoryGb(), Math.min(MenuModel.MIN_MEMORY_GB, model.maxMemoryGb()),
            model.maxMemoryGb(), 1).unit("GB").description("For the background search."));
        add(new StringSetting("status", "Status", "").readonlySummary());
        add(new StringSetting("candidates", "Candidates", "").readonlySummary());
        add(new ActionSetting("search", "Seed Search", model::search).buttonLabel("Search").availableOffline());
        add(new ActionSetting("stop", "Stop Search", model::stop).buttonLabel("Stop").availableOffline());
        model.follow(this::isEnabled, this::setEnabled);
    }

    @Override
    public boolean settingsShareable() {
        return false;
    }

    @Override
    public void onDisable() {
        model.turnedOff();
    }

    @Override
    protected String externalSettingValue(String id) {
        return switch (id) {
            case "masked" -> String.valueOf(model.masked());
            case "lush" -> String.valueOf(model.lush());
            case "azalea" -> String.valueOf(model.azalea());
            case "seed" -> {
                String seed = model.seed();
                yield seed == null ? "" : seed;
            }
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
