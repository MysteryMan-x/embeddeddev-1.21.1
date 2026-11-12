package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.Ds18b20Block;
import com.mysteryman.embeddeddev.block.Ds18b20Entity;
import com.mysteryman.embeddeddev.block.ModBlocks;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.compat.TemperatureSources;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.Ds18b20;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class TemperatureGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void fourGroupsAndOneWireFirmware(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        BlockPos boardPos = new BlockPos(2, 2, 2), probePos = new BlockPos(6, 2, 5);
        BlockPos oledPos = new BlockPos(6, 2, 2), lamp1 = new BlockPos(2, 2, 6), lamp2 = new BlockPos(4, 2, 6);
        h.setBlock(boardPos, ModBlocks.SystemBoard51.get());
        h.setBlock(probePos.below(), Blocks.CAMPFIRE);
        h.setBlock(probePos, ModBlocks.DS18B20.get().defaultBlockState().setValue(Ds18b20Block.FACING, Direction.DOWN));
        h.setBlock(oledPos, ModBlocks.OLED.get()); h.setBlock(lamp1, ModBlocks.LED.get()); h.setBlock(lamp2, ModBlocks.LED.get());
        SystemBoardEntity board = h.getBlockEntity(boardPos);
        board.crystalSocket().setItem(0, new ItemStack(ModItems.CRYSTAL_12.get()));
        board.flash(Files.readString(Path.of("..", "examples", "ds18b20_read.hex")), "ds18b20_read.hex");
        var groups = List.of(group("Lamp A", "GPIO,0,LED"), group("OLED", "GPIO,14,SDA\nGPIO,15,SCL"),
                group("Temperature", "GPIO,10,DQ"), group("Lamp B", "GPIO,1,LED"));
        for (Direction face : Direction.values()) {
            board.configureGroups(face.get3DDataValue(), groups);
            for (int group = 0; group < PinGroup.COUNT; group++) {
                var socket = new CableNetwork.End(h.absolutePos(boardPos), CableNetwork.boardSocket(face.get3DDataValue(), group));
                var hit = new BlockHitResult(socket.anchor(), face, socket.pos(), false);
                h.assertTrue(socket.equals(CableNetwork.target(level, hit)), "Each direction exposes all four distinct group targets");
            }
        }
        var cables = CableNetwork.get(level);
        var targets = List.of(lamp1, oledPos, probePos, lamp2);
        for (int group = 0; group < PinGroup.COUNT; group++) {
            var from = new CableNetwork.End(h.absolutePos(boardPos), CableNetwork.boardSocket(1, group));
            var to = new CableNetwork.End(h.absolutePos(targets.get(group)), CableNetwork.MODULE);
            cables.connect(level, from, to, cables.prepare(level, from, to).automatic());
        }
        Ds18b20Entity probe = h.getBlockEntity(probePos);
        probe.sample();
        var saved = board.saveCustomOnly(level.registryAccess());
        var copy = new SystemBoardEntity(h.absolutePos(boardPos), board.getBlockState());
        copy.loadCustomOnly(saved, level.registryAccess());
        h.assertTrue(copy.groups(1).equals(groups), "Group names and separate pin maps survive saving");
        var restored = CableNetwork.load(cables.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(restored.has(new CableNetwork.End(h.absolutePos(boardPos), CableNetwork.boardSocket(1, 2))), "Saved wires retain group IDs");
        board.run();
        h.runAfterDelay(25, () -> {
            h.assertTrue(board.cpu.inspectRam(0x3f) == 0xa5, "Firmware completes the GPIO-driven 1-Wire transaction");
            byte[] scratch = new byte[9];
            for (int i = 0; i < scratch.length; i++) scratch[i] = (byte) board.cpu.inspectRam(0x30 + i);
            h.assertTrue((short) ((scratch[0] & 255) | (scratch[1] << 8)) == 95 * 16 && Ds18b20.crc(scratch, 9) == 0,
                    "Firmware receives the sampled campfire temperature with a valid CRC");
            h.assertTrue(board.wiring.statusTag().size() == 4, "Four cables on one face stay independently connected");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty")
    public static void createTankControllerTemperatureWhenInstalled(GameTestHelper h) {
        if (!ModList.get().isLoaded("create")) { h.succeed(); return; }
        var tank = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "fluid_tank"));
        BlockPos controllerPos = new BlockPos(3, 2, 3), memberPos = new BlockPos(4, 2, 3);
        h.setBlock(controllerPos, tank); h.setBlock(memberPos, tank);
        var controller = h.getLevel().getBlockEntity(h.absolutePos(controllerPos));
        var member = h.getLevel().getBlockEntity(h.absolutePos(memberPos));
        h.assertTrue(controller != null && member != null, "Real Create tank entities are present");
        var data = controller.saveCustomOnly(h.getLevel().registryAccess());
        var boiler = new CompoundTag(); boiler.putInt("ActiveHeat", 6); boiler.putInt("Engines", 1);
        data.put("Boiler", boiler); data.putInt("Size", 2); data.putInt("Height", 1);
        controller.loadCustomOnly(data, h.getLevel().registryAccess());
        var memberData = member.saveCustomOnly(h.getLevel().registryAccess());
        memberData.put("Controller", NbtUtils.writeBlockPos(h.absolutePos(controllerPos)));
        member.loadCustomOnly(memberData, h.getLevel().registryAccess());
        var reading = TemperatureSources.read(h.getLevel(), h.absolutePos(memberPos), Direction.EAST);
        h.assertTrue(reading.celsius() == 50 && reading.estimated() && reading.source().equals("temperature.embeddeddev.create"),
                "A probe on a Create multiblock member reads the controller's heat and labels its conversion");
        EmbeddedDev.LOGGER.info("Verified DS18B20 temperature against installed Create tank controller");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void mekanismHeatCapabilityWhenInstalled(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) { h.succeed(); return; }
        var heater = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "resistive_heater"));
        BlockPos pos = new BlockPos(3, 2, 3);
        h.setBlock(pos, heater);
        var reading = TemperatureSources.read(h.getLevel(), h.absolutePos(pos), Direction.UP);
        h.assertTrue(reading.source().equals("temperature.embeddeddev.mekanism") && !reading.estimated()
                        && reading.celsius() > -100 && reading.celsius() < 100,
                "The installed Mekanism heat capability supplies a finite converted Celsius temperature");
        EmbeddedDev.LOGGER.info("Verified DS18B20 against installed Mekanism heat capability: {} C", reading.celsius());
        h.succeed();
    }

    private static PinGroup group(String name, String pins) { return new PinGroup(name, FaceBinding.parse(pins)); }
}
