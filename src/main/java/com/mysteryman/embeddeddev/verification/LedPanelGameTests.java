package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.wire.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class LedPanelGameTests {
    @GameTest(template="test_empty",timeoutTicks=60)
    public static void nineIndependentColoredLeds(GameTestHelper h) {
        var boardPos=new BlockPos(2,2,2);var panelPos=new BlockPos(5,2,5);
        h.setBlock(boardPos,ModBlocks.SystemBoard51.get());h.setBlock(panelPos,ModBlocks.LED.get());
        SystemBoardEntity board=h.getBlockEntity(boardPos);PeripheralEntity panel=h.getBlockEntity(panelPos);
        var names=java.util.stream.IntStream.range(0,9).mapToObj(LedPanel::defaultName).toList();
        int[] colors={0xFF0000,0x00FF00,0x0000FF,0xFFFFFF,0xFF8000,0xFF00FF,0x00FFFF,0x808080,0x123456};
        panel.configureLeds(names,colors);
        var pins=new ArrayList<FaceBinding.Pin>();for(int i=0;i<9;i++)pins.add(new FaceBinding.Pin(names.get(i),FaceBinding.Kind.GPIO,i));
        board.configureGroups(1,List.of(new PinGroup("A",new FaceBinding(pins.subList(0,5))),new PinGroup("B",new FaceBinding(pins.subList(5,9))),PinGroup.empty(2),PinGroup.empty(3)));
        var net=CableNetwork.get(h.getLevel());
        for(int i=0;i<9;i++){
            var from=new CableNetwork.End(h.absolutePos(boardPos),CableNetwork.boardSocket(1,i<5?0:1));
            var to=new CableNetwork.End(h.absolutePos(panelPos),CableNetwork.buttonSocket(i));
            var hit=new BlockHitResult(to.anchor(),Direction.UP,to.pos(),false);
            h.assertTrue(to.equals(CableNetwork.target(h.getLevel(),hit)),"Lamp "+i+" has its own hit target");
            net.connect(h.getLevel(),from,to,net.prepare(h.getLevel(),from,to).automatic());
        }
        h.runAfterDelay(5,()->{
            board.hardware.writePort(0,0x55);board.hardware.writePort(1,1);
            h.runAfterDelay(3,()->{
                h.assertTrue(panel.leds().litMask()==0x155,"Nine separate GPIO states drive nine LEDs");
                var saved=panel.saveCustomOnly(h.getLevel().registryAccess());
                var copy=new PeripheralEntity(h.absolutePos(panelPos),panel.getBlockState());copy.loadCustomOnly(saved,h.getLevel().registryAccess());
                h.assertTrue(copy.leds().count()==9&&copy.leds().color(8)==0x123456&&copy.leds().litMask()==0,"Colors and names persist; stale live state is cleared on load");
                var update=panel.getUpdateTag(h.getLevel().registryAccess());copy.handleUpdateTag(update,h.getLevel().registryAccess());
                h.assertTrue(copy.leds().litMask()==0x155,"Client receives all nine LED states");
                var renamed=new ArrayList<>(names);renamed.set(8,"STATUS");panel.configureLeds(renamed,colors);
                h.assertTrue(net.has(new CableNetwork.End(h.absolutePos(panelPos),CableNetwork.buttonSocket(8))),"Renaming and recoloring preserve physical wire");
                panel.configureLeds(List.of("LED"),new int[]{0xABCDEF});
                for(int i=1;i<9;i++)h.assertTrue(!net.has(new CableNetwork.End(h.absolutePos(panelPos),CableNetwork.buttonSocket(i))),"Shrinking removes only excess cables");
                h.assertTrue(net.has(new CableNetwork.End(h.absolutePos(panelPos),CableNetwork.MODULE)),"First lamp's original cable survives");
                h.succeed();
            });
        });
    }
}
