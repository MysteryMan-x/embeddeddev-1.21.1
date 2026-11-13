// Repository-native illustration: actual block geometry, flat colors, no game screenshot.
import fs from 'node:fs/promises';
const palette={pcb:'#387b56',blue:'#3544a0',black:'#1d252c',dark:'#52616d',metal:'#cbd6da',gold:'#dfb85b',red:'#b64751',led:'#97cb60',white:'#eceddd',copper:'#bc8761'};
const shade=(hex,n)=>'#'+hex.slice(1).match(/../g).map(c=>Math.min(255,Math.round(parseInt(c,16)*n)).toString(16).padStart(2,'0')).join('');
const project=([x,y,z])=>[(x+z)*.866,(x-z)*.40-y];
function render(model){
 const faces=[];
 for(const e of model.elements){
  if(e.rotation)throw Error('Rotated geometry needs explicit support');
  const [x,y,z]=e.from,[X,Y,Z]=e.to;
  for(const [side,pts,light] of [
   ['north',[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z]],.78],
   ['east',[[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z]],.60],
   ['up',[[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],1.05]
  ]){
   const face=e.faces[side];if(!face)continue;
   const color=palette[face.texture.replace('#','')]??'#70858c';
   const depth=pts.reduce((a,p)=>a+p[0]+p[1]*.8-p[2],0)/4;
   faces.push({depth,svg:`<polygon points="${pts.map(p=>project(p).map(v=>v.toFixed(2)).join(',')).join(' ')}" fill="${shade(color,light)}" stroke="${shade(color,light*.85)}" stroke-width=".035"/>`});
  }
 }
 return faces.sort((a,b)=>a.depth-b.depth).map(f=>f.svg).join('');
}
const cards=[['51systemboard','STC89C52RC','Programmable 8051 core'],['oled','SSD1306 OLED','128 × 64 / software I²C'],['seven_segment','8-SEGMENT DISPLAY','A–G + decimal point'],['ds1302','DS1302 RTC','Calendar clock / 31-byte RAM'],['pcf8591','PCF8591','4-channel ADC / 1-channel DAC'],['mpu6050','MPU6050','Acceleration + angular rate']];
let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="820" viewBox="0 0 1200 820" role="img" aria-labelledby="title desc"><title id="title">Embedded Development module gallery</title><desc id="desc">Isometric renderings generated from the actual Minecraft block model geometry. Flat colors; not an in-game screenshot.</desc><rect width="1200" height="820" rx="20" fill="#101c24"/><g font-family="Segoe UI,Arial,sans-serif"><text x="40" y="54" fill="#f4f5e8" font-size="35" font-weight="700">EMBEDDED DEVELOPMENT</text><text x="40" y="87" fill="#96d8c6" font-size="19">Write C. Flash HEX. Build circuits in Minecraft.</text><text x="40" y="119" fill="#9ab3be" font-size="14">Minecraft 1.21.1 / NeoForge / Java 21</text>`;
for(const [i,[file,title,sub]] of cards.entries()){
 const x=40+i%3*380,y=153+Math.floor(i/3)*303;
 const model=JSON.parse(await fs.readFile(`src/main/resources/assets/embeddeddev/models/block/${file}.json`,'utf8'));
 svg+=`<rect x="${x}" y="${y}" width="360" height="283" rx="12" fill="#192e38" stroke="#34505d"/><ellipse cx="${x+180}" cy="${y+169}" rx="121" ry="22" fill="#0e1a23"/><g transform="translate(${x+62} ${y+133}) scale(8.5)">${render(model)}</g><text x="${x+24}" y="${y+231}" fill="#f4f5e8" font-size="19" font-weight="600">${title}</text><text x="${x+24}" y="${y+258}" fill="#9ab3be" font-size="15">${sub}</text>`;
}
svg+='<text x="40" y="789" fill="#9ab3be" font-size="14">Actual block geometry / flat-color illustration · Also includes RTC, temperature, altitude, redstone and motor interfaces</text></g></svg>';
await fs.mkdir('docs/images',{recursive:true});
await fs.writeFile('docs/images/modules.svg',svg+'\n');
console.log('Generated module gallery from six repository block models.');
