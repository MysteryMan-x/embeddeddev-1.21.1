import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

// Original handbook prose and diagrams. Text is paired Chinese/English; IDs stay identical.
const entries=[];
const p=(zh,en)=>({type:'text',text:[zh,en]});
const h=(zh,en)=>({type:'heading',text:[zh,en]});
const tip=(zh,en)=>({type:'tip',text:[zh,en]});
const code=text=>({type:'code',text:[text,text]});
const recipe=id=>({type:'recipe',id:'embeddeddev:'+id});
const wire=(id,...labels)=>({type:'wiring',id:'embeddeddev:'+id,labels});
const table=(zh,en)=>({type:'table',rows:[zh,en]});
const link=(target,zh,en)=>({type:'link',target,text:[zh,en]});
function entry(id,category,icon,title,subtitle,blocks,items){entries.push({id,category,icon:'embeddeddev:'+icon,title,subtitle,blocks,items});}

entry('welcome','start','manual',['打开你的电子工作台','Your electronics workbench'],['从第一盏 LED 到一条软件总线','From one LED to a software bus'],[
 h('欢迎来到方块里的 51 实验室','Welcome to the 8052 laboratory'),
 p('这本手册是一条可以照着完成的实验路线：制造材料与开发板，给 GPIO 起名字，接上一盏 LED，烧录程序，再把按钮、显示器和传感器接进同一套电路。所有模块都在「模块」分类中有独立条目。','Follow a practical route: make materials and a board, name GPIO pins, wire an LED, flash a program, then add buttons, displays and sensors. Every module has its own entry in Modules.'),
 tip('第一次使用？先看「第一盏 LED」。成功后再做「按钮控制 LED」，最后尝试 OLED 或温度探头。每次只增加一个模块，问题更容易定位。','New here? Start with First LED, then Button controls LED. Try the OLED or thermometer afterwards. Add one device at a time so faults are easy to isolate.'),
 h('这本书怎么用','How to use this book'),
 p('上方四个分类切换目录；左侧搜索会查找整本书的标题、正文、端子和地址。滚轮在目录上滚动条目，在正文上翻页；左右方向键也能翻页。Ctrl+F 聚焦搜索，Home 回到这里。','Use the four tabs for categories. Search covers titles, prose, terminals and addresses throughout the book. Scroll over the index to move entries, or over the page to turn pages. Arrow keys turn pages, Ctrl+F focuses search, and Home returns here.'),
 p('点右上角的星标按钮收藏当前条目，点左下「书签」筛选收藏。配方直接读取当前世界的配方数据，材料会显示物品提示；有对应条目的材料可以点击跳转。代码框右上角可以复制完整代码。','Star the current entry and use Bookmarks to filter favorites. Recipe cards use the current world recipes. Hover items for their tooltips and click documented items to open their entries. The Copy button copies the entire code block.'),
 h('先准备一本手册','Make a handbook'),recipe('manual'),
 link('first_led','开始实验：第一盏 LED >','Start the first LED experiment >'),link('survival','生存路线：从沙子到开发板 >','Survival route: sand to circuit board >')]);

entry('survival','start','silicon_wafer',['从原料到芯片','Materials to microchips'],['矿物、硅片与覆铜板','Ore, wafers and circuit boards'],[
 h('两条获得粗硅料的路线','Two sources of raw silicon'),
 p('在主世界新生成的区块里寻找硅石矿，分布高度 Y=-56 到 40。石镐或更好的镐可以采出粗硅料；时运增加掉落，精准采集保留矿石。已有区块需要前往新生成区域。','Find silicon ore in newly generated Overworld chunks at Y=-56 to 40. Use a stone pickaxe or better. Fortune increases raw drops and Silk Touch preserves the ore. Existing chunks need newly explored terrain.'),
 p('不想先去挖矿，可以把木炭放在工作台中心，上下左右各放沙子，合成粗硅料。粗硅料、普通硅石矿和深层硅石矿都可以烧成硅片。','For an alternative, surround charcoal with four sand blocks. Raw silicon and both ore variants can be refined into wafers.'),recipe('raw_silicon'),recipe('silicon_wafer_from_smelting'),recipe('silicon_wafer_from_blasting'),
 h('制作电路板与 MCU','Make the PCB and MCU'),
 p('铜锭、黏土球与红石无序合成两块覆铜电路板。硅片是芯片和晶振的基础材料。下面的配方会生成 STC89C52RC，再与晶振和电路板组装成可运行的最小系统。','Copper, clay and redstone produce two circuit boards. Wafers are the basis of chips and oscillators. Package an STC89C52RC, then combine it with a crystal and PCB to make the minimum system.'),recipe('circuit_board'),recipe('stc89c52rc'),
 tip('拾取关键材料会解锁原版配方书中的对应配方。这本手册的配方图也会反映数据包对配方的修改。','Picking up key materials unlocks vanilla recipe-book entries. Handbook cards also reflect recipe changes made by data packs.'),link('board','下一步：组装最小系统板 >','Next: assemble the system board >')],['embeddeddev:raw_silicon','embeddeddev:silicon_wafer','embeddeddev:circuit_board','embeddeddev:silicon_ore','embeddeddev:deepslate_silicon_ore','embeddeddev:stc89c52rc']);

entry('first_led','start','led_module',['第一盏 LED','First LED'],['一块主板、一根线、一个输出','One board, one cable, one output'],[
 h('目标与材料','Goal and materials'),
 p('目标：用自己的 51 程序点亮 LED。准备最小系统板、LED 模块、绝缘线卷和一个 HEX 文件。新主板已装好 11.0592 MHz 晶振，六面端子初始没有绑定。','Goal: light an LED using your own 8052 program. Bring a system board, LED module, wire coil and HEX file. A new board includes an 11.0592 MHz crystal; all face terminals start unassigned.'),
 h('1 · 给端子一个名字','1 · Name a terminal'),
 p('放下主板，空手右键，打开「六面端子」。选一个容易够到的方向，管理 G1，引脚标号填 LED，用途选 GPIO，物理引脚选 P1.0。保存后点击「应用」。','Place the board, right-click it and open Six-face terminals. Choose an accessible face, edit G1, add label LED with kind GPIO and physical pin P1.0. Save and Apply.'),wire('led_module','P1.0|LED'),
 h('2 · 把模块接上','2 · Connect the lamp'),
 p('在旁边放 LED 模块。手持线卷，先点主板刚配置的线组，再点 LED。只有一个端子时会自动匹配。接线完成后把线卷收起来，线仍然可见。','Place the LED nearby. Use the coil on the configured board group, then on the LED. A single terminal matches automatically. The finished cable stays visible after putting the coil away.'),
 h('3 · 烧录一个最小程序','3 · Flash a minimal program'),
 p('在 Keil C51 中编译下面的程序并生成 HEX。把 HEX 路径填进开发板「烧录与调试」页，烧录后点击运行。也可以把 HEX 文件拖入该界面。','Compile this program in Keil C51 with HEX output enabled. Enter the HEX path on Programming and debugging, or drop the HEX file there. Flash it, then press Run.'),
 code('#include <REG52.H>\nsbit LED = P1^0;\nvoid main(void) {\n    LED = 1;\n    while (1) { }\n}'),
 tip('GPIO 1 点亮 LED，0 熄灭。点击「停止」会暂停 CPU；LED 保持端口电平。开发板与红石接口的红石输出在停止时会归零。','GPIO 1 lights the LED and 0 turns it off. Stop pauses the CPU and the lamp keeps the port level. Board and connector redstone outputs return to zero when stopped.'),
 link('button','下一个实验：按钮控制 LED >','Next experiment: button controls LED >'),link('troubleshooting','没有点亮？按现象排查 >','Lamp still dark? Troubleshoot it >')]);

entry('flashing','start','51systemboard',['烧录与首次运行','Flashing and first run'],['HEX 是固件，C 文件是源代码','HEX is firmware; C is source'],[
 h('在 Keil 准备固件','Prepare firmware in Keil'),
 p('新建 8051/C51 工程，选择兼容 STC89C52 的目标，使用 SMALL 内存模型和 12T 时序。在 Output 里勾选 Create HEX File，编译后选择生成的 .hex。本模组读取 Intel HEX，不能直接烧录 .c、.obj 或 .a51 源文件。','Create an 8051/C51 project for a compatible STC89C52 target. Start with the SMALL memory model and 12T timing. Enable Create HEX File under Output, then build and select the generated .hex. C, OBJ and A51 files are not flashable HEX images.'),
 h('烧录面板上的顺序','Panel workflow'),
 table([['步骤','作用'],['烧录 HEX','校验文件，然后替换程序并复位 CPU'],['运行','从当前 PC 继续执行'],['停止','暂停执行，保留 CPU 和 RAM'],['复位','回到程序开头，清空 CPU RAM'],['单步','执行一条指令并刷新状态']],[['Step','Effect'],['Flash HEX','Validate, replace firmware and reset CPU'],['Run','Continue at the current PC'],['Stop','Pause; keep CPU and RAM'],['Reset','Restart and clear CPU RAM'],['Step','Execute one instruction and refresh']]),
 p('路径是运行客户端的那台电脑上的文件路径。相对路径从游戏实例目录解析。烧录时只把固件内容发给服务器，服务器不会按你填写的路径去读文件。','Paths refer to files on the client computer. Relative paths start at the game instance directory. Flashing sends firmware bytes to the server; the server does not open that local path.'),
 tip('每次换线、换晶振或换驱动后，先停止再检查配置。烧录和复位保留接线；独立 OLED 的显存也不会被 CPU 复位清空。','Stop and check configuration after changing wiring, the crystal or a driver. Flashing and reset keep wiring; CPU reset also preserves the independent OLED framebuffer.'),
 h('HEX 校验失败时','If HEX validation fails'),
 p('检查是否完整复制了文件、是否有 EOF 结束记录、校验和是否正确，以及代码是否超出 0000H–1FFFH 的 8 KiB 程序区。损坏或越界的 HEX 不会覆盖原固件。文本文件上限为 128 KiB。','Check that the file is complete, has an EOF record and valid checksums, and stays within the 8 KiB CODE region 0000H–1FFFH. Invalid or out-of-range HEX does not replace existing firmware. The text payload limit is 128 KiB.'),link('examples','先用已附带的 HEX 验证接线 >','Check wiring with bundled HEX examples >')]);

entry('board','develop','51systemboard',['最小系统板','Minimum system board'],['程序、接线与时钟的中心','Firmware, wiring and clock'],[
 recipe('51systemboard'),
 p('STC89C52RC、晶振、铜锭和覆铜电路板组成最小系统。新板预装 11.0592 MHz 晶振；每块主板有自己的程序、CPU、RAM、GPIO 和线组配置，互不共享内存。','The STC89C52RC, crystal, copper and PCB form a minimum system. New boards include an 11.0592 MHz oscillator. Each board owns independent firmware, CPU, RAM, GPIO and terminal groups.'),
 h('三个常用页面','Three working pages'),
 table([['页面','用来做什么'],['烧录与调试','导入 HEX、运行、停止、复位和单步'],['六面端子','配置世界方向、G1–G4、标号及物理引脚'],['接线一览','查看远端设备、引脚、离线和地址冲突']],[['Page','Purpose'],['Programming','Flash, run, stop, reset and step'],['Terminals','World faces, G1–G4, labels and physical pins'],['Wiring status','Remote devices, pins, offline paths and conflicts']]),
 p('主板右上角的晶振槽连接真实背包。换晶振或取下晶振会暂停 CPU，装回后点运行。主板的接线使用世界东南西北上下，不会跟着你转身；拿罗盘确认方向。','The top-right crystal socket uses inventory items. Removing or replacing the oscillator pauses the CPU; Run resumes it after installation. Board faces use world directions, independent of your viewpoint. Check them with the compass.'),
 h('存档与掉落','Saving and breaking'),
 p('CPU 状态、RAM、固件、DAC 和接线随主板存档。区块卸载时 CPU 暂停，不补跑离线时间。破坏主板会回收相关线缆，但掉落的主板不保留程序和 RAM；晶振随主板物品保存。','CPU state, RAM, firmware, DAC and wiring persist with the board. Unloaded chunks pause the CPU without offline catch-up. Breaking a board refunds its cables, but the dropped board loses firmware and RAM; its crystal is retained.'),
 tip('比较器读取主板的运行状态：正在运行为 15，停止为 0。可以把它用于程序运行指示，而不额外占用一个 GPIO。','A comparator reads the running state: 15 while running and 0 while stopped. Use it as a run indicator without consuming a GPIO.'),link('pins','配置六面线组 >','Configure six-face terminal groups >'),link('memory','芯片存储器分区 >','MCU memory regions >')]);

entry('pins','develop','51systemboard',['六面端子与标号','Faces and pin labels'],['先定义接口，再连接模块','Define the interface before wiring'],[
 p('每个世界方向都有 G1–G4 四组独立端子。每组可以改名，并添加多个引脚。标号是模块理解的接口名，例如 SDA、SCL、DQ、LED；物理引脚则是程序使用的 P0.0–P3.7。','Every world face has four independent groups, G1–G4. Name each group and add multiple pins. A label such as SDA, SCL, DQ or LED identifies the module interface. A physical pin such as P1.6 identifies the MCU port bit used by your program.'),
 table([['用途','物理通道','适合连接'],['GPIO','P0.0–P3.7','按钮、LED、I²C、1-Wire、数码管'],['ADC','ADC0–ADC7','输入红石强度 0–15'],['DAC','DAC0–DAC7','输出红石强度 0–15']],[['Kind','Channel','Use'],['GPIO','P0.0–P3.7','Buttons, LEDs, buses and displays'],['ADC','ADC0–ADC7','Read redstone strength 0–15'],['DAC','DAC0–DAC7','Write redstone strength 0–15']]),
 h('添加与应用','Adding and applying'),
 p('选方向后打开管理引脚，选择线组并填写组名。点添加引脚，填写标号、用途和物理通道。组内标号不区分大小写且不能重复；同一物理通道在同组也只能使用一次。最后应用保存这一面的全部四组。','Open pin management for a face, choose a group and set its name. Add a label, kind and physical channel for each pin. Labels are case-insensitive and unique within a group, as are physical channels. Apply saves all four groups on that face.'),
 wire('oled','P1.6|SDA','P1.7|SCL'),
 p('同一面可以让 G1 接 OLED，G2 接另一个 I²C 总线，G3 接温度探头，G4 接 LED。不同线组可以使用相同标号，但应按电路意图选择独立 GPIO。只改标号会保留物理连线；删除或更换已接物理引脚后需要重新配对。','One face can use G1 for an OLED, G2 for another I²C bus, G3 for a thermometer and G4 for an LED. Groups may reuse labels, but choose independent GPIO where separate buses are intended. Renaming keeps physical wiring; removing or changing connected physical pins requires pairing again.'),
 tip('GPIO 同时负责输入与输出，不需要切换数字输入/输出模式。读取外部输入前，先把对应端口锁存位置 1，释放引脚。清空线组会拆除并回收该组的线。','GPIO handles both input and output without a direction selector. Before reading an external input, set its latch bit to 1 to release it. Clearing a group removes and refunds that group cables.'),link('cables','把接口连出去 >','Connect the interface with a cable >')]);

entry('cables','develop','wire_coil',['自由连线与接线柱','Cables and junctions'],['两点连线，按标号配对','Click two endpoints; match labels'],[
 recipe('wire_coil'),recipe('wire_cutter'),
 h('连接两个位置','Connect two positions'),
 p('线卷先点主板端子，再点模块。瞄准该面对应的 G1–G4 接线点，准星提示会显示组名和引脚。单引脚直接匹配，多引脚按标号自动配对；不能唯一匹配时会打开配对窗口。','Use the coil on a board group, then a module. Aim at the correct G1–G4 socket; the HUD shows the group name and pins. Single terminals match directly. Multiple terminals match by label, or open a pairing screen if the match is ambiguous.'),
 p('一根线缆可以带多个信号，例如一根线同时承载 OLED 的 SDA 与 SCL，或数码管的八个段信号。按钮面板则每个按键是独立接线点，一个键一根线。','A cable can carry several signals: SDA and SCL for an OLED, or all eight display segments. Button panels instead expose an independent endpoint for every key, with one cable per key.'),
 h('距离、分支与路由','Distance, branches and routing'),
 p('每段线缆在同一维度内最多 32 格，耗材按长度向上取整。可以用接线柱转角、延长和分支；空接线柱会继承第一次接入的端子名称。路径需要避开实心障碍。','Each segment spans at most 32 blocks within one dimension. Material cost is rounded up by distance. Junction posts extend, turn and branch cables. An empty post inherits the first connected terminal labels. Route around solid obstacles.'),recipe('wire_relay'),
 tip('每个连通端子网络由一块主板的一个线组控制。不要把两块主板的输出直接并在一起。相同总线地址的外设可以放到不同 GPIO 线组上使用。','Each connected terminal network has one controlling board group. Do not join two board outputs directly. Devices sharing an address can be placed on separate GPIO buses.'),
 h('拆线与离线','Disconnecting and offline devices'),
 p('潜行使用线卷取消当前选点。手持线钳瞄准可见线段右键拆线，或拆除端点回收相关线缆。两端和沿途接线柱所在区块都加载时才传输信号；接线一览中的离线条目会保留供排查。','Sneak-use the coil to cancel a selection. Use cutters on a visible cable, or break an endpoint to refund attached cables. Both endpoints and intermediate posts must be loaded. Offline routes remain visible in the wiring status for diagnosis.'),link('relay','接线柱的分支规则 >','Junction branching rules >'),link('i2c','多个 I²C 设备共享一组线 >','Share an I²C bus between devices >')],['embeddeddev:wire_coil','embeddeddev:wire_cutter']);

entry('crystals','develop','crystal_11_0592mhz',['晶振与时序','Crystals and timing'],['软件延时必须匹配频率','Software delays depend on frequency'],[
 p('可以安装 6、11.0592、12、22.1184 和 24 MHz 晶振。开发板右上角打开晶振槽，从背包放入或取出晶振。操作会暂停执行，但保留 CPU 与 RAM；重新点运行继续。','Choose 6, 11.0592, 12, 22.1184 or 24 MHz. Open the top-right board socket and move crystals from inventory. Changes pause execution while retaining CPU and RAM; Run resumes it.'),
 table([['晶振','12T 机器周期'],['6 MHz','2.000 μs'],['11.0592 MHz','约 1.085 μs'],['12 MHz','1.000 μs'],['22.1184 MHz','约 0.543 μs'],['24 MHz','0.500 μs']],[['Crystal','12T machine cycle'],['6 MHz','2.000 μs'],['11.0592 MHz','About 1.085 μs'],['12 MHz','1.000 μs'],['22.1184 MHz','About 0.543 μs'],['24 MHz','0.500 μs']]),
 tip('DS18B20 随附 HEX 固定按 12 MHz/12T 编写；默认 11.0592 MHz 晶振不能直接替代。OLED 的 GPIO 边沿驱动不做严格总线速率检查，但你自己的延时仍会改变。','The bundled DS18B20 HEX specifically uses 12 MHz/12T. Do not substitute the default 11.0592 MHz crystal. OLED GPIO transfers do not strictly validate bus speed, though your software delays still change.'),
 recipe('crystal_6mhz'),recipe('crystal_11_0592mhz'),recipe('crystal_12mhz'),recipe('crystal_22_1184mhz'),recipe('crystal_24mhz'),
 p('默认每游戏 tick 执行的机器周期按晶振频率除以 240 计算，对应 20 TPS 下的 12T 时序。服务器负载降低 TPS 时，游戏内运行进度也会放慢；区块卸载后不会继续计时执行。','The default cycle budget is oscillator frequency divided by 240 per game tick, matching 12T at 20 TPS. Lower server TPS slows simulated progress. Unloaded chunks do not execute firmware.'),link('temperature','按正确晶振驱动温度探头 >','Drive the thermometer at the right frequency >')],['embeddeddev:crystal_6mhz','embeddeddev:crystal_11_0592mhz','embeddeddev:crystal_12mhz','embeddeddev:crystal_22_1184mhz','embeddeddev:crystal_24mhz']);

entry('keil','develop','stc89c52rc',['Keil 工程与 GPIO','Keil projects and GPIO'],['用你熟悉的 C51 或汇编','Use C51 or assembly'],[
 h('推荐起点','Recommended starting point'),
 p('使用 8051/C51 工具链，程序限制在 8 KiB，先用 SMALL 内存模型。REG52.H 能提供常见 SFR 和位定义。勾选 HEX 输出，再把文件烧进游戏里的开发板。C 源码在电脑上编译，游戏负责执行机器码。','Use an 8051/C51 toolchain with code limited to 8 KiB. Start with the SMALL model. REG52.H provides common SFR and bit definitions. Enable HEX output and flash it into the board. Your computer compiles C; the game executes machine code.'),
 h('位操作与整口操作','Bit and byte port access'),
 code('#include <REG52.H>\nsbit LED = P1^0;\nsbit KEY = P1^1;\nvoid main(void) {\n    KEY = 1;\n    while (1) LED = !KEY;\n}'),
 p('上面是低有效按键驱动高有效 LED。KEY 对应的 GPIO 要先写 1 以释放端口，然后读取；写 0 会持续把它拉低。P1=0x3F 则一次设置 P1 的八个位，适合直接驱动数码管。','This maps an active-low key to an active-high LED. Write 1 to KEY before reading to release its port. Writing 0 keeps it low. P1=0x3F writes all eight bits together and is useful for a segment display.'),
 h('访问扩展模拟量','Access virtual analog channels'),
 code('#include <absacc.h>\nunsigned char level;\nlevel = XBYTE[0xF000];\nXBYTE[0xF010] = level;'),
 p('F000H 是虚拟 ADC0，F010H 是虚拟 DAC0，范围都是 0–15。它们属于本模组的 XDATA 扩展，并非 STC89C52 内置 ADC/DAC。可以使用 MOVX 或 Keil 的 XBYTE 访问。','F000H is virtual ADC0 and F010H is virtual DAC0, both ranging from 0 to 15. These are mod-specific XDATA extensions, not ADC/DAC peripherals built into the STC89C52. Use MOVX or Keil XBYTE.'),
 tip('随附 HEX 是项目脚本生成的等价手写机器码。提供的 C 文件方便你在 Keil 修改；具体软件延时可能随编译优化而变化。','Bundled HEX examples are equivalent hand-assembled programs generated by project scripts. The C sources are provided for editing in Keil; software delay lengths may vary with compiler optimization.'),link('registers','常见寄存器速查 >','Common registers at a glance >')]);

entry('debug','develop','51systemboard',['烧录面板里的调试','Debugging the board'],['PC、机器码、反汇编与单步','PC, opcodes and stepping'],[
 p('打开烧录与调试页，可以查看当前 PC 对应的机器码和反汇编，并预览后续三条顺序指令。遇到跳转时，预览仍是地址顺序，实际下一条执行位置由 CPU 决定。','Programming and debugging shows the opcode and disassembly at the current PC, plus three sequential instructions. After a branch, the preview remains sequential; the CPU determines the actual next instruction.'),
 h('一个可复现的调试顺序','A repeatable debugging process'),
 p('先停止并记录 PC。检查当前晶振和接线，再复位。用单步观察端口写入是否到达你预期的引脚。最小输出测试通过后，再加入按钮、传感器和循环逻辑。','Stop and note the PC. Check the crystal and wiring, then reset. Step through the program and inspect whether port writes reach the intended pins. Verify a minimal output before adding buttons, sensors and control loops.'),
 table([['现象','先检查'],['PC 不动','是否点运行、是否安装晶振、CPU 是否故障'],['PC 在同处反复','是否是正常等待循环或按键未释放'],['输出与程序不符','物理引脚、标号、共阳/共阴与红石模式'],['修改 HEX 没变化','是否重新烧录并运行了正确文件']],[['Symptom','First check'],['PC does not move','Run state, installed crystal and CPU fault'],['PC loops in one place','Intentional waits or an unreleased input'],['Wrong output','Pin mapping, label, polarity and connector mode'],['HEX change has no effect','Flash and run the correct file again']]),
 p('虚拟 UART 可作为调试控制台：写 SBUF 会立即追加字符并置 TI；输入需要程序开启接收并处理 RI。控制台保留最近 512 个字符，不模拟真实电压和串口电缆。','The virtual UART serves as a console: writing SBUF immediately appends a character and sets TI. Input needs receiver enable and RI handling in firmware. The console keeps the last 512 characters and does not simulate physical serial wiring.'),
 tip('A5H 是保留操作码，执行它会停机并报告故障。HEX 落在擦除区或跑飞时，先检查程序边界与跳转目标，再复位后运行。','A5H is reserved and stops execution with a fault. If execution enters erased code or jumps unexpectedly, check code limits and branch targets before resetting.'),link('troubleshooting','完整故障排查表 >','Full troubleshooting checklist >')]);

entry('button','modules','button_module',['按钮面板','Button panel'],['一个方块，最多九个独立按键','Up to nine independent keys'],[
 recipe('button_module'),
 p('潜行空手右键，或右键没有按键的面板区域，打开设置。数量可以选 1–9，每个键都有自己的标号。默认第一个键叫 KEY，新增键叫 KEY2–KEY9，也可以改成 START、STOP 等。','Sneak-use with an empty hand, or use a blank area, to configure 1–9 keys. Every key has its own label. The first is KEY, followed by KEY2–KEY9; rename them START, STOP or other meaningful labels.'),
 p('主板端子里添加同名 GPIO，再用线卷依次点主板和具体按键。几个按键就接几根线，多个按键可以接到同一个已配置线组。改名保留物理连线；减少数量会回收被移除键的线。','Add matching GPIO labels on the board. Use the coil on the board group and each individual key. Every key needs its own cable, and several keys can share a configured group. Renaming keeps wiring; shrinking the panel refunds cables for removed keys.'),
 wire('button_module','P1.0|KEY'),wire('led_module','P1.1|LED'),
 h('按下为 0，松开为 1','Pressed is 0; released is 1'),
 p('空手点一个键，会把该键信号拉低 10 tick，随后自动释放。读取前把 MCU 对应端口锁存位置 1。下面的完整程序让 LED 随按键点亮；请按接线图分别把按钮和 LED 接到主板。','Using a key pulls its signal low for 10 ticks, then releases it automatically. Release the corresponding MCU latch first. This complete program lights an LED while the key is active; wire the key and lamp separately as shown.'),
 code('#include <REG52.H>\nsbit KEY = P1^0;\nsbit LED = P1^1;\nvoid main(void) {\n    KEY = 1;\n    while (1) LED = !KEY;\n}'),
 tip('这里的按键按游戏 tick 保持低电平，不模拟机械触点抖动。移除正在按下的键也会释放它，不会留下永久低电平。','Keys hold a logical low for game ticks; mechanical contact bounce is not simulated. Removing an active key releases it rather than leaving a permanently low input.')]);

entry('led','modules','led_module',['LED 指示灯','LED indicator'],['最适合验证 GPIO 的模块','The simplest GPIO test'],[
 recipe('led_module'),wire('led_module','P1.0|LED'),
 p('模块只需要一个名为 LED 的 GPIO 端子。连接后 GPIO 高电平点亮，低电平熄灭。适合做运行状态、阈值报警、按键反馈，或用单步验证程序是否写到了预期的端口位。','The module needs one GPIO terminal named LED. A high lights it and a low turns it off. Use it for state indicators, threshold alarms, key feedback or checking port writes while stepping.'),
 code('#include <REG52.H>\nsbit LED = P1^0;\nvoid main(void) {\n    LED = 1;\n    while (1) { }\n}'),
 p('可以与主板相邻并在接触面的 G1 绑定 LED，也可以用线卷远程连接任意线组。若改用 P2.3，必须同时更改接线配置和程序里的 sbit 定义。','Use adjacent G1 named LED, or wire any remote group. Changing to P2.3 requires updating both the board mapping and the sbit declaration.'),
 tip('LED 是高有效逻辑指示灯。想把 GPIO 变为驱动机器的红石信号，请使用红石接口模块。','The LED is an active-high logic indicator. Use a redstone connector to turn GPIO levels into signals for machines.'),link('redstone','把逻辑信号送给机器 >','Send a logic signal to machinery >')]);

entry('seven_segment','modules','seven_segment',['八段数码管','Eight-segment display'],['七段字形与独立小数点','Seven bars and a decimal point'],[
 recipe('seven_segment'),
 p('模块显示一个数字，由 A–G 七个发光段和 DP 小数点组成。模块内置公共供电与限流，只连接八根信号即可。一根包含八条信号的线缆能完成全部配对。','One digit consists of segments A–G and decimal point DP. Common power and current limiting are built in, so only eight signal terminals are needed. One multi-signal cable can pair all eight.'),
 wire('seven_segment','P1.0|A','P1.1|B','P1.2|C','P1.3|D','P1.4|E','P1.5|F','P1.6|G','P1.7|DP'),
 h('极性与段位','Polarity and bit order'),
 p('默认共阴，1 点亮；潜行空手右键切换共阳，0 点亮。bit0–bit7 依次对应 A B C D E F G DP。共阳模式应把整个段码字节取反。断线或引脚不全时，两种模式都会熄灭。','Default common cathode lights on 1. Sneak-use switches to common anode, which lights on 0. Bits 0–7 map to A B C D E F G DP. Invert the whole code byte for common anode. Missing pins or disconnected wiring blank both modes.'),
 table([['数字','共阴 HEX'],['0 / 1 / 2','3F / 06 / 5B'],['3 / 4 / 5','4F / 66 / 6D'],['6 / 7 / 8 / 9','7D / 07 / 7F / 6F'],['小数点','在字形上加 80H（按位或）']],[['Digit','Cathode HEX'],['0 / 1 / 2','3F / 06 / 5B'],['3 / 4 / 5','4F / 66 / 6D'],['6 / 7 / 8 / 9','7D / 07 / 7F / 6F'],['Decimal point','OR 80H into the glyph']]),
 code('#include <REG52.H>\nvoid main(void) {\n    P1 = 0x5B;  /* digit 2 */\n    while (1) { }\n}'),
 p('随附 seven_segment_counter.hex 在默认晶振下循环显示 0–9，奇数时点亮小数点，约每 0.55 秒切换。IDATA 30H 保存当前数字，31H 保存段码。模式随模块存档保存。','seven_segment_counter.hex counts 0–9 at the default crystal frequency, lighting DP on odd digits, about every 0.55 seconds. IDATA 30H stores the digit and 31H the code. Polarity persists with the module.'),link('examples','查找计数示例文件 >','Find the counter example >')]);

entry('oled','modules','oled',['SSD1306 OLED','SSD1306 OLED'],['0.96 寸，128×64 独立显存','0.96 inch, 128×64 framebuffer'],[
 recipe('oled'),wire('oled','P1.6|SDA','P1.7|SCL'),
 p('OLED 是独立方块，拥有 SSD1306 控制器和 1024 字节显存。七位 I²C 地址是 3CH，写地址字节是 78H。右键只打开屏幕放大窗口，不打开主板。世界中的显示和放大窗口使用同一幅像素数据。','The separate OLED block owns an SSD1306 controller and 1024-byte framebuffer. Its 7-bit I²C address is 3CH and write byte is 78H. Right-click opens a magnified display, independently of the board. Both views use the same pixels.'),
 h('先跑可读文字示例','Start with readable text'),
 p('按接线图配置 SDA=P1.6、SCL=P1.7，烧录 i2c_oled_hello.hex 并运行。应显示 EMBEDDED DEV、STC89C52、HELLO MINECRAFT、I2C 0X3C。oled_pattern.hex 只是条纹诊断，画面本来就不是文字。','Map SDA=P1.6 and SCL=P1.7, then run i2c_oled_hello.hex. Expect EMBEDDED DEV, STC89C52, HELLO MINECRAFT and I2C 0X3C. oled_pattern.hex intentionally displays diagnostic stripes rather than text.'),
 h('发送指令与显存','Commands and display bytes'),
 table([['内容','发送顺序'],['指令流','START → 78H → 00H → 指令 → STOP'],['像素流','START → 78H → 40H → 显存字节 → STOP'],['常用指令','AE 关屏 / AF 开屏 / A6 正常 / A7 反显'],['地址范围','21 设置列范围 / 22 设置页范围']],[['Transfer','Sequence'],['Commands','START, 78H, 00H, commands, STOP'],['Pixels','START, 78H, 40H, bytes, STOP'],['Common commands','AE off / AF on / A6 normal / A7 inverse'],['Address ranges','21 column range / 22 page range']]),
 p('显存按页排列：一个字节是同一列竖向的 8 个像素，bit0 对应该页最上面的点。像素 (x,y) 的偏移为 (y>>3)×128+x，位掩码为 1<<(y&7)。初始化寻址模式后再发送自己的字模。','RAM is page-organized: each byte holds eight vertical pixels of one column, with bit 0 at the page top. Pixel (x,y) uses offset (y>>3)*128+x and mask 1<<(y&7). Initialize addressing before sending glyph data.'),
 tip('OLED 保存自己的显存和显示设置。CPU 复位不会自动清屏。若画面残留，应由程序发送清屏数据；若为空白，先检查 AF 开屏、SDA/SCL 接线和地址。','The OLED saves its own RAM and display settings. CPU reset does not clear it. Clear residual graphics in firmware. For a blank display, first check AF display-on, SDA/SCL mapping and the address.'),
 p('可选 MOVX 桥接访问第一块已通过 SDA/SCL 接上的 OLED：F020H 控制显示，F100H–F4FFH 为显存。oled_hello.hex 使用这个接口。它是模组扩展，不是 SSD1306 的原生引脚接口。','An optional MOVX bridge reaches the first OLED connected via SDA/SCL: F020H controls display and F100H–F4FFH is RAM. oled_hello.hex uses it. This bridge is a mod extension rather than an SSD1306 physical interface.'),link('i2c','I²C 起停、ACK 与共享总线 >','I²C starts, ACKs and shared buses >')]);

entry('rtc','modules','ds1302',['DS1302 实时时钟','DS1302 clock'],['三线、BCD 日历与 31 字节 RAM','Three wires, BCD calendar and RAM'],[
 recipe('ds1302'),wire('ds1302','P1.0|CE','P1.1|SCLK','P1.2|IO'),
 p('DS1302 使用 CE、SCLK、IO 三线接口，不是 I²C。CE 拉高开始一次传输，拉低提交并结束。命令和数据低位先行。上升沿写入，读取时在下降沿更新输出；读取前释放 IO。','DS1302 uses CE, SCLK and IO, not I²C. Raise CE to begin and lower it to commit and finish. Commands and data are LSB first. Rising edges capture writes; falling edges update read output. Release IO before reading.'),
 table([['寄存器','写 / 读'],['秒（bit7=CH）','80H / 81H'],['分 / 时','82/83H / 84/85H'],['日 / 月','86/87H / 88/89H'],['星期 / 年','8A/8BH / 8C/8DH'],['写保护（bit7）','8EH / 8FH'],['时钟突发','BEH / BFH'],['RAM 突发','FEH / FFH']],[['Register','Write / read'],['Seconds (CH bit 7)','80H / 81H'],['Minute / hour','82/83H / 84/85H'],['Date / month','86/87H / 88/89H'],['Weekday / year','8A/8BH / 8C/8DH'],['Write protect (bit 7)','8EH / 8FH'],['Clock burst','BEH / BFH'],['RAM burst','FEH / FFH']]),
 h('初始化时钟','Initialize the clock'),
 p('先向 8EH 写 00H 关闭写保护，再写入 BCD 日历。秒寄存器 bit7 为 CH，置 1 停表，清 0 运行。BCD 的 23 是 23H，不是十进制 23 的 17H；转换时十位乘 16 后加个位。','Write 00H to 8EH to disable write protection, then write a BCD calendar. Seconds bit 7, CH, halts at 1 and runs at 0. BCD 23 is 23H, not decimal 23 encoded as 17H. Encode tens times 16 plus units.'),
 p('读秒时发送 81H，再释放 IO 并读取 8 位。使用突发读可以拿到同一次日历快照，避免跨秒时分别读出的日期不一致。31 字节 RAM 也可用于保存小型参数。','For seconds, send 81H, release IO and read eight bits. Burst reads capture a consistent calendar snapshot instead of mixing fields across a second boundary. The 31-byte RAM can hold small parameters.'),
 tip('时钟按游戏 tick 推进，不读取电脑的真实时间。时间、停表位、写保护和 RAM 随模块保存。主板卸载不会执行程序，时钟的进度受游戏世界时间影响。','The clock follows game ticks rather than the computer wall clock. Calendar, halt state, write protection and RAM persist with the module. Firmware stops while its board is unloaded; clock progress follows world time.')]);

entry('adc','modules','pcf8591',['PCF8591 ADC / DAC','PCF8591 ADC / DAC'],['四路模拟采样，一路模拟输出','Four inputs and one output'],[
 recipe('pcf8591'),wire('pcf8591','P1.6|SDA','P1.7|SCL'),
 p('PCF8591 默认七位地址 48H，写地址 90H，读地址 91H。潜行空手右键在 48H–4FH 间切换地址。AIN0–AIN3 分别从世界北、东、南、西面采样，AOUT 向上输出红石。','The default 7-bit address is 48H: write byte 90H and read byte 91H. Sneak-use cycles addresses 48H–4FH. AIN0–AIN3 sample world north, east, south and west; AOUT drives redstone upward.'),
 p('红石输入强度 0–15 映射为 0–255，即每级乘 17。DAC 把 0–255 换算为 0–15 红石输出。与主板自带的虚拟 ADC/DAC 通道不同，这个芯片通过真实的 GPIO I²C 事务控制。','Redstone input 0–15 maps to 0–255 by multiplying by 17. The DAC maps 0–255 back to 0–15 redstone. Unlike the virtual MCU ADC/DAC channels, this chip is controlled through GPIO I²C transactions.'),
 table([['控制字位','作用'],['bit6','允许 AOUT 输出'],['bit5:4','输入模式：单端或差分'],['bit2','读取后自动递增通道'],['bit1:0','起始输入通道']],[['Control bits','Meaning'],['6','Enable AOUT'],['5:4','Single-ended or differential mode'],['2','Increment channel after conversion'],['1:0','Starting input channel']]),
 h('读取 AIN2 的完整顺序','Read AIN2'),
 p('START，发送 90H 和控制字 02H；重复 START，发送 91H。先读并丢弃第一个字节，给 ACK；再读取第二个字节，它才是当前采样值，最后给 NACK 并 STOP。','START, send 90H and control 02H. Repeated START, send 91H. Read and discard the first byte with ACK. The second byte is the current sample; finish with NACK and STOP.'),
 tip('首个读数是上一次转换值，不能把它当成当前输入。向写地址发送控制字 40H 后再发送 DAC 字节，可以启用并设置模拟输出。','The first read byte is the previous conversion, not the current input. Sending control 40H followed by a DAC byte enables and sets analog output.'),
 p('环境传感器也默认使用 48H。两者挂同一条 SDA/SCL 时，把 PCF8591 改到 49H，或者给它们分配两组独立 GPIO 总线。','The environment sensor also defaults to 48H. On one SDA/SCL bus, move the converter to 49H, or use independent GPIO buses.'),link('i2c','地址冲突与重复 START >','Address conflicts and repeated START >')]);

entry('temperature','modules','ds18b20',['DS18B20 温度探头','DS18B20 thermometer'],['贴着设备，用 DQ 读取温度','Touch a machine and read DQ'],[
 recipe('ds18b20'),wire('ds18b20','P1.2|DQ'),
 p('把探头贴在被测方块表面，探头只测接触的那个方块。主板任意线组绑定 GPIO 标号 DQ，再用线卷连上。右键探头查看温度、来源，以及是否超出量程。','Place the probe against the target block; it samples only its contacted neighbor. Bind GPIO label DQ in any board group and wire it to the probe. Right-click for temperature, source and range information.'),
 table([['特性','数值'],['量程','-55～125 ℃'],['分辨率','9–12 位，最高 1/16 ℃'],['12 位转换','最长约 750 ms'],['上电温度','85 ℃，需启动一次转换'],['数据顺序','低位先行，暂存器含 CRC8']],[['Feature','Value'],['Range','-55 to 125 °C'],['Resolution','9–12 bits, up to 1/16 °C'],['12-bit conversion','Up to about 750 ms'],['Power-on result','85 °C until a conversion finishes'],['Transfer','LSB first; scratchpad includes CRC8']]),
 h('先使用随附示例','Use the supplied example first'),
 p('安装 12 MHz 晶振，DQ=P1.2，烧录 ds18b20_read.hex。它发出复位，Skip ROM、Convert T，等待约 800 ms，然后读取 9 字节 Scratchpad。完成标志为 3FH=A5H，数据在 IDATA 30H–38H。','Install a 12 MHz crystal, map DQ=P1.2 and flash ds18b20_read.hex. It resets, sends Skip ROM and Convert T, waits about 800 ms, then reads the nine-byte scratchpad. Completion sets 3FH=A5H; IDATA 30H–38H holds the data.'),
 p('把前两个字节按有符号 16 位小端数合并，再除以 16，得到摄氏度。负温度应保留符号，不能把高字节当成无符号数直接拼接。最后一个字节是 Dallas CRC8。','Combine the first two bytes as a signed little-endian 16-bit value and divide by 16 for Celsius. Preserve the sign for negative temperatures. The last byte is a Dallas CRC8.'),
 tip('读到 85 ℃通常表示尚未转换完成；始终 FFH 通常表示 DQ 未接通、没有释放或没有正确复位。先核对 12 MHz 晶振，再查 1-Wire 时序。','85 °C often means conversion has not completed. All FFH often means DQ is disconnected, not released, or not reset correctly. Check the 12 MHz crystal before investigating 1-Wire timing.'),link('onewire','复位、转换命令与 CRC >','Reset timing, commands and CRC >'),link('compatibility','机械动力和通用温度来源 >','Create and other temperature sources >')]);

entry('sensor','modules','i2c_sensor',['环境传感器','Environment sensor'],['红石、光照与日进度','Redstone, light and day progress'],[
 recipe('i2c_sensor'),wire('i2c_sensor','P1.6|SDA','P1.7|SCL'),
 p('七位 I²C 地址 48H，写地址 90H，读地址 91H。先写入寄存器编号，再以重复 START 切换到读操作；连续读取会递增寄存器号。右键模块可直接查看当前采样值。','The 7-bit address is 48H, with write 90H and read 91H. Write a register number, then switch to reading with a repeated START. Consecutive reads increment the register. Right-click to inspect current samples.'),
 table([['寄存器','读数与范围'],['00H','周围除主板外的最大红石强度，0–15'],['01H','模块上方天光，0–15'],['02H','模块上方方块光，0–15'],['03H','一天进度百分比，0–99'],['其他','FFH']],[['Register','Value and range'],['00H','Strongest nearby redstone except board, 0–15'],['01H','Sky light above the module, 0–15'],['02H','Block light above the module, 0–15'],['03H','Day progress percentage, 0–99'],['Other','FFH']]),
 p('i2c_sensor.hex 按图中 P1.6/P1.7 读取寄存器 00H，再写入主板虚拟 DAC0。另选一组绑定 DAC0 并连接红石输出接口，就能把模块周围的红石强度转发到远处。','i2c_sensor.hex uses P1.6/P1.7 to read register 00H and write virtual DAC0. Bind DAC0 in another group and connect a redstone output to relay that strength to a remote location.'),
 tip('它和 PCF8591 默认地址相同。在同一总线使用时，需要把 PCF8591 改到 49H；OLED 的 3CH 不与它冲突。','Its default address matches the PCF8591. Move the converter to 49H when sharing a bus. The OLED at 3CH does not conflict.')]);

entry('redstone','modules','redstone_port',['红石接口模块','Redstone connector'],['让 MCU 接入红石与机器','Connect MCU logic to machinery'],[
 recipe('redstone_port'),
 p('把红石接口放在需要读取或控制的设备旁，用线卷接到主板。空手右键设置输入或输出，并选择朝向设备的红石侧。这里的输入/输出是相对主板而言：机器信号进 MCU 是输入，MCU 控制机器是输出。','Place a connector beside a device and wire it to the board. Right-click to select input or output and the redstone-facing side. Direction is relative to the MCU: machine signals enter as input; MCU control signals leave as output.'),
 table([['模式','主板引脚','范围'],['数字输入','GPIO','读取低/高电平'],['数字输出','GPIO','0 或 15 红石'],['模拟输入','ADC0–7','红石 0–15'],['模拟输出','DAC0–7','红石 0–15']],[['Mode','Board pin','Range'],['Digital input','GPIO','Read low/high'],['Digital output','GPIO','Redstone 0 or 15'],['Analog input','ADC0–7','Redstone 0–15'],['Analog output','DAC0–7','Redstone 0–15']]),
 h('一个模拟量转发实验','Analog relay experiment'),
 p('输入接口选择输入与取样侧，接主板 ADC0；输出接口选择输出与驱动侧，接主板 DAC1。烧录 adc_mirror.hex。它把 F000H 读到的强度写到 F011H，实现从 ADC0 到 DAC1 的转发。','Configure one input connector and wire it to ADC0. Configure an output connector and wire it to DAC1. Run adc_mirror.hex to copy F000H input strength into F011H, relaying ADC0 to DAC1.'),
 code('#include <REG52.H>\n#include <absacc.h>\nvoid main(void) {\n    while (1)\n        XBYTE[0xF011] = XBYTE[0xF000];\n}'),
 p('可以控制机械动力中接受红石的离合器、红石链路，以及其他科技模组的红石控制口。机器实际如何响应由目标模组决定。小于一个游戏 tick 的脉冲可能不会在红石世界中显现。','Use it for Create clutches, redstone links, and other mods accepting redstone control. The target mod determines machine behavior. Pulses shorter than one game tick may not appear in the redstone world.'),
 tip('停止主板或断线会把红石输出归零。接口紧贴旧主板时，普通右键可能打开主板；潜行右键可以打开接口自身的端子设置。','Stopping the board or cutting a cable clears redstone output. Beside a legacy adjacent board, normal use may open the board; sneak-use opens connector settings.')]);

entry('relay','modules','wire_relay',['接线柱','Junction post'],['转角、延长和总线分支','Corners, extensions and branches'],[
 recipe('wire_relay'),
 p('先把主板线组连到空接线柱，接线柱便会继承这组端子的名称。随后从柱子再拉线到模块，或连接下一根柱子。分支只传递已经映射的信号，不会自动创造新的 MCU 引脚。','First connect a board group to an empty post so it inherits those labels. Wire the post to modules or further posts. Branches carry the mapped signals; they do not create extra MCU pins.'),
 h('适合的电路','Useful circuits'),
 p('一组 SDA/SCL 可以通过接线柱分接 OLED、PCF8591 和环境传感器，但每个设备的七位地址要唯一。多个 LED 也可以分接到同一 GPIO，它们会跟随同一电平。','A shared SDA/SCL pair can branch to an OLED, PCF8591 and environment sensor if addresses are unique. Several LEDs can branch from one GPIO and follow the same level.'),
 tip('同一连通网络只能有一个主板线组作为控制端。柱子需要加载才传输信号；切断主干后，下游模块会离线。每根柱子最多连接八段线，每个网络最多 64 个端点。','A connected network has one controlling board group. Posts must be loaded to pass signals. Cutting the trunk disconnects downstream devices. Each post accepts up to eight segments and a network up to 64 endpoints.'),link('cables','查看线卷与线钳操作 >','Coil and cutter operations >')]);

entry('compass','modules','compass',['罗盘与世界方向','Compass and world faces'],['先分清方向，再给端子起名','Orient yourself before wiring'],[
 recipe('compass'),
 p('罗盘指向世界北方，右键在操作栏显示当前朝向。主板的六面接线页也显示玩家方向。站在不同位置看到的左和右会改变，配置中的东南西北上下不会改变。','The compass points to world north. Use it to show your current heading in the action bar. Board terminals also show player direction. Your visual left and right change with position; configured world faces do not.'),
 p('做相邻连接时，要在主板面向模块的那个方向配置 G1。例如模块放在主板东边，就编辑东面；想在远程连接中选择同面的不同线组，需要瞄准各组接线点并查看准星提示。','For adjacent connections, configure G1 on the board face touching the module. A module east of the board uses its east face. For remote groups on one face, aim at each group socket and check the HUD.'),
 tip('PCF8591 的 AIN0–AIN3 永远按世界北、东、南、西取样。红石接口的驱动侧则可在自身设置里选择，别把这两个规则混淆。','PCF8591 inputs always follow world north, east, south and west. A redstone connector instead lets you choose its own device-facing side.')]);

entry('memory','reference','stc89c52rc',['内存与 Flash 分区','RAM and Flash map'],['256 字节 IDATA，不含独立 SFR','256-byte IDATA plus separate SFRs'],[
 table([['空间','范围 / 容量','用途'],['CODE','0000H–1FFFH / 8 KiB','程序与 code 常量'],['IDATA','00H–FFH / 256 B','内部 RAM、变量、栈'],['SFR','80H–FFH / 独立空间','端口和控制寄存器'],['XDATA','0000H–FFFFH / 64 KiB','外部数据与虚拟设备窗口']],[['Space','Range / size','Use'],['CODE','0000H–1FFFH / 8 KiB','Code and code constants'],['IDATA','00H–FFH / 256 B','Internal RAM, variables, stack'],['SFR','80H–FFH / separate','Ports and control registers'],['XDATA','0000H–FFFFH / 64 KiB','External data and virtual devices']]),
 p('是的，256 字节指完整内部 RAM，也就是可以间接寻址的 IDATA。低 128 字节可直接或间接访问；高 128 字节只能间接访问。直接地址 80H–FFH 访问的是 SFR，不能用直接 MOV 把它当作高半区 RAM。','The 256 bytes are the full indirect-addressable internal RAM, IDATA. The low half supports direct and indirect access; the upper half requires indirect addressing. Direct addresses 80H–FFH access SFRs, not the upper RAM bank.'),
 table([['IDATA 区域','常见作用'],['00H–1FH','四组 R0–R7'],['20H–2FH','位寻址 RAM'],['30H–7FH','通用变量区'],['80H–FFH','上半区 RAM，间接寻址']],[['IDATA area','Typical use'],['00H–1FH','Four R0–R7 register banks'],['20H–2FH','Bit-addressable RAM'],['30H–7FH','General variables'],['80H–FFH','Upper RAM, indirect access']]),
 code('MOV 80H,#00H ; writes P0 SFR\nMOV R0,#80H\nMOV @R0,#55H ; writes upper IDATA'),
 tip('SP 复位为 07H，栈向高地址增长。复杂 C 工程要留意寄存器组、变量和栈的重叠。代码超过 8 KiB 会被 HEX 校验拒绝。','SP resets to 07H and the stack grows upward. Check register-bank, variable and stack overlap in larger C projects. HEX validation rejects code exceeding 8 KiB.'),link('xdata','虚拟 ADC/DAC 与 OLED 地址表 >','Virtual ADC/DAC and OLED address map >')]);

entry('registers','reference','stc89c52rc',['常见 SFR 与中断','Common SFRs and interrupts'],['按地址查控制寄存器','Control registers by address'],[
 table([['地址','寄存器','用途'],['80 / 90 / A0 / B0','P0 / P1 / P2 / P3','四组 GPIO 端口'],['81','SP','栈指针'],['82 / 83','DPL / DPH','数据指针'],['87','PCON','Idle / Power-down'],['88 / 89','TCON / TMOD','定时器 0/1'],['8A / 8C','TL0 / TH0','定时器 0 计数'],['8B / 8D','TL1 / TH1','定时器 1 计数'],['98 / 99','SCON / SBUF','虚拟串口'],['A8 / B8','IE / IP','中断允许 / 优先级'],['C8 / CA–CD','T2CON / RCAP2 / T2','定时器 2'],['D0 / E0 / F0','PSW / ACC / B','状态与算术寄存器']],[['Hex address','SFR','Purpose'],['80 / 90 / A0 / B0','P0 / P1 / P2 / P3','GPIO ports'],['81','SP','Stack pointer'],['82 / 83','DPL / DPH','Data pointer'],['87','PCON','Idle / Power-down'],['88 / 89','TCON / TMOD','Timers 0 and 1'],['8A / 8C','TL0 / TH0','Timer 0 counters'],['8B / 8D','TL1 / TH1','Timer 1 counters'],['98 / 99','SCON / SBUF','Virtual UART'],['A8 / B8','IE / IP','Interrupt enable / priority'],['C8 / CA–CD','T2CON / RCAP2 / T2','Timer 2'],['D0 / E0 / F0','PSW / ACC / B','Status and arithmetic']]),
 h('中断实验要检查三件事','Three interrupt checks'),
 p('先打开对应中断使能，再打开 EA 总使能，并确保服务函数以 RETI 返回。Timer 0/1/2、外部中断和串口按已实现的 IE/IP 两级优先级工作。不要依赖 STC 特有的额外中断源或四级优先级。','Enable the interrupt source, enable global EA, and return from the handler with RETI. Timers 0/1/2, external interrupts and UART use the implemented two-level IE/IP scheme. Do not depend on STC-specific extra sources or four-level priorities.'),
 tip('模拟器支持 255 个已定义基础操作码、位操作、MOVC/MOVX、乘除与 BCD 调整。看门狗、IAP/EEPROM 和 6T/1T 加速尚未实现，工程初始化不要依赖这些功能。','The interpreter supports all 255 defined base opcodes, bit operations, MOVC/MOVX, arithmetic and BCD adjustment. Watchdog, IAP/EEPROM and 6T/1T acceleration are not implemented; avoid requiring them in startup code.')]);

entry('xdata','reference','redstone_port',['扩展设备地址表','Virtual device address map'],['MOVX 与 XBYTE 的约定','MOVX and XBYTE conventions'],[
 table([['XDATA 地址','读写','内容'],['F000H–F007H','只读','ADC0–7，0–15'],['F010H–F017H','读写','DAC0–7，写入限制为 0–15'],['F020H','读写','OLED 桥接显示控制'],['F100H–F4FFH','读写','已连接 OLED 的 1024 B 显存']],[['XDATA address','Access','Contents'],['F000H–F007H','Read','ADC0–7, 0–15'],['F010H–F017H','Read/write','DAC0–7, clamped to 0–15'],['F020H','Read/write','OLED bridge control'],['F100H–F4FFH','Read/write','1024-byte connected OLED RAM']]),
 p('这些地址是本模组定义的设备窗口，会覆盖对应的普通 XDATA 字节。F020H 写 bit0 控制显示开关，写 bit1 为 1 请求清屏。OLED 桥接需要先通过 SDA/SCL 连接到实际模块。','These mod-defined windows replace ordinary XDATA bytes at those addresses. At F020H, bit 0 controls display enable and writing bit 1 requests a clear. The OLED bridge requires an actual module connected through SDA/SCL.'),
 code('#include <absacc.h>\nXBYTE[0xF010] = 15; /* DAC0 */\nXBYTE[0xF020] = 3;  /* on + clear */\nXBYTE[0xF100] = 0xFF;'),
 tip('不要让启动代码批量清零这些设备窗口。GPIO 软件 I²C 与桥接接口可分别使用；移植普通 SSD1306 驱动时，应按 GPIO 总线驱动，而不是假定真芯片拥有这些地址。','Do not bulk-clear device windows in startup code. Software I²C and bridge access are separate options. A normal SSD1306 driver should use the GPIO bus, not assume these addresses exist on physical hardware.')]);

entry('i2c','reference','oled',['软件 I²C 接口','Software I²C'],['任意两根 GPIO 都能接 SDA/SCL','SDA/SCL can use any GPIO pair'],[
 p('I²C 通过 GPIO 电平变化实现，不需要单独的硬件 I²C 页面。选择两根 GPIO，标号 SDA 和 SCL，再让固件操作这两根引脚。每次读 SDA 前先写 1 释放它，外设通过拉低表示 0 或 ACK。','I²C is driven by GPIO transitions and needs no separate hardware-I²C page. Choose two GPIOs labelled SDA and SCL and operate them in firmware. Release SDA by writing 1 before reading; a device pulls it low for 0 or ACK.'),
 table([['阶段','操作'],['START','SCL 高时 SDA 从高变低'],['STOP','SCL 高时 SDA 从低变高'],['发送一字节','高位先行，在 SCL 上升沿采样'],['第九个时钟','发送方释放 SDA，接收方给 ACK/NACK'],['控制器读最后字节','NACK，然后 STOP']],[['Phase','Operation'],['START','SDA high-to-low while SCL is high'],['STOP','SDA low-to-high while SCL is high'],['Byte transfer','MSB first; sample on rising SCL'],['Ninth clock','Sender releases SDA; receiver ACKs or NACKs'],['Final read byte','Controller NACK, then STOP']]),
 table([['模块','七位地址','写 / 读字节'],['OLED','3CH','78H / 79H（以写为主）'],['环境传感器','48H','90H / 91H'],['PCF8591','48H–4FH','地址左移一位，再加读位']],[['Device','7-bit address','Write / read byte'],['OLED','3CH','78H / 79H (primarily writes)'],['Environment','48H','90H / 91H'],['PCF8591','48H–4FH','Shift address left; add read bit']]),
 p('七位地址和发到线上的地址字节不是同一个数。48H 左移一位是 90H，最低位加 1 就是读地址 91H。把 90H 再左移会找错设备。','A seven-bit address differs from the byte on the bus. Shifting 48H left gives write 90H; setting bit 0 gives read 91H. Shifting 90H again targets the wrong address.'),
 tip('共享 SDA/SCL 的不同设备需要唯一地址。接线一览会报告地址冲突。I²C 传输按 CPU 的 GPIO 写入逐次处理，单个游戏 tick 内也能完成；红石世界本身仍按 tick 更新。','Different devices sharing SDA/SCL need unique addresses. The wiring page reports conflicts. I²C follows each MCU GPIO write and can complete inside one game tick; redstone world updates still occur by tick.'),
 p('支持 START/STOP、重复 START、地址匹配及 ACK/NACK。不模拟模拟电压、多主仲裁和时钟拉伸。SSD1306 的滚动命令可接收，但当前不会产生滚动动画。','START/STOP, repeated START, addresses and ACK/NACK are supported. Analog voltage, multi-master arbitration and clock stretching are not simulated. SSD1306 scroll commands are accepted but currently do not animate scrolling.')]);

entry('onewire','reference','ds18b20',['1-Wire 与温度数据','1-Wire and temperature data'],['复位、存在脉冲与 CRC8','Reset, presence and CRC8'],[
 p('标准速率 1-Wire 只用 DQ。控制器先拉低至少 480 μs，再释放；探头会在释放后约 30–150 μs 拉低作为存在脉冲。每个数据时隙都先拉低，之后根据要写或读的位及时释放。','Standard-speed 1-Wire uses DQ. The controller holds low for at least 480 μs and releases. The probe gives a low presence pulse about 30–150 μs later. Each data slot begins low, then releases at the appropriate write/read time.'),
 table([['命令','HEX','用途'],['Read ROM','33','读取单个器件 ROM'],['Match / Skip ROM','55 / CC','选择器件 / 省略选择'],['Search / Alarm Search','F0 / EC','搜索 ROM / 报警器件'],['Convert T','44','启动温度转换'],['Read Scratchpad','BE','读取 9 字节结果'],['Write / Copy Scratchpad','4E / 48','修改 / 保存阈值与分辨率'],['Recall / Power Supply','B8 / B4','恢复配置 / 查询供电']],[['Command','HEX','Use'],['Read ROM','33','Read one device ROM'],['Match / Skip ROM','55 / CC','Select / skip selection'],['Search / Alarm Search','F0 / EC','Search ROMs / alarms'],['Convert T','44','Begin conversion'],['Read Scratchpad','BE','Read nine bytes'],['Write / Copy Scratchpad','4E / 48','Set / save thresholds and resolution'],['Recall / Power Supply','B8 / B4','Recall config / query power']]),
 h('一次测量的顺序','One measurement sequence'),
 p('复位 → CC → 44 → 等待转换结束 → 再次复位 → CC → BE → 读 9 字节。12 位转换最长约 750 ms。低分辨率对应更短转换时间。所有数据字节都低位先行。','Reset, CC, 44, wait for conversion, reset, CC, BE, then read nine bytes. A 12-bit conversion takes up to about 750 ms; lower resolutions finish sooner. All bytes transfer LSB first.'),
 p('CRC 使用 Dallas 反射多项式 8CH，对前八个字节逐位计算，结果应等于第九字节。温度的低、高字节按有符号数合并；默认 12 位时，原始值除以 16 即为摄氏度。','CRC uses reflected Dallas polynomial 8CH over the first eight bytes and must match byte nine. Combine temperature low/high bytes as a signed number; at default 12-bit resolution divide by 16 for Celsius.'),
 tip('模组假定外部供电，不要求你模拟寄生电源的强上拉。DQ 仍必须按端口规则释放后读取，固定拉低会使存在脉冲和数据都无法识别。','External power is implicit, so parasite-power strong pull-up is unnecessary. DQ still must be released before reads; holding it low prevents presence and data detection.')]);

entry('examples','reference','manual',['示例固件索引','Example firmware index'],['先用已知程序验证，再自己修改','Verify a known program, then edit it'],[
 p('示例位于模组源码仓库的 examples 文件夹，和你电脑上项目目录中的同名文件夹。游戏安装 JAR 不会自动把示例复制到游戏目录；先取得需要的 HEX，再在主板选择它。','Examples are in the source repository examples folder and the same directory in your local project. Installing the JAR does not copy these files into the game instance. Obtain the desired HEX, then select it on the board.'),
 table([['HEX 文件','接线与频率'],['redstone_blink.hex','GPIO P1.2 → 红石数字输出'],['adc_mirror.hex','ADC0 输入 → DAC1 输出'],['i2c_oled_hello.hex','SDA=P1.6 / SCL=P1.7'],['oled_hello.hex','同样连接 OLED，使用 MOVX 桥接'],['i2c_sensor.hex','SDA=P1.6 / SCL=P1.7，结果到 DAC0'],['ds18b20_read.hex','DQ=P1.2，必须 12 MHz'],['seven_segment_counter.hex','A–DP=P1.0–7，共阴，默认晶振'],['oled_pattern.hex','诊断条纹，不是文字']],[['HEX file','Wiring and clock'],['redstone_blink.hex','GPIO P1.2 to digital redstone output'],['adc_mirror.hex','ADC0 input to DAC1 output'],['i2c_oled_hello.hex','SDA=P1.6 / SCL=P1.7'],['oled_hello.hex','Same OLED wiring; MOVX bridge'],['i2c_sensor.hex','SDA=P1.6 / SCL=P1.7, result to DAC0'],['ds18b20_read.hex','DQ=P1.2; requires 12 MHz'],['seven_segment_counter.hex','A–DP=P1.0–7; cathode; default clock'],['oled_pattern.hex','Diagnostic stripes, not text']]),
 h('验证顺序','Validation order'),
 p('先确认晶振和端子与上表一致，再接线、烧录、运行。示例能工作说明基本接口正常，再修改源码或换引脚。每换一个物理引脚，固件中的定义也必须一起改。','Match the oscillator and terminals first, then wire, flash and run. A working example verifies the basic interface before source or pin changes. Every physical-pin change must be reflected in firmware.'),
 p('ds18b20_read.hex 的 30H–38H 是结果，3FH=A5H 表示完成。数码管计数程序的 30H 是数字、31H 是段码。OLED 上传程序完成后 30H=A5H。它们是各示例自己的调试约定，不是通用寄存器。','The DS18B20 example stores data at 30H–38H and completion A5H at 3FH. The counter uses 30H for digit and 31H for code. The OLED upload marks completion A5H at 30H. These are per-example conventions, not universal registers.'),
 tip('同名 C 源码用于 Keil C51。随附 HEX 由项目脚本汇编生成，C 版本的软件延时会受编译器优化影响。不要把 .c 文件改扩展名冒充 .hex。','Matching C sources target Keil C51. Bundled HEX is script-assembled; C software delays depend on optimization. Renaming a C source to .hex does not produce firmware.')]);

entry('troubleshooting','reference','wire_cutter',['按现象排查','Troubleshooting'],['从最少的设备开始定位问题','Isolate problems with a minimal circuit'],[
 h('主板与烧录','Board and flashing'),
 table([['现象','处理'],['烧录被拒绝','检查 HEX 完整性、校验和、EOF 和 8 KiB 范围'],['运行按钮报错','装好晶振，检查是否有固件；故障后先复位'],['程序没有更新','重新编译、选择正确 HEX、烧录并运行'],['停在一处','区分正常等待循环与保留操作码故障']],[['Symptom','Action'],['Flash rejected','Check HEX, checksums, EOF and 8 KiB limit'],['Run fails','Install crystal and firmware; reset after faults'],['Old behavior remains','Rebuild, select correct HEX, flash and run'],['Stuck at one point','Distinguish intentional loops from opcode faults']]),
 h('接线与端口','Wiring and ports'),
 table([['现象','处理'],['模块未连接','检查方向、线组、标号及所有必需引脚'],['配对窗口为空','先应用主板引脚，再重新点线卷端点'],['输入始终为 0','先写 1 释放端口，再读取外部信号'],['远端离线','加载两端及接线柱区块，检查主干是否被拆'],['地址冲突','给同总线设备改地址或换独立 GPIO 线组']],[['Symptom','Action'],['Module disconnected','Check face, group, labels and all required pins'],['No pairing options','Apply board pins, then select endpoints again'],['Input always 0','Write 1 to release the latch before reading'],['Remote offline','Load endpoints and posts; check trunk cables'],['Address conflict','Change device address or use a separate bus']]),
 h('显示与传感器','Displays and sensors'),
 table([['现象','处理'],['OLED 条纹像乱码','改用 i2c_oled_hello.hex，确认字模与寻址'],['OLED 黑屏','检查 AF、3CH 地址、SDA/SCL 和接线状态'],['数码管字形相反','核对共阳/共阴及 A–DP 的位顺序'],['PCF8591 读数滞后','丢弃首字节，读取第二次转换值'],['DS18B20 返回 85 ℃','等转换完成，再读暂存器'],['DS18B20 全 FFH','核对 DQ、释放动作、12 MHz 与复位时序']],[['Symptom','Action'],['OLED stripes','Use readable-text HEX; check glyph addressing'],['OLED blank','Check AF, address 3CH, SDA/SCL and connection'],['Wrong digit shape','Check polarity and A–DP bit order'],['PCF8591 stale value','Discard first read; use the second conversion'],['DS18B20 reads 85 °C','Wait for conversion before scratchpad read'],['DS18B20 all FFH','Check DQ, release, 12 MHz and reset timing']]),
 tip('最有效的排查电路是主板 + 一个模块 + 一根线。确认已知 HEX 能工作，再逐一恢复接线柱、分支、传感器和控制程序。','The best diagnostic circuit is one board, one module and one cable. Verify a known HEX first, then restore posts, branches, sensors and control logic one at a time.')]);

entry('compatibility','reference','ds18b20',['科技模组与温度来源','Machines and temperature sources'],['红石兼容、热能力与游戏折算','Redstone, heat capabilities and estimates'],[
 h('通过红石控制机器','Control machines with redstone'),
 p('红石接口可以连接接受原版红石的科技模组组件，例如机械动力的离合器和红石链路。先在目标组件中确认红石启停含义，再选择数字 GPIO 或模拟 DAC 输出。主板不直接改写 Create 的内部转速。','Redstone connectors work with technology components accepting vanilla redstone, such as Create clutches and links. Check the target component behavior before selecting GPIO or DAC output. The board does not directly rewrite Create rotational speed.'),
 h('DS18B20 从哪里得到温度','Where DS18B20 readings come from'),
 table([['来源','读数约定'],['Mekanism','接触侧热容能力；开尔文减 273.15'],['Create 锅炉','读取控制器热量，再按游戏模型折算'],['点燃熔炉 / 营火','90 ℃ / 95 ℃ 模拟温度'],['岩浆块 / 冰雪','110 ℃ / 0 ℃ 模拟温度'],['熔岩','源温度 1000 ℃，探头显示超量程'],['其他方块','群系环境模型，或模组温度能力']],[['Source','Reading convention'],['Mekanism','Contact-side heat capability; K minus 273.15'],['Create boiler','Controller heat converted by a game model'],['Lit furnace / campfire','Simulated 90 °C / 95 °C'],['Magma / ice and snow','Simulated 110 °C / 0 °C'],['Lava','Source 1000 °C; probe reports overrange'],['Other blocks','Biome model or provided temperature capability']]),
 p('Create 的热量等级不是原生摄氏度。本模组把活跃热量 0–18 级映射为 20+5×等级 ℃，被动热为 60 ℃，停热为 20 ℃。探头右键会明确标注「Create 热量折算」。','Create heat levels are not native Celsius. Active heat levels 0–18 map to 20+5*level °C, passive heating to 60 °C and no heat to 20 °C. The probe explicitly labels this as Create heat conversion.'),
 tip('探头量程仍是 -55～125 ℃，超出范围会限制读数并提示超量程。其他有温度的模组不会自动全部兼容，需要提供 embeddeddev:temperature 能力或实现对应温度接口。','The probe still clamps to -55 to 125 °C and marks overrange. Other temperature mods are not universally detected: they need the embeddeddev:temperature capability or the corresponding provider interface.'),
 p('这些科技模组均为可选。没有安装它们时，开发板、GPIO 总线、所有电子模块和原版温度模型仍然可以独立使用。','Technology mods are optional. Boards, GPIO buses, electronics and vanilla temperature models remain usable without them.')]);

const categories=[['start',['入门','Start'],'manual'],['develop',['开发','Develop'],'51systemboard'],['modules',['模块','Modules'],'oled'],['reference',['速查','Reference'],'stc89c52rc']];
export async function generate(){
 const root=fileURLToPath(new URL('../src/main/resources/assets/embeddeddev/manual/',import.meta.url));
 await fs.mkdir(root,{recursive:true});
 for(const [language,index] of [['zh_cn',0],['en_us',1]]){
  const data={title:['嵌入式开发手册','Embedded Dev Handbook'][index],subtitle:['在方块里搭建你的电子实验室','Build your electronics laboratory'][index],
   categories:categories.map(([id,title,icon])=>({id,title:title[index],icon:'embeddeddev:'+icon})),
   entries:entries.map(entry=>({...entry,title:entry.title[index],subtitle:entry.subtitle[index],keywords:entry.id,
    blocks:entry.blocks.map(block=>({...block,text:block.text?.[index],rows:block.rows?.[index]}))}))};
  await fs.writeFile(root+language+'.json',JSON.stringify(data,null,2)+'\n');
 }
 return {entries:entries.length,recipes:entries.flatMap(e=>e.blocks).filter(b=>b.type==='recipe').length,categories:categories.length};
}
