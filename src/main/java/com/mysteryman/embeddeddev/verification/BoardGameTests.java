package com.mysteryman.embeddeddev.verification;

import com.mojang.authlib.GameProfile;
import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.*;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.menu.CrystalSocketMenu;
import com.mysteryman.embeddeddev.mcu.Pcf8591;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.FaceBinding.Pin;
import static com.mysteryman.embeddeddev.mcu.FaceBinding.Kind.*;

/** Development-only integration checks, excluded from the release JAR. */
@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class BoardGameTests {
    private static final String LOOP = ":0200000080FE80\n:00000001FF\n";
    private static final BlockPos BOARD = new BlockPos(3, 2, 3);
    private static FaceBinding pins(FaceBinding.Kind kind, int... channels) {
        return new FaceBinding(Arrays.stream(channels).mapToObj(n -> new Pin(kind.channelName(n), kind, n)).toList());
    }
    private static FaceBinding modulePins() {
        return new FaceBinding(List.of(new Pin("scl", GPIO, 15), new Pin("SDA", GPIO, 14)));
    }
    private static SystemBoardEntity board(GameTestHelper h) {
        h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        SystemBoardEntity board = h.getBlockEntity(BOARD); board.flash(LOOP, "test.hex"); return board;
    }

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void manualUseOpensIllustratedHandbook(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "manual-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        List<Packet<?>> packets = new ArrayList<>();
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {
                packets.add(packet);
            }
        };
        ItemStack manual = new ItemStack(ModItems.MANUAL.get());
        player.setItemInHand(InteractionHand.OFF_HAND, manual);
        manual.use(level, player, InteractionHand.OFF_HAND);
        h.assertTrue(packets.size() == 1 && packets.getFirst() instanceof ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof BoardNetwork.State state && state.open()
                        && state.data().getString("Screen").equals("manual")
                        && state.data().getString("Hand").equals(InteractionHand.OFF_HAND.name()),
                "An off-hand manual opens the illustrated handbook through a server-safe payload");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void sixDirectionalOutputs(GameTestHelper h) {
        SystemBoardEntity b = board(h);
        for (int i = 0; i < 6; i++) b.configure(i, pins(DIGITAL_OUTPUT, i));
        b.run();
        for (Direction face : Direction.values()) {
            int power = h.getLevel().getSignal(h.absolutePos(BOARD), face.getOpposite());
            h.assertTrue(power == 15, "Incorrect digital output on " + face + ": " + power);
        }
        b.stop();
        for (Direction face : Direction.values()) h.assertTrue(h.getLevel().getSignal(h.absolutePos(BOARD), face) == 0, "Stopped output must be off");
        h.succeed();
    }
    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void independentOledSync(GameTestHelper h) {
        SystemBoardEntity b = board(h);
        h.setBlock(BOARD.east(), ModBlocks.OLED.get());
        b.configure(5, modulePins());
        b.hardware.writeExternal(0xF020, 1);
        b.hardware.writeExternal(0xF100, 0xA5);
        h.runAfterDelay(8, () -> {
            OledEntity oled = h.getBlockEntity(BOARD.east());
            h.assertTrue(oled.enabled && (oled.pixels[0] & 255) == 0xA5, "OLED must mirror adjacent framebuffer");
            h.succeed();
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void persistenceAndAtomicFlash(GameTestHelper h) {
        SystemBoardEntity b = board(h);
        b.configure(3, pins(DIGITAL_OUTPUT, 5));
        FaceBinding north = new FaceBinding(List.of(new Pin("触发输入", DIGITAL_INPUT, 2),
                new Pin("SDA", GPIO, 14), new Pin("SCL", GPIO, 15)));
        b.configure(2, north);
        b.run(); b.cpu.runCycles(101);
        var tag = b.saveCustomOnly(h.getLevel().registryAccess()); byte[] cpuBefore = b.cpu.save();
        tag.remove("Pins5");
        tag.remove("Groups5");
        tag.putIntArray("Face5", new int[]{5, 2, 27});
        h.setBlock(BOARD, Blocks.AIR); h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        SystemBoardEntity restored = h.getBlockEntity(BOARD); restored.loadCustomOnly(tag, h.getLevel().registryAccess());
        h.assertTrue(restored.isRunning() && restored.output(3) == 15, "Restore run state and digital output");
        h.assertTrue(Arrays.equals(cpuBefore, restored.cpu.save()), "Restore exact CPU state");
        h.assertTrue(restored.hardware.binding(2).equals(north), "Restore named pins and mixed roles");
        FaceBinding migrated = restored.hardware.binding(5);
        h.assertTrue(migrated.gpio("SDA") == 2 && migrated.gpio("SCL") == 27, "Migrate old I2C wiring");
        try { restored.flash(":00000001FE", "bad.hex"); h.fail("Invalid HEX was accepted"); }
        catch (IllegalArgumentException expected) { /* Installation must remain untouched. */ }
        h.assertTrue(restored.isRunning() && restored.firmwareName().equals("test.hex") && restored.hardware.binding(2).equals(north),
                "Failed flash must preserve running firmware and multi-pin wiring");
        h.assertTrue(!tag.contains("Oled"), "Motherboard must not own OLED display RAM");
        h.succeed();
    }
    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void obsoleteAnalogMigrationPreservesGpioAndPauses(GameTestHelper h) {
        SystemBoardEntity b = board(h);
        b.run(); b.cpu.runCycles(37);
        var tag = b.saveCustomOnly(h.getLevel().registryAccess());
        byte[] cpuBefore = b.cpu.save();
        tag.getList("Groups0", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putString("Pins", "ANALOG_INPUT,0,VIN\nGPIO,8,LED\nANALOG_OUTPUT,1,OUT");
        var extra = tag.getList("Groups0", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(2);
        extra.putString("Name", "传感器"); extra.putString("Pins", "GPIO,14,SDA\nANALOG_INPUT,7,OLD\nGPIO,15,SCL");
        tag.remove("Pins5"); tag.remove("Groups5"); tag.putIntArray("Face5", new int[]{3, 7});
        SystemBoardEntity restored = new SystemBoardEntity(h.absolutePos(BOARD), b.getBlockState());
        restored.loadCustomOnly(tag, h.getLevel().registryAccess());
        h.assertTrue(!restored.isRunning(), "Boards with obsolete ADC/DAC wiring pause on migration");
        h.assertTrue(restored.binding(0, 0).pins().size() == 1 && restored.binding(0, 0).pins().getFirst().wireId() == 8,
                "Obsolete ADC/DAC is dropped; GPIO keeps its wire ID");
        h.assertTrue(restored.groups(0).get(2).name().equals("传感器") && restored.binding(0, 2).gpio("SDA") == 14
                        && restored.binding(0, 2).gpio("SCL") == 15 && restored.binding(5, 0).pins().isEmpty(),
                "Every group and the original numeric save format migrate without remapping GPIO");
        h.assertTrue(Arrays.equals(cpuBefore, restored.cpu.save()), "Migration retains exact CPU/RAM state");
        var dacOnly = b.saveCustomOnly(h.getLevel().registryAccess());
        dacOnly.getList("Groups1", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(3).putString("Pins", "ANALOG_OUTPUT,7,OUT\nGPIO,7,LED");
        restored.loadCustomOnly(dacOnly, h.getLevel().registryAccess());
        h.assertTrue(!restored.isRunning() && restored.binding(1, 3).pins().size() == 1
                        && restored.binding(1, 3).pins().getFirst().wireId() == 7,
                "A DAC-only group also pauses and preserves GPIO without reassigning its old DAC wire ID");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void gpioPortOutput(GameTestHelper h) {
        SystemBoardEntity b = board(h); h.setBlock(BOARD.east(), ModBlocks.REDSTONE_PORT.get());
        b.configure(5, pins(DIGITAL_OUTPUT, 0, 17)); b.run();
        h.runAfterDelay(3, () -> {
            RedstonePortEntity port = h.getBlockEntity(BOARD.east());
            h.assertTrue(port.output() == 15, "Default P0.0 latch drives expansion output");
            b.stop();
            h.runAfterDelay(3, () -> { h.assertTrue(port.output() == 0, "Stopped board disables GPIO module"); h.succeed(); });
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void comparatorTracksRunningWithZeroOutputs(GameTestHelper h) {
        SystemBoardEntity b = board(h);
        BlockPos comparator = BOARD.south();
        h.setBlock(comparator.below(), Blocks.STONE);
        h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        b.run();
        h.runAfterDelay(8, () -> {
            h.assertTrue(h.getBlockState(comparator).getValue(BlockStateProperties.POWERED), "Comparator should see running board even with no GPIO output");
            b.stop();
            h.runAfterDelay(8, () -> {
                h.assertTrue(!h.getBlockState(comparator).getValue(BlockStateProperties.POWERED), "Comparator should turn off after stop"); h.succeed();
            });
        });
    }
    private static void installExample(SystemBoardEntity b, String name) {
        try { b.flash(java.nio.file.Files.readString(java.nio.file.Path.of("..", "examples", name + ".hex")), name + ".hex"); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void i2cOledFirmwareDrivesIndependentModule(GameTestHelper h) {
        SystemBoardEntity b = board(h); h.setBlock(BOARD.east(), ModBlocks.OLED.get());
        b.configure(1, modulePins());
        b.configure(5, new FaceBinding(List.of(new Pin("SDA", GPIO, 14), new Pin("CLOCK", GPIO, 15))));
        h.assertTrue(b.hardware.device(5) == null, "Wrong or missing labels on the touching face must not connect");
        b.configure(5, new FaceBinding(List.of(new Pin("scl", GPIO, 15),
                new Pin("SPARE", GPIO, 0), new Pin("SDA", GPIO, 14))));
        installExample(b, "i2c_oled_hello"); b.run();
        h.runAfterDelay(20, () -> {
            OledEntity oled = h.getBlockEntity(BOARD.east());
            h.assertTrue(oled.enabled && oled.connected && (oled.display.ram[135] & 255) == 127, "Firmware must bit-bang readable E into attached SSD1306");
            h.assertTrue(b.cpu.inspectRam(0x30) == 0xA5, "Firmware must complete I2C upload");
            var saved = oled.saveCustomOnly(h.getLevel().registryAccess());
            h.setBlock(BOARD.east(), Blocks.AIR); h.setBlock(BOARD.east(), ModBlocks.OLED.get());
            OledEntity restored = h.getBlockEntity(BOARD.east()); restored.loadCustomOnly(saved, h.getLevel().registryAccess());
            h.assertTrue((restored.display.ram[135] & 255) == 127 && restored.display.enabled, "OLED saves its own RAM and controller state");
            h.succeed();
        });
    }
    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void i2cSensorFirmwareControlsGpioLed(GameTestHelper h) {
        SystemBoardEntity b = board(h); h.setBlock(BOARD.east(), ModBlocks.SENSOR.get());
        h.setBlock(BOARD.west(), ModBlocks.LED.get());
        h.setBlock(BOARD.east(2), Blocks.REDSTONE_BLOCK);
        b.configure(5, modulePins()); b.configure(4, new FaceBinding(List.of(new Pin("LED", GPIO, 10))));
        installExample(b, "i2c_sensor"); b.run();
        h.runAfterDelay(10, () -> {
            h.assertTrue(h.getBlockState(BOARD.west()).getValue(PeripheralBlock.ACTIVE), "I2C sensor nonzero sample lights the P1.2 LED");
            h.setBlock(BOARD.east(2), Blocks.AIR);
            h.runAfterDelay(10, () -> { h.assertTrue(!h.getBlockState(BOARD.west()).getValue(PeripheralBlock.ACTIVE), "LED turns off after redstone source removal"); h.succeed(); });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void buttonAndLedUseNamedPins(GameTestHelper h) {
        SystemBoardEntity board = board(h);
        h.setBlock(BOARD.east(), ModBlocks.BUTTON.get());
        h.setBlock(BOARD.west(), ModBlocks.LED.get());
        board.configure(5, new FaceBinding(List.of(new Pin("KEY", GPIO, 0))));
        board.configure(4, new FaceBinding(List.of(new Pin("LED", GPIO, 1))));
        h.useBlock(BOARD.east());
        h.assertTrue((board.cpu.inspect(0x80) & 1) == 0, "Button pulls only its named GPIO low");
        h.runAfterDelay(3, () -> {
            h.assertTrue(h.getBlockState(BOARD.west()).getValue(PeripheralBlock.ACTIVE), "LED lights from a high GPIO");
            board.hardware.writePort(0, 0xfd);
            h.runAfterDelay(10, () -> {
                h.assertTrue((board.cpu.inspect(0x80) & 1) != 0, "Button releases the GPIO after its pulse");
                h.assertTrue(!h.getBlockState(BOARD.west()).getValue(PeripheralBlock.ACTIVE), "LED follows a low GPIO");
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void pcf8591ConnectsWorldSignals(GameTestHelper h) {
        SystemBoardEntity board = board(h);
        BlockPos converter = BOARD.east();
        h.setBlock(converter, ModBlocks.PCF8591.get());
        h.setBlock(converter.north(), Blocks.REDSTONE_BLOCK);
        board.configure(5, modulePins());
        h.runAfterDelay(4, () -> {
            h.assertTrue(board.hardware.device(5) instanceof Pcf8591, "Named I2C pins find the converter");
            Pcf8591 adc = (Pcf8591) board.hardware.device(5);
            adc.begin(false);
            adc.write(0x40);
            adc.write(0x66);
            adc.begin(true);
            adc.read();
            h.assertTrue(adc.read() == 255, "AIN0 receives redstone from north");
            h.assertTrue(h.getLevel().getSignal(h.absolutePos(converter), Direction.DOWN) == 6, "AOUT emits upward");
            h.assertTrue(h.getLevel().getSignal(h.absolutePos(converter), Direction.WEST) == 0, "AIN faces do not emit AOUT");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void crystalSocketKeepsInventoryAndTiming(GameTestHelper h) {
        SystemBoardEntity board = board(h);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = new CrystalSocketMenu(1, player.getInventory(), board.crystalSocket());
        h.assertTrue(board.clockHz() == 11_059_200, "New boards start with the original nominal clock");
        int normalCycles = board.cyclesPerTick();
        h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Socket rejects non-crystals");
        h.assertTrue(!menu.quickMoveStack(player, 0).isEmpty() && board.clockHz() == 0, "Shift-click extracts the real crystal");
        player.getInventory().setItem(9, ModItems.CRYSTAL_22.get().getDefaultInstance());
        h.assertTrue(!menu.quickMoveStack(player, 1).isEmpty() && board.cyclesPerTick() == 2 * normalCycles, "22.1184 MHz doubles the cycle budget");
        h.assertTrue(player.getInventory().getItem(9).isEmpty(), "Socket insertion consumes the inventory item");
        board.run();
        byte[] before = board.cpu.save();
        board.crystalSocket().removeItem(0, 1);
        h.assertTrue(!board.isRunning() && Arrays.equals(before, board.cpu.save()), "Removing the crystal pauses without resetting the CPU");

        ItemStack drop = Block.getDrops(board.getBlockState(), h.getLevel(), h.absolutePos(BOARD), board).getFirst();
        h.setBlock(BOARD, Blocks.AIR);
        h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        SystemBoardEntity placed = h.getBlockEntity(BOARD);
        placed.applyComponentsFromItemStack(drop);
        h.assertTrue(placed.clockHz() == 0, "Empty socket survives break and replacement without a free crystal");
        placed.crystalSocket().setItem(0, ModItems.CRYSTAL_24.get().getDefaultInstance());
        var saved = placed.saveCustomOnly(h.getLevel().registryAccess());
        ItemStack fullDrop = Block.getDrops(placed.getBlockState(), h.getLevel(), h.absolutePos(BOARD), placed).getFirst();
        h.assertTrue(fullDrop.get(DataComponents.CONTAINER).copyOne().is(ModItems.CRYSTAL_24.get()), "Board drop carries installed crystal");
        placed.crystalSocket().removeItem(0, 1);
        placed.loadCustomOnly(saved, h.getLevel().registryAccess());
        h.assertTrue(placed.clockHz() == 24_000_000, "Installed crystal persists in the world");
        saved.remove("Crystal");
        placed.loadCustomOnly(saved, h.getLevel().registryAccess());
        h.assertTrue(placed.clockHz() == 11_059_200, "Legacy boards retain their default clock");
        h.succeed();
    }
}
