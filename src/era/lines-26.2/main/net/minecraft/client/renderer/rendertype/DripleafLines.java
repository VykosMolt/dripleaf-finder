package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dripleaffinder.Finder;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public final class DripleafLines {
    public static final RenderType SEE_THROUGH;

    static {
        RenderPipeline.Builder builder = RenderPipeline.builder();
        for (BindGroupLayout layout : RenderPipelines.LINES.getBindGroupLayouts()) builder.withBindGroupLayout(layout);
        RenderPipeline pipeline = builder
            .withLocation(Identifier.fromNamespaceAndPath("dripleaffinder", "pipeline/lines_see_through"))
            .withVertexShader(Identifier.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("dripleaffinder", "core/lines_fogless"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withDepthStencilState(Optional.empty())
            .build();
        SEE_THROUGH = RenderType.create("dripleaffinder_lines_see_through", RenderSetup.builder(pipeline).createRenderSetup());
    }

    public static void vertex(VertexConsumer lines, PoseStack.Pose pose, float x, float y, float z, int color, Vector3f normal) {
        lines.addVertex(pose, x, y, z).setColor(color).setNormal(pose, normal).setLineWidth(Finder.LINE_WIDTH);
    }

    private DripleafLines() {
    }
}
