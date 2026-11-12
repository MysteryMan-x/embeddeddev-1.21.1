/** Original board-expansion chapter; keep its addresses distinct from the PCF8591. */
export function addAnalogEntry({entry,h,p,tip,table,wire,code,link}) {
 const addresses=table(
  [['通道','ADC 只读地址','DAC 读写地址'],...Array.from({length:8},(_,i)=>[String(i),'F00'+i+'H','F01'+i+'H'])],
  [['Channel','ADC read address','DAC read/write'],...Array.from({length:8},(_,i)=>[String(i),'F00'+i+'H','F01'+i+'H'])]);
 addresses.id='analog_map';
 entry('analog_extension','develop','redstone_port',['主板 ADC/DAC 扩展','Board ADC/DAC expansion'],
  ['8 路红石输入 + 8 路红石输出','8 redstone inputs and 8 outputs'],[
  h('先分清芯片和开发板扩展','The MCU and its board expansion'),
  tip('STC89C52RC 本身没有 ADC，也没有 DAC。本模组在开发板外部提供虚拟红石采样/输出设备，通过 MOVX 访问 XDATA 窗口。它们不是 STC 的片内寄存器，也不是 PCF8591 芯片。','The STC89C52RC has no built-in ADC or DAC. This mod provides virtual redstone sampling/output devices accessed through MOVX in XDATA. They are not STC internal registers or the PCF8591 chip.'),
  p('每块主板独立拥有 ADC0–ADC7 与 DAC0–DAC7。通道和世界方向没有固定关系：在六面端子中选择方向、G1–G4 线组及通道，决定它接到哪里。ADC0 不是 P1.0，DAC1 也不是 P1.1；它们不占用 MCU 的 32 根 GPIO。','Each board owns ADC0–ADC7 and DAC0–DAC7. Choose a face, G1–G4 group and channel in the terminal editor; no channel has a fixed world direction. ADC0 is not P1.0 and DAC1 is not P1.1. These devices do not consume the 32 GPIO pins.'),
  h('全部通道地址','Every channel address'),addresses,
  p('公式：ADCn 地址为 F000H+n，DACn 地址为 F010H+n，n=0–7。它们是 16 位 XDATA 地址，必须用 MOVX，或 Keil C51 的 xdata 指针 / XBYTE。普通直接寻址 MOV 和普通 data 指针不会访问到这些设备。','ADCn is at F000H+n and DACn at F010H+n, for n=0–7. These are 16-bit XDATA addresses. Use MOVX, a C51 xdata pointer or XBYTE. Ordinary direct MOV or a data pointer does not reach these devices.'),
  h('数值与转换时机','Values and sampling'),
  table([['项目','实际行为'],['ADC 范围','0–15，读取结果的高四位为 0'],['DAC 范围','0–15，写 16–255 均限制为 15'],['换算示例','红石 7 → ADC 读 07H；DAC 写 0AH → 红石 10'],['分辨率','16 级红石强度，不是 0–5 V 或 0–255 电压'],['采样','每游戏 tick 更新，20 TPS 时约每 50 ms'],['控制寄存器','无：不需要启动位、忙位、完成位或 ADC 中断']],
   [['Item','Actual behavior'],['ADC range','0–15; upper four bits read zero'],['DAC range','0–15; byte writes 16–255 clamp to 15'],['Examples','Redstone 7 reads 07H; writing 0AH outputs 10'],['Resolution','16 redstone levels, not 0–5 V or 0–255 voltage'],['Sampling','Once per game tick; about 50 ms at 20 TPS'],['Control registers','None: no start, busy, done flag or ADC interrupt']]),
  p('选好模拟输入后，开发板自动采样，程序直接读取地址即可。向 ADC 地址写入不能改变采样结果。DAC 写入后保持该值，直到程序改写、复位或烧录。短于一个 tick 的输入脉冲可能漏采。','Once analog input is configured, sampling is automatic and firmware reads the address directly. ADC writes do not change its sampled value. A DAC holds its value until rewritten, reset or flashing. Input pulses shorter than a tick may be missed.'),
  h('实验：远程 ADC0 → DAC1','Experiment: remote ADC0 to DAC1'),
  p('材料：主板、两个红石接口模块、线卷、一个可调红石源和一个输出设备。下列东面 G2 输入、西面 G3 输出只是示例，可替换成其他方向和线组。','Bring a board, two redstone connectors, a coil, a variable redstone source and an output device. East G2 input and west G3 output below are examples; other faces and groups work too.'),
  table([['位置','标号 / 用途 / 通道'],['主板东面 G2','VIN / 模拟输入 ADC / ADC0'],['主板西面 G3','VOUT / 模拟输出 DAC / DAC1'],['输入红石接口','输入模式，红石侧朝向信号源'],['输出红石接口','输出模式，红石侧朝向目标设备']],
   [['Location','Label / kind / channel'],['Board east G2','VIN / Analog input ADC / ADC0'],['Board west G3','VOUT / Analog output DAC / DAC1'],['Input connector','Input mode; redstone side faces the source'],['Output connector','Output mode; redstone side faces the target']]),
  p('1. 主板右键 → 六面端子 → 东面 → 管理引脚 → 选 G2 → 添加引脚。名字填 VIN，用途选模拟输入 ADC，选 ADC0，保存并应用。西面 G3 同样添加 VOUT，选模拟输出 DAC 和 DAC1。','1. Open the board, Six-face terminals, east, Manage pins, G2, Add pin. Name it VIN, select Analog input ADC and ADC0, save and Apply. On west G3 add VOUT with Analog output DAC and DAC1.'),
  p('2. 信号源旁放一个红石接口，设置输入，红石侧朝向信号源。目标设备旁再放一个接口，设置输出，红石侧朝向设备。这里输入/输出都相对主板而言，最后点应用。','2. Place a connector beside the source, set Input and face its redstone side toward the source. Place another beside the target, set Output and face it toward the device. Direction is relative to the board. Apply each setting.'),
  wire('redstone_port','ADC0|SIGNAL'),wire('redstone_port','DAC1|SIGNAL'),
  p('3. 用线卷把东面 G2 接到输入接口，西面 G3 接到输出接口。接口只有一个 SIGNAL 端子，单通道会自动配对，VIN/VOUT 是便于识别的名字。先应用主板引脚再接线，确认接线一览中两个接口均已连接。','3. Wire east G2 to the input connector and west G3 to the output connector. A connector has one SIGNAL terminal, so a single channel pairs automatically. VIN/VOUT are descriptive labels. Apply pins before wiring and confirm both routes are connected.'),
  h('4 · 完整 Keil C51 程序','4 · Complete Keil C51 program'),
  code('#include <REG52.H>\n#include <absacc.h>\n\n#define ADC0 XBYTE[0xF000]\n#define DAC1 XBYTE[0xF011]\n\nvoid main(void) {\n    DAC1 = 0;\n    while (1) {\n        DAC1 = ADC0;\n    }\n}'),
  p('也可以烧录 adc_mirror.hex，使用默认晶振即可。它读取 ADC0 并写入 DAC1。输入调为 0、7、15，输出也应分别为 0、7、15。没有输出时先点运行，再检查接口模式、红石侧和线组。','Alternatively run adc_mirror.hex with the default crystal. It reads ADC0 and writes DAC1. Inputs 0, 7 and 15 should produce outputs 0, 7 and 15. If output is absent, check Run, connector modes, redstone sides and groups.'),
  h('汇编访问方法','Assembly access'),
  code('LOOP:\nMOV DPTR,#0F000H ; ADC0 address\nMOVX A,@DPTR     ; A = 0..15\nMOV DPTR,#0F011H ; DAC1 address\nMOVX @DPTR,A     ; same output level\nSJMP LOOP'),
  h('相邻连接与多路规则','Adjacent and multi-channel rules'),
  p('不拉线时，可在主板某方向 G1 绑定 ADC，从该面相邻红石取样；G1 绑定 DAC 时向该面输出。G2–G4 需要用线缆接到接口，不能直接当作该面的本地红石。','Without cables, G1 ADC samples adjacent redstone on its face and G1 DAC drives that face. G2–G4 require cabled connectors; they do not directly sample or emit local redstone on that face.'),
  p('多个输入绑同一 ADC 通道时取最大强度，不是求和或平均。同一 DAC 通道可驱动多个输出，数值相同。若一个本地 G1 面绑定多个 DAC，该面取这些通道的最大值。各通道仍有独立地址。','Inputs sharing an ADC combine by maximum, not sum or average. Outputs sharing a DAC mirror its value. A local G1 face with multiple DACs emits their maximum. Channels still have independent addresses.'),
  h('停止、复位与保存','Stop, reset and saving'),
  table([['操作 / 状态','结果'],['停止或单步暂停','DAC 值可保留，但红石输出为 0；运行后重新驱动'],['复位或成功烧录','DAC 清零；接线配置保留'],['保存与加载','DAC 数值及接线随主板保存'],['未绑定 ADC','采样刷新后读 0'],['区块卸载或线路离线','不跨未加载区块传输']],
   [['Action / state','Result'],['Stopped or stepping','DAC can retain its value; redstone is 0 until Run'],['Reset or successful flash','DAC clears; wiring remains'],['Save and reload','DAC values and wiring persist'],['Unassigned ADC','Reads 0 after sampling refresh'],['Unloaded or offline','No signal through unloaded chunks']]),
  tip('这些设备窗口覆盖普通 XDATA。不要把普通 xdata 变量、数组或启动清零区放到 F000H–F017H，也不要套用其他 STC 型号的 ADCCON/ADCDATA 初始化。没有固定 ADC 引脚，也无需 SFR 使能。','These windows override ordinary XDATA. Keep normal variables, arrays and startup clearing away from F000H–F017H. Do not use other STC models ADCCON/ADCDATA initialization. No fixed ADC pin or SFR enable is required.'),
  link('adc','需要独立 I²C 芯片？看 PCF8591 >','Need a separate I²C chip? See PCF8591 >'),link('redstone','红石接口模式和方向 >','Connector modes and directions >')],[]);
}
