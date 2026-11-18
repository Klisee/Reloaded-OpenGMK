# Compilar en Windows

Guía del entorno local. El repositorio ya trae el motor vendorizado en
`engine/src/OpenGMK`, así que **no hace falta clonar nada aparte**.

## Requisitos

| Herramienta | Versión | Instalación |
| --- | --- | --- |
| JDK | 21 | `scoop install temurin21-jdk` |
| Rust | 1.99.0 (pin del repo) | `scoop install rustup-msvc` |
| CMake | 4.x | `scoop install cmake` |
| Visual Studio | 2022/2026 con *C++ build tools* + Windows SDK | ya instalado |
| Android SDK | sólo para Android | ya instalado |

El proyecto fija Rust con `engine/src/OpenGMK/rust-toolchain.toml`, así que
`rustup` descarga sola la versión correcta la primera vez.

## Preparar la sesión

Scoop escribe `JAVA_HOME`, `RUSTUP_HOME` y `CARGO_HOME` a nivel de usuario,
pero las terminales **ya abiertas no las heredan**. Cargá el entorno:

```powershell
. .\tools\win-env.ps1
```

## Compilar todo (motor + instaladores)

```powershell
.\tools\build-windows.ps1
```

Eso hace tres cosas: compila el motor Rust, copia `gm8emulator.exe` a
`desktopApp/src/main/resources/` y genera MSI y NSIS.

Salida:

```
desktopApp\build\compose\binaries\main\msi\*.msi
desktopApp\build\compose\binaries\main\nsis\*.exe
```

## Sólo el motor

```powershell
cd engine\src\OpenGMK
cargo build --release -p gm8emulator --features backend-wgpu
```

Tarda ~3-5 min en frío y segundos en incremental. Deja el binario en
`engine\src\OpenGMK\target\release\gm8emulator.exe`.

## Sólo la app de escritorio

Con el motor ya copiado a `desktopApp/src/main/resources/`:

```powershell
.\gradlew.bat :desktopApp:packageMsi :desktopApp:packageNsis
```

Para probarla sin empaquetar:

```powershell
.\gradlew.bat :desktopApp:run
```

## Notas de Windows

- **`Documents` está bloqueada por Defender** (Controlled Folder Access).
  Por eso el repo vive en `C:\Users\Eduardo\src\Reloaded-OpenGMK`. No clones
  ni compiles dentro de `Documents`, `Desktop` ni `Downloads`.
- El archivo `desktopApp/src/main/resources/gm8emulator` (sin extensión) es
  el binario **ELF de Linux** versionado en el repo. En Windows el que se usa
  es `gm8emulator.exe`, que está en `.gitignore` porque es un artefacto.
- `local.properties` apunta al Android SDK y está en `.gitignore`.
- Si `git push` pide credenciales, corré `gh auth setup-git`.
