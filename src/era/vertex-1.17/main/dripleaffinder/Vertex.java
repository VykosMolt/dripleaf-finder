package dripleaffinder;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

final class Vertex {
    static void add(BufferBuilder lines, PoseStack.Pose pose, float x, float y, float z, int color, float nx, float ny, float nz) {
        lines.vertex(pose.pose(), x, y, z).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
            .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
