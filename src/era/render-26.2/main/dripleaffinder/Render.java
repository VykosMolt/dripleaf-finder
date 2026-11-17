package dripleaffinder;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.DripleafLines;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Render {
    static void register(Finder finder) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            finder.boxes(camera.x, camera.y, camera.z, (box, color) -> {
                AABB relative = box.move(-camera.x, -camera.y, -camera.z);
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DripleafLines.SEE_THROUGH, (pose, lines) ->
                    Boxes.edges(relative, (x, y, z, normal) -> DripleafLines.vertex(lines, pose, x, y, z, color, normal)));
            });
        });
    }
}
