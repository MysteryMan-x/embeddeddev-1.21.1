import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

// Original 5x7 uppercase demo font, stored as vertical columns (bit 0 = top).
const glyphs={
 ' ':[0,0,0,0,0],A:[126,9,9,9,126],B:[127,73,73,73,54],C:[62,65,65,65,34],D:[127,65,65,34,28],E:[127,73,73,73,65],F:[127,9,9,9,1],
 G:[62,65,73,73,122],H:[127,8,8,8,127],I:[65,65,127,65,65],J:[32,64,65,63,1],K:[127,8,20,34,65],L:[127,64,64,64,64],M:[127,2,12,2,127],
 N:[127,4,8,16,127],O:[62,65,65,65,62],P:[127,9,9,9,6],Q:[62,65,81,33,94],R:[127,9,25,41,70],S:[38,73,73,73,50],T:[1,1,127,1,1],
 U:[63,64,64,64,63],V:[31,32,64,32,31],W:[127,32,24,32,127],X:[99,20,8,20,99],Y:[3,4,120,4,3],Z:[97,81,73,69,67],
 '0':[62,81,73,69,62],'1':[0,66,127,64,0],'2':[98,81,73,73,70],'3':[34,65,73,73,54],'4':[24,20,18,127,16],
 '5':[39,69,69,69,57],'6':[60,74,73,73,48],'7':[1,113,9,5,3],'8':[54,73,73,73,54],'9':[6,73,73,41,30],':':[0,54,54,0,0],'.':[0,96,96,0,0],'-':[8,8,8,8,8]
};
function frame(){
 const pixels=new Uint8Array(1024);
 const text=(x,page,value)=>{for(const ch of value){for(const col of glyphs[ch]??glyphs[' ']){if(x<128)pixels[page*128+x]=col;x++;}x++;}};
 text(7,1,'EMBEDDED DEV');text(7,3,'STC89C52');text(7,5,'HELLO MINECRAFT');text(7,7,'I2C 0X3C');
 for(let x=6;x<122;x++)pixels[x]=2;
 for(let y=12;y<62;y++) pixels[(y>>3)*128+123]|=1<<(y&7);
 return pixels;
}
class Asm {
 bytes=[]; labels=new Map(); refs=[];
 emit(...v){this.bytes.push(...v.map(x=>x&255));return this;}
 label(name){this.labels.set(name,this.bytes.length);return this;}
 call(label){return this.absolute(0x12,label);}
 absolute(op,label){this.emit(op);this.refs.push({at:this.bytes.length,label,relative:false});return this.emit(0,0);}
 rel(op,label,...args){this.emit(op,...args);this.refs.push({at:this.bytes.length,label,relative:true});return this.emit(0);}
 resolve(){for(const r of this.refs){const value=this.labels.get(r.label);if(value===undefined)throw Error('Missing '+r.label);if(r.relative){const n=value-r.at-1;if(n<-128||n>127)throw Error('Long branch '+r.label);this.bytes[r.at]=n&255;}else{this.bytes[r.at]=value>>8;this.bytes[r.at+1]=value&255;}}return Uint8Array.from(this.bytes);}
}
function i2cSubroutines(a){
 a.label('start').emit(0xD2,0x96,0xD2,0x97,0xC2,0x96,0xC2,0x97,0x22);
 a.label('stop').emit(0xC2,0x96,0xD2,0x97,0xD2,0x96,0x22);
 a.label('write').emit(0x7F,8).label('bit').emit(0x33,0x92,0x96,0xD2,0x97,0xC2,0x97).rel(0xDF,'bit')
  .emit(0xD2,0x96,0xD2,0x97,0xA2,0x96,0xC2,0x97,0x22);
 a.label('read').emit(0xD2,0x96,0x7F,8,0xE4).label('readbit').emit(0xD2,0x97,0xA2,0x96,0x33,0xC2,0x97).rel(0xDF,'readbit')
  .emit(0xD2,0x96,0xD2,0x97,0xC2,0x97,0x22); // Controller NACKs the single-byte read.
}
function oledProgram(pixels){
 const a=new Asm();a.emit(0x75,0x90,0xC0);
  a.call('start');for(const v of [0x78,0x00,0xAE,0xA1,0xC8,0x20,0,0x21,0,127,0x22,0,7,0xAF])a.emit(0x74,v).call('write');a.call('stop');
  a.call('start');for(const v of [0x78,0x40])a.emit(0x74,v).call('write');
  a.absolute(0x90,'frame').emit(0x7D,4,0x7E,0).label('copy').emit(0xE4,0x93).call('write').emit(0xA3).rel(0xDE,'copy').rel(0xDD,'copy').call('stop');
 a.emit(0x75,0x30,0xA5).label('forever').rel(0x80,'forever');i2cSubroutines(a);
 a.label('frame').emit(...pixels);return a.resolve();
}
function sensorProgram(){
 const a=new Asm();a.emit(0x75,0x90,0xC0).label('poll').call('start');for(const v of [0x90,0])a.emit(0x74,v).call('write');
 a.call('start').emit(0x74,0x91).call('write').call('read').emit(0xF5,0x30).call('stop').emit(0xE5,0x30)
  .rel(0x60,'sensor_low').emit(0xD2,0x92).rel(0x80,'poll').label('sensor_low').emit(0xC2,0x92).rel(0x80,'poll');
 i2cSubroutines(a);return a.resolve();
}
function pcf8591Program(){
 const a=new Asm();a.emit(0x75,0x90,0xC0).label('poll').call('start');
 for(const v of [0x90,0x40])a.emit(0x74,v).call('write');
 a.call('start').emit(0x74,0x91).call('write').call('read_ack').call('read').emit(0xF5,0x30).call('stop');
 a.call('start');for(const v of [0x90,0x40])a.emit(0x74,v).call('write');
 a.emit(0xE5,0x30).call('write').call('stop').rel(0x80,'poll');
 i2cSubroutines(a);
 // ACK the stale conversion, then use the existing NACK reader for the fresh sample.
 a.label('read_ack').emit(0xD2,0x96,0x7F,8,0xE4).label('ack_bit')
  .emit(0xD2,0x97,0xA2,0x96,0x33,0xC2,0x97).rel(0xDF,'ack_bit')
  .emit(0xC2,0x96,0xD2,0x97,0xC2,0x97,0xD2,0x96,0x22);
 return a.resolve();
}
function thermometerProgram() {
 const a = new Asm();
 a.emit(0x75,0x90,0xff).call('ow_reset');
 for (const value of [0xcc,0x44]) a.emit(0x74,value).call('ow_write');
 a.emit(0x7c,8).label('ms_outer').emit(0x7d,200).label('ms_inner').emit(0x7e,250)
  .label('ms_delay').rel(0xde,'ms_delay').rel(0xdd,'ms_inner').rel(0xdc,'ms_outer');
 a.call('ow_reset');
 for (const value of [0xcc,0xbe]) a.emit(0x74,value).call('ow_write');
 a.emit(0x78,0x30,0x7d,9).label('scratch').call('ow_read').emit(0xf6,0x08).rel(0xdd,'scratch');
 a.emit(0x75,0x3f,0xa5).label('ow_forever').rel(0x80,'ow_forever');
 a.label('ow_reset').emit(0xc2,0x92,0x7e,250).label('reset_low').rel(0xde,'reset_low')
  .emit(0xd2,0x92).call('ow_wait').emit(0xa2,0x92).call('ow_wait').call('ow_wait').emit(0x22);
 a.label('ow_wait').emit(0x7e,32).label('ow_wait_loop').rel(0xde,'ow_wait_loop').emit(0x22);
 a.label('ow_write').emit(0x7f,8).label('ow_write_bit').emit(0xc2,0x92,0x13).rel(0x40,'ow_one')
  .call('ow_wait').emit(0xd2,0x92).rel(0x80,'ow_written');
 a.label('ow_one').emit(0,0,0,0,0xd2,0x92);
 a.label('ow_written').call('ow_wait').rel(0xdf,'ow_write_bit').emit(0x22);
 a.label('ow_read').emit(0xe4,0x7f,8).label('ow_read_bit').emit(0xc2,0x92,0,0,0,0xd2,0x92,0,0,0,0,0,0,0xa2,0x92,0x13)
  .call('ow_wait').rel(0xdf,'ow_read_bit').emit(0x22);
 return a.resolve();
}
function sevenSegmentProgram() {
 const a = new Asm();
 a.emit(0x75,0x90,0,0x78,0).absolute(0x90,'digit_codes');
 a.label('digit_loop').emit(0xe8,0x93,0xf5,0x90,0x88,0x30,0x85,0x90,0x31,0x7c,5);
 a.label('digit_delay_outer').emit(0x7d,200).label('digit_delay_middle').emit(0x7e,250)
  .label('digit_delay_inner').rel(0xde,'digit_delay_inner').rel(0xdd,'digit_delay_middle').rel(0xdc,'digit_delay_outer');
 a.emit(0x08).rel(0xb8,'digit_loop',10).emit(0x78,0).rel(0x80,'digit_loop');
 a.label('digit_codes').emit(0x3f,0x86,0x5b,0xcf,0x66,0xed,0x7d,0x87,0x7f,0xef);
 return a.resolve();
}
function hex(bytes){const rows=[];for(let at=0;at<bytes.length;at+=16){const data=bytes.slice(at,at+16);let sum=data.length+(at>>8)+(at&255);const x=n=>(n&255).toString(16).padStart(2,'0').toUpperCase();let r=':'+x(data.length)+at.toString(16).padStart(4,'0').toUpperCase()+'00';for(const v of data){r+=x(v);sum+=v;}rows.push(r+x(-sum));}return rows.join('\n')+'\n:00000001FF\n';}
export async function generate(){
 const directory=fileURLToPath(new URL('../examples/',import.meta.url));const pixels=frame();
 for(const [name,bytes] of [['oled_hello',oledProgram(pixels)],['i2c_oled_hello',oledProgram(pixels)],['oled_pattern',oledProgram(Uint8Array.from({length:1024},(_,i)=>(i&255)^0xAA))],['i2c_sensor',sensorProgram()],['ds18b20_read',thermometerProgram()],['seven_segment_counter',sevenSegmentProgram()],
  ['pcf8591_mirror',pcf8591Program()]])await fs.writeFile(directory+name+'.hex',hex(bytes));
 await fs.writeFile(directory+'oled_hello_bitmap.h','/* Original 128x64 page bitmap: EMBEDDED DEV / STC89C52 / HELLO MINECRAFT / I2C 0X3C */\nconst unsigned char code hello_bitmap[1024] = {\n'+Array.from(pixels).map((v,i)=>(i%16===0?'    ':'')+'0x'+v.toString(16).padStart(2,'0')+(i===1023?'':',')+(i%16===15?'\n':' ')).join('')+'};\n');
 return {readablePixels:pixels.reduce((n,v)=>n+v.toString(2).replaceAll('0','').length,0),i2c:oledProgram(pixels).length,sensor:sensorProgram().length};
}
