package com.mysteryman.embeddeddev.manual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mysteryman.embeddeddev.mcu.Ds1302;
import com.mysteryman.embeddeddev.mcu.Ds18b20;
import com.mysteryman.embeddeddev.mcu.SevenSegment;
import com.mysteryman.embeddeddev.mcu.BoardHardware;
import com.mysteryman.embeddeddev.firmware.BuiltinFirmware;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cross-checks prose links and diagrams against real recipes, examples and peripheral terminals. */
public final class HandbookVerification {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path resources = Path.of("src/main/resources"), manual = resources.resolve("assets/embeddeddev/manual");
        Handbook chinese, english;
        try (var reader = Files.newBufferedReader(manual.resolve("zh_cn.json"), StandardCharsets.UTF_8)) { chinese = Handbook.read(reader); }
        try (var reader = Files.newBufferedReader(manual.resolve("en_us.json"), StandardCharsets.UTF_8)) { english = Handbook.read(reader); }
        require(chinese.entries().size() >= 25, "A detailed catalog covers the electronics workflow");
        require(chinese.entries().stream().map(Handbook.Entry::id).toList().equals(english.entries().stream().map(Handbook.Entry::id).toList()), "Both languages have the same entries");
        var modulePins = Map.of("embeddeddev:ds1302", new Ds1302().terminals(), "embeddeddev:ds18b20", new Ds18b20().terminals(),
                "embeddeddev:seven_segment", new SevenSegment().terminals(), "embeddeddev:oled", List.of("SDA", "SCL"),
                "embeddeddev:pcf8591", List.of("SDA", "SCL"), "embeddeddev:i2c_sensor", List.of("SDA", "SCL"),
                "embeddeddev:led_module", List.of("LED"), "embeddeddev:button_module", List.of("KEY"),
                "embeddeddev:redstone_port", List.of("SIGNAL"));
        for (var book : List.of(chinese, english)) {
            var documentedItems = new HashSet<String>(); var illustratedRecipes = new HashSet<String>();
            for (var entry : book.entries()) {
                documentedItems.add(entry.icon()); documentedItems.addAll(entry.items());
                for (var block : entry.blocks()) {
                    if (block.type().equals("recipe")) {
                        String recipe = block.id().substring("embeddeddev:".length()); illustratedRecipes.add(recipe);
                        require(Files.exists(resources.resolve("data/embeddeddev/recipe/" + recipe + ".json")), "Recipe exists: " + recipe);
                    }
                    if (block.type().equals("wiring")) {
                        var terminals = modulePins.get(block.id());
                        require(terminals != null, "Diagram references a real module");
                        var used = new HashSet<String>();
                        for (var label : block.labels()) {
                            String[] pair = label.split("\\|", -1);
                            require(pair[0].matches("P[0-3]\\.[0-7]"), "Diagram uses a valid GPIO pin");
                            require(terminals.contains(pair[1]) && used.add(pair[1]), "Diagram uses real unique device terminals");
                        }
                        require(used.containsAll(terminals), "A complete wiring diagram covers every required device pin");
                    }
                }
            }
            try (var recipes = Files.list(resources.resolve("data/embeddeddev/recipe"))) {
                for (Path path : recipes.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                    String item = json.getAsJsonObject("result").get("id").getAsString();
                    require(documentedItems.contains(item), "Every craftable mod item has a handbook entry: " + item);
                    String name = path.getFileName().toString().replace(".json", "");
                    require(illustratedRecipes.contains(name), "Every survival recipe has an illustrated card: " + name);
                }
            }
            for (String example : List.of("redstone_blink.hex", "pcf8591_mirror.hex", "i2c_oled_hello.hex", "oled_hello.hex",
                    "i2c_sensor.hex", "ds18b20_read.hex", "seven_segment_counter.hex", "oled_pattern.hex")) {
                require(Files.exists(Path.of("examples", example)) && book.entry("examples").matches(example), "Example index resolves actual file: " + example);
            }
            require(book.entry("adc").matches("PCF8591") && book.entry("temperature").matches("DQ"), "Search includes titles and wiring labels");
            var analog = book.entry("analog_io");
            require(analog.matches("STC89C52RC") && analog.matches("PCF8591") && analog.matches("pcf8591_mirror.hex"), "Analog conversion is documented as an external chip and tested example");
            var hardware = new BoardHardware();
            require(book.entry("xdata").matches("64 KiB") && book.entry("xdata").matches("3CH"), "Handbook distinguishes full XRAM from I2C addresses");
            require(java.util.stream.IntStream.range(0, 65536).allMatch(address -> hardware.readExternal(address) == -1),
                    "Documented 64 KiB XDATA has no peripheral overlay");
        }
        for (String language : List.of("zh_cn", "en_us")) {
            var captions = JsonParser.parseString(Files.readString(resources.resolve("assets/embeddeddev/lang/" + language + ".json"))).getAsJsonObject();
            for (var example : BuiltinFirmware.values()) {
                require(captions.has(example.titleKey()) && captions.has(example.helpKey()), "Every selectable example has a localized name and wiring hint");
            }
        }
        String original = Files.readString(manual.resolve("zh_cn.json"), StandardCharsets.UTF_8);
        var broken = JsonParser.parseString(original).getAsJsonObject();
        var invalidLink = new JsonObject(); invalidLink.addProperty("type", "link"); invalidLink.addProperty("text", "invalid"); invalidLink.addProperty("target", "missing");
        broken.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("blocks").add(invalidLink);
        boolean rejected = false;
        try { Handbook.read(new StringReader(broken.toString())); } catch (RuntimeException expected) { rejected = true; }
        require(rejected, "Broken internal links are rejected before rendering");
        System.out.println("Handbook verification passed: " + checks + " checks; " + chinese.entries().size() + " bilingual entries");
    }
}
