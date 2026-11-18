# Compila y empaqueta Reloaded OpenGMK en local (Windows).
#
# Uso:
#   .\tools\build-local.ps1              # motor + MSI + NSIS
#   .\tools\build-local.ps1 -App         # solo la app (motor ya copiado)
#   .\tools\build-local.ps1 -Engine      # solo el motor
#   .\tools\build-local.ps1 -Run         # compila y abre la app al terminar
#   .\tools\build-local.ps1 -Clean       # limpia artefactos antes de compilar
#
# Sustituye al workflow de GitHub Actions para el trabajo diario: ~2-3 min frente
# a los ~11 de un runner, y permite probar antes de subir nada.

param(
    [switch]$App,
    [switch]$Engine,
    [switch]$Run,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$rustupHome = "C:\Users\$env:USERNAME\scoop\persist\rustup-msvc\.rustup"
$cargoHome = "C:\Users\$env:USERNAME\scoop\persist\rustup-msvc\.cargo"
$jdk = "C:\Users\$env:USERNAME\scoop\apps\temurin21-jdk\current"

$env:RUSTUP_HOME = $rustupHome
$env:CARGO_HOME = $cargoHome
$env:JAVA_HOME = $jdk
foreach ($p in @("$cargoHome\bin", "$jdk\bin")) {
    if (($env:PATH -split ';') -notcontains $p) { $env:PATH = "$p;$env:PATH" }
}

# Sin -App ni -Engine se hace todo.
$doEngine = $Engine -or (-not $App)
$doApp = $App -or (-not $Engine)

if ($Clean) {
    Write-Host "Limpiando artefactos..." -ForegroundColor Yellow
    Get-Process reloaded-opengmk -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    foreach ($dir in @("$root\desktopApp\build", "$root\shared\build", "$root\build")) {
        Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($doEngine) {
    $engineDir = Join-Path $root "engine\src\OpenGMK"
    Write-Host "`n=== Motor (Rust) ===" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Push-Location $engineDir
    try {
        cargo build --release -p gm8emulator --features backend-wgpu
        if ($LASTEXITCODE -ne 0) { throw "El motor no compiló" }
    } finally {
        Pop-Location
    }
    $sw.Stop()

    $built = Join-Path $engineDir "target\release\gm8emulator.exe"
    if (-not (Test-Path $built)) { throw "El motor no generó gm8emulator.exe" }
    Copy-Item $built (Join-Path $root "desktopApp\src\main\resources\gm8emulator.exe") -Force
    $size = [math]::Round((Get-Item $built).Length / 1MB, 1)
    Write-Host ("Motor listo: {0} MB en {1:0.0}s" -f $size, $sw.Elapsed.TotalSeconds) -ForegroundColor Green
}

if ($doApp) {
    # La app no se puede recompilar con la ventana abierta: Windows bloquea los
    # archivos del runtime empaquetado.
    Get-Process reloaded-opengmk -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

    Write-Host "`n=== App (Gradle) ===" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & .\gradlew.bat --no-daemon :desktopApp:packageMsi :desktopApp:packageNsis
    if ($LASTEXITCODE -ne 0) { throw "El empaquetado falló" }
    $sw.Stop()

    $bin = Join-Path $root "desktopApp\build\compose\binaries\main"
    $msi = Get-ChildItem "$bin\msi" -Filter *.msi -ErrorAction SilentlyContinue | Select-Object -First 1
    $nsis = Get-ChildItem "$bin\nsis" -Filter *.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    Write-Host ("`nInstaladores listos en {0:0.0}s:" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    if ($msi) { Write-Host ("  MSI : {0} ({1} MB)" -f $msi.FullName, [math]::Round($msi.Length / 1MB, 1)) }
    if ($nsis) { Write-Host ("  NSIS: {0} ({1} MB)" -f $nsis.FullName, [math]::Round($nsis.Length / 1MB, 1)) }

    if ($Run) {
        $appExe = Join-Path $bin "app\reloaded-opengmk\reloaded-opengmk.exe"
        if (Test-Path $appExe) {
            Write-Host "`nAbriendo la app..." -ForegroundColor Cyan
            Start-Process -FilePath $appExe
        } else {
            Write-Warning "No encontré la distribución en $appExe"
        }
    }
}

Write-Host "`nListo." -ForegroundColor Green
