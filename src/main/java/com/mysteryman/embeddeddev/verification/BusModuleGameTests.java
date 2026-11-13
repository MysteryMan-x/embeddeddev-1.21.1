package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.mcu.*;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class BusModuleGameTests {
    private static void write(I2cDevice d,int reg,int... values){d.begin(false);d.write(reg);for(int v:values)d.write(v);d.end();}
    private static int speed(net.minecraft.world.level.block.entity.BlockEntity e){
        try {Object setting=e.getClass().getField("generatedSpeed").get(e);return ((Number)setting.getClass().getMethod("getValue").invoke(setting)).intValue();}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    @GameTest(template="test_empty",timeoutTicks=40)
    public static void standaloneRawSensorsAndSavedAddress(GameTestHelper h){
        BlockPos p=new BlockPos(3,2,3),q=new BlockPos(5,2,3);
        h.setBlock(p,ModBlocks.MPU6050.get());h.setBlock(q,ModBlocks.ALTIMETER.get());
        BusModuleEntity imu=h.getBlockEntity(p),alt=h.getBlockEntity(q);Mpu6050 mpu=(Mpu6050)imu.i2cDevice();
        write(mpu,0x6b,0);BusModuleEntity.tick(h.getLevel(),h.absolutePos(p),imu.getBlockState(),imu);
        h.assertTrue(mpu.peek(0x3f)==0x40&&mpu.peek(0x43)==0,"Stationary MPU reports +1 g up and zero gyro with no physics body");
        BusModuleEntity.tick(h.getLevel(),h.absolutePos(q),alt.getBlockState(),alt);
        h.assertTrue(alt.i2cDevice().peek(0)==0x41,"Standalone height sensor responds without Create or a sublevel");
        imu.address(0x69);var tag=imu.saveCustomOnly(h.getLevel().registryAccess());var restored=new BusModuleEntity(h.absolutePos(p),imu.getBlockState());
        restored.loadCustomOnly(tag,h.getLevel().registryAccess());h.assertTrue(restored.i2cDevice().address()==0x69,"AD0 persists");
        h.assertTrue(!((Mpu6050)restored.i2cDevice()).sleeping(),"Power/range configuration survives reload alongside a resumed MCU");h.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=50)
    public static void motorDriverControlsOnlyNeighborsAndTimesOut(GameTestHelper h){
        if(!ModList.get().isLoaded("create")){h.succeed();return;}
        BlockPos p=new BlockPos(4,2,4),b=new BlockPos(1,2,1),remote=new BlockPos(7,2,4);
        h.setBlock(p,ModBlocks.MOTOR_INTERFACE.get());h.setBlock(b,ModBlocks.SystemBoard51.get());
        var motor=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:creative_motor")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP);
        for(Direction face:Direction.values())h.setBlock(p.relative(face),motor);h.setBlock(remote,motor);
        SystemBoardEntity board=h.getBlockEntity(b);board.configure(1,new FaceBinding(List.of(new FaceBinding.Pin("SDA",FaceBinding.Kind.GPIO,14),new FaceBinding.Pin("SCL",FaceBinding.Kind.GPIO,15))));
        board.flash(":0200000080FE80\n:00000001FF\n","watchdog-test.hex");
        var cables=CableNetwork.get(h.getLevel());var a=new CableNetwork.End(h.absolutePos(b),1);var end=new CableNetwork.End(h.absolutePos(p),CableNetwork.MODULE);
        // A motor directly above the interface requires approaching from its clear south side.
        var draft=cables.prepare(h.getLevel(),a,end);cables.connect(h.getLevel(),a,end,draft.automatic());board.run();
        h.runAfterDelay(5,()->{
            board.wiring.refresh(h.getLevel());BusModuleEntity e=h.getBlockEntity(p);MotorDriver d=(MotorDriver)e.i2cDevice();
            int before=speed(h.getBlockEntity(remote));write(d,1,0xa5);write(d,0x20,40,0,176,255,60,0,70,0,80,0,90,0,0xa5);
            BusModuleEntity.tick(h.getLevel(),h.absolutePos(p),e.getBlockState(),e);
            h.assertTrue(speed(h.getBlockEntity(p.above()))==-80&&speed(h.getBlockEntity(p.east()))==90,"Independent signed RPM reaches adjacent faces");
            h.assertTrue(speed(h.getBlockEntity(remote))==before,"Non-adjacent motor is untouched");
            h.runAfterDelay(7,()->{
                h.assertTrue(!d.armed()&&speed(h.getBlockEntity(p.east()))==0,"Missing heartbeat disarms and stops adjacent motors");
                h.assertTrue(speed(h.getBlockEntity(remote))==before,"Watchdog never modifies distant motors");h.succeed();
            });
        });
    }
}
