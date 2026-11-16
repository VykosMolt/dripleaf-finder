package dripleaffinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.DripleafLines;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class Render {
    static void register(Finder finder) {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Vec3 camera = context.worldState().cameraRenderState.pos;
            finder.boxes(camera.x, camera.y, camera.z, (box, color) -> box(context, box.move(-camera.x, -camera.y, -camera.z), color));
        });
    }

    static void box(WorldRenderContext context, AABB b, int color) {
        context.commandQueue().submitCustomGeometry(context.matrices(), DripleafLines.SEE_THROUGH, (pose, lines) -> {
            double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
            for (double y : ys) for (double z : zs) line(pose, lines, b.minX, y, z, b.maxX, y, z, color);
            for (double x : xs) for (double z : zs) line(pose, lines, x, b.minY, z, x, b.maxY, z, color);
            for (double x : xs) for (double y : ys) line(pose, lines, x, y, b.minZ, x, y, b.maxZ, color);
        });
    }

    static void line(PoseStack.Pose pose, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() < 1e-8f) return;
        normal.normalize();
        DripleafLines.vertex(lines, pose, (float) x1, (float) y1, (float) z1, color, normal);
        DripleafLines.vertex(lines, pose, (float) x2, (float) y2, (float) z2, color, normal);
    }
}
