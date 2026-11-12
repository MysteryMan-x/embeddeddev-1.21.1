package com.mysteryman.embeddeddev.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mysteryman.embeddeddev.block.SevenSegmentBlock;
import com.mysteryman.embeddeddev.block.SevenSegmentEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

public final class SevenSegmentRenderer implements BlockEntityRenderer<SevenSegmentEntity> {
    private static final float[][][] SEGMENTS = {
            bar(2.1F, 1.0F, 7.5F, 1.8F, true), bar(7.6F, 1.9F, 8.4F, 4.9F, false),
            bar(7.6F, 5.9F, 8.4F, 8.9F, false), bar(2.1F, 9.0F, 7.5F, 9.8F, true),
            bar(1.2F, 5.9F, 2.0F, 8.9F, false), bar(1.2F, 1.9F, 2.0F, 4.9F, false),
            bar(2.1F, 5.0F, 7.5F, 5.8F, true), bar(9.0F, 9.0F, 10.0F, 10.0F, true)
    };

    public SevenSegmentRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SevenSegmentEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (display.getBlockState().getValue(SevenSegmentBlock.FACING)) {
            case SOUTH -> 180; case WEST -> 90; case EAST -> 270; default -> 0;
        }));
        pose.translate(-.5, 0, -.5);
        // Viewed from the front, right is west on a north-facing module.
        pose.translate(13.0 / 16, 13.0 / 16, 2.87 / 16);
        pose.scale(-1F / 16, -1F / 16, 1F / 16);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        for (int segment = 0; segment < SEGMENTS.length; segment++) {
            boolean lit = (display.segments() & (1 << segment)) != 0;
            int red = lit ? 255 : 47, green = lit ? 48 : 8, blue = lit ? 30 : 9;
            var polygon = SEGMENTS[segment];
            // Triangle fans use one repeated vertex to fit the quad buffer format.
            for (int edge = 1; edge < polygon.length - 1; edge++) {
                vertex(vertices, matrix, polygon[0], red, green, blue);
                vertex(vertices, matrix, polygon[edge], red, green, blue);
                vertex(vertices, matrix, polygon[edge + 1], red, green, blue);
                vertex(vertices, matrix, polygon[edge + 1], red, green, blue);
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, float[] point, int red, int green, int blue) {
        vertices.addVertex(matrix, point[0], point[1], 0).setColor(red, green, blue, 255);
    }
    private static float[][] bar(float x0, float y0, float x1, float y1, boolean horizontal) {
        float bevel = Math.min(x1 - x0, y1 - y0) / 2;
        if (horizontal) return new float[][] {{x0 + bevel, y0}, {x1 - bevel, y0}, {x1, (y0 + y1) / 2},
                {x1 - bevel, y1}, {x0 + bevel, y1}, {x0, (y0 + y1) / 2}};
        return new float[][] {{(x0 + x1) / 2, y0}, {x1, y0 + bevel}, {x1, y1 - bevel},
                {(x0 + x1) / 2, y1}, {x0, y1 - bevel}, {x0, y0 + bevel}};
    }
}
