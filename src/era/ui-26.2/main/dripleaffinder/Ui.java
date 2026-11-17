package dripleaffinder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class Ui {
    static void register(Finder finder) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
            dispatcher.register(Commands.<FabricClientCommandSource>tree(finder)));
    }
}
