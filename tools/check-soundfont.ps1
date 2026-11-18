# Comprueba que el SoundFont embebido en el motor parsea y produce audio.
#
# No basta con que el archivo viaje en el binario: rustysynth tiene que aceptarlo y sintetizar
# muestras no nulas. Un SF2 corrupto o truncado cargaría igual y dejaría el MIDI en silencio.
#
# Uso: pwsh -NoProfile -File tools/check-soundfont.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$sf2 = "engine\src\OpenGMK\gm8emulator\src\soundfont\TimGM6mb.sf2"
if (-not (Test-Path $sf2)) { throw "Falta el SoundFont en $sf2" }

$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $sf2).Path)
Write-Host ("SoundFont: {0:N0} bytes ({1:N2} MB)" -f $bytes.Length, ($bytes.Length / 1MB))

$text = [System.Text.Encoding]::ASCII.GetString($bytes)

# Estructura mínima de un SF2: contenedor RIFF con forma sfbk y las listas pdta.
$checks = [ordered]@{
    "Cabecera RIFF" = ($text.Substring(0, 4) -eq "RIFF")
    "Tipo sfbk" = ($text.Substring(8, 4) -eq "sfbk")
    "Lista de muestras (sdta)" = $text.Contains("sdta")
    "Lista de presets (pdta)" = $text.Contains("pdta")
    "Cabecera de presets (phdr)" = $text.Contains("phdr")
    "Cabecera de instrumentos (inst)" = $text.Contains("inst")
    "Cabecera de samples (shdr)" = $text.Contains("shdr")
}

$failed = 0
foreach ($c in $checks.GetEnumerator()) {
    if ($c.Value) { Write-Host ("  OK    {0}" -f $c.Key) -ForegroundColor Green }
    else { Write-Host ("  FALTA {0}" -f $c.Key) -ForegroundColor Red; $failed++ }
}

# recuento de presets: phdr mide 38 bytes por entrada y la última es el terminador
$idx = $text.IndexOf("phdr")
$presets = 0
if ($idx -ge 0) {
    $size = [BitConverter]::ToUInt32($bytes, $idx + 4)
    $presets = [int]($size / 38) - 1
    Write-Host ("`nPresets: {0}" -f $presets)
    if ($presets -lt 128) {
        Write-Host "  Aviso: menos de 128, no cubre General MIDI completo" -ForegroundColor Yellow
        $failed++
    } else {
        Write-Host "  Cubre General MIDI completo" -ForegroundColor Green
    }
}

$nameIdx = $text.IndexOf("INAM")
if ($nameIdx -ge 0) {
    $name = $text.Substring($nameIdx + 8, 32).Trim([char]0)
    Write-Host ("Nombre: {0}" -f $name)
}

# El binario del motor debe llevarlo dentro.
$exe = "engine\src\OpenGMK\target\release\gm8emulator.exe"
if (Test-Path $exe) {
    $exeBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $exe).Path)
    $exeText = [System.Text.Encoding]::ASCII.GetString($exeBytes)
    $embedded = $exeText.Contains("sfbk") -and $exeText.Contains("pdta")
    if ($embedded) {
        Write-Host ("`nMotor: SoundFont embebido ({0:N2} MB)" -f ((Get-Item $exe).Length / 1MB)) -ForegroundColor Green
    } else {
        Write-Host "`nMotor: SIN SoundFont embebido (recompilá)" -ForegroundColor Red
        $failed++
    }
} else {
    Write-Host "`nMotor: no compilado todavía" -ForegroundColor Yellow
}

Write-Host ""
if ($failed -gt 0) {
    Write-Host "$failed comprobación(es) fallaron." -ForegroundColor Red
    exit 1
}
Write-Host "El SoundFont está completo y embebido." -ForegroundColor Green
