package com.mysteryman.embeddeddev.block;

import com.mysteryman.embeddeddev.compat.SableCompat;
import com.mysteryman.embeddeddev.mcu.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Optional physics input and adjacent motor I/O. No PID, mixing or orientation estimation. */
public final class BusModuleEntity extends BlockEntity implements I2cModule {
    private final RegisterI2cDevice device;
    private int sequence;
    private long seenActivity,lastActivity,lastSampleTime=-1;
    private Vector3d previousVelocity;
    private SableCompat.State telemetry;
    public BusModuleEntity(BlockPos p,BlockState s) {
        super(ModBlockEntities.BUS_MODULE.get(),p,s);
        device=s.getBlock() instanceof BusModuleBlock b ? switch(b.kind()) {
            case MPU6050 -> new Mpu6050(); case ALTIMETER -> new Altimeter(); case MOTOR -> new MotorDriver();
        } : new MotorDriver();
    }
    @Override public RegisterI2cDevice i2cDevice() { return device; }
    public SableCompat.State telemetry() { return telemetry; }
    public void address(int address) {
        if(device instanceof MotorDriver motor) { if(motor.armed()) return; motor.address(address); }
        if(device instanceof Mpu6050 mpu) mpu.ad0(address==0x69);
        setChanged();
    }
    public void changeAddress() {
        if(device instanceof MotorDriver motor && !motor.armed()) address(motor.address()==0x67?0x60:motor.address()+1);
        if(device instanceof Mpu6050 mpu) address(mpu.address()==0x68?0x69:0x68);
    }
    public String status() {
        if(device instanceof MotorDriver motor) return (motor.armed()?"ARMED":"SAFE")+" / mask="+Integer.toHexString(device.peek(4));
        if(device instanceof Mpu6050 mpu) return mpu.sleeping()?"SLEEP":"RAW IMU";
        return String.format(java.util.Locale.ROOT,"Y %.2f m",SableCompat.worldPosition(level,worldPosition.getCenter()).y);
    }
    private boolean motor(BlockPos p) {
        return level.hasChunkAt(p) && BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString().equals("create:creative_motor");
    }
    private void rpm(BlockPos pos,int rpm) {
        if(level==null || !motor(pos)) return;
        try {
            BlockEntity entity=level.getBlockEntity(pos); if(entity==null)return;
            Object setting=entity.getClass().getField("generatedSpeed").get(entity); if(setting==null)return;
            if(((Number)setting.getClass().getMethod("getValue").invoke(setting)).intValue()!=rpm) {
                setting.getClass().getMethod("setValue",int.class).invoke(setting,rpm); entity.setChanged();
            }
        } catch(ReflectiveOperationException error) { if(device instanceof MotorDriver motor) motor.disarm(); }
    }
    public void stopMotors() {
        if(!(device instanceof MotorDriver motor))return;
        motor.disarm(); if(level!=null) for(Direction face:Direction.values()) rpm(worldPosition.relative(face),0);
    }
    public static void tick(Level l,BlockPos p,BlockState state,BusModuleEntity e) {
        if(!(l instanceof ServerLevel server)) return;
        if(e.device instanceof MotorDriver motor) {
            SystemBoardEntity board=SystemBoardEntity.connectedModuleBoard(l,p);
            boolean running=board!=null && board.isRunning(); int mask=0;
            if(motor.activity()!=e.seenActivity) { e.seenActivity=motor.activity(); e.lastActivity=l.getGameTime(); }
            if(!running || l.getGameTime()-e.lastActivity>4) motor.disarm();
            for(Direction face:Direction.values()) {
                BlockPos neighbor=p.relative(face); if(e.motor(neighbor)) mask|=1<<face.get3DDataValue();
                e.rpm(neighbor,motor.armed()?motor.output(face.get3DDataValue()):0);
            }
            motor.publish(mask,running);
        } else {
            e.telemetry=SableCompat.sample(server,p);
            var sample=e.telemetry;
            if(e.device instanceof Altimeter alt) {
                double y=sample==null?p.getCenter().y:sample.position().y;
                alt.sample(e.sequence++,y,sample==null?0:sample.verticalSpeed(),sample!=null);
            } else if(e.device instanceof Mpu6050 mpu) {
                Vector3d force=new Vector3d(0,9.80665,0),rate=new Vector3d();
                if(sample!=null) {
                    Vector3d velocity=new Vector3d(sample.linearVelocity());
                    long elapsed=l.getGameTime()-e.lastSampleTime;
                    if(e.previousVelocity!=null && elapsed>0 && elapsed<=4) force.set(velocity).sub(e.previousVelocity).mul(20.0/elapsed).add(0,sample.gravity(),0);
                    else force.set(0,sample.gravity(),0);
                    e.previousVelocity=velocity; sample.orientation().transformInverse(force);
                    rate.set(sample.rateX(),sample.rateY(),sample.rateZ());
                }
                e.lastSampleTime=l.getGameTime();
                mpu.sample(force.x/9.80665,-force.z/9.80665,force.y/9.80665,rate.x,-rate.z,rate.y,25);
            }
        }
        e.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.saveAdditional(tag,r); tag.putInt("Address",device.address());
        if(device instanceof Altimeter alt) tag.putInt("HomeCm",alt.homeCm());
        if(device instanceof Mpu6050 mpu) tag.putIntArray("MpuConfig",mpu.saveConfiguration());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r) {
        super.loadAdditional(tag,r);
        if(device instanceof MotorDriver motor) { motor.disarm(); motor.address(Math.clamp(tag.getInt("Address"),0x60,0x67)); }
        if(device instanceof Mpu6050 mpu) { mpu.ad0(tag.getInt("Address")==0x69); mpu.restoreConfiguration(tag.getIntArray("MpuConfig")); }
        if(device instanceof Altimeter alt) alt.homeCm(tag.getInt("HomeCm"));
    }
}
