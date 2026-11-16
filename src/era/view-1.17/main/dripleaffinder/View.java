package dripleaffinder;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

final class View {
    static PoseStack.Pose pose(WorldRenderContext context) {
        return new PoseStack().last();
    }
}
