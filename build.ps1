$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$buildRoot = Join-Path $projectRoot 'build'
$classesDir = Join-Path $buildRoot 'classes'
$jarDir = Join-Path $buildRoot 'package-input'
$distDir = Join-Path $projectRoot 'dist'
$appImageDir = Join-Path $distDir 'Expert-System'
$jarPath = Join-Path $jarDir 'Expert-System.jar'

$javac = Get-Command javac -ErrorAction Stop
$jar = Get-Command jar -ErrorAction Stop
$jpackage = Get-Command jpackage -ErrorAction Stop

New-Item -ItemType Directory -Force -Path $classesDir, $jarDir, $distDir | Out-Null
Get-ChildItem -LiteralPath $classesDir -Force -ErrorAction SilentlyContinue |
    Remove-Item -Recurse -Force
Get-ChildItem -LiteralPath $jarDir -Force -ErrorAction SilentlyContinue |
    Remove-Item -Recurse -Force

$sources = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -File |
    ForEach-Object { $_.FullName }
if (-not $sources) {
    throw 'No Java source files were found in src.'
}

& $javac.Source -encoding UTF-8 -d $classesDir @sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

@('Main-Class: MainGUI', '') | Set-Content -LiteralPath (Join-Path $buildRoot 'MANIFEST.MF') -Encoding ascii
& $jar.Source --create --file $jarPath --manifest (Join-Path $buildRoot 'MANIFEST.MF') -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE."
}
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $distDir 'Expert-System.jar') -Force

if (Test-Path -LiteralPath $appImageDir) {
    Remove-Item -LiteralPath $appImageDir -Recurse -Force
}
& $jpackage.Source --type app-image --name 'Expert-System' --input $jarDir `
    --main-jar 'Expert-System.jar' --main-class MainGUI --dest $distDir
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

Write-Host "Executable JAR: $(Join-Path $distDir 'Expert-System.jar')"
Write-Host "Windows app:    $(Join-Path $appImageDir 'Expert-System.exe')"
