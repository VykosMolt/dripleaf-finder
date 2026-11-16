package dripleaffinder;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.FogParameters;

final class Gl {
    static final float WIDTH = 1.5f;
    private static FogParameters fog;

    static void setup() {
        RenderSystem.setShader(CoreShaders.RENDERTYPE_LINES);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(WIDTH);
        fog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);
    }

    static void restore() {
        RenderSystem.setShaderFog(fog);
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
