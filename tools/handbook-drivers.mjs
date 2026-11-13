import fs from 'node:fs';
import {fileURLToPath} from 'node:url';

// Read the authoritative, independently compilable files verbatim.
const root=fileURLToPath(new URL('../examples/drivers/',import.meta.url));
export function addDriverAppendix({entry,h,p,tip,code,link,table,entries}) {
 const files=list=>list.flatMap(file=>[h(file,file),code(fs.readFileSync(root+file,'utf8').replace(/\r\n?/g,'\n'))]);
 const modules=[
  ['mpu6050','mpu6050','MPU6050','mpu6050.c、i2c_bus.c','mpu6050.c, i2c_bus.c','原始数据、量程与大端解析','Raw samples, ranges and big-endian conversion'],
  ['altimeter','altimeter','ALT-01','altimeter.c、i2c_bus.c','altimeter.c, i2c_bus.c','32 位高度、零点与小端解析','32-bit altitude, zero reference and little-endian conversion'],
  ['motor_driver','motor_interface','多通道电机接口','motor_driver.c、i2c_bus.c','motor_driver.c, i2c_bus.c','六路有符号 RPM、原子提交；默认示例仅读状态，不需要机械动力','Six signed RPM channels and atomic commit; read-only demo needs no Create'],
  ['pcf8591','pcf8591','PCF8591','pcf8591.c、i2c_bus.c','pcf8591.c, i2c_bus.c','丢弃旧转换、四通道读取与 DAC','Previous-conversion discard, four-channel reads and DAC'],
  ['environment','i2c_sensor','环境传感器','environment.c、i2c_bus.c','environment.c, i2c_bus.c','红石、天空光、方块光与日进度','Redstone, sky/block light and day percentage'],
  ['gpio_panel','button_module','按钮面板与 LED','gpio_panel.c','gpio_panel.c','九键低有效、按下沿检测与 LED 输出','Nine active-low keys, press edges and LED output'],
  ['gpio_redstone','redstone_port','GPIO 红石接口','gpio_redstone.c','gpio_redstone.c','数字读写，模拟强度请用 PCF8591','Digital I/O; use PCF8591 for analog strength'],
  ['seven_segment','seven_segment','八段数码管','seven_segment.c','seven_segment.c','A–G/DP、十六进制字形与共阳反相','A–G/DP, hexadecimal font and common-anode inversion'],
  ['ds1302','ds1302','DS1302','ds1302.c','ds1302.c','LSB 三线、BCD、闰年校验、12/24 小时读取与 RAM','LSB-first 3-wire, BCD, leap-year validation, 12/24h reads and RAM'],
  ['ds18b20','ds18b20','DS18B20','ds18b20.c、ed_delay.c','ds18b20.c, ed_delay.c','1-Wire、ROM 搜索、CRC、分辨率与有符号温度','1-Wire, ROM search, CRC, resolution and signed temperature'],
  ['ssd1306','oled','SSD1306 OLED','ssd1306.c、ed_font5x7.c、i2c_bus.c','ssd1306.c, ed_font5x7.c, i2c_bus.c','初始化、分页、清屏、列数据与 ASCII 文本','Initialization, pages, clear, column data and ASCII text']
 ];
 entry('driver_sources','appendix','manual',['附录：可复制的完整驱动','Appendix: complete copyable drivers'],['11 组驱动，逐文件复制 .h / .c / 最小主程序','11 driver groups; copy each .h, .c and minimal main'],[
  p('下面的代码来自随模组工程提供的 examples/drivers 原始文件，生成手册时逐字读取，复制按钮会复制该文件的完整内容。每个驱动独立提供头文件、实现文件和完整 main 示例；公共依赖只需复制一份。','Code is read verbatim from examples/drivers when the handbook is generated. Each Copy button copies the entire file. Each driver includes its header, implementation and complete main demo; copy shared dependencies once.'),
  tip('默认晶振为 12 MHz / 12T，特别是 DS18B20 的短时隙读采样。把开发板晶振改为 12 MHz，或先修改 ed_platform.h 的 ED_FOSC_HZ 和相关短延时。GPIO 线名与端口号必须匹配。','Defaults use 12 MHz / 12T, especially DS18B20 short-slot sampling. Install a 12 MHz crystal, or adjust ED_FOSC_HZ and short delays first. Configured GPIO labels and pin numbers must match.'),
  p('Keil C51：新建 SMALL 内存模型的 8051 工程，添加对应 .c 和 main 示例，把头文件目录加入 Include Path。SDCC：每次 -c 编译一个 .c 得到 .rel，再与 main.c 链接；不要把所有模块实现都加入同一个 8 KiB 工程。实际验证使用 SDCC，C51 声明分支未在 Keil 编译器中验证。','Keil C51: create an 8051 SMALL-model project, add the required .c files and one demo main, and add the header directory to Include Path. SDCC: compile each .c with -c into .rel, then link with main.c. Do not add every module to one 8 KiB project. Actual validation uses SDCC; the C51 declarations have not been verified with the Keil compiler.'),
  p('返回约定：I²C 和多数芯片函数返回 1 表示成功、0 表示 NACK/CRC/参数错误。错误时不要继续使用旧采样。1-Wire 和三线接口不是 I²C。驱动不占用额外的硬件映射地址，只使用普通程序存储器和 RAM。','I²C and most chip functions return 1 for success, 0 for NACK/CRC/invalid arguments. Do not use stale samples after failure. 1-Wire and 3-wire are separate protocols. Drivers use ordinary program memory/RAM, reserving no mapped peripheral window.'),
  link('source_common','先复制公共平台与软件 I²C >','Copy platform and software I2C first >'),
  ...modules.map(([id,icon,title])=>link('source_'+id,'完整驱动：'+title+' >','Complete driver: '+id+' >'))
 ]);
 entry('source_common','appendix','51systemboard',['源码：平台、延时与软件 I²C','Source: platform, delay and software I2C'],['公共头文件和完整传输实现','Shared header and complete transport'],[
  p('每个项目都需要 ed_platform.h。I²C 驱动还需 i2c_bus.h/.c；DS18B20 需 ed_delay.h/.c。ed_delay 占用 Timer0 并关闭 ET0，其他定时业务请用 Timer1/2。软件 I²C 实现检查 ACK，并在错误时发 STOP；它不实现硬件时钟拉伸。','Every project needs ed_platform.h. I²C drivers also need i2c_bus.h/.c; DS18B20 needs ed_delay.h/.c. ed_delay owns Timer0 and disables ET0; use Timer1/2 for other timing. Software I²C checks ACK and issues STOP on errors. Hardware clock stretching is not implemented.'),
  table([['信号','默认 GPIO'],['SDA / SCL','P1.6 / P1.7'],['LED / 红石 SIGNAL','P1.0 / P1.2'],['按钮 1–8 / 按钮9','P0.0–P0.7 / P2.0'],['DS1302 CE / SCLK / IO','P3.0 / P3.1 / P3.2'],['DS18B20 DQ','P3.7'],['数码管 A–G / DP','P2.0–P2.6 / P2.7']],
  [['Signal','Default GPIO'],['SDA / SCL','P1.6 / P1.7'],['LED / redstone SIGNAL','P1.0 / P1.2'],['Keys1–8 / key9','P0.0–P0.7 / P2.0'],['DS1302 CE / SCLK / IO','P3.0 / P3.1 / P3.2'],['DS18B20 DQ','P3.7'],['Seven-segment A–G / DP','P2.0–P2.6 / P2.7']]),
  tip('完整数码管占用整个 P2，因此与默认按钮9的 P2.0 冲突；RTC 默认 P3.0/P3.1 与 UART 冲突。单模块例子彼此独立，组合时先修改这个公共头文件，再在方块里配置同样的引脚。切勿机械地复制默认接线后把所有例子合并。','The display owns all of P2, conflicting with default key9 on P2.0. RTC P3.0/P3.1 conflict with UART. Demos are independent. For a combined project, edit this header and configure matching block pins first.'),
  ...files(['ed_platform.h','ed_delay.h','ed_delay.c','i2c_bus.h','i2c_bus.c'])
 ]);
 for(const [id,icon,title,zhDeps,enDeps,zhPurpose,enPurpose] of modules){
  entry('source_'+id,'appendix',icon,['源码：'+title,'Source: '+id],[zhPurpose,enPurpose],[
   p('用途：'+zhPurpose+'。项目加入 '+zhDeps+'，再加入下面的 demos/'+id+'_demo.c 作为唯一 main。先从公共源码条目复制所需头文件。','Purpose: '+enPurpose+'. Add '+enDeps+' and demos/'+id+'_demo.c as the only main. Copy required shared headers from the common source entry.'),
   link('source_common','公共依赖与引脚配置 >','Shared dependencies and pin map >'),
   ...files([id+'.h',id+'.c',...(id==='ssd1306'?['ed_font5x7.h','ed_font5x7.c']:[]),'demos/'+id+'_demo.c']),
   p('示例中的 demo_ok=1 表示最近一次调用成功，demo_value 保存示例结果，demo_done=A5H 表示至少执行完一轮。它们是普通 RAM 变量，便于调试；不对应任何额外外设地址。源码可在此基础上修改为 OLED 显示或红石输出。','demo_ok=1 indicates success, demo_value stores the example result, and demo_done=A5H marks one completed pass. These are ordinary RAM variables for debugging, not mapped peripheral addresses. Extend the example with OLED or redstone output as needed.'),
   link('driver_sources','返回完整驱动目录 >','Back to driver index >')
  ]);
 }
 for(const e of entries.filter(e=>!e.id.startsWith('source_')&&e.id!=='driver_sources')) {
  const match=modules.find(m=>'embeddeddev:'+m[1]===e.icon);
  if(match)e.blocks.push(link('source_'+match[0],'附录：复制本模块完整 .h / .c >','Appendix: copy complete .h / .c >'));
 }
}
