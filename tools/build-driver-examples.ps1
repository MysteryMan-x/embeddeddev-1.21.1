param([string]$Compiler='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
if(-not $Compiler){$installed=Get-Command sdcc -ErrorAction SilentlyContinue;$Compiler=if($installed){$installed.Source}else{Join-Path $projectRoot 'build/tools/sdcc/sdcc/bin/sdcc.exe'}}
$source=Join-Path $projectRoot 'examples/drivers'
$output=Join-Path $projectRoot 'build/driver-examples'
New-Item -ItemType Directory -Path $output -Force|Out-Null
$base=@('-mmcs51','--model-small','--iram-size','256','--code-size','8192','--xram-size','65536','--opt-code-speed','-I',$source)
$library=Join-Path (Split-Path -Parent (Split-Path -Parent $Compiler)) 'lib/mcs51-small'
if(Test-Path -LiteralPath (Join-Path $library 'mcs51.lib')){$base+=@('-L',$library)}
foreach($file in Get-ChildItem -LiteralPath $source -Filter '*.c'){
    & $Compiler @base '-c' $file.FullName '-o' (Join-Path $output ($file.BaseName+'.rel'))
    if($LASTEXITCODE -ne 0){throw "Compile failed: $($file.Name)"}
}
$dependencies=[ordered]@{
    mpu6050=@('mpu6050','i2c_bus');altimeter=@('altimeter','i2c_bus');motor_driver=@('motor_driver','i2c_bus')
    pcf8591=@('pcf8591','i2c_bus');environment=@('environment','i2c_bus');gpio_panel=@('gpio_panel')
    seven_segment=@('seven_segment');gpio_redstone=@('gpio_redstone');ds1302=@('ds1302');ds18b20=@('ds18b20','ed_delay');ssd1306=@('ssd1306','ed_font5x7','i2c_bus')
}
foreach($name in $dependencies.Keys){
    $arguments=$base+@((Join-Path $source "demos/${name}_demo.c"))
    foreach($dep in $dependencies[$name]){$arguments+=Join-Path $output ($dep+'.rel')}
    $arguments+=@('-o',(Join-Path $output ('demo_'+$name+'.ihx')))
    & $Compiler @arguments
    if($LASTEXITCODE -ne 0){throw "Link failed: $name"}
    $map=Get-Content -LiteralPath (Join-Path $output ('demo_'+$name+'.map')) -Raw
    if($map -match 'Undefined Global|Couldn.t find library'){throw "Unresolved symbol: $name"}
    Write-Output "Compiled $name (8051, 8 KiB Flash limit, 256-byte IRAM)"
}
