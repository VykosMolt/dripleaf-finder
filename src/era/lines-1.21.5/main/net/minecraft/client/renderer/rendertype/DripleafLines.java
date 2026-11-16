package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public final class DripleafLines {
    static final float WIDTH = 1.5f;
    public static final RenderType SEE_THROUGH;

    static {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("dripleaffinder", "pipeline/lines_see_through"))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
        SEE_THROUGH = RenderType.create("dripleaffinder_lines_see_through", 1536, pipeline, RenderType.CompositeState.builder()
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(WIDTH))).createCompositeState(false));
    }

    public static void vertex(VertexConsumer lines, PoseStack.Pose pose, float x, float y, float z, int color, Vector3f normal) {
        lines.addVertex(pose, x, y, z).setColor(color).setNormal(pose, normal);
    }

    private DripleafLines() {
    }
}
