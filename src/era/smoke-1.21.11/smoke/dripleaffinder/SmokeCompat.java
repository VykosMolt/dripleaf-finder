package dripleaffinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

final class SmokeCompat {
    static void screenshot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> { });
    }

    static Screen screen(Minecraft mc) {
        return mc.screen;
    }
}
