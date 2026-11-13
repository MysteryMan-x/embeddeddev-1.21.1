// Keep the release changes alongside the base handbook's protocol reference pages.
export function updateLiveExamples({entries,h,p,tip,table,link}) {
 const find=id=>{const e=entries.find(e=>e.id===id);if(!e)throw Error('Missing handbook entry '+id);return e;};
 const examples=[
  ['redstone_blink.hex','GPIO P1.2 → 红石数字输出；默认晶振','P1.2 to digital redstone output; default clock'],
  ['i2c_oled_hello.hex','OLED 3CH；P1.6 SDA / P1.7 SCL；默认晶振','OLED 3CH; P1.6 SDA / P1.7 SCL; default clock'],
  ['pcf8591_mirror.hex','12 MHz；PCF8591 48H + OLED 3CH；四路采集、AIN0 → AOUT','12 MHz; PCF8591 48H + OLED 3CH; four ADC channels and AIN0 to AOUT'],
  ['i2c_sensor.hex','12 MHz；环境传感器 48H + OLED 3CH；LED=P1.2 可选','12 MHz; environment 48H + OLED 3CH; optional P1.2 LED'],
  ['ds18b20_read.hex','12 MHz；DQ=P1.2；OLED P1.6 SDA / P1.7 SCL','12 MHz; DQ=P1.2; OLED P1.6 SDA / P1.7 SCL'],
  ['mpu6050_oled.hex','12 MHz；MPU6050 68H + OLED 3CH；加速度与角速度','12 MHz; MPU6050 68H + OLED 3CH; acceleration and angular rate'],
  ['altimeter_oled.hex','12 MHz；ALT-01 76H + OLED 3CH；高度与垂直速度','12 MHz; ALT-01 76H + OLED 3CH; height and vertical speed'],
  ['seven_segment_counter.hex','A–DP=P1.0–7；共阴、默认晶振','A–DP=P1.0–7; common cathode; default clock'],
  ['oled_pattern.hex','OLED 软件 I²C 诊断条纹；默认晶振','OLED software-I2C diagnostic stripes; default clock']
 ];
 find('examples').blocks=[
  p('当前内置 9 个 HEX：OLED 文字示例只保留 i2c_oled_hello.hex，重复的 oled_hello.hex 已移除。点击烧录页的示例名称选择，选择后再烧录、运行。旧存档中已经烧录的固件不会被自动清除。','There are nine bundled HEX files. The sole OLED text example is i2c_oled_hello.hex; the duplicate oled_hello.hex was removed. Select, flash, then run. Existing firmware in saved boards is preserved.'),
  table([['HEX 文件','接线与晶振'],...examples.map(x=>[x[0],x[1]])],[['HEX file','Wiring and oscillator'],...examples.map(x=>[x[0],x[2]])]),
  h('传感器与 OLED 共用一条总线','Share a sensor bus with OLED'),
  p('五个传感器显示程序都使用 12 MHz / 12T。OLED 七位地址 3CH，SDA=P1.6、SCL=P1.7。I²C 传感器与 OLED 接到同一线组，用接线柱分支；也可以在其他方向配置同样的两个物理引脚。DS18B20 的 DQ=P1.2 放在另一组 GPIO，OLED 仍接 P1.6/P1.7。','All five sensor display programs use 12 MHz / 12T. OLED is at seven-bit 3CH on P1.6 SDA / P1.7 SCL. Branch the same group to the sensor and screen using a relay, or reuse the same GPIO pair on another face. DS18B20 uses a separate P1.2 DQ group; OLED stays on P1.6/P1.7.'),
  tip('环境传感器与 PCF8591 默认都为 48H，这两个示例请分别搭建。MPU6050 示例要求 68H；改成 69H 后要同步修改固件。OLED 不占 MCU 映射窗口。','Environment and PCF8591 both default to 48H: build these examples separately. MPU6050 firmware expects 68H; update its source if using 69H. OLED occupies no MCU memory window.'),
  h('实时读数、故障与恢复','Live data, faults and recovery'),
  p('传感器示例连续刷新，固定宽度文字会覆盖旧数字，负温度保留符号。断开传感器会显示 SENSOR OFFLINE；DS18B20 显示 OFFLINE / CRC ERROR，错误时不会把旧读数继续当作有效数据。重新接线后自动重试，OLED 重新接入后也会初始化并恢复显示。','Sensor demos continuously refresh and pad text to erase old digits, preserving negative temperatures. A missing sensor shows SENSOR OFFLINE; DS18B20 shows OFFLINE / CRC ERROR. Invalid readings are not presented as current data. Reconnecting the sensor or OLED automatically retries and restores display.'),
  p('环境页显示红石、天光、方块光、日进度；PCF8591 显示四路 0–255 ADC 和 DAC；DS18B20 显示摄氏温度和 CRC 状态；MPU6050 显示三轴加速度（mg）与角速度（°/s）；ALT-01 显示绝对/相对/零点高度（cm）及垂直速度（cm/s），程序不会重置保存的零点。','Environment shows redstone, sky/block light and day progress; PCF8591 shows four 0–255 ADC values and DAC; DS18B20 shows Celsius and CRC; MPU6050 shows acceleration in mg and angular rate in degrees/s; ALT-01 shows absolute, relative and zero height in cm and vertical speed in cm/s, without resetting its saved zero.'),
  h('源码与重新编译','Sources and rebuilding'),
  p('五个显示示例的完整 C 源码在 examples，公共显示辅助为 sensor_display.c/.h，协议驱动在 examples/drivers。tools/build-sensor-examples.ps1 使用 SDCC SMALL、stack-auto 与对应运行库，限制 8 KiB Flash、256 B IDATA，不使用 XDATA。DS18B20 工程的所有源文件需统一 ED_DQ_BIT_ADDRESS=0x92；附录通用驱动默认 DQ=P3.7，不能混淆。','Complete C sources live in examples, with sensor_display.c/.h and protocol drivers in examples/drivers. tools/build-sensor-examples.ps1 uses SDCC SMALL, stack-auto and its matching library, limited to 8 KiB Flash and 256 B IDATA with no XDATA. Every source in the DS18B20 display project uses ED_DQ_BIT_ADDRESS=0x92; the generic appendix driver defaults to P3.7.'),
  p('SDCC 编译脚本保留 DATA 30H–3FH 给调试变量：环境与 PCF8591 的 30H 为采样值；DS18B20 的 30H–38H 为带 CRC 的 Scratchpad，3FH=A5H 成功、E1H 失败。Keil 工程需要按其内存分配与调用约定配置；这些新 HEX 已用 SDCC 编译执行验证，未在 Keil 中验证。','The SDCC script reserves DATA 30H–3FH for debug variables: environment/PCF8591 sample at 30H; DS18B20 scratchpad at 30H–38H and status A5H success/E1H error at 3FH. Configure Keil memory allocation and calling conventions separately; these new HEX files were compiled and executed with SDCC, not validated with Keil.'),
  link('driver_sources','完整驱动与依赖说明 >','Full drivers and dependencies >')
 ];
 const led=find('led');
 led.title=['LED 面板','LED panel'];led.subtitle=['最多 3×3、逐灯选色与接线','Up to 3x3, individual colors and wires'];
 const first=led.blocks.find(b=>b.type==='text');first.text=[
  '空手右键打开设置，数量可选 1–9，灯珠排列在固定的 3×3 位置。点击预览中的灯号，填写该灯 GPIO 标号，选择 16 色预设或输入六位 RGB 十六进制（例如 FF4040）。点击应用保存；关闭或取消不保存。每颗灯高电平点亮、低电平熄灭。',
  'Right-click empty-handed to configure 1–9 lamps at stable 3x3 positions. Select a lamp, enter its GPIO label and choose a 16-color preset or six-digit RGB hex such as FF4040. Apply saves; cancel/close does not. Every lamp is active-high.'
 ];
 led.blocks.push(h('逐灯独立拉线','Wire each lamp independently'),
  p('默认第一颗名为 LED，其余依次 LED2–LED9。主板给每个标号绑定 GPIO，用线卷先点主板端子，再点具体灯珠；每颗灯是一根独立可见线缆，不是串行 RGB 灯带。9 灯可用 P0.0–P0.7 和 P1.0，主板端子可分到两个线组，避免一个端点超过 8 条线。芯片没有新增控制寄存器或地址。','Defaults are LED and LED2–LED9. Bind GPIO labels on the board, then use the coil on a board terminal and a particular lamp. Each has its own visible cable; this is not a serial RGB strip. Nine lamps may use P0.0–P0.7 and P1.0; split them across two groups to stay within eight cables per endpoint. No MCU registers or addresses were added.'),
  tip('灯珠编号和位置保持稳定。改名或改色保留原连线；减少数量只拆除被移除灯珠的线并回收线材。颜色、数量、名字保存到世界，客户端同步各灯状态；重载时不会把上次亮灯值当作新输入。旧单灯模块自动按默认绿色、LED 标号加载，原连线可继续使用。','Indices and positions remain stable. Rename/recolor preserves wires; shrinking removes and refunds only excess-lamp wires. Colors, names and count persist, with live states synchronized to clients. Reload clears stale input states. Legacy single lamps load with the default green color and LED label, keeping the original wire.')
 );
 for(const id of ['sensor','pcf8591','temperature','mpu6050','altimeter']){
  const target=entries.find(e=>e.id===id);if(!target)continue;
  const file={sensor:'i2c_sensor.hex',pcf8591:'pcf8591_mirror.hex',temperature:'ds18b20_read.hex',mpu6050:'mpu6050_oled.hex',altimeter:'altimeter_oled.hex'}[id];
  target.blocks.push(h('OLED 实时数据显示示例','Live OLED data example'),p(`选择 ${file}，安装 12 MHz 晶振，并连接 OLED 3CH（SDA=P1.6、SCL=P1.7）。程序会连续显示本模块读数，断线提示并自动恢复；完整接线、单位及源码依赖见「示例固件索引」。`,`Select ${file}, install a 12 MHz crystal and connect OLED 3CH on P1.6 SDA/P1.7 SCL. It continuously displays this module's readings with disconnect errors and recovery. See Example firmware index for wiring, units and source dependencies.`),link('examples','接线、单位与完整示例索引 >','Wiring, units and example index >'));
 }
 // Replace legacy example claims while leaving low-level protocol reference code intact.
 for(const e of entries)for(const b of e.blocks){
  if(b.type!=='text')continue;
  if(b.text[0].startsWith('i2c_sensor.hex 用'))b.text=['i2c_sensor.hex 现在同时读取四项环境数据并在 OLED 上显示；P1.2 仍提供非零红石指示。安装 12 MHz 晶振并将 OLED 与传感器接到 P1.6/P1.7。','i2c_sensor.hex now shows all four environmental readings on OLED, retaining the P1.2 nonzero indicator. Install 12 MHz and share P1.6/P1.7 with sensor and display.'];
  if(b.text[0].startsWith('安装 12 MHz 晶振，DQ=P1.2，烧录'))b.text=['安装 12 MHz，DQ=P1.2，并把 OLED 接到 P1.6/P1.7，运行 ds18b20_read.hex。每轮转换等待至少 800 ms，校验完整 Scratchpad 的 CRC 后显示带符号温度，持续重试采集。','Install 12 MHz, DQ=P1.2 and OLED on P1.6/P1.7, then run ds18b20_read.hex. Each pass waits at least 800 ms, validates scratchpad CRC, displays signed temperature and retries continuously.'];
  if(b.text[0].includes('新版这两个文件都已改为软件 I²C'))b.text=['旧 MOVX 桥接固件需要改用当前软件 I²C 示例。文字程序只保留 i2c_oled_hello.hex，oled_pattern.hex 用于条纹诊断；重复的 oled_hello.hex 已从列表移除，已有主板中保存的固件不会自动清除。','Replace legacy MOVX firmware with current software-I2C examples. Use i2c_oled_hello.hex for text and oled_pattern.hex for stripes. Duplicate oled_hello.hex was removed from the catalog; existing flashed boards keep their firmware.'];
 }
}
