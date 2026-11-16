# Backend wgpu (Vulkan): estado y hallazgos

Última actualización: 15 de noviembre de 2026
Rama: `feat/wgpu-backend` (local, **sin push**)
Último commit: `de19f29`

---

## Resumen

El motor OpenGMK ya tiene un **segundo backend de renderizado basado en wgpu**, que corre
sobre Vulkan en Linux, Windows y Android (y GLES como alternativa). El juego arranca, dibuja
el logotipo y el menú principal correctamente, y funciona.

OpenGL sigue siendo el **backend por defecto**: sin variables de entorno, todo se comporta
como antes. wgpu es opt-in.

---

## Cómo usarlo

```bash
# OpenGL (por defecto, comportamiento histórico)
gm8emulator "juego.exe"

# wgpu / Vulkan
OPENGMK_RENDERER=wgpu gm8emulator "juego.exe"

# diagnóstico del backend
OPENGMK_WGPU_DEBUG=1 OPENGMK_RENDERER=wgpu gm8emulator "juego.exe"
```

`OPENGMK_RENDERER` solo acepta `wgpu`; cualquier otro valor (o ninguno) usa OpenGL.

### Compilar

```bash
cd engine/src/OpenGMK
cargo build --release -p gm8emulator --features backend-wgpu
```

Sin `--features backend-wgpu` el backend no existe y el binario no paga su coste
(~4,5 MB de wgpu, que es `MIT OR Apache-2.0`, compatible con la GPL-2.0-only del motor).

### Verificar qué backend se eligió

Al arrancar, con wgpu activo, escribe por stderr:

```
wgpu: backend Vulkan | dispositivo: NVIDIA GeForce RTX 5060 | tipo: DiscreteGpu | driver: NVIDIA | API: 580.173.02
wgpu: surface 320x180 | formato Bgra8Unorm | present AutoNoVsync | alpha Opaque
```

Con OpenGL aparece lo de siempre: `GL_VERSION: ...` / `GL_VENDOR: ...`.

---

## Arquitectura

```
engine/src/OpenGMK/gm8emulator/src/render/
├── render.rs                  # trait RendererTrait (115 métodos) + RenderBackend
├── opengl.rs                  # backend histórico (4258 líneas)
└── wgpu_backend/
    ├── mod.rs                 # device, surface, render pass, batching
    ├── pipeline.rs            # pipeline de sprites, uniforms (608 B), sampler nearest
    ├── shaders.wgsl           # traducción de los shaders GLSL
    ├── atlas.rs               # texturas, alineación de 256 B, dump/restore
    ├── draw.rs                # DrawList, vertex buffers, rect de presentación
    ├── tests.rs               # tests de layout
    ├── trait_impl.rs          # impl del trait que delega en los cuatro módulos
    ├── trait_state.rs         # blending, luces, fog, matrices
    ├── trait_textures.rs      # atlas y sprites
    ├── trait_geometry.rs      # sprites, rects, líneas, elipses, primitivas
    └── trait_surfaces.rs      # present, superficies, píxeles, stored
```

### Selección de backend

`RenderBackend { OpenGl, Wgpu }` en `render.rs`, con:

- `from_env()` — lee `OPENGMK_RENDERER`
- `resolve()` — error claro si pides wgpu sin compilarlo
- `Renderer::new(impl Into<RenderBackend>, ...)` — despacha al backend

### `vendor/ramen`

Se le añadió soporte de `raw-window-handle` 0.6 con la feature opcional `rwh-06`:

- `HasWindowHandle` / `HasDisplayHandle` en `src/window.rs`
- Implementación por plataforma: X11 (`Xlib`), Win32 (`Win32`), Android (`AndroidNdk`)
- Android detecta el puntero obsoleto tras rotar comparando con `AppState`

Verificado compilando también para `x86_64-pc-windows-msvc`.

---

## Hallazgos técnicos importantes

### 1. Vulkan no tiene `TriangleFan`

El juego dibuja casi todo con abanicos de 4 vértices (quads). `wgpu::PrimitiveTopology`
no tiene `TriangleFan` (Vulkan tampoco en core), así que hay que **expandir abanicos y
tiras a listas de triángulos**. Sin eso solo se pinta el primer triángulo de cada quad:
se ve un recorte diagonal en el texto y en los paneles.

Implementado en `trait_geometry.rs` (`expand_triangles`), usando `PrimitiveBuilder::get_ptype()`.

### 2. NDC: GL `[-1,1]` vs Vulkan `[0,1]`

El shader de wgpu corrige la profundidad:

```wgsl
out.pos = vec4<f32>(ndc.xy, (ndc.z + ndc.w) * 0.5, ndc.w);
```

Sin esto el 3D y el z-buffer se comportan distinto que en OpenGL.

### 3. Ejes de textura invertidos

El juego dibuja con proyección de OpenGL (Y hacia arriba) y las texturas de wgpu tienen
el origen arriba-izquierda. En el muestreo hay que invertir la V.

### 4. La instancia necesita `DisplayHandle`

`wgpu::Instance` debe crearse con `InstanceDescriptor::display` (vía
`raw_window_handle`), o `create_surface` falla con
*"No `DisplayHandle` is available to create this surface with"*.

Como `ramen::Window` no implementa `Clone`, se creó un `OwnedDisplay` en `mod.rs` que
posee el `RawDisplayHandle` (con `unsafe impl Send/Sync` documentado).

### 5. **El bug de los menús en negro (el importante)**

Síntoma: al abrir un submenú (Cargar partida, menú del juego, escapar de un Pokémon
salvaje) la pantalla quedaba negra, y un clic extra la arreglaba.

Causa: el juego hace un `present` **extra** sin dibujar nada, solo para re-presentar.
OpenGL conserva el framebuffer entre frames y eso mantiene la imagen. En wgpu **cada
textura del swapchain llega vacía**, así que presentar sin dibujar pinta negro.

Solución (`de19f29`, 5 líneas en `present`):

```rust
// OpenGL conserva el framebuffer entre frames, así que presentar sin haber dibujado deja
// la imagen anterior. En wgpu cada textura del swapchain llega vacía: presentar un frame
// sin contenido lo pintaría de negro.
if r.frame.prepared.is_empty() && r.frame.clears.is_empty() {
    return;
}
```

**Lección**: se perdió mucho tiempo intentando lienzos persistentes, blits y cambios de
clear cuando el problema era solo que un frame vacío no debía presentarse.

### 6. Alineación de 256 bytes

`queue.write_texture` exige `bytes_per_row` múltiplo de 256
(`COPY_BYTES_PER_ROW_ALIGNMENT`). En `atlas.rs` se rellena la fila cuando hace falta
(cubierto por el test `rows_are_padded_to_256_bytes`).

### 7. Uniforms: 608 bytes

`SpriteUniforms` replica todo el estado del shader GLSL: proyección, modelo, color
ambiente, 8 luces, flags (lighting, gouraud, colour blending, alpha test, repeat, lerp,
fog), rango y color de fog, `interp_amount`. Los offsets están verificados con
`offset_of!` en `pipeline.rs`.

---

## Lo que está hecho

- [x] Backend wgpu completo implementando los 115 métodos del trait, sin `todo!()`
- [x] `vendor/ramen` con `raw-window-handle` en las tres plataformas
- [x] Selección por `OPENGMK_RENDERER` con OpenGL por defecto
- [x] Feature de Cargo `backend-wgpu`
- [x] Pipeline de sprites con blending premultiplicado y sampler nearest
- [x] Shaders WGSL portados del GLSL (luces, fog, alpha test, repeat/lerp)
- [x] Atlas con dump/restore para savestates
- [x] Batching y `DrawList`
- [x] 24 tests pasando (`cargo test -p gm8emulator --features backend-wgpu --lib wgpu_backend`)
- [x] El juego arranca, dibuja el logotipo y el menú
- [x] Los submenús ya no se quedan en negro

## Lo que falta

- [ ] **Probar el mundo y el combate** con wgpu (solo se verificaron los menús)
- [ ] Superficies (render-to-texture): `set_target`/`reset_target` no renderizan a una
      textura real; los menús que usan superficies pueden fallar
- [ ] `get_pixels` / `stored_*`: devuelven la copia en CPU, no leen la GPU
- [ ] z-buffer (3D en combate)
- [ ] Filtros CRT y Game Boy (shaders de post-proceso)
- [ ] Escalado del blit si se ve estirado
- [ ] Paridad de píxeles con OpenGL
- [ ] Probar en Android y Windows
- [ ] CI que compile con la feature

---

## Intentos guardados en stash

```
stash@{0}: intentos posteriores (canvas, blit, clear, fan) - no funcionaron
```

Contienen: lienzo persistente, blit escalado, `LoadOp::Clear` y expansión de fan/strip.
Se descartaron porque el fix real era el guard de frames vacíos. **No se perdieron**; si
alguna vez se necesita render-to-texture de verdad, hay material ahí.

Para verlos:

```bash
git stash show -p stash@{0} --stat
git stash pop   # aplicarlos (¡sobrescribe el estado actual!)
```

**Cuidado**: `git stash pop` sobre el estado actual puede reintroducir los bugs.

---

## Comandos útiles

```bash
# ver la rama y los commits
cd "/home/klisee/Documentos/Reloaded OpenGMK"
git log --oneline -5
git branch --show-current

# compilar el motor con wgpu
cd engine/src/OpenGMK
cargo build --release -p gm8emulator --features backend-wgpu

# compilar sin wgpu (como siempre)
cargo build --release -p gm8emulator

# tests del backend
cargo test -p gm8emulator --features backend-wgpu --lib wgpu_backend

# lanzar con Vulkan
cd "$HOME/.local/share/Reloaded OpenGMK/game"
OPENGMK_RENDERER=wgpu \
  "/home/klisee/Documentos/Reloaded OpenGMK/engine/src/OpenGMK/target/release/gm8emulator" \
  "Proyecto Reloaded The Last Beta 1.9.2.2 Full.exe"

# lanzar con OpenGL (como siempre)
cd "$HOME/.local/share/Reloaded OpenGMK/game"
"/home/klisee/Documentos/Reloaded OpenGMK/engine/src/OpenGMK/target/release/gm8emulator" \
  "Proyecto Reloaded The Last Beta 1.9.2.2 Full.exe"
```

Al lanzar en segundo plano desde una shell, usar `setsid nohup ... & disown` para que
el proceso no muera al terminar el comando.

---

## Dependencias y licencias

| Crate | Licencia | ¿Compatible con GPL-2.0-only? |
|---|---|---|
| `wgpu` 30 | MIT OR Apache-2.0 | Sí (eligiendo MIT) |
| `raw-window-handle` 0.6 | MIT OR Apache-2.0 OR Zlib | Sí |
| `bytemuck` 1 | Zlib OR Apache-2.0 OR MIT | Sí |
| `ramen` (vendor) | Zlib | Sí |

**Prohibidos** (Apache-2.0 sola, incompatible con GPLv2): `winit`, `glutin`, `MoltenVK`.
Por eso no se migró el windowing y se extendió `ramen` en su lugar.

---

## Archivos de referencia

- Investigación completa previa: `docs/investigacion/renderizado-vulkan-wgpu.md`
- Backend: `engine/src/OpenGMK/gm8emulator/src/render/wgpu_backend/`
- Shaders GL originales: `engine/src/OpenGMK/gm8emulator/src/render/glsl/`
- Ventanas: `engine/vendor/ramen/`
