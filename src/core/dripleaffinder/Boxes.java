package dripleaffinder;

import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

final class Boxes {
    interface VertexSink {
        void vertex(float x, float y, float z, Vector3f normal);
    }

    static void edges(AABB box, VertexSink sink) {
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (double y : ys) for (double z : zs) edge(sink, box.minX, y, z, box.maxX, y, z);
        for (double x : xs) for (double z : zs) edge(sink, x, box.minY, z, x, box.maxY, z);
        for (double x : xs) for (double y : ys) edge(sink, x, y, box.minZ, x, y, box.maxZ);
    }

    private static void edge(VertexSink sink, double x1, double y1, double z1, double x2, double y2, double z2) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() < 1e-8f) return;
        normal.normalize();
        sink.vertex((float) x1, (float) y1, (float) z1, normal);
        sink.vertex((float) x2, (float) y2, (float) z2, normal);
    }

    private Boxes() {
    }
}
