# Mide el color real de la ventana del launcher para comprobar el modo oscuro.
#
# No se fía del log ni de la configuración: captura la ventana y promedia el
# brillo de una zona de fondo. Un tema oscuro ronda 20-40 de brillo; uno claro,
# 230-250.
#
# Uso: pwsh -NoProfile -File tools/check-theme.ps1

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Theme {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$proc = Get-Process reloaded-opengmk -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle } | Select-Object -First 1
if (-not $proc) { throw "La app no está abierta." }

$hwnd = $proc.MainWindowHandle
if ($hwnd -eq [IntPtr]::Zero) { throw "La ventana todavía no tiene handle." }

# Traerla al frente para que no la tape otra ventana al capturar.
[Win32Theme]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
[Win32Theme]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 900

$rect = New-Object Win32Theme+RECT
[Win32Theme]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top
if ($w -le 0 -or $h -le 0) { throw "La ventana no tiene tamaño válido." }
Write-Host ("Ventana: {0}x{1} en ({2},{3})" -f $w, $h, $rect.Left, $rect.Top)

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bmp.Size)
$gfx.Dispose()

# Zonas de muestreo, en fracciones del tamaño de la ventana. Se evitan el texto
# y las tarjetas con imagen: interesa el fondo de la ventana.
$zones = @(
    @{ Name = "Fondo bajo la lista lateral"; X = 0.09; Y = 0.82 },
    @{ Name = "Fondo central vacío";         X = 0.45; Y = 0.85 },
    @{ Name = "Barra superior";              X = 0.55; Y = 0.055 }
)

$lumas = @()
foreach ($zone in $zones) {
    $cx = [int]($w * $zone.X)
    $cy = [int]($h * $zone.Y)
    $sum = 0.0
    $n = 0
    # Promedio de un bloque de 20x20 para no depender de un píxel suelto.
    for ($dx = -10; $dx -lt 10; $dx++) {
        for ($dy = -10; $dy -lt 10; $dy++) {
            $px = [Math]::Min([Math]::Max($cx + $dx, 0), $w - 1)
            $py = [Math]::Min([Math]::Max($cy + $dy, 0), $h - 1)
            $c = $bmp.GetPixel($px, $py)
            # Luminancia perceptual (Rec. 709).
            $sum += 0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B
            $n++
        }
    }
    $luma = $sum / $n
    $lumas += $luma
    Write-Host ("  {0,-32} brillo {1,6:0.0}" -f $zone.Name, $luma)
}
$bmp.Dispose()

$avg = ($lumas | Measure-Object -Average).Average
Write-Host ""
Write-Host ("Brillo medio: {0:0.0}" -f $avg)

if ($avg -lt 90) {
    Write-Host "Resultado: OSCURO" -ForegroundColor Green
    exit 0
} elseif ($avg -gt 170) {
    Write-Host "Resultado: CLARO" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "Resultado: intermedio (revisar a ojo)" -ForegroundColor Magenta
    exit 2
}
