import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { generateSurvivalData } from './generate-survival.mjs';

const root = fileURLToPath(new URL('../src/main/resources/', import.meta.url));
const materials = { pcb:'green_concrete', blue:'blue_concrete', black:'black_concrete', dark:'gray_concrete',
  metal:'iron_block', gold:'yellow_concrete', red:'red_concrete', led:'lime_concrete', white:'white_concrete', copper:'cut_copper' };
const textures = Object.fromEntries(Object.entries(materials).map(([k,v])=>[k,'minecraft:block/'+v]));
textures.particle = textures.pcb;
const cube = (from,to,material) => ({from,to,faces:Object.fromEntries(['down','up','north','south','west','east'].map(f=>[f,{uv:[4,4,12,12],texture:'#'+material}]))});
const model = elements => ({ambientocclusion:true,textures,elements,display:{
  gui:{rotation:[30,225,0],translation:[0,1,0],scale:[0.88,0.88,0.88]},
  ground:{translation:[0,3,0],scale:[0.5,0.5,0.5]},
  fixed:{rotation:[-30,0,0],scale:[0.8,0.8,0.8]},
  firstperson_righthand:{rotation:[0,-45,25],translation:[0,3,1],scale:[0.8,0.8,0.8]},
  thirdperson_righthand:{rotation:[75,45,0],translation:[0,2.5,0],scale:[0.65,0.65,0.65]}
}});
export async function generate() {
  async function json(path,value) {
    const target=root+'/'+path; await fs.mkdir(target.slice(0,target.lastIndexOf('/')),{recursive:true}); await fs.writeFile(target,JSON.stringify(value,null,2)+'\n'); }
  const board=[]; const b=(a,z,m)=>board.push(cube(a,z,m));
  b([1,0,1],[15,0.8,15],'dark'); b([1.3,0.8,1.3],[14.7,1.35,14.7],'pcb');
  for(const [x,z] of [[2,2],[14,2],[2,14],[14,14]]) { b([x-.5,0,z-.5],[x+.5,1.5,z+.5],'metal'); b([x-.2,1.5,z-.2],[x+.2,1.65,z+.2],'black'); }
  b([5.4,1.8,3.7],[10.6,3.4,12.1],'black'); b([5.75,3.4,4.05],[10.25,3.55,11.75],'dark');
  for(let i=0;i<10;i++){let z=4.1+i*.76; b([4.75,1.35,z],[5.7,2.3,z+.32],'metal'); b([10.3,1.35,z],[11.25,2.3,z+.32],'metal');}
  const letters={S:['111','100','111','001','111'],T:['111','010','010','010','010'],C:['111','100','100','100','111']};
  [...'STC'].forEach((letter,l)=>letters[letter].forEach((row,z)=>[...row].forEach((on,x)=>{if(on==='1')b([6.7+(l*4+x)*.23,3.56,5.1+z*.23],[6.7+(l*4+x)*.23+.19,3.58,5.1+z*.23+.19],'white');})));
  b([6.6,3.56,7.3],[9.4,3.58,7.43],'white'); b([6.6,3.56,7.7],[8.7,3.58,7.81],'white');
  b([5.85,3.56,10.75],[6.25,3.6,11.15],'white');
  for(let i=0;i<8;i++) {
    let z=3+i*1.35; b([2.3,1.35,z],[3.55,3.15,z+.9],'black'); b([2.7,3.15,z+.25],[3.15,4.15,z+.65],'gold');
    b([12.45,1.35,z],[13.7,3.15,z+.9],'black'); b([12.85,3.15,z+.25],[13.3,4.15,z+.65],'gold');
  }
  b([6,1.35,1.6],[10,3.15,2.75],'blue'); for(let i=0;i<3;i++) b([6.5+i*1.1,3.15,1.9],[7.05+i*1.1,3.3,2.45],'metal');
  b([4,1.35,12.5],[5.3,2.2,14],'metal'); b([4.2,2.2,12.7],[5.1,2.55,13.8],'red');
  b([6,1.35,13],[8.9,2.1,14.2],'metal'); b([10,1.35,13],[10.7,2.2,13.8],'led'); b([11.2,1.35,13],[11.9,2.2,13.8],'red');
  for(let i=0;i<5;i++) b([3.6,1.36,4+i*1.7],[4.55,1.4,4.12+i*1.7],'gold');
  await json('assets/embeddeddev/models/block/51systemboard.json',model(board));
  const oled=[];const o=(a,z,m)=>oled.push(cube(a,z,m));
  o([1,0,1],[15,.65,15],'dark');o([1.4,.65,1.4],[14.6,1.05,14.6],'blue');
  o([6.5,1.05,5],[9.5,5.8,8],'dark');o([1,5,2.1],[15,13,4],'blue');
  o([1.25,5.25,1.8],[14.75,12.75,3.3],'dark');o([1.75,5.75,1.75],[14.25,12.25,1.81],'black');
  for(const [x,y] of [[1.5,5.5],[14.5,5.5],[1.5,12.5],[14.5,12.5]])o([x-.2,y-.2,1.65],[x+.2,y+.2,1.8],'metal');
  for(let i=0;i<4;i++){o([4.25+i*2,3.8,2.8],[5.05+i*2,5,3.3],'gold');o([4.25+i*2,1.05,9],[5.05+i*2,2.2,10],'gold');}
  o([2,1.05,11],[14,1.3,11.15],'gold');o([2,1.05,12],[14,1.3,12.15],'gold');
  await json('assets/embeddeddev/models/block/oled.json',model(oled));
  const port=[];const p=(a,z,m)=>port.push(cube(a,z,m));
  p([2,0,2],[14,.7,14],'dark');p([2.4,.7,2.4],[13.6,1.2,13.6],'pcb');p([4,1.2,4],[12,4.2,9],'blue');
  for(let i=0;i<3;i++){p([4.6+i*2.5,4.2,5],[6.1+i*2.5,4.35,6.6],'metal');p([4.9+i*2.5,1.8,3.9],[5.8+i*2.5,3.5,4],'black');}
  for(let i=0;i<6;i++)p([3.3+i*1.7,1.2,11],[3.9+i*1.7,3.7,11.7],'gold');
  await json('assets/embeddeddev/models/block/redstone_port.json',model(port));
  const sensor=[];const s=(a,z,m)=>sensor.push(cube(a,z,m));
  s([2,0,2],[14,.6,14],'dark');s([2.3,.6,2.3],[13.7,1.15,13.7],'blue');s([5,1.15,4],[11,4.8,10],'metal');s([5.5,4.8,4.5],[10.5,5,9.5],'dark');
  for(let i=0;i<3;i++)s([6+i*1.4,5,5.3],[6.55+i*1.4,5.1,8.7],'black');
  s([3.3,1.15,4],[4.3,2,5.3],'red');s([11.7,1.15,4],[12.7,2,5.3],'led');
  for(let i=0;i<4;i++)s([4+i*2.25,1.15,11.5],[4.8+i*2.25,3.5,12.3],'gold');
  await json('assets/embeddeddev/models/block/i2c_sensor.json',model(sensor));
  await json('assets/embeddeddev/blockstates/i2c_sensor.json',{variants:{'':{model:'embeddeddev:block/i2c_sensor'}}});
  await json('assets/embeddeddev/models/item/i2c_sensor.json',{parent:'embeddeddev:block/i2c_sensor'});
  await json('data/embeddeddev/loot_table/blocks/i2c_sensor.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'embeddeddev:i2c_sensor'}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
  const base = color => [cube([1,0,1],[15,.7,15],'dark'), cube([1.4,.7,1.4],[14.6,1.25,14.6],color)];
  const terminal = (x,z) => cube([x,1.25,z],[x+.9,2.5,z+1.1],'gold');
  const button = [...base('pcb'), ...[[2,2],[14,2],[2,14],[14,14]].map(([x,z]) =>
    cube([x-.5,1.25,z-.5],[x+.5,1.43,z+.5],'metal'))];
  const buttonItem = model([...button, cube([6.35,1.25,6.35],[9.65,3.1,9.65],'black'),
    cube([6.6,3.1,6.6],[9.4,5.3,9.4],'red')]);
  const led = [...base('pcb'), cube([5,1.25,5],[11,2.2,11],'black'), terminal(6,11.8), terminal(9,11.8)];
  const rtc = [...base('red'), cube([3,1.25,4],[9,2.5,9],'black'),
    cube([9.5,1.25,4],[13.5,2.9,9.8],'metal'), cube([8.9,1.25,5],[14.1,2.9,8.8],'metal'),
    terminal(5,11.8), terminal(7.5,11.8), terminal(10,11.8)];
  const adc = [...base('blue'), cube([5.2,1.25,4],[10.8,2.9,12],'black'),
    cube([7.5,2.9,7.5],[8.5,4,8.5],'gold'),
    terminal(7.5,2), terminal(12.8,7.5), terminal(7.5,12.8), terminal(2.2,7.5)];
  for (let i=0; i<8; i++) {
    const z=4.2+i*.95;
    adc.push(cube([4.35,1.25,z],[5.4,2.25,z+.5],'metal'), cube([10.6,1.25,z],[11.65,2.25,z+.5],'metal'));
    if (i<4) rtc.push(cube([2.5,1.25,4.4+i],[3.3,2.05,4.85+i],'metal'), cube([8.8,1.25,4.4+i],[9.4,2.05,4.85+i],'metal'));
  }
  const modules = {
    button_module: [model(button)],
    led_module: [model([...led,cube([5.7,2.2,5.7],[10.3,4.6,10.3],'pcb')]), model([...led,cube([5.7,2.2,5.7],[10.3,4.6,10.3],'led')])],
    ds1302: [model(rtc)],
    pcf8591: [model(adc)]
  };
  for (const [id,[idle,active]] of Object.entries(modules)) {
    await json('assets/embeddeddev/models/block/'+id+'.json',idle);
    const blockModel={model:'embeddeddev:block/'+id};
    let variants={'':blockModel};
    if (active) {
      await json('assets/embeddeddev/models/block/'+id+'_active.json',active);
      variants={'lit=false':blockModel,'lit=true':{model:blockModel.model+'_active'}};
    }
    await json('assets/embeddeddev/blockstates/'+id+'.json',{variants});
    await json('assets/embeddeddev/models/item/'+id+'.json',id === 'button_module' ? buttonItem : {parent:blockModel.model});
    await json('data/embeddeddev/loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,
      entries:[{type:'minecraft:item',name:'embeddeddev:'+id}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
  }

  const crystalBase = model([
    cube([4,4,7],[12,12,9.5],'metal'), cube([5,7.5,6.9],[11,9.7,7],'mark'),
    cube([5.7,1,7.7],[6.5,4,8.3],'gold'), cube([9.5,1,7.7],[10.3,4,8.3],'gold')
  ]);
  crystalBase.display.gui={rotation:[8,22,0],translation:[0,2,0],scale:[1.18,1.18,1.18]};
  await json('assets/embeddeddev/models/item/crystal_base.json',crystalBase);
  const crystalColors={crystal_6mhz:'light_blue',crystal_11_0592mhz:'green',crystal_12mhz:'yellow',
    crystal_22_1184mhz:'red',crystal_24mhz:'purple'};
  for (const [id,color] of Object.entries(crystalColors)) {
    await json('assets/embeddeddev/models/item/'+id+'.json',{parent:'embeddeddev:item/crystal_base',
      textures:{mark:'minecraft:block/'+color+'_concrete'}});
  }
  await json('assets/embeddeddev/models/item/manual.json',{parent:'minecraft:item/written_book'});
  const probe = model([cube([3,0,3],[13,1.2,13],'pcb'), cube([5.5,1.2,6],[10.5,3.8,10],'black'),
    cube([7,1.2,3.5],[9,5.8,5.5],'metal'), cube([7,5.8,3.5],[9,6.2,5.5],'white'),
    cube([4,1.2,11],[5,2.8,12],'gold'),cube([7.5,1.2,11],[8.5,2.8,12],'gold'),cube([11,1.2,11],[12,2.8,12],'gold')]);
  await json('assets/embeddeddev/models/block/ds18b20.json',probe);
  await json('assets/embeddeddev/models/item/ds18b20.json',{parent:'embeddeddev:block/ds18b20'});
  await json('assets/embeddeddev/blockstates/ds18b20.json',{variants:{
    'facing=down':{model:'embeddeddev:block/ds18b20'}, 'facing=up':{model:'embeddeddev:block/ds18b20',x:180},
    'facing=north':{model:'embeddeddev:block/ds18b20',x:90}, 'facing=south':{model:'embeddeddev:block/ds18b20',x:270},
    'facing=east':{model:'embeddeddev:block/ds18b20',x:90,y:90}, 'facing=west':{model:'embeddeddev:block/ds18b20',x:90,y:270}
  }});
  await json('data/embeddeddev/loot_table/blocks/ds18b20.json',{type:'minecraft:block',pools:[{rolls:1,
    entries:[{type:'minecraft:item',name:'embeddeddev:ds18b20'}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
  const parts = {
    raw_silicon: [cube([3,4,5],[8,9,12],'dark'), cube([6,3,4],[12,10,10],'metal'),
      cube([8,9,5],[12,12,9],'silicon'), cube([11,5,7],[14,8,12],'silicon')],
    silicon_wafer: [cube([3,6.5,5],[13,7.25,11],'metal'), cube([5,6.5,3],[11,7.25,5],'metal'),
      cube([5,6.5,11],[11,7.25,13],'metal'), cube([3.5,7.25,5.5],[12.5,7.5,10.5],'silicon'),
      cube([5.5,7.25,3.5],[10.5,7.5,5.5],'silicon'), cube([5.5,7.25,10.5],[10.5,7.5,12.5],'silicon')],
    circuit_board: [cube([2,6,3],[14,7,13],'pcb'), cube([3,7,5],[13,7.3,5.7],'copper'),
      cube([4,7,9],[12,7.3,9.7],'copper'), cube([8,7,5.7],[8.7,7.3,9],'copper'),
      ...[4,6.5,9,11.5].map(x => cube([x,7,11],[x+0.75,7.3,12],'gold'))]
  };
  for (const [id,elements] of Object.entries(parts)) {
    await json(`assets/embeddeddev/models/item/${id}.json`, {
      ...model(elements), textures: {...textures, silicon: 'minecraft:block/light_blue_concrete'}
    });
  }
  const coil = [cube([4,3,5],[12,4.5,11],'dark'), cube([4,11.5,5],[12,13,11],'dark'),
    cube([5,4.5,5.7],[11,11.5,10.3],'copper'), cube([6,7,4],[12,8,5.7],'copper')];
  const cutter = [cube([4,3,7],[6,9,9],'red'), cube([10,3,7],[12,9,9],'red'),
    cube([5,8,7],[11,10,9],'dark'), cube([6,10,7],[8,13,9],'metal'), cube([8.4,10,7],[10.4,13,9],'metal')];
  const relay = [cube([3,0,3],[13,2,13],'pcb'), cube([5,2,5],[11,9,11],'white'),
    cube([4,5,4],[12,6,12],'dark'), cube([4,8,4],[12,9,12],'dark'),
    cube([6,9,6],[10,12,10],'copper'), cube([5.5,10,5.5],[10.5,11.5,10.5],'gold')];
  await json('assets/embeddeddev/models/item/wire_coil.json', model(coil));
  await json('assets/embeddeddev/models/item/wire_cutter.json', model(cutter));
  await json('assets/embeddeddev/models/block/wire_relay.json', model(relay));
  await json('assets/embeddeddev/models/item/wire_relay.json', {parent:'embeddeddev:block/wire_relay'});
  await json('assets/embeddeddev/blockstates/wire_relay.json', {variants:{'':{model:'embeddeddev:block/wire_relay'}}});
  await json('data/embeddeddev/loot_table/blocks/wire_relay.json', {type:'minecraft:block',pools:[{rolls:1,
    entries:[{type:'minecraft:item',name:'embeddeddev:wire_relay'}],conditions:[{condition:'minecraft:survives_explosion'}]}]});
  const ores = ['silicon_ore', 'deepslate_silicon_ore'];
  for (const id of ores) {
    const blockModel = `embeddeddev:block/${id}`;
    await json(`assets/embeddeddev/blockstates/${id}.json`, {variants: {'': {model: blockModel}}});
    await json(`assets/embeddeddev/models/block/${id}.json`, {parent: 'minecraft:block/cube_all', textures: {all: blockModel}});
    await json(`assets/embeddeddev/models/item/${id}.json`, {parent: blockModel});
  }
  await json('data/minecraft/tags/block/mineable/pickaxe.json',{replace:false,
    values:['51systemboard','redstone_port','oled','i2c_sensor','wire_relay','ds18b20',...Object.keys(modules),...ores].map(v=>'embeddeddev:'+v)});
  await generateSurvivalData(json);
  return {board:board.length,oled:oled.length,port:port.length,sensor:sensor.length};
}
