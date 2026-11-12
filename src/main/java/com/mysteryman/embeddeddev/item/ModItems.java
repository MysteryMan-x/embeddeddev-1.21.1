package com.mysteryman.embeddeddev.item;

import com.mysteryman.embeddeddev.EmbeddedDev;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmbeddedDev.MODID);
    public static final DeferredItem<WiringToolItem> WIRE_COIL = ITEMS.register("wire_coil", () -> new WiringToolItem(false));
    public static final DeferredItem<WiringToolItem> WIRE_CUTTER = ITEMS.register("wire_cutter", () -> new WiringToolItem(true));

    public static final DeferredItem<Item> RAW_SILICON = ITEMS.registerSimpleItem("raw_silicon");
    public static final DeferredItem<Item> SILICON_WAFER = ITEMS.registerSimpleItem("silicon_wafer");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> STC89C52RC = ITEMS.register("stc89c52rc",() -> new Item(new Item.Properties().fireResistant()));
    public static final DeferredItem<SurveyCompassItem> COMPASS = ITEMS.register("compass",
            () -> new SurveyCompassItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ManualItem> MANUAL = ITEMS.register("manual", ManualItem::new);
    public static final DeferredItem<CrystalItem> CRYSTAL_6 = ITEMS.register("crystal_6mhz", () -> new CrystalItem(6_000_000));
    public static final DeferredItem<CrystalItem> CRYSTAL_11 = ITEMS.register("crystal_11_0592mhz", () -> new CrystalItem(CrystalItem.DEFAULT_HZ));
    public static final DeferredItem<CrystalItem> CRYSTAL_12 = ITEMS.register("crystal_12mhz", () -> new CrystalItem(12_000_000));
    public static final DeferredItem<CrystalItem> CRYSTAL_22 = ITEMS.register("crystal_22_1184mhz", () -> new CrystalItem(22_118_400));
    public static final DeferredItem<CrystalItem> CRYSTAL_24 = ITEMS.register("crystal_24mhz", () -> new CrystalItem(24_000_000));

    public static void register(IEventBus eventbus)
    {
        ITEMS.register(eventbus);
    }
}
