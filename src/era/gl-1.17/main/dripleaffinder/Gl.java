package dripleaffinder;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;

final class Gl {
    static final float WIDTH = 1.5f;
    private static float fogStart;

    static void setup() {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(WIDTH);
        fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
    }

    static void restore() {
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
