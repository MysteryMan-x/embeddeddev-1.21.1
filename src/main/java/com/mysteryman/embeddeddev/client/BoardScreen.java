package com.mysteryman.embeddeddev.client;

import com.mysteryman.embeddeddev.item.CrystalItem;
import com.mysteryman.embeddeddev.mcu.IntelHex;
import com.mysteryman.embeddeddev.mcu.FaceBinding;
import com.mysteryman.embeddeddev.mcu.Mcs51Disassembler;
import com.mysteryman.embeddeddev.network.BoardNetwork;
import com.mysteryman.embeddeddev.wire.PinGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class BoardScreen extends Screen implements DeviceStateScreen {
    private final BlockPos pos;
    private CompoundTag state;
    private final FaceBinding[] bindings = new FaceBinding[6];
    private final List<List<PinGroup>> groups = new ArrayList<>();
    private List<String> assembly = List.of();
    private ItemStack crystal = ItemStack.EMPTY;
    private EditBox path;
    private String lastPath = "", message = "";
    private int x, y, w, polls, tab, wirePage;
    private static final int WIRE_ROWS = 5;
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    private static final int[] FACE_ORDER = {5, 3, 4, 2, 1, 0};
    private long messageUntil;
    public BoardScreen(BoardNetwork.State packet) {
        super(Component.translatable("gui.embeddeddev.title"));
        pos = packet.pos();
        for (int face = 0; face < 6; face++) groups.add(List.of());
        update(packet.data());
    }
    public boolean matches(BlockPos p) { return pos.equals(p); }
    public void update(CompoundTag tag) {
        state = tag;
        var level = Minecraft.getInstance().level;
        if (level != null) crystal = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Crystal"));
        for (int face = 0; face < bindings.length; face++) {
            try { bindings[face] = FaceBinding.parse(tag.getString("Pins" + face)); }
            catch (IllegalArgumentException e) { bindings[face] = FaceBinding.EMPTY; }
            try { groups.set(face, PinGroup.load(tag.getList("Groups" + face, Tag.TAG_COMPOUND))); }
            catch (IllegalArgumentException e) { groups.set(face, List.of(new PinGroup("G1", bindings[face]), PinGroup.empty(1), PinGroup.empty(2), PinGroup.empty(3))); }
        }
        byte[] code = tag.getByteArray("Instructions");
        int pc = tag.getInt("PC");
        var rows = new ArrayList<String>(4);
        for (int offset = 0; offset < code.length && rows.size() < 4;) {
            int address = (pc + offset) & 0xffff;
            var instruction = Mcs51Disassembler.decode(code, offset, address);
            StringJoiner bytes = new StringJoiner(" ");
            for (int i = 0; i < instruction.length() && offset + i < code.length; i++) {
                bytes.add(String.format("%02X", code[offset + i] & 0xff));
            }
            rows.add(String.format("%s %04X  %-8s  %s", offset == 0 ? ">" : " ", address, bytes, instruction.text()));
            offset += instruction.length();
        }
        assembly = rows;
        String next = tag.getString("Message");
        if (!next.isEmpty()) showMessage(Component.translatable(next).getString());
    }
    @Override protected void init() {
        w = Math.min(480, width - 12); x = (width - w) / 2; y = Math.max(4, (height - 226) / 2);
        String[] tabs = {"gui.embeddeddev.program_tab", "gui.embeddeddev.connectors_tab", "wire.embeddeddev.tab"};
        int tw = (w - 24) / 3;
        for (int i = 0; i < tabs.length; i++) {
            int selected = i;
            addRenderableWidget(Button.builder(Component.translatable(tabs[i]), b -> { tab = selected; rebuildWidgets(); })
                    .bounds(x + 10 + i * (tw + 2), y + 27, tw, 18).build());
        }
        if (tab == 2) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> wirePage = Math.max(0, wirePage - 1))
                    .bounds(x + 10, y + 183, 24, 17).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> wirePage = Math.min(wirePages() - 1, wirePage + 1))
                    .bounds(x + w - 34, y + 183, 24, 17).build());
            return;
        }
        if (tab == 1) {
            int cw = (w - 28) / 3;
            for (int i = 0; i < 6; i++) {
                final int face = FACE_ORDER[i];
                int cx = x + 10 + (i % 3) * (cw + 4), cy = y + 52 + (i / 3) * 74;
                addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.configure"), b -> minecraft.setScreen(new FaceConfigScreen(this, face, groups.get(face))))
                        .bounds(cx + 5, cy + 51, cw - 10, 17).build());
            }
            return;
        }
        addRenderableWidget(Button.builder(Component.empty(), b -> send(BoardNetwork.CRYSTAL_SOCKET, "", ""))
                .tooltip(Tooltip.create(Component.translatable("gui.embeddeddev.crystal_socket")))
                .bounds(x + w - 30, y + 4, 20, 20).build());
        path = new EditBox(font, x + 10, y + 54, w - 100, 18, Component.translatable("gui.embeddeddev.path"));
        path.setMaxLength(2048); path.setHint(Component.translatable("gui.embeddeddev.path")); path.setValue(lastPath);
        path.setResponder(v -> lastPath = v); addRenderableWidget(path);
        addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev.flash"), b -> flash())
                .bounds(x + w - 84, y + 53, 74, 20).build());
        int bw = (w - 24) / 5;
        String[] labels = {"run", "stop", "reset", "step", "close"};
        int[] actions = {BoardNetwork.RUN, BoardNetwork.STOP, BoardNetwork.RESET, BoardNetwork.STEP};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.translatable("gui.embeddeddev." + labels[i]), b -> {
                if (index == 4) onClose(); else send(actions[index], "", "");
            }).bounds(x + 10 + i * (bw + 1), y + 78, bw, 20).build());
        }
    }
    public static Component faceName(int f) { return Component.translatable("gui.embeddeddev." + FACES[f]); }
    public static Component kindName(FaceBinding.Kind kind) {
        return Component.translatable("gui.embeddeddev.pin_kind." + kind.panelKind().name().toLowerCase(Locale.ROOT));
    }
    public void configure(int face, FaceBinding binding) { send(BoardNetwork.CONFIGURE, Integer.toString(face), binding.wireText()); }
    public void configureGroups(int face, List<PinGroup> groups) {
        var data = new CompoundTag();
        data.put("Groups", PinGroup.save(groups));
        send(BoardNetwork.CONFIGURE_GROUPS, Integer.toString(face), data.toString());
    }
    private void send(int action, String name, String text) { PacketDistributor.sendToServer(new BoardNetwork.Action(pos, action, name, text)); }
    private void showMessage(String text) { message = text; messageUntil = System.currentTimeMillis() + 8000; }
    private void flash() {
        try {
            String value = path.getValue().strip();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) value = value.substring(1, value.length() - 1);
            Path file = Path.of(value);
            if (!file.isAbsolute()) file = minecraft.gameDirectory.toPath().resolve(file);
            if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".hex")) throw new IllegalArgumentException("Select a .hex file");
            // Read at most the limit plus one byte, even if the file grows during the read.
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(IntelHex.MAX_TEXT_LENGTH + 1); }
            if (bytes.length > IntelHex.MAX_TEXT_LENGTH) throw new IllegalArgumentException("HEX exceeds 128 KiB text limit");
            String hex = IntelHex.encode(IntelHex.parse(new String(bytes, StandardCharsets.UTF_8)).code());
            String name = file.getFileName().toString();
            send(BoardNetwork.FLASH, name.substring(0, Math.min(96, name.length())), hex);
        } catch (IOException | IllegalArgumentException | SecurityException e) { showMessage(e.getMessage() == null ? "Cannot read HEX file" : e.getMessage()); }
    }
    @Override public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty()) { lastPath = files.getFirst().toAbsolutePath().toString(); tab = 0; rebuildWidgets(); }
    }
    @Override public void tick() {
        if (++polls >= 5) { polls = 0; send(BoardNetwork.POLL, "", ""); }
        if (minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 64) onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(x, y, x + w, y + 226, 0xF018232D);
        g.fill(x, y, x + w, y + 2, 0xFF43D9BC);
        int titleWidth = w - (tab == 0 ? 142 : 120);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), titleWidth), x + 10, y + 9, 0xFFE0F5F0, false);
        if (tab == 1) {
            int cw = (w - 28) / 3;
            for (int i = 0; i < 6; i++) {
                int cx = x + 10 + (i % 3) * (cw + 4), cy = y + 52 + (i / 3) * 74;
                g.fill(cx, cy, cx + cw, cy + 71, 0xFF223540);
            }
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (tab == 2) {
            renderWires(g);
            renderFooter(g);
            return;
        }
        if (tab == 1) {
            renderConnectors(g);
            if (minecraft.player != null) {
                Component heading = Component.translatable("gui.embeddeddev.heading",
                        faceName(minecraft.player.getDirection().get3DDataValue()));
                g.drawString(font, heading, x + w - 10 - font.width(heading), y + 9, 0xFF43D9BC, false);
            }
            renderFooter(g);
            return;
        }
        Component frequency = crystal.getItem() instanceof CrystalItem item
                ? Component.literal(BigDecimal.valueOf(item.frequencyHz(), 6).stripTrailingZeros().toPlainString() + " MHz")
                : Component.translatable("gui.embeddeddev.empty_socket");
        g.drawString(font, frequency, x + w - 35 - font.width(frequency), y + 10, 0xFFA4B8C6, false);
        g.renderItem(crystal, x + w - 28, y + 6);
        String status = Component.translatable(state.getBoolean("Running") ? "gui.embeddeddev.running" : "gui.embeddeddev.stopped").getString();
        line(g, status + "  " + state.getString("Name"), 107, 0xFF43D9BC);
        line(g, String.format("PC %04X   A %02X   Cycles %d", state.getInt("PC"), state.getInt("A"), state.getLong("Cycles")), 121, 0xFFD5E4EB);
        int[] ports = state.getIntArray("Ports");
        if (ports.length == 4) line(g, String.format("P0 %02X  P1 %02X  P2 %02X  P3 %02X", ports[0], ports[1], ports[2], ports[3]), 135, 0xFFD5E4EB);
        String serial = state.getString("Serial").replace('\n', ' ').replace('\r', ' ');
        String tail = serial.substring(Math.max(0, serial.length() - 60));
        if (assembly.isEmpty()) {
            line(g, Component.translatable("gui.embeddeddev.no_assembly").getString(), 150, 0xFFA4B8C6);
        }
        for (int row = 0; row < assembly.size(); row++) {
            line(g, assembly.get(row), 148 + row * 11, row == 0 ? 0xFF43D9BC : 0xFFD5E4EB);
        }
        line(g, "UART: " + tail, 195, 0xFFA4B8C6);
        renderFooter(g);
    }
    private int wirePages() { return Math.max(1, (state.getList("Wires", Tag.TAG_COMPOUND).size() + WIRE_ROWS - 1) / WIRE_ROWS); }

    private void renderWires(GuiGraphics g) {
        var routes = state.getList("Wires", Tag.TAG_COMPOUND);
        wirePage = Math.min(wirePage, wirePages() - 1);
        if (routes.isEmpty()) line(g, Component.translatable("wire.embeddeddev.empty").getString(), 58, 0xFFA4B8C6);
        for (int row = 0; row < WIRE_ROWS && row + wirePage * WIRE_ROWS < routes.size(); row++) {
            var route = routes.getCompound(row + wirePage * WIRE_ROWS);
            int y = 52 + row * 25;
            String name = route.getString("Name");
            String terminal = route.getString("Terminal");
            String caption = faceName(route.getInt("Face")).getString() + " " + route.getString("Group") + "  ·  "
                    + (name.isEmpty() ? "" : Component.translatable(name).getString() + "  ")
                    + (terminal.isEmpty() ? "" : "[" + terminal + "]  ")
                    + BlockPos.of(route.getLong("Pos")).toShortString();
            line(g, caption, y, 0xFFE0F5F0);
            String status = route.getString("Status");
            String pins = route.getString("Pins"), address = route.getInt("Address") < 0 ? "" : String.format("  0x%02X", route.getInt("Address"));
            line(g, Component.translatable(status).getString() + (pins.isEmpty() ? "" : "  ·  " + pins) + address,
                    y + 11, status.endsWith(".connected") ? 0xFF43D9BC : 0xFFFFC080);
        }
        g.drawCenteredString(font, (wirePage + 1) + " / " + wirePages(), x + w / 2, y + 188, 0xFFA4B8C6);
    }
    private void renderConnectors(GuiGraphics g) {
        int cw = (w - 28) / 3;
        for (int i = 0; i < 6; i++) {
            int face = FACE_ORDER[i], cx = x + 10 + (i % 3) * (cw + 4), cy = y + 52 + (i / 3) * 74;
            g.drawString(font, faceName(face), cx + 5, cy + 3, 0xFF43D9BC, false);
            int count = groups.get(face).stream().mapToInt(group -> group.binding().pins().size()).sum();
            long connectedGroups = groups.get(face).stream().filter(group -> !group.binding().pins().isEmpty()).count();
            g.drawString(font, Component.translatable("groups.embeddeddev.summary", connectedGroups, count), cx + 5, cy + 15, 0xFFE0F5F0, false);
            StringJoiner labels = new StringJoiner(", ");
            for (var group : groups.get(face)) if (!group.binding().pins().isEmpty()) labels.add(group.name());
            String pins = count == 0 ? "—" : labels.toString();
            g.drawString(font, font.plainSubstrByWidth(pins, cw - 10), cx + 5, cy + 27, 0xFFA4B8C6, false);
            String info = state.getString("Device" + face);
            if (info.isEmpty() && count > 0) {
                int[] output = state.getIntArray("Output");
                if (output.length == 6) info = Component.translatable("gui.embeddeddev.signal", output[face]).getString();
            }
            g.drawString(font, font.plainSubstrByWidth(info, cw - 10), cx + 5, cy + 39, 0xFFE4C888, false);
        }
    }
    private void renderFooter(GuiGraphics g) {
        String fault = state.getString("Fault");
        String footer = !fault.isEmpty() ? fault : System.currentTimeMillis() < messageUntil ? message : Component.translatable("gui.embeddeddev.hint").getString();
        g.drawString(font, font.plainSubstrByWidth(footer, w - 20), x + 10, y + 207, fault.isEmpty() ? 0xFFE4C888 : 0xFFFF7777, false);
    }
    private void line(GuiGraphics g, String text, int offset, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, w - 20), x + 10, y + offset, color, false);
    }
}
