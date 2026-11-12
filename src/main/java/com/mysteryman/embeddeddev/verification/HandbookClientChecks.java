package com.mysteryman.embeddeddev.verification;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.mysteryman.embeddeddev.client.ManualLayout;
import com.mysteryman.embeddeddev.client.ManualScreen;
import com.mysteryman.embeddeddev.manual.Handbook;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Opt-in development checks; excluded from the release JAR with the other verification classes. */
@EventBusSubscriber(modid = EmbeddedDev.MODID, value = Dist.CLIENT)
public final class HandbookClientChecks {
    private static int ticks;
    private static boolean complete;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (complete || !Boolean.getBoolean("embeddeddev.handbookQA")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ++ticks < 20) return;
        complete = true;
        int checks = 0, pageCount = 0;
        for (String language : new String[] {"zh_cn", "en_us"}) {
            var resource = mc.getResourceManager().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "manual/" + language + ".json"));
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                var book = Handbook.read(reader);
                for (int[] size : new int[][] {{158, 116}, {235, 116}, {440, 236}}) for (var entry : book.entries()) {
                    var pages = ManualLayout.pages(mc.font, entry, size[0], size[1]);
                    for (var page : pages) {
                        int used = page.stream().mapToInt(ManualLayout.Flow::height).sum();
                        if (used > size[1]) throw new AssertionError("Handbook overflow: " + language + "/" + entry.id() + " " + used);
                        if (!page.isEmpty() && page.getLast().kind().equals("heading")) {
                            throw new AssertionError("Orphan handbook heading: " + language + "/" + entry.id());
                        }
                        checks++; pageCount++;
                    }
                }
            }
        }
        EmbeddedDev.LOGGER.info("Handbook client layout passed: {} checks, {} pages across both languages and three viewport sizes", checks, pageCount);
        mc.setScreen(new ManualScreen());
    }
    private HandbookClientChecks() {}
}
