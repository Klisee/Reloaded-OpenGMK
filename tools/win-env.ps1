# Entorno de compilación para Windows.
# Uso:  . .\tools\win-env.ps1
#
# Carga Rust (scoop), JDK 21 (scoop) y CMake en la sesión actual.
# Necesario porque Scoop escribe las variables a nivel de usuario y las
# terminales ya abiertas no las heredan.

$ErrorActionPreference = "Stop"

$rustupHome = "C:\Users\$env:USERNAME\scoop\persist\rustup-msvc\.rustup"
$cargoHome = "C:\Users\$env:USERNAME\scoop\persist\rustup-msvc\.cargo"
$jdk = "C:\Users\$env:USERNAME\scoop\apps\temurin21-jdk\current"

if (-not (Test-Path $cargoHome)) { throw "No encontré Rust en $cargoHome. Instalá con: scoop install rustup-msvc" }
if (-not (Test-Path $jdk)) { throw "No encontré el JDK 21 en $jdk. Instalá con: scoop install temurin21-jdk" }

$env:RUSTUP_HOME = $rustupHome
$env:CARGO_HOME = $cargoHome
$env:JAVA_HOME = $jdk

$prepend = @("$cargoHome\bin", "$jdk\bin")
foreach ($p in $prepend) {
    if (($env:PATH -split ';') -notcontains $p) { $env:PATH = "$p;$env:PATH" }
}

Write-Host "Rust  : $(rustc --version)"
Write-Host "Cargo : $(cargo --version)"
Write-Host "Java  : $(& "$jdk\bin\java.exe" -version 2>&1 | Select-Object -First 1)"
Write-Host "CMake : $(cmake --version | Select-Object -First 1)"
Write-Host ""
Write-Host "Listo. Ahora podés compilar:"
Write-Host "  .\tools\build-windows.ps1"
