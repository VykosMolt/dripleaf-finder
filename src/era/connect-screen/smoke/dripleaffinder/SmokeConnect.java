package dripleaffinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

final class SmokeConnect {
    static boolean connect(Minecraft mc, String address) {
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return false;
        ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address), new ServerData("smoke", address, false));
        return true;
    }
}
