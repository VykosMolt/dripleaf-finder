package dripleaffinder;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Render {
    static void register(Finder finder) {
        WorldRenderEvents.LAST.register(context -> {
            Vec3 camera = context.camera().getPosition();
            PoseStack.Pose pose = context.matrixStack().last();
            BufferBuilder[] buffer = {null};
            finder.boxes(camera.x, camera.y, camera.z, (box, color) -> {
                if (buffer[0] == null) buffer[0] = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                box(pose, buffer[0], box.move(-camera.x, -camera.y, -camera.z), color);
            });
            if (buffer[0] == null) return;
            MeshData mesh = buffer[0].build();
            if (mesh == null) return;
            Gl.setup();
            BufferUploader.drawWithShader(mesh);
            Gl.restore();
        });
    }

    static void box(PoseStack.Pose pose, BufferBuilder lines, AABB b, int color) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) line(pose, lines, b.minX, y, z, b.maxX, y, z, color);
        for (double x : xs) for (double z : zs) line(pose, lines, x, b.minY, z, x, b.maxY, z, color);
        for (double x : xs) for (double y : ys) line(pose, lines, x, y, b.minZ, x, y, b.maxZ, color);
    }

    static void line(PoseStack.Pose pose, BufferBuilder lines, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1), length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4f) return;
        dx /= length;
        dy /= length;
        dz /= length;
        lines.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, dx, dy, dz);
        lines.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, dx, dy, dz);
    }
}
