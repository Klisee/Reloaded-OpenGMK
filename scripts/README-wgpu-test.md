# Probar el backend wgpu sin GPU real

`scripts/test-wgpu.sh` compila `gm8emulator` con la feature `backend-wgpu`, lo
ejecuta bajo Xvfb con `OPENGMK_RENDERER=gl` y/o `wgpu`, y analiza los logs. No
arranca el juego completo desde el launcher ni necesita una GPU: en Linux se
apoya en **lavapipe** (Vulkan software) y **llvmpipe** (OpenGL software).

Códigos de salida:

| Código | Significado |
|---|---|
| `0` | Todos los backends pedidos arrancaron y no hubo panic. |
| `1` | Falló la compilación o algún backend no arrancó / paniqueó. |
| `2` | Faltan dependencias o los argumentos no son válidos. |

Dependencias que el script comprueba:

- `cargo`, `xvfb-run` y `vulkaninfo`: obligatorios (si falta alguno, sale con
  `2`).
- ICD de lavapipe (`/usr/share/vulkan/icd.d/lvp_icd*.json`): si falta, avisa;
  puede haber una GPU Vulkan real y entonces no es necesario.
- `xauth` y `timeout`: si faltan solo avisa (`timeout` de coreutils acota la
  ejecución; sin él el test no tiene límite de tiempo).

## Uso rápido

```bash
# Ambos backends, en secuencia (por defecto)
scripts/test-wgpu.sh --exe "/ruta/al/juego.exe"

# Solo wgpu
scripts/test-wgpu.sh --exe "/ruta/al/juego.exe" --renderer wgpu

# Solo OpenGL, como referencia
scripts/test-wgpu.sh --exe "/ruta/al/juego.exe" --renderer gl

# Más tiempo de ejecución (600 fotogramas ≈ 20 s a 30 pasos/s)
scripts/test-wgpu.sh --exe "/ruta/al/juego.exe" --renderer wgpu --frames 600
```

Opciones:

- `--exe <ruta>` — obligatorio; es el `.exe` de GM8 que se pasa al motor.
- `--frames <N>` — objetivo de fotogramas (por defecto `300`). El motor no
  tiene una opción para salir tras N fotogramas, así que el script lo traduce a
  un tiempo máximo: `N/30 s + 20 s` de arranque. `timeout` termina el proceso y
  el código `124` se considera normal (el juego seguía vivo).
- `--renderer gl|wgpu` — se puede repetir; sin esta opción se ejecutan ambos.
- `-h`, `--help` — ayuda.

El script compila **una sola vez** (`cargo build --release -p gm8emulator
--features backend-wgpu`) y luego ejecuta el binario una vez por backend.

## Instalación de dependencias (Pop!_OS / Ubuntu)

```bash
sudo apt update
sudo apt install mesa-vulkan-drivers vulkan-tools xvfb libvulkan-dev
```

- `mesa-vulkan-drivers` — incluye el ICD de lavapipe
  (`/usr/share/vulkan/icd.d/lvp_icd*.json`).
- `vulkan-tools` — aporta `vulkaninfo`, que el script usa para comprobar qué
  dispositivos Vulkan hay.
- `xvfb` — servidor X virtual; el script lanza el motor con `xvfb-run`.
  En algunas versiones también hace falta `xauth` (`sudo apt install xauth`).
- `libvulkan-dev` — cabeceras y loader de Vulkan; el motor las necesita para
  compilar wgpu con el backend Vulkan.

Además hace falta Rust (rustup). El repo fija la toolchain en
`engine/src/OpenGMK/rust-toolchain.toml` (1.99.0), así que rustup la descarga
sola la primera vez. La primera compilación en release con LTO tarda varios
minutos.

Verificación rápida:

```bash
vulkaninfo --summary
```

Debe listar al menos un dispositivo. Con lavapipe aparece algo como
`deviceName = llvmpipe (LLVM 19.1.7, 256 bits)` y `driverName = llvmpipe`.

## Forzar lavapipe (Vulkan software)

El script lo hace solo cuando `vulkaninfo` no ve una GPU real, pero se puede
forzar a mano antes de lanzarlo:

```bash
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json
export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/lvp_icd.json

# Comprobar que el loader solo ve lavapipe
vulkaninfo --summary
```

- Los loaders modernos (Vulkan 1.3.207+) prefieren `VK_DRIVER_FILES`.
- Los antiguos solo entienden `VK_ICD_FILENAMES`.
- Definir ambas variables es lo más portable; el script respeta cualquiera de
  las dos si ya vienen definidas en el entorno.
- También existe `OPENGMK_TEST_FORCE_LAVAPIPE=1`, que hace que el script
  exporte ambas variables aunque detecte una GPU real.

En algunas distros el archivo se llama `lvp_icd.x86_64.json`; localízalo con:

```bash
ls /usr/share/vulkan/icd.d/
```

## Forzar llvmpipe (OpenGL software)

```bash
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
```

- `LIBGL_ALWAYS_SOFTWARE=1` obliga a Mesa a usar un rasterizador software.
- `GALLIUM_DRIVER=llvmpipe` elige explícitamente llvmpipe (frente a
  `softpipe`, más lento todavía).
- Los mismos ajustes valen para el backend GL de wgpu cuando Vulkan no está
  disponible.
- El script exporta ambas variables con `OPENGMK_TEST_FORCE_LLVMPIPE=1`.

## Qué salida se espera

La línea que importa la escribe `wgpu_backend/mod.rs` en **stderr** al crear el
renderer:

```text
wgpu: backend Vulkan | dispositivo: llvmpipe (LLVM 19.1.7, 256 bits) | tipo: Cpu | driver: llvmpipe | API: 1.4.305
wgpu: surface 1280x720 | formato Bgra8Unorm | present AutoNoVsync | alpha Auto
```

Con eso, el script imprime:

```text
--- Resumen: wgpu ---
  Log stderr:      /tmp/opengmk-wgpu-test-.../wgpu.log
  Log stdout:      /tmp/opengmk-wgpu-test-.../wgpu.stdout.log
  Estado proceso:  124 (tiempo agotado: el motor seguía vivo, es lo normal)
  ¿wgpu arrancó?:  SÍ
    wgpu: backend Vulkan | dispositivo: llvmpipe (LLVM 19.1.7, 256 bits) | ...
    API elegida:   Vulkan
  ...
  Líneas [render]: 12
    última:        [render] 30 dibujos/s (30 pasos/s, room 30)
```

Si wgpu funciona pero **cae a OpenGL** (Vulkan no disponible), la línea cambia
de `Vulkan` a `Gl` y el script lo señala:

```text
wgpu: backend Gl | dispositivo: llvmpipe (...) | tipo: Cpu | driver: llvmpipe | API: OpenGL ES 3.2 Mesa ...
    Aviso:         wgpu cayó a OpenGL/GLES (Vulkan no disponible)
```

En ese caso el script **no** falla: el backend wgpu arrancó, aunque no por
Vulkan. Si lo que se quiere validar es Vulkan concretamente, hay que revisar la
línea `wgpu: backend ...` o forzar lavapipe y repetir.

Para OpenGL (`--renderer gl`) el motor imprime:

```text
creating graphics context
  > GL_VERSION: "4.5 (Core Profile) Mesa 25.0.7"
  > GL_VENDOR: "Mesa"
```

Fallos típicos:

- **No aparece ninguna línea `wgpu: backend ...`**: el renderer no llegó a
  crearse. El script marca `¿wgpu arrancó?: NO` y sale con `1`.
- **`panicked at ... not yet implemented`**: el backend wgpu está incompleto
  (hoy `SpritePipeline::new` y varios métodos del trait son `todo!()`). El
  script lo detecta como panic y sale con `1`.
- **`wgpu: no hay adaptador compatible` / `no se pudo crear la surface`**: no
  hay ICD utilizable o Xvfb no está bien configurado. Revisar `vulkaninfo`.
- **`Failed to launch game` / `Runtime error:`**: el problema es del juego o de
  sus assets, no del renderer.

## Logs

Cada ejecución crea un directorio nuevo en `${TMPDIR:-/tmp}`:

```text
${TMPDIR:-/tmp}/opengmk-wgpu-test-YYYYmmdd-HHMMSS-PID/
├── build.log            # salida de cargo
├── gl.log               # stderr del motor con OPENGMK_RENDERER=gl
├── gl.stdout.log        # stdout del motor
├── wgpu.log             # stderr del motor con OPENGMK_RENDERER=wgpu
└── wgpu.stdout.log      # stdout del motor
```

Las líneas `wgpu:`, los avisos `[render]` (se activan con
`OPENGMK_RENDER_LOG=1`, que el script ya exporta) y los panics van a
`<backend>.log`. Al terminar, el script imprime las rutas.

## Limitaciones conocidas

- **lavapipe y llvmpipe son software**: van por CPU y son mucho más lentos que
  una GPU. Los FPS que se vean en `[render]` no sirven para medir rendimiento
  real ni para comparar wgpu contra OpenGL.
- **El backend wgpu está en desarrollo**: si un método sigue en `todo!()`, el
  test fallará con panic aunque el adaptador Vulkan se haya elegido bien. Eso
  es precisamente lo que el script detecta.
- **`--frames` es aproximado**: el motor no ofrece "salir tras N fotogramas",
  así que el script corta por tiempo (`N/30 s + 20 s`). Un juego con rooms a
  otra velocidad o con pantallas de carga puede no llegar a N fotogramas.
- **Xvfb no tiene vsync ni GPU**: `present AutoNoVsync`/`AutoVsync` y los
  formatos son los que ofrezca el driver software; no reflejan lo que hará una
  GPU real.
- **La primera compilación es lenta** (release + LTO). Las siguientes
  reutilizan `engine/src/OpenGMK/target`.
- **No valida píxeles**: solo comprueba que el renderer arranca y no paniquea.
  Una paridad visual GL vs wgpu requiere capturar frames y compararlos aparte.
- **Audio/entrada**: bajo Xvfb no hay dispositivos reales; pueden aparecer
  errores de ALSA o de gamepad en el log que no afectan al resultado (el script
  solo falla por panic o error fatal de arranque).
- Los logs se acumulan en `$TMPDIR`; bórralos cuando ya no los necesites.
