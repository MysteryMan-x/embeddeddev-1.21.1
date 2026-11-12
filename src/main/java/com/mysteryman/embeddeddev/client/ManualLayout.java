package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.manual.Handbook;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Reflows paragraphs and table cells at the current GUI scale instead of clipping book text. */
public final class ManualLayout {
    public record Flow(Handbook.Block source, String kind, List<FormattedCharSequence> lines,
                List<List<FormattedCharSequence>> cells, int height, boolean header) {}

    public static List<List<Flow>> pages(Font font, Handbook.Entry entry, int width, int height) {
        var flows = new ArrayList<Flow>();
        for (var block : entry.blocks()) {
            switch (block.type()) {
                case "recipe" -> flows.add(new Flow(block, "recipe", List.of(), List.of(), 88, false));
                case "wiring" -> flows.add(new Flow(block, "wiring", List.of(), List.of(), 34 + block.labels().size() * 10, false));
                case "table" -> {
                    int cellWidth = width / block.rows().getFirst().size() - 10;
                    for (int row = 0; row < block.rows().size(); row++) {
                        var cells = block.rows().get(row).stream().map(text -> wrap(font, text, cellWidth)).toList();
                        int lineCount = cells.stream().mapToInt(List::size).max().orElse(1);
                        int maxLines = Math.max(1, (height - 42) / 11);
                        for (int start = 0; start < lineCount; start += maxLines) {
                            var part = new ArrayList<List<FormattedCharSequence>>();
                            int end = Math.min(start + maxLines, lineCount);
                            for (var cell : cells) part.add(start < cell.size() ? cell.subList(start, Math.min(end, cell.size())) : List.of());
                            flows.add(new Flow(block, "table", List.of(), part, (end - start) * 11 + 8, row == 0));
                        }
                    }
                }
                default -> {
                    int padding = switch (block.type()) { case "tip" -> 16; case "code" -> 22; case "link" -> 12; default -> 6; };
                    int textWidth = width - (block.type().equals("text") ? 2 : 16);
                    var lines = block.type().equals("code") ? font.split(Component.literal(block.text()), textWidth) : wrap(font, block.text(), textWidth);
                    // Leave room for a two-line heading beside the first text fragment.
                    int maxLines = Math.max(1, (height - padding - 32) / 11);
                    for (int start = 0; start < lines.size(); start += maxLines) {
                        var part = lines.subList(start, Math.min(start + maxLines, lines.size()));
                        flows.add(new Flow(block, block.type(), part, List.of(), part.size() * 11 + padding, false));
                    }
                }
            }
        }
        var pages = new ArrayList<List<Flow>>();
        var page = new ArrayList<Flow>();
        int used = 0;
        Flow tableHeader = null;
        for (int index = 0; index < flows.size(); index++) {
            var flow = flows.get(index);
            if (flow.kind().equals("table") && flow.header()) tableHeader = flow;
            int reserve = flow.kind().equals("heading") && index + 1 < flows.size()
                    ? Math.min(flows.get(index + 1).height(), height - flow.height()) : 0;
            if (!page.isEmpty() && used + flow.height() + reserve > height) {
                pages.add(List.copyOf(page)); page.clear(); used = 0;
                if (flow.kind().equals("table") && !flow.header() && tableHeader != null
                        && tableHeader.source() == flow.source() && tableHeader.height() + flow.height() <= height) {
                    page.add(tableHeader); used += tableHeader.height();
                }
            }
            page.add(flow); used += flow.height();
        }
        if (!page.isEmpty()) pages.add(List.copyOf(page));
        return pages.isEmpty() ? List.of(List.of()) : List.copyOf(pages);
    }

    private static List<FormattedCharSequence> wrap(Font font, String text, int width) {
        var lines = new ArrayList<FormattedCharSequence>();
        String closing = "，。！？；：、）》」』】…,.!?;:%";
        String opening = "（《「『【(";
        for (String paragraph : text.split("\\n", -1)) {
            String remaining = paragraph;
            while (font.width(remaining) > width) {
                int end = font.plainSubstrByWidth(remaining, width).length();
                if (end < 1) end = Character.charCount(remaining.codePointAt(0));
                int space = remaining.lastIndexOf(' ', Math.min(end, remaining.length() - 1));
                if (space > end / 2) end = space;
                if (end < remaining.length() && closing.indexOf(remaining.charAt(end)) >= 0 && end > 1) {
                    int word = remaining.lastIndexOf(' ', end - 1);
                    end = word > end / 2 ? word : remaining.offsetByCodePoints(end, -1);
                }
                if (end > 1 && opening.indexOf(remaining.charAt(end - 1)) >= 0) end = remaining.offsetByCodePoints(end, -1);
                lines.add(Component.literal(remaining.substring(0, end)).getVisualOrderText());
                remaining = remaining.substring(end).stripLeading();
            }
            lines.add(Component.literal(remaining).getVisualOrderText());
        }
        return List.copyOf(lines);
    }
    private ManualLayout() {}
}
