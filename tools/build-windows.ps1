param(
    [string]$EngineRepo = "..\pokemon-reloaded-experimental"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$engine = Join-Path $EngineRepo "src\OpenGMK"
if (-not (Test-Path $engine)) {
    throw "No encontré el motor en $engine"
}

Push-Location $engine
try {
    cargo build --release -p gm8emulator
} finally {
    Pop-Location
}

$built = Join-Path $engine "target\release\gm8emulator.exe"
if (-not (Test-Path $built)) {
    throw "El motor no generó gm8emulator.exe"
}

Copy-Item $built (Join-Path $root "desktopApp\src\main\resources\gm8emulator.exe") -Force
Push-Location $root
try {
    .\gradlew.bat :desktopApp:packageMsi :desktopApp:packageNsis
} finally {
    Pop-Location
}

Write-Host "Listo: revisa desktopApp\build\compose\binaries\main\msi y \nsis"
