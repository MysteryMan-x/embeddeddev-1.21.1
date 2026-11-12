package com.mysteryman.embeddeddev.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class ManualItem extends WrittenBookItem {
    private static final List<List<String>> CHAPTERS = List.of(
            List.of("survival", "survival_assembly"),
            List.of("stc", "stc_memory"),
            List.of("board", "wiring", "wire_tools", "wire_redstone"),
            List.of("crystals", "timing"),
            List.of("oled", "oled_protocol"),
            List.of("ds1302", "ds1302_registers", "ds1302_example"),
            List.of("pcf8591", "pcf8591_control", "pcf8591_example"),
            List.of("button"), List.of("led"), List.of("sensor"), List.of("redstone"), List.of("compass"),
            List.of("ds18b20", "ds18b20_protocol", "ds18b20_temperature"));

    public ManualItem() {
        super(new Properties().stacksTo(1).component(DataComponents.WRITTEN_BOOK_CONTENT, content()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            // The vanilla server only sends this packet for Items.WRITTEN_BOOK.
            serverPlayer.connection.send(new ClientboundOpenBookPacket(hand));
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static WrittenBookContent content() {
        List<Filterable<Component>> pages = new ArrayList<>();
        int indexPages = (CHAPTERS.size() + 4) / 5;
        int firstChapterPage = indexPages + 1;
        int pageNumber = firstChapterPage;
        MutableComponent index = indexHeading(1, indexPages);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            if (i > 0 && i % 5 == 0) {
                pages.add(Filterable.passThrough(index));
                index = indexHeading(i / 5 + 1, indexPages);
            }
            List<String> chapter = CHAPTERS.get(i);
            String page = Integer.toString(pageNumber);
            index.append(Component.translatable("manual.embeddeddev." + chapter.getFirst() + ".title")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_BLUE)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, page))))
                    .append("\n");
            pageNumber += chapter.size();
        }
        pages.add(Filterable.passThrough(index));
        for (List<String> chapter : CHAPTERS) {
            for (String section : chapter) {
                pages.add(Filterable.passThrough(Component.translatable("manual.embeddeddev." + section + ".title")
                        .withStyle(ChatFormatting.BOLD).append("\n\n")
                        .append(Component.translatable("manual.embeddeddev." + section + ".body").withStyle(style -> style.withBold(false)))));
            }
        }
        return new WrittenBookContent(Filterable.passThrough("手册"), "Embedded Dev", 0, pages, true);
    }

    private static MutableComponent indexHeading(int part, int pages) {
        return Component.translatable("manual.embeddeddev.index", part, pages).append("\n\n");
    }
}
