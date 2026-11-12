package com.mysteryman.embeddeddev.verification;

import com.mojang.authlib.GameProfile;
import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.ButtonPanel;
import com.mysteryman.embeddeddev.block.ModBlocks;
import com.mysteryman.embeddeddev.block.OledEntity;
import com.mysteryman.embeddeddev.block.PeripheralBlock;
import com.mysteryman.embeddeddev.block.PeripheralEntity;
import com.mysteryman.embeddeddev.block.RedstonePortEntity;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.Pcf8591;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.mysteryman.embeddeddev.mcu.FaceBinding.Kind.*;
import static com.mysteryman.embeddeddev.wire.CableNetwork.*;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class WiringGameTests {
    private static final BlockPos BOARD = new BlockPos(2, 2, 2);
    private static final int TOP = Direction.UP.get3DDataValue();
    private static final String LOOP = ":0200000080FE80\n:00000001FF\n";

    @GameTest(template = "test_empty", timeoutTicks = 80)
    public static void oledFirmwareAndAddressConflictAcrossPost(GameTestHelper h) throws IOException {
        BlockPos post = new BlockPos(5, 2, 2), oledPos = new BlockPos(7, 2, 2);
        BlockPos pcfPos = new BlockPos(5, 2, 6), sensorPos = new BlockPos(2, 2, 6);
        h.setBlock(post, ModBlocks.WIRE_RELAY.get());
        h.setBlock(oledPos, ModBlocks.OLED.get());
        h.setBlock(pcfPos, ModBlocks.PCF8591.get());
        h.setBlock(sensorPos, ModBlocks.SENSOR.get());
        var board = board(h, Files.readString(Path.of("..", "examples", "i2c_oled_hello.hex")));
        board.configure(TOP, pins(new FaceBinding.Pin("SDA", GPIO, 14), new FaceBinding.Pin("SCL", GPIO, 15)));
        var postEnd = end(h, post, RELAY);
        var branch = connect(h, end(h, BOARD, TOP), postEnd);
        connect(h, postEnd, end(h, oledPos, MODULE));
        connect(h, postEnd, end(h, pcfPos, MODULE));
        connect(h, end(h, BOARD, TOP), end(h, sensorPos, MODULE));
        board.run();

        h.runAfterDelay(12, () -> {
            OledEntity oled = h.getBlockEntity(oledPos);
            h.assertTrue(oled.enabled && oled.connected && (oled.display.ram[135] & 255) == 127,
                    "The original HEX must draw text on the distant OLED through the wiring post");
            h.assertTrue(board.cpu.inspectRam(0x30) == 0xA5, "The firmware completes its I2C upload");
            h.assertTrue(!board.wiring.connected(h.absolutePos(pcfPos)) && !board.wiring.connected(h.absolutePos(sensorPos)),
                    "Both slaves sharing address 0x48 are isolated while the OLED stays online");
            PeripheralEntity pcf = h.getBlockEntity(pcfPos);
            ((Pcf8591) pcf.i2cDevice()).nextAddress();
            h.runAfterDelay(3, () -> {
                h.assertTrue(board.wiring.connected(h.absolutePos(pcfPos)) && board.wiring.connected(h.absolutePos(sensorPos)),
                        "Moving the converter to 0x49 resolves the conflict");
                CableNetwork.get(h.getLevel()).remove(branch.id());
                h.runAfterDelay(5, () -> {
                    h.assertTrue(!oled.connected && !board.wiring.connected(h.absolutePos(pcfPos)), "Cutting the trunk disconnects its downstream branches");
                    h.assertTrue(board.wiring.connected(h.absolutePos(sensorPos)), "Another branch of the same GPIO pair still works");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void remoteGpioAndBidirectionalRedstonePorts(GameTestHelper h) {
        BlockPos button = new BlockPos(2, 2, 6), led = new BlockPos(7, 2, 2);
        BlockPos out = new BlockPos(6, 2, 5), in = new BlockPos(4, 2, 7);
        h.setBlock(button, ModBlocks.BUTTON.get());
        h.setBlock(led, ModBlocks.LED.get());
        h.setBlock(out, ModBlocks.REDSTONE_PORT.get());
        h.setBlock(in, ModBlocks.REDSTONE_PORT.get());
        h.setBlock(in.below(), Blocks.REDSTONE_BLOCK);
        RedstonePortEntity output = h.getBlockEntity(out), input = h.getBlockEntity(in);
        output.wireConfigure(Direction.EAST, false);
        input.wireConfigure(Direction.DOWN, true);
        var board = board(h, LOOP);
        int analogFace = Direction.EAST.get3DDataValue();
        int inputFace = Direction.SOUTH.get3DDataValue();
        board.configure(TOP, pins(new FaceBinding.Pin("KEY", GPIO, 0), new FaceBinding.Pin("LED", GPIO, 1)));
        board.configure(analogFace, pins(new FaceBinding.Pin("SIGNAL", ANALOG_OUTPUT, 1)));
        board.configure(inputFace, pins(new FaceBinding.Pin("SIGNAL", ANALOG_INPUT, 7)));
        connect(h, end(h, BOARD, TOP), end(h, button, MODULE));
        connect(h, end(h, BOARD, TOP), end(h, led, MODULE));
        var outputWire = connect(h, end(h, BOARD, analogFace), end(h, out, REDSTONE));
        connect(h, end(h, BOARD, inputFace), end(h, in, REDSTONE));
        board.hardware.writeExternal(0xF011, 9);
        board.run();
        h.runAfterDelay(4, () -> {
            h.assertTrue(h.getBlockState(led).getValue(PeripheralBlock.ACTIVE), "The remote LED sees P0.1 high");
            h.useBlock(button);
            h.assertTrue((board.cpu.inspect(0x80) & 1) == 0, "The remote button pulls P0.0 low");
            h.assertTrue(board.hardware.adc[7] == 15, "A distant redstone terminal samples into ADC7");
            h.assertTrue(output.output() == 9 && h.getLevel().getSignal(h.absolutePos(out), Direction.WEST) == 9,
                    "DAC1 drives redstone on the selected side");
            h.assertTrue(h.getLevel().getSignal(h.absolutePos(out), Direction.NORTH) == 0, "Other sides do not emit the signal");
            h.assertTrue(board.output(analogFace) == 0, "A cabled channel does not also emit local redstone");
            CableNetwork.get(h.getLevel()).remove(outputWire.id());
            h.runAfterDelay(3, () -> {
                h.assertTrue(output.output() == 0, "A disconnected remote output returns to zero");
                h.succeed();
            });
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void legacyDigitalPinWorksAsGpioOverOneWire(GameTestHelper h) {
        BlockPos pos = new BlockPos(6, 2, 2);
        h.setBlock(pos, ModBlocks.REDSTONE_PORT.get());
        RedstonePortEntity port = h.getBlockEntity(pos);
        port.wireConfigure(Direction.UP, false);
        var board = board(h, LOOP);
        var binding = FaceBinding.parse("DIGITAL_INPUT,10,SIGNAL");
        h.assertTrue(binding.pins().getFirst().kind().panelKind() == GPIO, "Legacy digital modes appear as GPIO");
        board.configure(TOP, binding);
        var wire = connect(h, end(h, BOARD, TOP), end(h, pos, REDSTONE));
        board.run();

        h.runAfterDelay(3, () -> {
            h.assertTrue(port.output() == 15, "A wired GPIO can drive the redstone port even with a legacy input binding");
            port.wireConfigure(Direction.UP, true);
            h.runAfterDelay(3, () -> {
                h.assertTrue((board.cpu.inspect(0x90) & 4) == 0, "The same wired pin can read a low signal");
                h.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
                h.runAfterDelay(3, () -> {
                    h.assertTrue((board.cpu.inspect(0x90) & 4) != 0, "The same wired pin can read a high signal");
                    h.assertTrue(CableNetwork.get(h.getLevel()).wire(wire.id()) != null, "Changing the adapter direction preserves the wire");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "test_empty")
    public static void twoClicksChargeOnceAndEndpointRemovalClearsSavedWire(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos led = new BlockPos(7, 2, 2), obstacle = new BlockPos(5, 2, 2);
        var board = board(h, LOOP);
        board.configure(TOP, pins(new FaceBinding.Pin("Lamp", GPIO, 3)));
        h.setBlock(led, ModBlocks.LED.get());
        h.setBlock(obstacle, Blocks.STONE);

        var profile = new GameProfile(UUID.randomUUID(), "wire-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override public void send(Packet<?> packet) {}
        };
        ItemStack coil = new ItemStack(ModItems.WIRE_COIL.get(), 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, coil);
        var a = end(h, BOARD, TOP);
        var b = end(h, led, MODULE);
        var cables = CableNetwork.get(level);
        click(player, coil, a.pos());
        click(player, coil, b.pos());
        h.assertTrue(coil.getCount() == 16 && !cables.has(a), "A blocked second click leaves both the world and coil intact");
        h.setBlock(obstacle, Blocks.AIR);
        click(player, coil, b.pos());
        h.assertTrue(coil.getCount() == 16 - CableNetwork.units(a, b) && cables.at(a).size() == 1,
                "An unambiguous second click creates one cable and consumes its length");
        var wire = cables.at(a).iterator().next();
        board.configure(TOP, pins(new FaceBinding.Pin("Renamed lamp", GPIO, 3)));
        h.assertTrue(board.wiring.connected(h.absolutePos(led)), "Renaming a label preserves the mapped physical pin");
        var restored = CableNetwork.load(cables.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(wire.equals(restored.wire(wire.id())) && restored.has(b), "Both ends and channel assignments survive serialization");
        h.setBlock(led, Blocks.AIR);
        h.assertTrue(!cables.has(a) && !cables.has(b) && cables.remove(wire.id()) == null, "Breaking an endpoint removes both references exactly once");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 60)
    public static void ninePanelButtonsUseDistinctTerminals(GameTestHelper h) {
        BlockPos pos = new BlockPos(6, 2, 5);
        h.setBlock(pos, ModBlocks.BUTTON.get());
        PeripheralEntity panel = h.getBlockEntity(pos);
        var board = board(h, LOOP);
        var names = IntStream.range(0, ButtonPanel.MAX_KEYS).mapToObj(ButtonPanel::defaultName).toList();
        board.configure(TOP, new FaceBinding(IntStream.range(0, names.size())
                .mapToObj(key -> new FaceBinding.Pin(names.get(key), GPIO, key)).toList()));
        var level = h.getLevel();
        var cables = CableNetwork.get(level);
        var source = end(h, BOARD, TOP);
        var originalWire = connect(h, source, end(h, pos, MODULE));
        panel.configureButtons(names);
        for (int key = 0; key < names.size(); key++) {
            var hit = buttonHit(h.absolutePos(pos), key);
            var target = CableNetwork.target(level, hit);
            h.assertTrue(target != null && target.socket() == buttonSocket(key), "Aim must pick key " + (key + 1));
            if (key > 0) connect(h, source, target);
        }
        h.assertTrue(cables.at(source).size() == 9 && cables.wire(originalWire.id()) != null,
                "Nine cables fit on one board socket and keep the original KEY cable");
        board.run();
        h.runAfterDelay(3, () -> {
            h.assertTrue(board.wiring.statusTag().size() == 9, "Board lists every key on the shared panel independently");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            h.useBlock(pos, player, buttonHit(h.absolutePos(pos), 1));
            h.assertTrue((board.cpu.inspect(0x80) & 3) == 1, "Pressing key 2 pulls only P0.1 low");
            h.useBlock(pos, player, buttonHit(h.absolutePos(pos), 8));
            h.assertTrue((board.cpu.inspect(0x90) & 1) == 0, "Pressing key 9 independently pulls P1.0 low");
            var renamed = new java.util.ArrayList<>(names);
            renamed.set(1, "Stop");
            panel.configureButtons(renamed.subList(0, 2));
            var saved = panel.saveCustomOnly(level.registryAccess());
            var restored = new PeripheralEntity(h.absolutePos(pos), panel.getBlockState());
            restored.loadCustomOnly(saved, level.registryAccess());
            h.assertTrue(restored.buttons().names().equals(List.of("KEY", "Stop")), "Button count and custom names survive saving");
            h.assertTrue(cables.at(source).size() == 2 && cables.wire(originalWire.id()) != null, "Shrinking only removes the hidden keys' cables");
            h.runAfterDelay(11, () -> {
                h.assertTrue((board.cpu.inspect(0x80) & 3) == 3 && (board.cpu.inspect(0x90) & 1) == 1,
                        "Pressed keys release, including a key removed while pressed");
                h.succeed();
            });
        });
    }

    private static BlockHitResult buttonHit(BlockPos pos, int key) {
        return new BlockHitResult(Vec3.atLowerCornerOf(pos).add(ButtonPanel.x(key), .33, ButtonPanel.z(key)), Direction.UP, pos, false);
    }

    private static void click(ServerPlayer player, ItemStack coil, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ModItems.WIRE_COIL.get().onItemUseFirst(coil, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static SystemBoardEntity board(GameTestHelper h, String hex) {
        h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        SystemBoardEntity board = h.getBlockEntity(BOARD);
        board.flash(hex, "wire-test.hex");
        return board;
    }

    private static FaceBinding pins(FaceBinding.Pin... pins) { return new FaceBinding(List.of(pins)); }
    private static CableNetwork.End end(GameTestHelper h, BlockPos pos, int socket) { return new CableNetwork.End(h.absolutePos(pos), socket); }
    private static CableNetwork.Wire connect(GameTestHelper h, CableNetwork.End a, CableNetwork.End b) {
        var level = h.getLevel();
        var cables = CableNetwork.get(level);
        return cables.connect(level, a, b, cables.prepare(level, a, b).automatic());
    }
}
