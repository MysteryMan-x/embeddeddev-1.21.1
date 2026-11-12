package com.mysteryman.embeddeddev.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mysteryman.embeddeddev.block.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import org.joml.Matrix4f;

public final class OledRenderer implements BlockEntityRenderer<OledEntity> {
    public OledRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(OledEntity oled, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!oled.enabled) return;
        pose.pushPose(); pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (oled.getBlockState().getValue(OledBlock.FACING)) {
            case SOUTH -> 180; case WEST -> 90; case EAST -> 270; default -> 0;
        }));
        // From outside the north face, screen-right is world west.
        pose.translate(0.375, 0.25, -0.391);
        pose.scale(-0.75F / 128, -0.375F / 64, 1);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        // Merge horizontal pixel runs to keep the renderer inexpensive.
        for (int row = 0; row < 64; row++) {
            int start = -1;
            for (int col = 0; col <= 128; col++) {
                boolean lit = col < 128 && (oled.pixels[(row >>> 3) * 128 + col] & (1 << (row & 7))) != 0;
                if (lit && start < 0) start = col;
                if (!lit && start >= 0) {
                    vertices.addVertex(matrix, start, row, 0).setColor(141, 238, 255, 255);
                    vertices.addVertex(matrix, start, row + 1, 0).setColor(141, 238, 255, 255);
                    vertices.addVertex(matrix, col, row + 1, 0).setColor(141, 238, 255, 255);
                    vertices.addVertex(matrix, col, row, 0).setColor(141, 238, 255, 255);
                    start = -1;
                }
            }
        }
        pose.popPose();
    }
}
