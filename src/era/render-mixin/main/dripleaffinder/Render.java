package dripleaffinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.DripleafLines;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Render {
    static Finder finder;

    static void register(Finder finder) {
        Render.finder = finder;
    }

    public static void draw(Camera camera, Matrix4f view) {
        if (finder == null) return;
        Vec3 at = camera.position();
        MultiBufferSource.BufferSource consumers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack stack = new PoseStack();
        stack.mulPose(view);
        PoseStack.Pose pose = stack.last();
        VertexConsumer[] lines = {null};
        finder.boxes(at.x, at.y, at.z, (box, color) -> {
            if (lines[0] == null) lines[0] = consumers.getBuffer(DripleafLines.SEE_THROUGH);
            box(pose, lines[0], box.move(-at.x, -at.y, -at.z), color);
        });
        if (lines[0] != null) consumers.endBatch(DripleafLines.SEE_THROUGH);
    }

    static void box(PoseStack.Pose pose, VertexConsumer lines, AABB b, int color) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) line(pose, lines, b.minX, y, z, b.maxX, y, z, color);
        for (double x : xs) for (double z : zs) line(pose, lines, x, b.minY, z, x, b.maxY, z, color);
        for (double x : xs) for (double y : ys) line(pose, lines, x, y, b.minZ, x, y, b.maxZ, color);
    }

    static void line(PoseStack.Pose pose, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() < 1e-8f) return;
        normal.normalize();
        DripleafLines.vertex(lines, pose, (float) x1, (float) y1, (float) z1, color, normal);
        DripleafLines.vertex(lines, pose, (float) x2, (float) y2, (float) z2, color, normal);
    }
}
