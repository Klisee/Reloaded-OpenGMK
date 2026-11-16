// Grupo `state`: implementación real. Las funciones del trait en mod.rs delegan aquí.
use super::*;
use crate::types::Colour;
use std::cell::RefCell;
use std::collections::HashMap;

/// Estado de render que la GPU todavía no consume.
///
/// `RendererImpl` se define en `mod.rs`, que este módulo no puede editar, así que no hay un campo
/// donde guardarlo. El motor dibuja en un solo hilo (el contexto de render vive en el hilo
/// principal, igual que en el backend OpenGL), por lo que un `thread_local` es suficiente y seguro:
/// el estado se indexa por la dirección del renderer para que dos instancias del mismo hilo no se
/// pisen.
///
/// Limitaciones conocidas:
/// - El motor crea un renderer por ejecución; si uno nuevo reutilizara la misma dirección que uno
///   ya destruido heredaría su estado. Con el ciclo de vida actual (uno por proceso) no ocurre.
/// - `pixel_interpolation` arranca en `false`, el valor por defecto de `RendererOptions`:
///   `RendererImpl` no conserva el `interpolate_pixels` con el que se construyó y no hay forma de
///   recuperarlo desde aquí.
///
/// Los valores iniciales replican `RenderState::default()` y el resto de campos del backend OpenGL.
struct StateCache {
    alpha_blending: bool,
    colour_blending: bool,
    blend_mode: (BlendType, BlendType),
    pixel_interpolation: bool,
    texture_repeat: bool,
    circle_precision: i32,
    zbuf_trashed: bool,
    /// Espejo mutable de `RendererImpl::vsync`, que no se puede reconfigurar con `&self`.
    vsync: bool,
    using_3d: bool,
    depth: f32,
    depth_test: bool,
    write_depth: bool,
    culling: bool,
    perspective: bool,
    fog: Option<Fog>,
    gouraud: bool,
    lighting_enabled: bool,
    /// Canales normalizados, como el `ambient_colour: [f32; 4]` de OpenGL: así el ida y vuelta
    /// `set_ambient_colour`/`get_ambient_colour` da exactamente el mismo `i32`.
    ambient_colour: [f32; 4],
    lights: [(bool, Light); 8],
    /// Ajustes de los filtros CRT y Game Boy. Se guardan para no perderlos, pero aún no se aplican:
    /// falta el shader de post-proceso (ver `set_crt`/`set_gb`).
    #[allow(dead_code)]
    crt_settings: Option<CrtSettings>,
    #[allow(dead_code)]
    gb_settings: Option<GbSettings>,
    #[allow(dead_code)]
    gb_inverted: bool,
}

impl StateCache {
    fn new(r: &RendererImpl) -> Self {
        Self {
            alpha_blending: true,
            colour_blending: true,
            blend_mode: (BlendType::SrcAlpha, BlendType::InvSrcAlpha),
            pixel_interpolation: false,
            texture_repeat: false,
            circle_precision: 24,
            zbuf_trashed: false,
            vsync: r.vsync,
            using_3d: false,
            depth: 0.0,
            depth_test: false,
            write_depth: false,
            culling: false,
            perspective: false,
            fog: None,
            gouraud: true,
            lighting_enabled: false,
            ambient_colour: [0.0; 4],
            lights: [(false, Light::Directional { direction: [0.0; 3], colour: 0 }); 8],
            crt_settings: None,
            gb_settings: None,
            gb_inverted: false,
        }
    }
}

thread_local! {
    /// Estado por renderer del hilo actual. Ver la documentación de [`StateCache`].
    static STATES: RefCell<HashMap<usize, StateCache>> = RefCell::new(HashMap::new());
}

fn state_key(r: &RendererImpl) -> usize {
    r as *const RendererImpl as usize
}

/// Lee el estado del renderer, creándolo con los valores por defecto la primera vez.
fn with_state<R>(r: &RendererImpl, f: impl FnOnce(&StateCache) -> R) -> R {
    let key = state_key(r);
    STATES.with(|states| {
        let mut states = states.borrow_mut();
        let state = states.entry(key).or_insert_with(|| StateCache::new(r));
        f(state)
    })
}

/// Muta el estado del renderer. No anidar con otra llamada a `with_state`/`with_state_mut` sobre el
/// mismo renderer: el `RefCell` haría panic.
fn with_state_mut<R>(r: &RendererImpl, f: impl FnOnce(&mut StateCache) -> R) -> R {
    let key = state_key(r);
    STATES.with(|states| {
        let mut states = states.borrow_mut();
        let state = states.entry(key).or_insert_with(|| StateCache::new(r));
        f(state)
    })
}

/// Igual que `split_colour` del backend OpenGL: `i32` ABGR a canales normalizados.
fn split_colour(rgb: i32, alpha: f64) -> [f32; 4] {
    [
        ((rgb & 0xFF) as f32) / 255.0,
        (((rgb >> 8) & 0xFF) as f32) / 255.0,
        (((rgb >> 16) & 0xFF) as f32) / 255.0,
        alpha.max(0.0).min(1.0) as f32,
    ]
}

/// Recorrido de ida y vuelta que hace el backend OpenGL con los colores de fog y luces: los
/// normaliza al guardarlos y los reconstruye en el getter. Se replica para que un color con bits
/// altos se recorte igual y `get_fog`/`get_lights` devuelvan el mismo valor que allí.
fn normalize_colour(rgb: i32) -> i32 {
    let c = split_colour(rgb, 1.0);
    u32::from(Colour::from((f64::from(c[0]), c[1].into(), c[2].into()))) as i32
}

/// Aplica a una luz el mismo recorte de color que `set_light` de OpenGL.
fn normalize_light(light: Light) -> Light {
    match light {
        Light::Directional { direction, colour } => {
            Light::Directional { direction, colour: normalize_colour(colour) }
        },
        Light::Point { position, range, colour } => {
            Light::Point { position, range, colour: normalize_colour(colour) }
        },
    }
}

pub(crate) fn get_alpha_blending(r: &RendererImpl)-> bool {
        with_state(r, |s| s.alpha_blending)
}

pub(crate) fn set_alpha_blending(r: &mut RendererImpl, alphablend: bool) {
        // TODO(wgpu): el pipeline solo implementa el blend premultiplicado por defecto; cuando
        // tenga estados de blend, esta bandera elegirá entre mezclar y reemplazar.
        with_state_mut(r, |s| s.alpha_blending = alphablend);
}

pub(crate) fn get_colour_blending(r: &RendererImpl)-> bool {
        with_state(r, |s| s.colour_blending)
}

pub(crate) fn set_colour_blending(r: &mut RendererImpl, modulate: bool) {
        // TODO(wgpu): el shader ya multiplica el color del vértice por la textura; falta el modo
        // `GL_REPLACE` (modulate = false).
        with_state_mut(r, |s| s.colour_blending = modulate);
}

pub(crate) fn get_blend_mode(r: &RendererImpl)-> (BlendType, BlendType) {
        with_state(r, |s| s.blend_mode)
}

pub(crate) fn set_blend_mode(r: &mut RendererImpl, src: BlendType, dst: BlendType) {
        // TODO(wgpu): el pipeline tiene un único blend fijo; guardarlo aquí permite elegir la
        // factorización cuando el pipeline soporte los modos.
        with_state_mut(r, |s| s.blend_mode = (src, dst));
}

pub(crate) fn get_pixel_interpolation(r: &RendererImpl)-> bool {
        with_state(r, |s| s.pixel_interpolation)
}

pub(crate) fn set_pixel_interpolation(r: &mut RendererImpl, lerping: bool) {
        // El shader lee `flags2.y` (`lerp`); el sampler es NEAREST y la mezcla la hace a mano.
        // TODO(wgpu): `prepare_batches` todavía no copia esta bandera a `SpriteUniforms`.
        with_state_mut(r, |s| s.pixel_interpolation = lerping);
}

pub(crate) fn get_texture_repeat(r: &RendererImpl)-> bool {
        with_state(r, |s| s.texture_repeat)
}

pub(crate) fn set_texture_repeat(r: &mut RendererImpl, repeat: bool) {
        // El shader lee `flags2.x`; ver el TODO de `set_pixel_interpolation`.
        with_state_mut(r, |s| s.texture_repeat = repeat);
}

pub(crate) fn get_circle_precision(r: &RendererImpl)-> i32 {
        with_state(r, |s| s.circle_precision)
}

pub(crate) fn set_circle_precision(r: &mut RendererImpl, prec: i32) {
        // Mismo redondeo que OpenGL: entre 4 y 64, múltiplo de 4.
        with_state_mut(r, |s| s.circle_precision = (prec.max(4).min(64) >> 2) << 2);
}

pub(crate) fn get_zbuf_trashed(r: &RendererImpl)-> bool {
        with_state(r, |s| s.zbuf_trashed)
}

pub(crate) fn set_zbuf_trashed(r: &mut RendererImpl, trashed: bool) {
        // OpenGL solo hace flush cuando el valor cambia; aquí se conserva ese orden de dibujo.
        // TODO(wgpu): no hay depth attachment todavía; cuando exista, al trastear hay que dejar de
        // escriturar en él, igual que OpenGL desengancha la textura de profundidad.
        let changed = with_state(r, |s| s.zbuf_trashed != trashed);
        if changed {
            r.flush_now();
            with_state_mut(r, |s| s.zbuf_trashed = trashed);
        }
}

pub(crate) fn set_vsync(r: &RendererImpl, vsync: bool) {
        // El trait recibe `&self` y `SurfaceConfiguration::present_mode` necesita `&mut` para
        // reconfigurar la surface, así que el modo real queda como lo dejó el constructor.
        // TODO(wgpu): aplicar aquí `config.present_mode` (AutoVsync/AutoNoVsync) y llamar a
        // `reconfigure_surface` cuando `set_vsync` pueda tomar `&mut self`; mientras tanto `get_vsync`
        // devuelve el último valor pedido para que guardar/cargar estado sea coherente.
        with_state_mut(r, |s| s.vsync = vsync);
}

pub(crate) fn get_vsync(r: &RendererImpl)-> bool {
        with_state(r, |s| s.vsync)
}

pub(crate) fn wait_vsync(_r: &RendererImpl) {
        // wgpu sincroniza en `present`; nada que esperar aquí.
}

pub(crate) fn get_3d(r: &RendererImpl)-> bool {
        with_state(r, |s| s.using_3d)
}

pub(crate) fn set_3d(r: &mut RendererImpl, use_3d: bool) {
        // Igual que OpenGL: activar 3D activa depth test y perspectiva; desactivarlo apaga ambos.
        with_state_mut(r, |s| {
            s.using_3d = use_3d;
            s.depth_test = use_3d;
            s.perspective = use_3d;
        });
}

pub(crate) fn get_depth(r: &RendererImpl)-> f32 {
        with_state(r, |s| s.depth)
}

pub(crate) fn set_depth(r: &mut RendererImpl, depth: f32) {
        // Fuera del modo 3D la profundidad se fuerza a 0, como OpenGL.
        with_state_mut(r, |s| s.depth = if s.using_3d { depth.max(-16000.0).min(16000.0) } else { 0.0 });
}

pub(crate) fn get_depth_test(r: &RendererImpl)-> bool {
        with_state(r, |s| s.depth_test)
}

pub(crate) fn set_depth_test(r: &mut RendererImpl, depth_test: bool) {
        // TODO(wgpu): no hay depth attachment; se guarda para cuando el pipeline lo tenga.
        with_state_mut(r, |s| s.depth_test = depth_test && s.using_3d);
}

pub(crate) fn get_write_depth(r: &RendererImpl)-> bool {
        with_state(r, |s| s.write_depth)
}

pub(crate) fn set_write_depth(r: &mut RendererImpl, write_depth: bool) {
        // TODO(wgpu): pendiente del depth attachment.
        with_state_mut(r, |s| s.write_depth = write_depth);
}

pub(crate) fn get_culling(r: &RendererImpl)-> bool {
        with_state(r, |s| s.culling)
}

pub(crate) fn set_culling(r: &mut RendererImpl, culling: bool) {
        // TODO(wgpu): pendiente de un pipeline con `front_face`/`cull_mode` configurables.
        with_state_mut(r, |s| s.culling = culling);
}

pub(crate) fn get_perspective(r: &RendererImpl)-> bool {
        with_state(r, |s| s.perspective)
}

pub(crate) fn set_perspective(r: &mut RendererImpl, perspective: bool) {
        // Solo afecta a `set_view`, así que no hace falta volcar la cola.
        with_state_mut(r, |s| s.perspective = perspective);
}

pub(crate) fn get_fog(r: &RendererImpl)-> Option<Fog> {
        with_state(r, |s| s.fog.clone())
}

pub(crate) fn set_fog(r: &mut RendererImpl, fog: Option<Fog>) {
        // El shader lee `fog_range` y `fog_colour` de las uniforms; ver el TODO de
        // `set_pixel_interpolation` sobre copiar el estado a `SpriteUniforms`.
        // OpenGL normaliza el color al guardarlo, así que se hace aquí para que `get_fog` coincida.
        with_state_mut(r, |s| s.fog = fog.map(|f| Fog { colour: normalize_colour(f.colour), ..f }));
}

pub(crate) fn get_gouraud(r: &RendererImpl)-> bool {
        with_state(r, |s| s.gouraud)
}

pub(crate) fn set_gouraud(r: &mut RendererImpl, gouraud: bool) {
        // TODO(wgpu): el shader interpolara las normales/colores por defecto; falta el modo plano.
        with_state_mut(r, |s| s.gouraud = gouraud);
}

pub(crate) fn get_lighting_enabled(r: &RendererImpl)-> bool {
        with_state(r, |s| s.lighting_enabled)
}

pub(crate) fn set_lighting_enabled(r: &mut RendererImpl, enabled: bool) {
        // TODO(wgpu): `flags.x` de las uniforms ya existe; falta que `prepare_batches` lo rellene.
        with_state_mut(r, |s| s.lighting_enabled = enabled);
}

pub(crate) fn get_ambient_colour(r: &RendererImpl)-> i32 {
        let colour = with_state(r, |s| s.ambient_colour);
        let col = &colour[0..3];
        // Misma conversión que OpenGL: los canales ya están normalizados en 0..1.
        u32::from(Colour::from((f64::from(col[0]), col[1].into(), col[2].into()))) as i32
}

pub(crate) fn set_ambient_colour(r: &mut RendererImpl, colour: i32) {
        with_state_mut(r, |s| s.ambient_colour = split_colour(colour, 1.0));
}

pub(crate) fn get_lights(r: &RendererImpl)-> [(bool, Light); 8] {
        with_state(r, |s| s.lights)
}

pub(crate) fn set_lights(r: &mut RendererImpl, lights: [(bool, Light); 8]) {
        // `set_lights` de OpenGL delega en `set_light_enabled` + `set_light` para cada índice, y
        // `set_light` normaliza el color: mismo resultado aquí.
        with_state_mut(r, |s| {
            for (i, (enabled, light)) in lights.iter().enumerate() {
                s.lights[i] = (*enabled, normalize_light(*light));
            }
        });
}

pub(crate) fn set_light_enabled(r: &mut RendererImpl, id: usize, enabled: bool) {
        // Indexar directamente mantiene el mismo panic por id fuera de rango que OpenGL.
        with_state_mut(r, |s| s.lights[id].0 = enabled);
}

pub(crate) fn set_light(r: &mut RendererImpl, id: usize, light: Light) {
        // OpenGL no toca el flag `enabled` aquí, solo la definición de la luz.
        with_state_mut(r, |s| s.lights[id].1 = normalize_light(light));
}

// El motor no llama a `as_any` todavía (el trait lo marca como muerto igual que en OpenGL).
#[allow(dead_code)]
pub(crate) fn as_any(r: &RendererImpl)-> &dyn Any {
        r
}

pub(crate) fn max_texture_size(r: &RendererImpl)-> u32 {
        r.max_texture_size
}

pub(crate) fn set_draw_offset(r: &mut RendererImpl, x: f64, y: f64) {
        // Un NaN o infinito del juego acabaría en el shader como `NaN * 0`; se neutraliza aquí,
        // igual que hace el backend OpenGL.
        let finite = |v: f64| {
            let v = v as f32;
            if v.is_finite() { v } else { 0.0 }
        };
        r.frame.draw_offset = [finite(x), finite(y)];
}

pub(crate) fn set_interp_amount(r: &mut RendererImpl, amount: f32) {
        r.frame.interp_amount = amount.clamp(0.0, 1.0);
}

pub(crate) fn set_view_matrix(r: &mut RendererImpl, view: [f32; 16]) {
        r.frame.view_matrix = view;
        r.frame.update_projection();
}

pub(crate) fn set_viewproj_matrix(r: &mut RendererImpl, view: [f32; 16], proj: [f32; 16]) {
        r.frame.view_matrix = view;
        r.frame.proj_matrix = proj;
        r.frame.update_projection();
}

pub(crate) fn get_model_matrix(r: &RendererImpl)-> [f32; 16] {
        r.frame.model_matrix
}

pub(crate) fn set_model_matrix(r: &mut RendererImpl, model: [f32; 16]) {
        r.frame.model_matrix = model;
}

pub(crate) fn mult_model_matrix(r: &mut RendererImpl, model: [f32; 16]) {
        r.frame.model_matrix = mat4mult(r.frame.model_matrix, model);
}

pub(crate) fn set_projection_ortho(r: &mut RendererImpl, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        #[rustfmt::skip]
        let proj_matrix: [f32; 16] = [
            // Comprime a pantalla, voltea verticalmente y limita z a 1..32000 (igual que OpenGL).
            2.0 / w as f32, 0.0,             0.0,            0.0,
            0.0,            -2.0 / h as f32, 0.0,            0.0,
            0.0,            0.0,             1.0 / 31999.0,  0.0,
            0.0,            0.0,             -1.0 / 31999.0, 1.0,
        ];

        r.set_viewproj_matrix(make_view_matrix(x, y, -16000.0, w, h, angle), proj_matrix);
}

pub(crate) fn set_projection_perspective(r: &mut RendererImpl, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        #[rustfmt::skip]
        let proj_matrix: [f32; 16] = [
            // Comprime a pantalla, voltea verticalmente y limita z a 1..32000 (igual que OpenGL).
            2.0, 0.0,                  0.0,                0.0,
            0.0, 2.0 * (w / h) as f32, 0.0,                0.0,
            0.0, 0.0,                  32000.0 / 31999.0,  1.0,
            0.0, 0.0,                  -32000.0 / 31999.0, 0.0,
        ];

        r.set_viewproj_matrix(make_view_matrix(x, y, -w, w, h, angle), proj_matrix);
}

pub(crate) fn set_view(r: &mut RendererImpl, src_x: i32,         src_y: i32,         src_w: i32,         src_h: i32,         src_angle: f64,         port_x: i32,         port_y: i32,         port_w: i32,         port_h: i32,) {
        r.flush_now();
        // DX8 ignora el viewport si se dibuja sobre una surface; en wgpu eso lo decide
        // `trait_surfaces::set_target` cuando exista. Mientras el destino sea siempre el
        // framebuffer del juego, se aplica el puerto igual que OpenGL cuando es válido.
        if port_x >= 0 && port_y >= 0 && port_w >= 0 && port_h >= 0 {
            r.apply_view(src_x, src_y, src_w, src_h, src_angle, port_x, port_y, port_w, port_h);
        }
        // OpenGL elige la proyección según el modo 3D y la perspectiva activos.
        let perspective = with_state(r, |s| s.using_3d && s.perspective);
        if perspective {
            set_projection_perspective(r, src_x.into(), src_y.into(), src_w.into(), src_h.into(), src_angle);
        } else {
            set_projection_ortho(r, src_x.into(), src_y.into(), src_w.into(), src_h.into(), src_angle);
        }
}

pub(crate) fn set_crt(r: &mut RendererImpl, settings: Option<crate::render::CrtSettings>) {
        // TODO(wgpu): falta el shader de post-proceso del CRT (curvatura, scanlines, máscara,
        // aberración, viñeta, ruido...). Se guardan los ajustes para que `present` pueda aplicarlos
        // en cuanto exista. Ojo: OpenGL descarta los ajustes si su efecto no compiló; cuando el
        // shader esté, hay que replicar esa condición y no aplicar nada si no se pudo crear.
        with_state_mut(r, |s| s.crt_settings = settings);
}

pub(crate) fn set_gb(r: &mut RendererImpl, settings: Option<crate::render::GbSettings>) {
        // TODO(wgpu): falta el shader de paleta Game Boy y su dithering. Además, OpenGL solo acepta
        // los ajustes si el efecto CRT existe (`gb_settings = if self.crt.is_some()`); replicarlo
        // cuando el shader esté implementado.
        with_state_mut(r, |s| s.gb_settings = settings);
}

pub(crate) fn set_gb_inverted(r: &mut RendererImpl, inverted: bool) {
        // TODO(wgpu): lo aplica el shader de Game Boy, que aún no existe.
        with_state_mut(r, |s| s.gb_inverted = inverted);
}
