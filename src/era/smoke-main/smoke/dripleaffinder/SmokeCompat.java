package dripleaffinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class SmokeCompat {
    static void screenshot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> { });
    }
}
