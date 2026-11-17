package dripleaffinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

final class Ui {
    static void register(Finder finder) {
        KeyMapping toggle = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.dripleaffinder.toggle", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggle.consumeClick()) finder.toggle();
        });
        if (!DripleafClient.autismAddon()) {
            KeyMapping menu = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.dripleaffinder.menu", InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));
            MenuModel model = DripleafClient.menu();
            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                while (menu.consumeClick()) mc.setScreen(new MenuScreen(model));
            });
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
            dispatcher.register(Commands.<FabricClientCommandSource>tree(finder)));
    }
}
