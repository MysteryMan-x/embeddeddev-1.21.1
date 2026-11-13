package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.stream.IntStream;

public final class LedPanelScreen extends Screen implements DeviceStateScreen {
    private static final int[] PALETTE={0xF9FFFE,0xF9801D,0xC74EBD,0x3AB3DA,0xFED83D,0x80C71F,0xF38BAA,0x474F52,
            0x9D9D97,0x169C9C,0x8932B8,0x3C44AA,0x835432,0x5E7C16,0xB02E26,0x1D1D21};
    private final BlockPos pos;
    private final String[] names=new String[9];
    private final int[] colors=new int[9];
    private int count,selected,x,y,w,paletteStep;
    private String message="";
    private boolean validColor=true;
    private Button apply;
    public LedPanelScreen(BoardNetwork.State packet) {
        super(Component.translatable("leds.embeddeddev.title"));pos=packet.pos();
        var panel=new LedPanel();panel.load(packet.data());count=panel.count();
        for(int i=0;i<9;i++){names[i]=i<count?panel.names().get(i):LedPanel.defaultName(i);colors[i]=i<count?panel.color(i):LedPanel.DEFAULT_COLOR;}
    }
    @Override protected void init() {
        validColor=true;
        w=Math.min(width-12,390);x=(width-w)/2;y=Math.max(2,(height-233)/2);
        paletteStep=Math.min(24,(w-163)/8);
        addRenderableWidget(BoardScreen.closeButton(x+w-28,y+3,this::onClose));
        addRenderableWidget(CycleButton.<Integer>builder(n->Component.literal(n.toString()))
                .withValues(IntStream.rangeClosed(1,9).boxed().toList()).withInitialValue(count)
                .create(x+10,y+24,w-20,20,Component.translatable("leds.embeddeddev.count"),(b,n)->{count=n;selected=Math.min(selected,n-1);message="";rebuildWidgets();}));
        for(int i=0;i<count;i++){
            final int index=i;
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(i+1)),b->{selected=index;message="";rebuildWidgets();})
                    .bounds(x+10+ButtonPanel.column(i)*43,y+50+ButtonPanel.row(i)*43,39,39).build());
        }
        int right=x+151;
        var name=new EditBox(font,right,y+69,w-163,18,Component.translatable("leds.embeddeddev.name"));
        name.setMaxLength(FaceBinding.MAX_NAME_LENGTH);name.setValue(names[selected]);name.setResponder(v->{names[selected]=v;message="";});addRenderableWidget(name);
        var rgb=new EditBox(font,right,y+106,w-163,18,Component.translatable("leds.embeddeddev.color"));
        rgb.setMaxLength(6);rgb.setValue(String.format("%06X",colors[selected]));
        rgb.setResponder(v->{validColor=v.matches("[0-9a-fA-F]{6}");if(validColor){colors[selected]=Integer.parseInt(v,16);message="";}else message="leds.embeddeddev.invalid_color";});addRenderableWidget(rgb);
        for(int i=0;i<PALETTE.length;i++){
            final int color=PALETTE[i];
            addRenderableWidget(Button.builder(Component.empty(),b->{colors[selected]=color;rgb.setValue(String.format("%06X",color));})
                    .bounds(right+i%8*paletteStep,y+132+i/8*22,paletteStep-3,19).build());
        }
        apply=addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.apply"),b->save())
                .bounds(x+w/2-84,y+207,80,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.cancel"),b->onClose()).bounds(x+w/2+4,y+207,80,20).build());
    }
    private void save(){
        if(!validColor){message="leds.embeddeddev.invalid_color";return;}
        try{
            var labels=ButtonPanel.validate(Arrays.asList(names).subList(0,count));
            String rgb=IntStream.range(0,count).mapToObj(i->String.format("%06X",colors[i])).collect(java.util.stream.Collectors.joining(","));
            PacketDistributor.sendToServer(new BoardNetwork.Action(pos,BoardNetwork.LED_CONFIGURE,rgb,String.join("\n",labels)));apply.active=false;
        }catch(IllegalArgumentException e){message=e.getMessage();}
    }
    @Override public boolean matches(BlockPos pos){return this.pos.equals(pos);}
    @Override public void update(CompoundTag tag){if(!tag.getString("Screen").equals("leds"))return;message=tag.getString("Message");if(message.isEmpty())onClose();else apply.active=true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(minecraft.player==null||com.mysteryman.embeddeddev.compat.SableCompat.distanceSquared(minecraft.level,minecraft.player.position(),pos)>64)onClose();}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);g.fill(x,y,x+w,y+233,0xF018232D);g.fill(x,y,x+w,y+2,0xFF43D9BC);
        g.drawString(font,title,x+10,y+8,0xFFE0F5F0,false);
        g.drawString(font,Component.translatable("leds.embeddeddev.lamp",selected+1),x+151,y+51,0xFFE0F5F0,false);
        g.drawString(font,Component.translatable("leds.embeddeddev.color"),x+151,y+93,0xFFA4B8C6,false);
        String footer=Component.translatable(message.isEmpty()?"leds.embeddeddev.hint":message).getString();
        g.drawString(font,font.plainSubstrByWidth(footer,w-20),x+10,y+186,message.isEmpty()?0xFFA4B8C6:0xFFFFA080,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        super.render(g,mx,my,dt);
        for(int i=0;i<count;i++){
            int left=x+10+ButtonPanel.column(i)*43,top=y+50+ButtonPanel.row(i)*43;
            g.fill(left+8,top+5,left+31,top+12,0xFF000000|colors[i]);
            if(i==selected)g.renderOutline(left,top,39,39,0xFF43D9BC);
        }
        for(int i=0;i<PALETTE.length;i++)g.fill(x+154+i%8*paletteStep,y+135+i/8*22,x+151+(i%8+1)*paletteStep-6,y+148+i/8*22,0xFF000000|PALETTE[i]);
    }
}
