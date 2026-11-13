package com.mysteryman.embeddeddev.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mysteryman.embeddeddev.block.ButtonPanel;
import com.mysteryman.embeddeddev.block.PeripheralEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;

public final class ButtonPanelRenderer implements BlockEntityRenderer<PeripheralEntity> {
    public ButtonPanelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(PeripheralEntity module, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if(module.leds()!=null){renderLeds(module,pose,buffers,light,overlay);return;}
        var buttons = module.buttons();
        if (buttons == null) return;
        for (int key = 0; key < buttons.count(); key++) {
            double x = ButtonPanel.x(key), z = ButtonPanel.z(key);
            part(Blocks.BLACK_CONCRETE, x, z, .084, .196, .206, pose, buffers, light, overlay);
            boolean pressed = buttons.pressed(key);
            part(pressed ? Blocks.ORANGE_CONCRETE : Blocks.RED_CONCRETE, x, z, .195, pressed ? .264 : .333, .175,
                    pose, buffers, light, overlay);
            var font=Minecraft.getInstance().font;String label=buttons.names().get(key);
            float scale=Math.min(.0045F,.15F/Math.max(1,font.width(label)));
            pose.pushPose();pose.translate(x,pressed?.265:.334,z);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(scale,scale,scale);
            font.drawInBatch(label,-font.width(label)/2F,-font.lineHeight/2F,0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    private static void renderLeds(PeripheralEntity module,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var leds=module.leds();
        for(int i=0;i<leds.count();i++) {
            double x=ButtonPanel.x(i),z=ButtonPanel.z(i);
            part(Blocks.BLACK_CONCRETE,x,z,.08,.14,.19,pose,buffers,light,overlay);
            int color=leds.color(i);float gain=leds.lit(i)?1:.16F;
            int r=(int)((color>>16&255)*gain),g=(int)((color>>8&255)*gain),b=(int)((color&255)*gain);
            var v=buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());var m=pose.last().pose();
            float x0=(float)x-.075F,x1=(float)x+.075F,z0=(float)z-.075F,z1=(float)z+.075F,y0=.14F,y1=.29F;
            float[][] quads={{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0},
                {x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0}, {x1,y0,z1,x1,y1,z1,x0,y1,z1,x0,y0,z1},
                {x0,y0,z1,x0,y1,z1,x0,y1,z0,x0,y0,z0}, {x1,y0,z0,x1,y1,z0,x1,y1,z1,x1,y0,z1}};
            for(int face=0;face<quads.length;face++)for(int n=0;n<12;n+=3){float[] q=quads[face];float shade=face==0?1:.75F;v.addVertex(m,q[n],q[n+1],q[n+2]).setColor((int)(r*shade),(int)(g*shade),(int)(b*shade),255);}
            var font=Minecraft.getInstance().font;String label=leds.names().get(i);float scale=Math.min(.0035F,.20F/Math.max(1,font.width(label)));
            pose.pushPose();pose.translate(x,.085,z+.105);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(scale,scale,scale);
            font.drawInBatch(label,-font.width(label)/2F,-font.lineHeight/2F,0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);pose.popPose();
        }
    }
    private static void part(Block block, double x, double z, double bottom, double top, double width,
                             PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(x - width / 2, bottom, z - width / 2);
        pose.scale((float) width, (float) (top - bottom), (float) width);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, light, overlay);
        pose.popPose();
    }
}
