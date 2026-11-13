package com.mysteryman.embeddeddev.firmware;

import com.mysteryman.embeddeddev.mcu.IntelHex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Examples shipped inside the mod, independent of a player's local filesystem. */
public enum BuiltinFirmware {
    REDSTONE_BLINK("redstone_blink"),
    I2C_OLED_HELLO("i2c_oled_hello"),
    PCF8591_MIRROR("pcf8591_mirror"),
    I2C_SENSOR("i2c_sensor"),
    DS18B20_READ("ds18b20_read"),
    MPU6050_OLED("mpu6050_oled"),
    ALTIMETER_OLED("altimeter_oled"),
    SEVEN_SEGMENT_COUNTER("seven_segment_counter"),
    OLED_PATTERN("oled_pattern");

    private final String id;
    BuiltinFirmware(String id) { this.id = id; }
    public String fileName() { return id + ".hex"; }
    public String titleKey() { return "firmware.embeddeddev." + id; }
    public String helpKey() { return titleKey() + ".help"; }

    public String hex() throws IOException {
        try (var input = BuiltinFirmware.class.getResourceAsStream("/assets/embeddeddev/firmware/" + fileName())) {
            if (input == null) throw new IOException("Missing bundled firmware: " + fileName());
            byte[] bytes = input.readNBytes(IntelHex.MAX_TEXT_LENGTH + 1);
            if (bytes.length > IntelHex.MAX_TEXT_LENGTH) throw new IOException("Bundled HEX exceeds text limit");
            return IntelHex.encode(IntelHex.parse(new String(bytes, StandardCharsets.UTF_8)).code());
        }
    }
}
