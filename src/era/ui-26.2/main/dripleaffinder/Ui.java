package dripleaffinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

final class Ui {
    static void register(Finder finder) {
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.dripleaffinder.toggle", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) finder.toggle();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
            dispatcher.register(Commands.<FabricClientCommandSource>tree(finder)));
    }
}
