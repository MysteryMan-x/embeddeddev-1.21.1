package com.mysteryman.embeddeddev.manual;

import com.google.gson.Gson;

import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Resource-pack friendly handbook data, independent of the Minecraft client. */
public record Handbook(String title, String subtitle, List<Category> categories, List<Entry> entries) {
    private static final Set<String> TYPES = Set.of("heading", "text", "tip", "table", "code", "recipe", "wiring", "link");

    public Handbook {
        if (title == null || title.isBlank() || categories == null || categories.isEmpty() || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Handbook needs a title, categories and entries");
        }
        subtitle = subtitle == null ? "" : subtitle;
        categories = List.copyOf(categories);
        entries = List.copyOf(entries);
        var categoryIds = new HashSet<String>();
        for (var category : categories) if (!categoryIds.add(category.id())) throw new IllegalArgumentException("Duplicate handbook category");
        var ids = new HashSet<String>();
        for (var entry : entries) {
            if (!ids.add(entry.id()) || !categoryIds.contains(entry.category())) throw new IllegalArgumentException("Invalid handbook entry: " + entry.id());
        }
        for (var entry : entries) for (var block : entry.blocks()) {
            if (block.type().equals("link") && !ids.contains(block.target())) throw new IllegalArgumentException("Broken handbook link: " + block.target());
        }
    }

    public static Handbook read(Reader reader) { return new Gson().fromJson(reader, Handbook.class); }
    public Entry entry(String id) { return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(entries.getFirst()); }
    public Category category(String id) { return categories.stream().filter(c -> c.id().equals(id)).findFirst().orElse(categories.getFirst()); }

    public record Category(String id, String title, String icon) {
        public Category { require(id); require(title); location(icon); }
    }

    public record Entry(String id, String category, String title, String subtitle, String icon, String keywords,
                        List<String> items, List<Block> blocks) {
        public Entry {
            require(id); require(category); require(title); location(icon);
            subtitle = subtitle == null ? "" : subtitle;
            keywords = keywords == null ? "" : keywords;
            items = items == null ? List.of(icon) : List.copyOf(items);
            items.forEach(Handbook::location);
            if (blocks == null || blocks.isEmpty()) throw new IllegalArgumentException("Empty handbook entry: " + id);
            blocks = List.copyOf(blocks);
        }
        public boolean matches(String query) {
            if (query.isBlank()) return true;
            var text = new StringBuilder(title).append(' ').append(subtitle).append(' ').append(keywords);
            items.forEach(item -> text.append(' ').append(item));
            for (var block : blocks) {
                text.append(' ').append(block.text());
                block.labels().forEach(label -> text.append(' ').append(label));
                block.rows().forEach(row -> row.forEach(cell -> text.append(' ').append(cell)));
            }
            return text.toString().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));
        }
    }

    public record Block(String type, String text, String id, List<String> labels, List<List<String>> rows, String target) {
        public Block {
            if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown handbook block: " + type);
            text = text == null ? "" : text;
            labels = labels == null ? List.of() : List.copyOf(labels);
            rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
            if (Set.of("heading", "text", "tip", "code", "link").contains(type)) require(text);
            if (type.equals("recipe") || type.equals("wiring")) location(id);
            if (type.equals("link")) require(target);
            if (type.equals("wiring") && (labels.isEmpty() || labels.size() > 8
                    || labels.stream().anyMatch(label -> label.split("\\|", -1).length != 2))) {
                throw new IllegalArgumentException("Invalid handbook wiring diagram");
            }
            int columns = rows.isEmpty() ? 0 : rows.getFirst().size();
            if (type.equals("table") && (columns == 0 || columns > 4 || rows.stream().anyMatch(row -> row.size() != columns))) {
                throw new IllegalArgumentException("Invalid handbook table");
            }
        }
    }

    private static void require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing handbook text");
    }
    private static void location(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid handbook resource ID");
    }
}
