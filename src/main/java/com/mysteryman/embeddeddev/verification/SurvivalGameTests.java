package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.block.ModBlocks;
import com.mysteryman.embeddeddev.block.SystemBoardEntity;
import com.mysteryman.embeddeddev.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

@GameTestHolder(EmbeddedDev.MODID)
@PrefixGameTestTemplate(false)
public final class SurvivalGameTests {
    @GameTest(template = "test_empty")
    public static void buildsSystemBoardFromOverworldMaterials(GameTestHelper h) {
        var level = h.getLevel();
        var raw = craft(h, 3, Items.AIR, Items.SAND, Items.AIR, Items.SAND, Items.CHARCOAL, Items.SAND,
                Items.AIR, Items.SAND, Items.AIR);
        h.assertTrue(raw.is(ModItems.RAW_SILICON.get()), "Sand and charcoal make raw silicon");
        var furnaceInput = new SingleRecipeInput(raw);
        var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, furnaceInput, level).orElseThrow();
        var wafer = smelting.value().assemble(furnaceInput, level.registryAccess());
        h.assertTrue(wafer.is(ModItems.SILICON_WAFER.get()), "Raw silicon smelts into a wafer");
        var blasting = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, furnaceInput, level).orElseThrow();
        h.assertTrue(blasting.value().assemble(furnaceInput, level.registryAccess()).is(wafer.getItem()),
                "The blast furnace also refines raw silicon");
        var pcb = craft(h, 3, Items.COPPER_INGOT, Items.CLAY_BALL, Items.REDSTONE);
        h.assertTrue(pcb.is(ModItems.CIRCUIT_BOARD.get()) && pcb.getCount() == 2, "Copper, clay and redstone make two boards");
        var mcu = craft(h, 3, Items.IRON_NUGGET, Items.REDSTONE, Items.IRON_NUGGET,
                Items.GOLD_NUGGET, wafer.getItem(), Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.REDSTONE, Items.IRON_NUGGET);
        h.assertTrue(mcu.is(ModItems.STC89C52RC.get()), "A wafer can be packaged as an MCU");
        var crystal = craft(h, 3, wafer.getItem(), Items.IRON_NUGGET, Items.GOLD_NUGGET);
        h.assertTrue(crystal.is(ModItems.CRYSTAL_11.get()), "The default crystal can use a wafer");
        var boardStack = craft(h, 3, Items.AIR, crystal.getItem(), Items.AIR, Items.COPPER_INGOT,
                mcu.getItem(), Items.COPPER_INGOT, Items.AIR, pcb.getItem(), Items.AIR);
        h.assertTrue(boardStack.is(ModBlocks.SystemBoard51.get().asItem()), "The MCU, crystal and circuit board assemble into a system board");
        BlockPos pos = new BlockPos(3, 2, 3);
        h.setBlock(pos, ModBlocks.SystemBoard51.get());
        SystemBoardEntity board = h.getBlockEntity(pos);
        board.applyComponentsFromItemStack(boardStack);
        h.assertTrue(board.crystal().is(crystal.getItem()), "The crafted board has its clock installed");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void siliconOreGeneratesAndDropsMaterials(GameTestHelper h) {
        var level = h.getLevel();
        var registries = level.registryAccess();
        var oreFeatureKey = ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "silicon_ore"));
        var oreFeature = registries.registryOrThrow(Registries.PLACED_FEATURE).getHolderOrThrow(oreFeatureKey);
        var plains = registries.registryOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        h.assertTrue(plains.getGenerationSettings().features().get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()).contains(oreFeature),
                "The placed ore feature is enabled in Overworld biomes");

        var pick = new ItemStack(Items.STONE_PICKAXE);
        var silkPick = pick.copy();
        silkPick.enchant(registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        for (Block ore : new Block[] {ModBlocks.SILICON_ORE.get(), ModBlocks.DEEPSLATE_SILICON_ORE.get()}) {
            var state = ore.defaultBlockState();
            var pos = h.absolutePos(new BlockPos(2, 2, 2));
            h.assertTrue(pick.isCorrectToolForDrops(state) && !Items.WOODEN_PICKAXE.getDefaultInstance().isCorrectToolForDrops(state),
                    "Silicon ore needs a stone pickaxe or better");
            var drops = Block.getDrops(state, level, pos, null, null, pick);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.RAW_SILICON.get()), "A normal pick yields raw silicon");
            var silkDrops = Block.getDrops(state, level, pos, null, null, silkPick);
            h.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(ore.asItem()), "Silk Touch preserves the ore block");
        }
        h.succeed();
    }

    private static ItemStack craft(GameTestHelper h, int width, ItemLike... items) {
        var level = h.getLevel();
        var input = CraftingInput.of(width, items.length / width, Arrays.stream(items).map(ItemStack::new).toList());
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow(() -> new IllegalStateException("No matching crafting recipe for " + Arrays.toString(items)))
                .value().assemble(input, level.registryAccess());
    }
}
