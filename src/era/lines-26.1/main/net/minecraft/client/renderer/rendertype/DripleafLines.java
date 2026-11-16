package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public final class DripleafLines {
    public static final RenderType SEE_THROUGH;

    static {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("dripleaffinder", "pipeline/lines_see_through"))
            .withVertexShader(Identifier.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(Optional.empty())
            .build();
        SEE_THROUGH = RenderType.create("dripleaffinder_lines_see_through", RenderSetup.builder(pipeline).createRenderSetup());
    }

    static final float WIDTH = 1.5f;

    public static void vertex(VertexConsumer lines, PoseStack.Pose pose, float x, float y, float z, int color, Vector3f normal) {
        lines.addVertex(pose, x, y, z).setColor(color).setNormal(pose, normal).setLineWidth(WIDTH);
    }

    private DripleafLines() {
    }
}
