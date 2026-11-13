# 内置 HEX 示例与 OLED 数据显示

现在共有 9 个内置示例。OLED 文字演示保留 `i2c_oled_hello.hex`，重复的 `oled_hello.hex` 已删除；条纹诊断仍保留。

| 文件 | 接线与晶振 | 显示 / 行为 |
| --- | --- | --- |
| redstone_blink.hex | 默认晶振；P1.2 接红石数字输出 | 红石闪烁 |
| i2c_oled_hello.hex | 默认晶振；OLED SDA=P1.6、SCL=P1.7 | 欢迎文字 |
| oled_pattern.hex | 同上 | 原始条纹测试图 |
| seven_segment_counter.hex | 默认晶振；A–DP=P1.0–P1.7，共阴 | 数字 0–9 |
| i2c_sensor.hex | **12 MHz**；OLED 3CH 与环境传感器 48H 共用 P1.6/P1.7；LED=P1.2 可选 | 红石、天光、方块光、日进度 |
| pcf8591_mirror.hex | **12 MHz**；OLED 3CH 与 PCF8591 48H 共用 P1.6/P1.7 | 四路 ADC 0–255、DAC；AIN0 → AOUT |
| ds18b20_read.hex | **12 MHz**；DQ=P1.2；OLED P1.6/P1.7 | 有符号摄氏温度、CRC 状态 |
| mpu6050_oled.hex | **12 MHz**；OLED 3CH 与 MPU6050 68H 共用 P1.6/P1.7 | 三轴加速度 mg、三轴角速度 °/s |
| altimeter_oled.hex | **12 MHz**；OLED 3CH 与 ALT-01 76H 共用 P1.6/P1.7 | 高度 / 零点 cm、垂直速度 cm/s |

地址均为七位 I²C 地址。SDA=P1.6、SCL=P1.7，主板一组引脚可用接线柱分到 OLED 和 I²C 传感器。DS18B20 的 DQ 用单独的 GPIO 线组。环境传感器和 PCF8591 默认同为 48H，请分别搭建这两个示例。

传感器程序持续刷新：普通传感器在每轮显示后等待 200 ms；DS18B20 还等待 800 ms 转换。实际刷新耗时包括 CPU 运行和 OLED 通信。断线 / CRC 失败会显示错误，重新接线会自动重试；屏幕重连会重新初始化。数值行填充空格，避免三位数变成一位数后留下旧数字。

PCF8591 的 AIN0–3 按北、东、南、西采样；AOUT 从上面输出。MPU6050 显示原始数据换算的物理单位，不执行姿态解算。高度计保留模块原来的零点，不会每次启动自动归零。DS18B20 保留负温度小数的符号，验证 Scratchpad CRC 后才显示。

## 编译五个传感器显示程序

```powershell
.\tools\build-sensor-examples.ps1
# 或指定已安装的 SDCC
.\tools\build-sensor-examples.ps1 -Compiler 'C:\path\to\sdcc.exe'
```

脚本为每个主程序链接 `sensor_display.c`、`drivers/ssd1306.c`、`drivers/ed_font5x7.c`、`drivers/i2c_bus.c`、`drivers/ed_delay.c` 和对应协议驱动。采用 SDCC SMALL、stack-auto 与配套的 small-stack-auto 运行库，最多 8 KiB Flash、256 B IDATA，不使用 XDATA，也不分配 1024 字节帧缓冲。

所有源文件统一定义 `ED_FOSC_HZ=12000000UL` 和 `ED_DQ_BIT_ADDRESS=0x92`。附录通用驱动默认 DQ=P3.7；本目录 DS18B20 显示例子使用 P1.2，请勿混淆。DATA 从 40H 分配，保留 30H–3FH 给调试变量：环境 / PCF8591 的 30H 为采样值，DS18B20 的 30H–38H 为 Scratchpad，3FH=A5H 成功、E1H 失败。不要把调试地址当作外设寄存器。

这些 HEX 由实际 C 源码编译，并在模组 MCU 解释器中测试。Keil C51 的头文件分支可作为移植起点，但需按 Keil 的内存分配与调用约定配置；本版本未在 Keil 编译器中验证。原有欢迎文字、条纹和数码管 HEX 仍由 `tools/generate-firmware.mjs` 汇编生成，传感器文件由上面的编译脚本独立生成，互不覆盖。
