package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.EmbeddedDev;
import com.google.gson.Gson;
import com.mysteryman.embeddeddev.manual.Handbook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BooleanSupplier;

/** An illustrated field handbook with live recipes, wiring diagrams and searchable chapters. */
public final class ManualScreen extends Screen {
    private static final int INK = 0xff303e37, MUTED = 0xff777767, GREEN = 0xff24584a;
    private static final int PAPER = 0xfff2e9cf, GOLD = 0xffba9657, LINE = 0xffd5c9a8;
    private static final Set<String> BOOKMARKS = new LinkedHashSet<>();
    private static String lastEntry = "welcome";
    private static int lastPage;
    private static boolean preferencesLoaded;
    private final Handbook book;
    private Handbook.Entry selected;
    private String category, query = "";
    private boolean onlyBookmarks;
    private int page, offset, x, y, w, h, nav, cx, cy, cw, ch;
    private EditBox search;
    private List<Handbook.Entry> filtered = List.of();
    private List<List<ManualLayout.Flow>> pages = List.of(List.of());
    private final List<EntryButton> entryButtons = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<Visit> history = new ArrayDeque<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private JournalButton previous, next, back;
    private long copiedUntil;
    private record Visit(String entry, int page) {}
    private record Preferences(String entry, int page, List<String> bookmarks) {}
    private record Hit(int x, int y, int w, int h, ItemStack item, Runnable action) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    public ManualScreen() {
        super(Component.translatable("handbook.embeddeddev.title"));
        book = loadBook(); loadPreferences(); selected = book.entry(lastEntry); category = selected.category(); page = lastPage;
    }

    private static Handbook loadBook() {
        var mc = Minecraft.getInstance();
        String language = mc.options.languageCode;
        for (String candidate : new LinkedHashSet<>(List.of(language, language.startsWith("zh_") ? "zh_cn" : "en_us", "en_us"))) {
            var resource = mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(EmbeddedDev.MODID, "manual/" + candidate + ".json"));
            if (resource.isEmpty()) continue;
            try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) { return Handbook.read(reader); }
            catch (Exception error) { EmbeddedDev.LOGGER.warn("Cannot load handbook language {}", candidate, error); }
        }
        var block = new Handbook.Block("text", Component.translatable("handbook.embeddeddev.load_error").getString(), null, null, null, null);
        return new Handbook("Embedded Dev", "", List.of(new Handbook.Category("start", "Manual", "minecraft:book")),
                List.of(new Handbook.Entry("welcome", "start", "Manual", "", "minecraft:book", "", null, List.of(block))));
    }

    @Override protected void init() {
        w = Math.min(660, width - 16); h = Math.min(390, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        nav = Math.clamp(w / 3, 110, 164); cx = x + nav + 18; cy = y + 73; cw = w - nav - 36; ch = h - 108;
        entryButtons.clear();
        search = new EditBox(font, x + 18, y + 42, nav - 32, 14, Component.translatable("handbook.embeddeddev.search"));
        search.setBordered(false); search.setTextColor(INK); search.setMaxLength(120);
        search.setHint(Component.translatable("handbook.embeddeddev.search")); search.setValue(query);
        search.setResponder(value -> { query = value; offset = 0; filter(); }); addRenderableWidget(search);
        int rows = Math.max(1, (h - 97) / 24);
        for (int row = 0; row < rows; row++) {
            var button = new EntryButton(x + 11, y + 64 + row * 24, nav - 22, row);
            entryButtons.add(addRenderableWidget(button));
        }
        int tabWidth = (w - nav - 34) / book.categories().size();
        for (int i = 0; i < book.categories().size(); i++) {
            var tab = book.categories().get(i);
            addRenderableWidget(new JournalButton(x + nav + 8 + i * tabWidth, y + 7, tabWidth - 3, 18, tab.title(),
                    () -> { category = tab.id(); onlyBookmarks = false; query = ""; search.setValue(""); offset = 0; filter(); },
                    () -> category.equals(tab.id()) && query.isBlank() && !onlyBookmarks));
        }
        addRenderableWidget(new JournalButton(x + w - 22, y + 7, 16, 18, "x", this::onClose, () -> false));
        var favorite = addRenderableWidget(new JournalButton(x + w - 38, y + 36, 22, 21, "★", () -> {
            if (!BOOKMARKS.add(selected.id())) BOOKMARKS.remove(selected.id());
            savePreferences(); filter();
        }, () -> BOOKMARKS.contains(selected.id())));
        favorite.setTooltip(Tooltip.create(Component.translatable("handbook.embeddeddev.bookmark")));
        back = addRenderableWidget(new JournalButton(x + 11, y + h - 27, 28, 18, "<", () -> {
            if (!history.isEmpty()) { var visit = history.pop(); open(visit.entry(), false, false); page = Math.min(visit.page(), pages.size() - 1); }
        }, () -> false));
        back.setTooltip(Tooltip.create(Component.translatable("handbook.embeddeddev.back")));
        addRenderableWidget(new JournalButton(x + 42, y + h - 27, nav - 53, 18,
                Component.translatable("handbook.embeddeddev.bookmarks").getString(), () -> { onlyBookmarks = !onlyBookmarks; offset = 0; filter(); }, () -> onlyBookmarks));
        previous = addRenderableWidget(new JournalButton(cx, y + h - 27, 28, 18, "<", () -> flip(-1), () -> false));
        next = addRenderableWidget(new JournalButton(cx + cw - 28, y + h - 27, 28, 18, ">", () -> flip(1), () -> false));
        reflow(); filter();
    }

    private void reflow() { pages = ManualLayout.pages(font, selected, cw, ch); page = Math.clamp(page, 0, pages.size() - 1); }
    private void filter() {
        filtered = book.entries().stream().filter(entry -> !onlyBookmarks || BOOKMARKS.contains(entry.id()))
                .filter(entry -> !query.isBlank() || onlyBookmarks || entry.category().equals(category)).filter(entry -> entry.matches(query)).toList();
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - entryButtons.size()));
        for (var button : entryButtons) {
            int index = offset + button.row; button.entry = index < filtered.size() ? filtered.get(index) : null;
            button.visible = button.entry != null;
            if (button.entry != null) button.setMessage(Component.literal(button.entry.title()));
        }
    }
    private void open(String id, boolean remember, boolean keepFilter) {
        if (remember) history.push(new Visit(selected.id(), page));
        selected = book.entry(id); page = 0;
        if (!keepFilter) { category = selected.category(); onlyBookmarks = false; query = ""; search.setValue(""); offset = 0; }
        reflow(); filter();
    }
    private void flip(int delta) {
        int next = Math.clamp(page + delta, 0, pages.size() - 1);
        if (next != page) { page = next; minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1)); }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xb0091711); hits.clear();
        // Leather binding, paper edges, a recessed spine and restrained brass details.
        g.fill(x - 3, y + 3, x + w + 4, y + h + 5, 0x70000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xff173e35);
        g.fill(x + 3, y + 28, x + w - 3, y + h - 3, 0xffc6b78e);
        g.fill(x + 5, y + 27, x + w - 5, y + h - 6, PAPER);
        g.fill(x + 6, y + 27, x + nav - 2, y + h - 7, 0xffe9dfc1);
        for (int i = 0; i < 7; i++) g.fill(x + nav - 3 + i, y + 28, x + nav - 2 + i, y + h - 7, 0xffbdb18e + i * 0x00060504);
        g.fill(x + 8, y + 25, x + w - 8, y + 26, GOLD);
        g.drawString(font, fit(Component.translatable("handbook.embeddeddev.title").getString(), nav - 22), x + 12, y + 12, 0xfff5e9c8, false);
        g.fill(x + 13, y + 38, x + nav - 13, y + 58, 0xfff8f0dc);
        g.fill(x + 13, y + 57, x + nav - 13, y + 58, LINE);
        if (filtered.isEmpty()) g.drawString(font, fit(Component.translatable("handbook.embeddeddev.no_results").getString(), nav - 25), x + 13, y + 69, MUTED, false);
        String range = filtered.isEmpty() ? "0 / 0" : (offset + 1) + "–" + Math.min(offset + entryButtons.size(), filtered.size()) + " / " + filtered.size();
        g.drawString(font, range, x + 13, y + h - 40, MUTED, false);
        icon(g, icon(selected.icon()), cx, y + 35, 1.5F, true);
        g.drawString(font, fit(selected.title(), cw - 63), cx + 30, y + 37, GREEN, false);
        g.drawString(font, fit(selected.subtitle(), cw - 63), cx + 30, y + 51, MUTED, false);
        g.fill(cx, y + 66, cx + cw, y + 67, LINE);
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int top = cy;
        for (var flow : pages.get(page)) { renderFlow(g, flow, cx, top, cw); top += flow.height(); }
        g.disableScissor();
        previous.active = page > 0; next.active = page + 1 < pages.size(); back.active = !history.isEmpty();
        String number = (page + 1) + " / " + pages.size();
        g.drawString(font, number, cx + (cw - font.width(number)) / 2, y + h - 22, INK, false);
        super.render(g, mouseX, mouseY, partialTick);
        for (var hit : hits) if (hit.contains(mouseX, mouseY) && !hit.item().isEmpty()) g.renderTooltip(font, hit.item(), mouseX, mouseY);
    }

    // Screen.render calls this again before widgets; a second post-process would blur our paper and text.
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private void renderFlow(GuiGraphics g, ManualLayout.Flow flow, int left, int top, int width) {
        var block = flow.source();
        switch (flow.kind()) {
            case "heading" -> { g.fill(left, top + 2, left + 3, top + flow.height() - 4, GOLD); lines(g, flow.lines(), left + 9, top + 2, GREEN); }
            case "text" -> lines(g, flow.lines(), left, top + 2, INK);
            case "tip" -> {
                g.fill(left, top + 2, left + width, top + flow.height() - 4, 0xffe0e8d2);
                g.fill(left, top + 2, left + 2, top + flow.height() - 4, GREEN);
                lines(g, flow.lines(), left + 8, top + 8, GREEN);
            }
            case "code" -> {
                g.fill(left, top + 1, left + width, top + flow.height() - 3, 0xff233b35);
                String copy = Component.translatable(System.currentTimeMillis() < copiedUntil ? "handbook.embeddeddev.copied_short" : "handbook.embeddeddev.copy").getString();
                g.drawString(font, "C51 / ASM", left + 7, top + 4, 0xffb9c6ac, false);
                g.drawString(font, copy, left + width - font.width(copy) - 7, top + 4, 0xffe5c58b, false);
                hits.add(new Hit(left + width - font.width(copy) - 12, top + 1, font.width(copy) + 12, 14, ItemStack.EMPTY,
                        () -> { minecraft.keyboardHandler.setClipboard(block.text()); copiedUntil = System.currentTimeMillis() + 1600; }));
                lines(g, flow.lines(), left + 8, top + 17, 0xfff0ead8);
            }
            case "table" -> {
                int cellWidth = width / flow.cells().size();
                for (int column = 0; column < flow.cells().size(); column++) {
                    int cellX = left + column * cellWidth;
                    g.fill(cellX, top, cellX + cellWidth - 1, top + flow.height() - 1, flow.header() ? 0xffd6dfc7 : 0xffe9e0c7);
                    lines(g, flow.cells().get(column), cellX + 5, top + 4, flow.header() ? GREEN : INK);
                }
            }
            case "wiring" -> {
                g.fill(left, top, left + width, top + flow.height() - 4, 0xffe7e4ca);
                icon(g, icon("embeddeddev:51systemboard"), left + 7, top + 4, 1, true);
                icon(g, icon(block.id()), left + width - 23, top + 4, 1, true);
                String title = Component.translatable("handbook.embeddeddev.wiring").getString();
                g.drawString(font, title, left + (width - font.width(title)) / 2, top + 8, GREEN, false);
                for (int row = 0; row < block.labels().size(); row++) {
                    String[] labels = block.labels().get(row).split("\\|", -1); int ry = top + 27 + row * 10;
                    g.drawString(font, labels[0], left + 8, ry, INK, false);
                    g.drawString(font, labels[1], left + width - 8 - font.width(labels[1]), ry, INK, false);
                    int start = left + 12 + font.width(labels[0]), end = left + width - 12 - font.width(labels[1]);
                    g.fill(start, ry + 4, end, ry + 5, GOLD); g.fill(start, ry + 2, start + 2, ry + 7, GREEN); g.fill(end - 2, ry + 2, end, ry + 7, GREEN);
                }
            }
            case "recipe" -> recipe(g, block.id(), left, top, width);
            case "link" -> {
                g.fill(left, top + 1, left + width, top + flow.height() - 3, 0xffdce5cf);
                lines(g, flow.lines(), left + 8, top + 6, GREEN);
                hits.add(new Hit(left, top, width, flow.height() - 3, ItemStack.EMPTY, () -> open(block.target(), true, false)));
            }
        }
    }

    private void recipe(GuiGraphics g, String id, int left, int top, int width) {
        g.fill(left, top, left + width, top + 83, 0xffe9e0c6);
        var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(id));
        if (recipe.isEmpty()) {
            lines(g, font.split(Component.translatable("handbook.embeddeddev.missing_recipe", id), width - 16), left + 8, top + 8, MUTED); return;
        }
        var value = recipe.get().value();
        String label = value.getType() == RecipeType.SMELTING ? "smelting" : value.getType() == RecipeType.BLASTING ? "blasting"
                : value instanceof ShapedRecipe ? "shaped" : "shapeless";
        g.drawString(font, Component.translatable("handbook.embeddeddev." + label), left + 8, top + 5, GREEN, false);
        if (value.getType() == RecipeType.SMELTING || value.getType() == RecipeType.BLASTING) {
            int start = left + (width - 124) / 2;
            g.fill(start, top + 31, start + 24, top + 55, 0xffd3c8a9);
            var options = value.getIngredients().getFirst().getItems();
            if (options.length > 0) icon(g, options[(int) (System.currentTimeMillis() / 1500 % options.length)], start + 4, top + 35, 1, true);
            g.drawString(font, ">", start + 32, top + 39, GOLD, false);
            icon(g, (value.getType() == RecipeType.SMELTING ? Items.FURNACE : Items.BLAST_FURNACE).getDefaultInstance(), start + 50, top + 31, 1.5F, true);
            g.drawString(font, ">", start + 83, top + 39, GOLD, false);
            g.fill(start + 100, top + 31, start + 124, top + 55, 0xffd4dfc4);
            icon(g, value.getResultItem(minecraft.level.registryAccess()), start + 104, top + 35, 1, true);
            return;
        }
        int gridX = left + Math.max(9, (width - 124) / 2), gridY = top + 21;
        int gridWidth = value instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        var ingredients = value.getIngredients();
        for (int slot = 0; slot < 9; slot++) {
            int sx = gridX + slot % 3 * 18, sy = gridY + slot / 3 * 18;
            g.fill(sx, sy, sx + 17, sy + 17, 0xffd3c8a9);
            int index = value instanceof ShapedRecipe shaped
                    ? (slot % 3 < shaped.getWidth() && slot / 3 < shaped.getHeight() ? slot / 3 * gridWidth + slot % 3 : -1) : slot;
            if (index >= 0 && index < ingredients.size()) {
                var options = ingredients.get(index).getItems();
                if (options.length > 0) icon(g, options[(int) (System.currentTimeMillis() / 1500 % options.length)], sx, sy, 1, true);
            }
        }
        g.drawString(font, ">", gridX + 64, gridY + 22, GOLD, false);
        g.fill(gridX + 82, gridY + 16, gridX + 108, gridY + 42, 0xffd4dfc4);
        icon(g, value.getResultItem(minecraft.level.registryAccess()), gridX + 87, gridY + 21, 1, true);
    }

    private ItemStack icon(String id) {
        return icons.computeIfAbsent(id, key -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(key)).orElse(Items.BOOK).getDefaultInstance());
    }
    private void icon(GuiGraphics g, ItemStack stack, int left, int top, float scale, boolean interactive) {
        g.pose().pushPose(); g.pose().translate(left, top, 0); g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0); g.renderItemDecorations(font, stack, 0, 0); g.pose().popPose();
        if (interactive && !stack.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            var related = book.entries().stream().filter(entry -> entry.items().contains(id)).findFirst();
            hits.add(new Hit(left, top, (int) (16 * scale), (int) (16 * scale), stack,
                    related.isPresent() ? () -> open(related.get().id(), true, false) : null));
        }
    }
    private void lines(GuiGraphics g, List<FormattedCharSequence> lines, int left, int top, int color) {
        for (int row = 0; row < lines.size(); row++) g.drawString(font, lines.get(row), left, top + row * 11, color, false);
    }
    private String fit(String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(1, width - 9)) + "..."; }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) for (var hit : List.copyOf(hits)) if (hit.action() != null && hit.contains(mouseX, mouseY)) { hit.action().run(); return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return false;
        if (mouseX < x + nav) { offset += vertical < 0 ? 1 : -1; filter(); }
        else flip(vertical < 0 ? 1 : -1);
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) { setFocused(search); search.setFocused(true); return true; }
        if (!search.isFocused()) {
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { flip(1); return true; }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { flip(-1); return true; }
            if (key == GLFW.GLFW_KEY_HOME) { open("welcome", true, false); return true; }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private void loadPreferences() {
        if (preferencesLoaded) return;
        preferencesLoaded = true;
        var file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/embeddeddev-handbook.json");
        if (!Files.isRegularFile(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var state = new Gson().fromJson(reader, Preferences.class);
            if (state != null) {
                if (state.entry() != null) lastEntry = state.entry();
                lastPage = Math.max(0, state.page());
                if (state.bookmarks() != null) for (String id : state.bookmarks()) {
                    if (book.entries().stream().anyMatch(entry -> entry.id().equals(id))) BOOKMARKS.add(id);
                }
            }
        } catch (Exception error) { EmbeddedDev.LOGGER.warn("Cannot read handbook bookmarks", error); }
    }
    private void savePreferences() {
        var file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/embeddeddev-handbook.json");
        var temporary = file.resolveSibling("embeddeddev-handbook.json.tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, new Gson().toJson(new Preferences(selected.id(), page, List.copyOf(BOOKMARKS))), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception error) { EmbeddedDev.LOGGER.warn("Cannot save handbook bookmarks", error); }
    }
    @Override public void removed() { lastEntry = selected.id(); lastPage = page; savePreferences(); }

    private class JournalButton extends Button {
        final BooleanSupplier selected;
        JournalButton(int x, int y, int w, int h, String text, Runnable action, BooleanSupplier selected) {
            super(x, y, w, h, Component.literal(text), b -> action.run(), DEFAULT_NARRATION); this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
            boolean picked = selected.getAsBoolean();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), picked ? GREEN : isHoveredOrFocused() && active ? 0xffddd1ae : 0xffe9dfc2);
            g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), GOLD);
            String label = fit(getMessage().getString(), getWidth() - 6);
            g.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2,
                    !active ? 0xffa19c86 : picked ? PAPER : INK, false);
        }
    }
    private final class EntryButton extends JournalButton {
        final int row;
        Handbook.Entry entry;
        EntryButton(int x, int y, int width, int row) {
            super(x, y, width, 22, "", () -> {}, () -> false); this.row = row;
        }
        @Override public void onPress() { if (entry != null) open(entry.id(), true, true); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            if (entry == null) return;
            boolean picked = entry.id().equals(ManualScreen.this.selected.id());
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), picked ? 0xffd4dfc5 : isHoveredOrFocused() ? 0xffe0d5b6 : 0xffe9dfc1);
            if (picked) g.fill(getX(), getY() + 2, getX() + 2, getY() + getHeight() - 2, GREEN);
            icon(g, icon(entry.icon()), getX() + 5, getY() + 3, 1, false);
            g.drawString(font, fit(entry.title(), getWidth() - 29), getX() + 25, getY() + 7, picked ? GREEN : INK, false);
        }
    }
}
