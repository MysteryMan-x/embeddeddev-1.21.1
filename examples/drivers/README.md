# 可复制的模块驱动

这些文件与游戏手册「附录」中的代码逐字一致，包含 11 组驱动、公共依赖和最小主程序。所有接口均通过普通 GPIO、软件 I²C、三线或 1-Wire 通信，不使用 MCU 内存映射窗口。

在 Keil C51 中建立 SMALL 模型的 8051 项目，将本目录加入 Include Path。复制 `ed_platform.h`，按实际接线改引脚定义，添加下表中的 `.c` 和对应 `demos/*_demo.c` 作为唯一主程序。实际编译和执行验证使用 SDCC，未验证 Keil 编译器分支。

|模块|加入工程的实现文件|
|---|---|
|MPU6050|mpu6050.c、i2c_bus.c|
|ALT-01 高度计|altimeter.c、i2c_bus.c|
|多通道电机接口|motor_driver.c、i2c_bus.c|
|PCF8591|pcf8591.c、i2c_bus.c|
|环境传感器|environment.c、i2c_bus.c|
|九键面板和 LED|gpio_panel.c|
|GPIO 红石接口|gpio_redstone.c|
|八段数码管|seven_segment.c|
|DS1302|ds1302.c|
|DS18B20|ds18b20.c、ed_delay.c|
|SSD1306 OLED|ssd1306.c、ed_font5x7.c、i2c_bus.c|

I²C 默认 SDA=P1.6、SCL=P1.7，地址参数全部为七位地址。多数函数返回 1 表示成功，0 表示 NACK、CRC 或参数错误；错误时不要使用旧采样。完整寄存器、引脚、端序、单位和操作流程见游戏内各模块条目。

时序例子默认 **12 MHz、12T**，请安装 12 MHz 晶振。DS18B20 的初始短采样槽依赖指令时间，换晶振时需同时改 `ED_FOSC_HZ` 和相关 NOP；Timer0 被 `ed_delay` 独占并关闭 ET0。其他定时任务使用 Timer1/2。

默认引脚：LED=P1.0、红石 SIGNAL=P1.2、按钮1–8=P0.0–7、按钮9=P2.0、DS1302 CE/SCLK/IO=P3.0/1/2、DS18B20 DQ=P3.7、数码管 A–G/DP=P2.0–7。数码管与按钮9冲突，RTC 与 UART 冲突，组合前必须规划引脚。每个例子是独立项目，不要把全部实现堆进同一 8 KiB 工程。

`motor_driver_demo.c` 默认仅读状态，没有机械动力也可使用；输出控制 API 仍需相邻的受支持马达。MPU6050 和高度计无需任何科技模组即可工作，装在 Sable 结构上时可读取运动反馈。

调试变量 `demo_ok`、`demo_value`、`demo_done` 都是普通 RAM；`demo_done=A5H` 表示完成过一轮，`demo_ok=1` 表示成功。DS18B20 温度是有符号 Q4，除以16为摄氏度；PCF8591 例子会先丢弃旧转换，再把 AIN0 送入 DAC。

在完整模组仓库中可运行：

```powershell
./tools/build-driver-examples.ps1
./gradlew.bat verifyDriverExamples
```

脚本逐个编译实现文件为 `.rel`，再链接独立主程序，并限制 8 KiB Flash / 256 字节内部 RAM。运行验证在实际 MCU 模拟器上检查 I²C、OLED、GPIO、RTC、1-Wire ROM 搜索、CRC、分辨率和负温度。
