param(
    [int]$EnginePid,
    [string]$IconPath,
    [string]$LogPath,
    [string]$AppExe,
    [switch]$DarkTitleBar
)

Add-Type -AssemblyName System.Drawing

Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class ReloadedWin {
    public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern bool SetWindowText(IntPtr hWnd, string text);
    [DllImport("user32.dll")] public static extern IntPtr SendMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
}
"@

$icon = [IntPtr]::Zero
$bitmap = $null
if (Test-Path $IconPath) {
    $bitmap = [System.Drawing.Bitmap]::FromFile($IconPath)
    $icon = $bitmap.GetHicon()
}

$title = "Reloaded OpenGMK"
$iconBig = [IntPtr]1
$iconSmall = [IntPtr]0
$wmSetIcon = 0x0080

# Atributos de DWM: 20 (o 19 en Windows 10 antiguo) para la barra oscura, 35/34 para el color de
# la barra y del borde en Windows 11. Se aplican junto con el titulo porque la ventana del motor
# se crea despues de arrancar y DWM necesita el handle ya visible.
$dwmaDark = 20
$dwmaDarkOld = 19
$dwmaCaption = 35
$dwmaBorder = 34

function Set-DarkTitleBar([IntPtr]$hWnd) {
    $on = 1
    $ok = ([ReloadedWin]::DwmSetWindowAttribute($hWnd, $dwmaDark, [ref]$on, 4) -eq 0)
    if (-not $ok) {
        $null = [ReloadedWin]::DwmSetWindowAttribute($hWnd, $dwmaDarkOld, [ref]$on, 4)
    }
    $caption = 0x00202020
    $null = [ReloadedWin]::DwmSetWindowAttribute($hWnd, $dwmaCaption, [ref]$caption, 4)
    $border = 0x003D3D3D
    $null = [ReloadedWin]::DwmSetWindowAttribute($hWnd, $dwmaBorder, [ref]$border, 4)
}

function Update-Windows {
    $cb = [ReloadedWin+EnumProc]{
        param($hWnd, $lParam)
        if ([ReloadedWin]::IsWindowVisible($hWnd)) {
            $owner = 0
            [void][ReloadedWin]::GetWindowThreadProcessId($hWnd, [ref]$owner)
            $sb = New-Object System.Text.StringBuilder 512
            [void][ReloadedWin]::GetWindowText($hWnd, $sb, 512)
            $text = $sb.ToString()
            if ($owner -eq $EnginePid -or $text.Contains("gm8emulator")) {
                [void][ReloadedWin]::SetWindowText($hWnd, $title)
                if ($icon -ne [IntPtr]::Zero) {
                    [void][ReloadedWin]::SendMessage($hWnd, $wmSetIcon, $iconBig, $icon)
                    [void][ReloadedWin]::SendMessage($hWnd, $wmSetIcon, $iconSmall, $icon)
                }
                if ($DarkTitleBar) {
                    Set-DarkTitleBar $hWnd
                }
            }
        }
        return $true
    }
    [void][ReloadedWin]::EnumWindows($cb, [IntPtr]::Zero)
}

$process = Get-Process -Id $EnginePid -ErrorAction SilentlyContinue
while ($process -ne $null -and -not $process.HasExited) {
    Update-Windows
    Start-Sleep -Milliseconds 1000
    $process.Refresh()
}

$code = "desconocido"
if ($process -ne $null) {
    try { $code = $process.ExitCode } catch { }
}

if ($LogPath) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::AppendAllText($LogPath, "Motor terminado con código $code`r`n", $utf8)
}

if ($bitmap -ne $null) {
    $bitmap.Dispose()
}

Start-Sleep -Milliseconds 800
if ($AppExe -and (Test-Path -LiteralPath $AppExe)) {
    $name = [System.IO.Path]::GetFileNameWithoutExtension($AppExe)
    if (-not (Get-Process -Name $name -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $AppExe
    }
}
