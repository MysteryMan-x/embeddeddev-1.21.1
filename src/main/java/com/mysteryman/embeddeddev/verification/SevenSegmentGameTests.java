package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.ModBlocks;
import com.mysteryman.embeddeddev.block.SevenSegmentEntity;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.SevenSegment;
import com.mysteryman.embeddeddev.wire.CableNetwork;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class SevenSegmentGameTests {
    private static final BlockPos BOARD = new BlockPos(2, 2, 2);
    private static final Set<Integer> COUNTER_CODES = Set.of(0x3f, 0x86, 0x5b, 0xcf, 0x66, 0xed, 0x7d, 0x87, 0x7f, 0xef);

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void adjacentPolarityPersistenceAndRecipe(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos digitPos = BOARD.east();
        h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        h.setBlock(digitPos, ModBlocks.SEVEN_SEGMENT.get());
        SystemBoardEntity board = h.getBlockEntity(BOARD);
        SevenSegmentEntity digit = h.getBlockEntity(digitPos);
        // MOV P1,#5Bh; SJMP $ -- an asymmetric "2" suitable for checking the display direction.
        board.flash(":0500000075905B80FE1D\n:00000001FF\n", "digit-2.hex");
        board.configure(Direction.EAST.get3DDataValue(), pins());
        board.run();
        h.runAfterDelay(3, () -> {
            h.assertTrue(digit.connected() && digit.segments() == 0x5b, "An adjacent GPIO-only module displays firmware digit 2");
            digit.commonAnode(true);
            h.assertTrue(digit.segments() == 0xa4, "Common anode inverts every segment including DP");
            var restored = new SevenSegmentEntity(h.absolutePos(digitPos), digit.getBlockState());
            restored.loadCustomOnly(digit.saveCustomOnly(level.registryAccess()), level.registryAccess());
            h.assertTrue(restored.display.commonAnode(), "Display polarity survives saving");
            h.assertTrue(restored.segments() == 0 && !restored.connected(), "An unloaded display does not retain a false live signal");
            var client = new SevenSegmentEntity(h.absolutePos(digitPos), digit.getBlockState());
            client.handleUpdateTag(digit.getUpdateTag(level.registryAccess()), level.registryAccess());
            h.assertTrue(client.display.commonAnode() && client.connected() && client.segments() == 0xa4,
                    "Initial tracking and update packets carry both polarity and live segment bits");
            var materials = new ArrayList<ItemStack>();
            for (int slot = 0; slot < 9; slot++) materials.add(new ItemStack(slot == 4 ? ModItems.CIRCUIT_BOARD.get() : ModBlocks.LED.get().asItem()));
            var input = CraftingInput.of(3, 3, materials);
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            h.assertTrue(recipe.value().assemble(input, level.registryAccess()).is(ModBlocks.SEVEN_SEGMENT.get().asItem()),
                    "Eight LED modules surrounding a circuit board craft the display");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 180)
    public static void counterFirmwareOverGroupedRelayCable(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        BlockPos postPos = new BlockPos(5, 2, 2), digitPos = new BlockPos(7, 2, 2);
        h.setBlock(BOARD, ModBlocks.SystemBoard51.get());
        h.setBlock(postPos, ModBlocks.WIRE_RELAY.get());
        h.setBlock(digitPos, ModBlocks.SEVEN_SEGMENT.get());
        SystemBoardEntity board = h.getBlockEntity(BOARD);
        SevenSegmentEntity digit = h.getBlockEntity(digitPos);
        board.flash(Files.readString(Path.of("..", "examples", "seven_segment_counter.hex")), "seven_segment_counter.hex");
        int face = Direction.UP.get3DDataValue();
        board.configureGroups(face, List.of(PinGroup.empty(0), PinGroup.empty(1), new PinGroup("Digit", pins()), PinGroup.empty(3)));
        var source = new CableNetwork.End(h.absolutePos(BOARD), CableNetwork.boardSocket(face, 2));
        var post = new CableNetwork.End(h.absolutePos(postPos), CableNetwork.RELAY);
        var target = new CableNetwork.End(h.absolutePos(digitPos), CableNetwork.MODULE);
        var cables = CableNetwork.get(level);
        var draft = cables.prepare(level, source, post);
        var trunk = cables.connect(level, source, post, draft.automatic());
        var displayDraft = cables.prepare(level, post, target);
        h.assertTrue(displayDraft.automatic().size() == 8, "All eight named pins match even when the board list is reversed");
        cables.connect(level, post, target, displayDraft.automatic());
        board.run();
        h.runAfterDelay(3, () -> {
            h.assertTrue(digit.connected() && digit.segments() == 0x3f, "A remote display receives the counter's first digit through G3 and a relay");
            observeCounter(h, digit, cables, trunk, new HashSet<>());
        });
    }

    private static void observeCounter(GameTestHelper h, SevenSegmentEntity digit, CableNetwork cables,
                                       CableNetwork.Wire trunk, Set<Integer> observed) {
        int mask = digit.segments();
        h.assertTrue(COUNTER_CODES.contains(mask), "Counter only emits valid digits with the intended decimal point");
        boolean wrapped = observed.size() == 10 && mask == 0x3f;
        observed.add(mask);
        if (wrapped) {
            digit.commonAnode(true);
            cables.remove(trunk.id());
            h.runAfterDelay(3, () -> {
                h.assertTrue(!digit.connected() && digit.segments() == 0, "Cutting the cable blanks a common-anode display instead of lighting every segment");
                h.succeed();
            });
        } else h.runAfterDelay(1, () -> observeCounter(h, digit, cables, trunk, observed));
    }

    private static FaceBinding pins() {
        var pins = new ArrayList<FaceBinding.Pin>();
        for (int pin = 7; pin >= 0; pin--) pins.add(new FaceBinding.Pin(SevenSegment.TERMINALS.get(pin), FaceBinding.Kind.GPIO, 8 + pin));
        return new FaceBinding(pins);
    }
}
