# Verifica que el MSI recién generado tenga todo lo nuevo.
# Uso: pwsh -NoProfile -File tools/verify-msi.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$msi = Get-ChildItem "desktopApp\build\compose\binaries\main\msi\*.msi" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $msi) { throw "No hay ningún MSI en desktopApp\build\compose\binaries\main\msi" }

Write-Host "MSI: $($msi.Name)" -ForegroundColor Cyan
Write-Host ("     {0} MB  {1}" -f [math]::Round($msi.Length / 1MB, 1), $msi.LastWriteTime)
Write-Host ""

# El MSI es una base de datos OLE. En vez de parsearlo, se comprueba la entrada
# que le da de comer: la distribución de createDistributable, que viaja dentro.
$appDir = "desktopApp\build\compose\binaries\main\app\reloaded-opengmk"
if (-not (Test-Path $appDir)) { throw "Falta la distribución; empaquetá primero con tools\build-local.ps1 -App" }

$checks = [ordered]@{}
$checks["Ejecutable del launcher"] = Test-Path "$appDir\reloaded-opengmk.exe"
$checks["Runtime de Java (jlink)"] = Test-Path "$appDir\runtime\lib\modules"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Get-ChildItem "$appDir\app" -Filter "desktopApp-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
$checks["Jar de la app"] = $null -ne $jar

$hasTitleBar = $false
$engineSize = 0
# AppSettings vive en el jar de `shared`, no en el de `desktopApp`: el contrato y
# su implementación de escritorio están en el módulo compartido.
$settingsClass = $false
$settingsJar = Get-ChildItem "$appDir\app" -Filter "shared-desktop-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($settingsJar) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($settingsJar.FullName)
    try {
        $settingsClass = ($zip.Entries | Where-Object { $_.FullName -match "AppSettings\.class" } | Measure-Object).Count -gt 0
    } finally {
        $zip.Dispose()
    }
}

if ($jar) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $hasTitleBar = ($zip.Entries | Where-Object { $_.FullName -match "WindowsDarkTitleBar" } | Measure-Object).Count -gt 0
        $engine = $zip.Entries | Where-Object { $_.Name -eq "gm8emulator.exe" }
        if ($engine) { $engineSize = $engine.Length }
    } finally {
        $zip.Dispose()
    }
}

$checks["Modo oscuro (WindowsDarkTitleBar)"] = $hasTitleBar
$checks["Ajustes (AppSettings)"] = $settingsClass
$checks["Motor gm8emulator.exe (14 MB)"] = $engineSize -gt 10MB

$failed = 0
foreach ($entry in $checks.GetEnumerator()) {
    if ($entry.Value) {
        Write-Host ("  OK   {0}" -f $entry.Key) -ForegroundColor Green
    } else {
        Write-Host ("  FALTA {0}" -f $entry.Key) -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
if ($engineSize -gt 0) {
    Write-Host ("Motor dentro del jar: {0:N1} MB" -f ($engineSize / 1MB))
}
if ($failed -gt 0) {
    Write-Host "$failed comprobación(es) fallaron." -ForegroundColor Red
    exit 1
}
Write-Host "El paquete está completo." -ForegroundColor Green
