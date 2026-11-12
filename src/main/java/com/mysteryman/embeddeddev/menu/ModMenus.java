package com.mysteryman.embeddeddev.menu;

import com.mysteryman.embeddeddev.EmbeddedDev;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EmbeddedDev.MODID);
    public static final Supplier<MenuType<CrystalSocketMenu>> CRYSTAL_SOCKET = MENUS.register("crystal_socket",
            () -> new MenuType<>(CrystalSocketMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
