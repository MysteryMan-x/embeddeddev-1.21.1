param([string]$Compiler='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
if(-not $Compiler){$installed=Get-Command sdcc -ErrorAction SilentlyContinue;$Compiler=if($installed){$installed.Source}else{Join-Path $projectRoot 'build/tools/sdcc/sdcc/bin/sdcc.exe'}}
if(-not(Test-Path -LiteralPath $Compiler)){throw 'Install SDCC or pass -Compiler (sdcc.exe).'}
$source=Join-Path $projectRoot 'examples';$drivers=Join-Path $source 'drivers';$output=Join-Path $projectRoot 'build/sensor-examples'
New-Item -ItemType Directory -Path $output -Force|Out-Null
$base=@('-mmcs51','--model-small','--stack-auto','--iram-size','256','--data-loc','0x40','--code-size','8192','--xram-size','0','--opt-code-speed','-DED_FOSC_HZ=12000000UL','-DED_DQ_BIT_ADDRESS=0x92','-I',$source,'-I',$drivers)
$library=Join-Path (Split-Path -Parent (Split-Path -Parent $Compiler)) 'lib/mcs51-small-stack-auto'
if(Test-Path -LiteralPath (Join-Path $library 'mcs51.lib')){$base+=@('-L',$library)}
$deps=@('ssd1306','ed_font5x7','i2c_bus','ed_delay','environment','pcf8591','ds18b20','mpu6050','altimeter')
foreach($dep in $deps){& $Compiler @base '-c' (Join-Path $drivers ($dep+'.c')) '-o' (Join-Path $output ($dep+'.rel'));if($LASTEXITCODE -ne 0){throw "Compile failed: $dep"}}
& $Compiler @base '-c' (Join-Path $source 'sensor_display.c') '-o' (Join-Path $output 'sensor_display.rel');if($LASTEXITCODE -ne 0){throw 'Display helper compile failed'}
$examples=[ordered]@{i2c_sensor='environment';pcf8591_mirror='pcf8591';ds18b20_read='ds18b20';mpu6050_oled='mpu6050';altimeter_oled='altimeter'}
foreach($name in $examples.Keys){
 $argsForCompiler=$base+@((Join-Path $source ($name+'.c')))
 foreach($dep in @('sensor_display','ssd1306','ed_font5x7','i2c_bus','ed_delay',$examples[$name])){$argsForCompiler+=Join-Path $output ($dep+'.rel')}
 & $Compiler @argsForCompiler '-o' (Join-Path $output ($name+'.ihx'));if($LASTEXITCODE -ne 0){throw "Link failed: $name"}
 $map=Get-Content (Join-Path $output ($name+'.map')) -Raw;if($map -match 'Undefined Global|Couldn.t find library'){throw "Unresolved symbol: $name"}
 Copy-Item -LiteralPath (Join-Path $output ($name+'.ihx')) -Destination (Join-Path $source ($name+'.hex'))
 Write-Output "Built $name (12 MHz, <=8 KiB Flash, no XDATA)"
}
