//! Backend de renderizado basado en wgpu.
//!
//! wgpu selecciona la API gráfica en tiempo de ejecución: Vulkan en Linux/Windows/Android,
//! y GLES como alternativa cuando Vulkan no está disponible. Eso permite un único
//! `RendererTrait` para las tres plataformas en lugar de tres backends separados.
//!
//! Licencia: wgpu es `MIT OR Apache-2.0`, compatible con la GPL-2.0-only del motor.

mod atlas;
mod draw;
mod pipeline;
#[cfg(test)]
mod tests;

use crate::render::{
    BlendType, CrtSettings, Fog, GbSettings, Light, PrimitiveBuilder, PrimitiveShape, PrimitiveType, RendererOptions,
    RendererTrait, SavedTexture, Scaling, Vertex, VertexBuffer,
    atlas::{AtlasBuilder as AtlasBuilderAny, AtlasRect, AtlasRef},
    mat4mult,
};
use crate::types::Colour;
use ramen::{connection::Connection, window::Window};
use std::any::Any;

/// Un lote de vértices ya subido a la GPU, listo para el render pass de `present`.
///
/// Las uniforms se crean junto al lote (en vez de compartir el buffer del pipeline) porque todos
/// los lotes pendientes se dibujan de golpe en `present`: con un solo buffer todos verían la
/// última proyección escrita y un frame que cambia de vista a mitad de dibujo saldría torcido.
struct PreparedBatch {
    buffer: wgpu::Buffer,
    vertices: u32,
    topology: wgpu::PrimitiveTopology,
    /// Rectángulo del puerto de la vista, en píxeles del framebuffer del juego.
    port: (i32, i32, i32, i32),
    uniform_group: wgpu::BindGroup,
    atlas_group: wgpu::BindGroup,
}

/// Una limpieza de color pedida por `clear_view`, en el orden en que ocurrió.
///
/// Se dibuja como un quad que rellena su puerto en vez de vaciar el target entero: así una limpieza
/// de una vista concreta no borra las demás, que es lo que consigue `glClear` con el scissor activo
/// en el backend OpenGL.
struct ClearCommand {
    /// Número de lotes preparados cuando se pidió: se dibuja justo antes del lote con este índice.
    after: usize,
    colour: wgpu::Color,
    /// Puerto de la vista en píxeles del framebuffer; `(0,0,0,0)` significa el lienzo entero.
    port: (i32, i32, i32, i32),
}

/// Estado de dibujo del frame en curso.
///
/// El motor dibuja de forma inmediata, pero wgpu no puede tocar la surface hasta que `present`
/// adquiere su textura. Aquí se acumula el frame: los vértices en `draw_list`, y en `flush_queue`
/// se convierten en buffers de GPU (`prepared`) que `present` dibuja en un solo render pass.
struct FrameState {
    draw_list: draw::DrawList,
    prepared: Vec<PreparedBatch>,
    clears: Vec<ClearCommand>,
    /// `view * proj` en columnas, que es lo que el shader espera como `projection`.
    projection: [[f32; 4]; 4],
    /// Matriz de proyección suelta: `set_view_matrix` recombina con ella sin perderla.
    proj_matrix: [f32; 16],
    interp_amount: f32,
    view_matrix: [f32; 16],
    model_matrix: [f32; 16],
    clear_colour: Colour,
    clear_alpha: f64,
    /// Desplazamiento de interpolación de los vértices que se encolen a partir de ahora.
    draw_offset: [f32; 2],
    /// Puerto de la vista activa, en píxeles del framebuffer (lo fija `set_view`).
    port: (i32, i32, i32, i32),
}

impl FrameState {
    fn new(clear_colour: Colour) -> Self {
        Self {
            draw_list: draw::DrawList::new(),
            prepared: Vec::new(),
            clears: Vec::new(),
            projection: identity_columns(),
            proj_matrix: identity(),
            interp_amount: 0.0,
            view_matrix: identity(),
            model_matrix: identity(),
            clear_colour,
            clear_alpha: 1.0,
            draw_offset: [0.0; 2],
            port: (0, 0, 0, 0),
        }
    }

    /// Recalcula `projection` a partir de la vista y la proyección actuales.
    ///
    /// La matriz de proyección del juego ya trae su propio `-2/h`, suficiente para que la Y del
    /// juego (que crece hacia abajo) acabe arriba en pantalla, así que aquí no hace falta el
    /// volteo vertical que `update_matrix` aplica en OpenGL (allí compensa la textura volcada del
    /// FBO).
    ///
    /// Sí hace falta, en cambio, el **medio píxel** que GM8 aplica para compensar el offset de DX:
    /// es una traslación de `1/ancho` y `1/alto` en clip space, y sin ella cada sprite y cada
    /// primitiva cae en un píxel distinto al del backend OpenGL, lo que además rompe la alineación
    /// de los bordes de los sprites al moverse (se ven cortados). El tamaño que cuenta es el del
    /// puerto de la vista activa, igual que el viewport que OpenGL consulta en `update_matrix`.
    fn update_projection(&mut self) {
        let (_, _, port_w, port_h) = self.port;
        let (port_w, port_h) = (port_w.max(1) as f32, port_h.max(1) as f32);
        let offset = [
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            1.0 / port_w, 1.0 / port_h, 0.0, 1.0,
        ];
        self.projection = columns_from_mat4(mat4mult(mat4mult(self.view_matrix, self.proj_matrix), offset));
    }

    fn wgpu_clear_colour(&self) -> wgpu::Color {
        wgpu::Color { r: self.clear_colour.r, g: self.clear_colour.g, b: self.clear_colour.b, a: self.clear_alpha }
    }
}

#[rustfmt::skip]
fn identity() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0,
    ]
}

fn identity_columns() -> [[f32; 4]; 4] {
    columns_from_mat4(identity())
}

/// Convierte una matriz `[f32; 16]` en columnas (orden de GLSL/wgpu) al formato `[[f32; 4]; 4]`.
fn columns_from_mat4(m: [f32; 16]) -> [[f32; 4]; 4] {
    let mut out = [[0.0; 4]; 4];
    for (column, chunk) in out.iter_mut().zip(m.chunks_exact(4)) {
        column.copy_from_slice(chunk);
    }
    out
}

/// Igual que `render::opengl::make_view_matrix`: coloca la cámara y la rota por el ángulo de vista.
fn make_view_matrix(x: f64, y: f64, z: f64, w: f64, h: f64, angle: f64) -> [f32; 16] {
    // `sin` va negado porque es lo mismo que negar el ángulo, que es como GM8 trata los ángulos.
    let angle = angle.to_radians();
    let sin_angle = -angle.sin() as f32;
    let cos_angle = angle.cos() as f32;

    // Centro del rectángulo fuente: -(x + w/2), -(y + h/2).
    let scx = -((x as f32) + (w as f32 / 2.0));
    let scy = -((y as f32) + (h as f32 / 2.0));
    let scz = -z as f32;

    mat4mult(
        // Cámara en (scx, scy, scz)
        [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, scx, scy, scz, 1.0],
        // Rotación al ángulo de vista
        [cos_angle, sin_angle, 0.0, 0.0, -sin_angle, cos_angle, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0],
    )
}

/// Posee un `RawDisplayHandle` para poder pasárselo a `wgpu::InstanceDescriptor`, que exige
/// un valor `'static`. El handle apunta a la conexión X11/Win32/Android que vive en la ventana;
/// `RendererImpl` no sobrevive a la `Window`, así que el préstamo es válido.
struct OwnedDisplay {
    raw: raw_window_handle::RawDisplayHandle,
}

// SAFETY: el handle nativo (Display* de X11, HWND de Win32, ANativeWindow de Android) es válido
// mientras viva la ventana, y el renderer se destruye antes que ella. Todo el render ocurre en
// el hilo principal, que es el que creó la ventana.
unsafe impl Send for OwnedDisplay {}
unsafe impl Sync for OwnedDisplay {}

impl std::fmt::Debug for OwnedDisplay {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("OwnedDisplay")
    }
}

impl raw_window_handle::HasDisplayHandle for OwnedDisplay {
    fn display_handle(
        &self,
    ) -> Result<raw_window_handle::DisplayHandle<'_>, raw_window_handle::HandleError> {
        // SAFETY: el handle sigue siendo válido mientras exista la ventana que lo creó.
        Ok(unsafe { raw_window_handle::DisplayHandle::borrow_raw(self.raw) })
    }
}

/// Lienzo intermedio a la resolución del juego.
///
/// OpenGL dibuja al framebuffer del juego (320x180 típicamente) y luego lo escala a la ventana con
/// `glBlitFramebuffer` y filtro NEAREST. Si se dibuja directo a la surface, el rasterizador pinta
/// la geometría sin textura (sombras, elipses, líneas) a resolución de ventana y los bordes salen
/// suaves: eso redondeaba las sombras de los personajes. Este lienzo reproduce el camino de OpenGL
/// y el blit nearest mantiene el pixel-art.
struct Canvas {
    texture: wgpu::Texture,
    view: wgpu::TextureView,
    /// Bind group (textura + sampler) para el blit.
    bind_group: wgpu::BindGroup,
    width: u32,
    height: u32,
    format: wgpu::TextureFormat,
}

/// Pipeline que vuelca el lienzo a la ventana con un quad texturizado y sampler nearest.
///
/// wgpu no tiene `glBlitFramebuffer`, así que el escalado se hace con un draw de 6 vértices
/// generados en el shader: `rect` es el destino en NDC y las UV recorren el lienzo entero.
struct BlitPipeline {
    pipeline: wgpu::RenderPipeline,
    uniform_buffer: wgpu::Buffer,
    uniform_group: wgpu::BindGroup,
    texture_layout: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
}

impl BlitPipeline {
    fn new(device: &wgpu::Device, format: wgpu::TextureFormat) -> Self {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("gm8emulator blit shader"),
            source: wgpu::ShaderSource::Wgsl(
                r#"
struct Blit {
    // (izquierda, abajo, ancho, alto) en NDC.
    rect: vec4<f32>,
};

@group(0) @binding(0) var<uniform> blit: Blit;
@group(1) @binding(0) var source: texture_2d<f32>;
@group(1) @binding(1) var source_sampler: sampler;

struct VsOut {
    @builtin(position) pos: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs(@builtin(vertex_index) index: u32) -> VsOut {
    // Dos triángulos que cubren el rectángulo destino; un solo triángulo dejaría media pantalla
    // sin dibujar (la diagonal clásica).
    var corners = array<vec2<f32>, 6>(
        vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
        vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0),
    );
    let corner = corners[index];
    var out: VsOut;
    out.pos = vec4<f32>(
        blit.rect.x + corner.x * blit.rect.z,
        blit.rect.y + corner.y * blit.rect.w,
        0.0,
        1.0,
    );
    // En wgpu la textura del lienzo está con el origen arriba: la V 0 es su fila superior.
    out.uv = vec2<f32>(corner.x, 1.0 - corner.y);
    return out;
}

@fragment
fn fs(input: VsOut) -> @location(0) vec4<f32> {
    return textureSample(source, source_sampler, input.uv);
}
"#
                .into(),
            ),
        });

        let uniform_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator blit uniform layout"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let texture_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator blit texture layout"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
            ],
        });

        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("gm8emulator blit layout"),
            bind_group_layouts: &[Some(&uniform_layout), Some(&texture_layout)],
            immediate_size: 0,
        });

        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("gm8emulator blit pipeline"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: Some("vs"),
                buffers: &[],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: Some("fs"),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    // El lienzo ya está compuesto; el volcado reemplaza el destino.
                    blend: None,
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview_mask: None,
            cache: None,
        });

        let uniform_buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("gm8emulator blit uniform"),
            size: 16,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let uniform_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("gm8emulator blit uniform group"),
            layout: &uniform_layout,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: uniform_buffer.as_entire_binding() }],
        });

        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("gm8emulator blit sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Nearest,
            min_filter: wgpu::FilterMode::Nearest,
            ..Default::default()
        });

        Self { pipeline, uniform_buffer, uniform_group, texture_layout, sampler }
    }
}

/// Pipeline del overlay de depuración, en píxeles de ventana.
///
/// Comparte forma con `BlitPipeline` pero dibuja muchos quads con color y textura propios: la
/// lista llega ya en píxeles de ventana desde el motor, así que aquí solo se convierte a NDC.
struct UiPipeline {
    pipeline: wgpu::RenderPipeline,
    texture_layout: wgpu::BindGroupLayout,
    sampler: wgpu::Sampler,
}

impl UiPipeline {
    fn new(device: &wgpu::Device, format: wgpu::TextureFormat) -> Self {
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("gm8emulator ui shader"),
            source: wgpu::ShaderSource::Wgsl(
                r#"
struct UiUniforms {
    // (ancho, alto) de la ventana en píxeles.
    size: vec2<f32>,
    _pad: vec2<f32>,
};

@group(0) @binding(0) var<uniform> ui: UiUniforms;
@group(1) @binding(0) var source: texture_2d<f32>;
@group(1) @binding(1) var source_sampler: sampler;

struct VertexIn {
    @location(0) pos: vec2<f32>,
    @location(1) colour: vec4<f32>,
    @location(2) uv: vec2<f32>,
};

struct VertexOut {
    @builtin(position) pos: vec4<f32>,
    @location(0) colour: vec4<f32>,
    @location(1) uv: vec2<f32>,
};

@vertex
fn vs(input: VertexIn) -> VertexOut {
    var out: VertexOut;
    // De píxeles de ventana (origen arriba-izquierda) a NDC.
    let ndc_x = input.pos.x / ui.size.x * 2.0 - 1.0;
    let ndc_y = 1.0 - input.pos.y / ui.size.y * 2.0;
    out.pos = vec4<f32>(ndc_x, ndc_y, 0.0, 1.0);
    out.colour = input.colour;
    out.uv = input.uv;
    return out;
}

@fragment
fn fs(input: VertexOut) -> @location(0) vec4<f32> {
    let tex = textureSample(source, source_sampler, input.uv);
    let colour = tex * input.colour;
    // El overlay se mezcla con lo ya presentado, en alfa directo.
    return vec4<f32>(colour.rgb, colour.a);
}
"#
                .into(),
            ),
        });

        let uniform_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator ui uniform layout"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let texture_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator ui texture layout"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: true },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::FRAGMENT,
                    ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                    count: None,
                },
            ],
        });
        let layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("gm8emulator ui layout"),
            bind_group_layouts: &[Some(&uniform_layout), Some(&texture_layout)],
            immediate_size: 0,
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("gm8emulator ui pipeline"),
            layout: Some(&layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: Some("vs"),
                buffers: &[Some(wgpu::VertexBufferLayout {
                    array_stride: std::mem::size_of::<[f32; 8]>() as wgpu::BufferAddress,
                    step_mode: wgpu::VertexStepMode::Vertex,
                    attributes: &[
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 0, shader_location: 0 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x4, offset: 8, shader_location: 1 },
                        wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 24, shader_location: 2 },
                    ],
                })],
                compilation_options: Default::default(),
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: Some("fs"),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    blend: Some(wgpu::BlendState::ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
                compilation_options: Default::default(),
            }),
            primitive: wgpu::PrimitiveState::default(),
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            multiview_mask: None,
            cache: None,
        });
        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("gm8emulator ui sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Nearest,
            min_filter: wgpu::FilterMode::Nearest,
            ..Default::default()
        });
        Self { pipeline, texture_layout, sampler }
    }
}

/// Creates a `wgpu::Surface` over `window`, reclaiming `'static`.
///
/// `ramen::window::Window` is not `Clone`, so it cannot be handed over by value (which is what
/// would let wgpu keep it alive). The surface is built from the raw handles instead.
///
/// SAFETY: `SurfaceTargetUnsafe::from_window` only reads the native handle; the window (and its
/// display connection) stays the owner. The engine creates the `Window` before the `Renderer` and
/// destroys it after, so the surface never outlives the window, and every surface access happens
/// on the main thread.
fn create_surface(instance: &wgpu::Instance, window: &Window) -> Result<wgpu::Surface<'static>, String> {
    unsafe {
        let target = wgpu::SurfaceTargetUnsafe::from_window(window)
            .map_err(|e| format!("wgpu: no se pudo obtener el handle nativo de la ventana: {e}"))?;
        instance.create_surface_unsafe(target).map_err(|e| format!("wgpu: no se pudo crear la surface: {e}"))
    }
}

thread_local! {
    /// Artwork of the touch controls: the atlas texture id and, per control name, its UV rectangle
    /// and aspect. Filled once, when the overlay first needs it.
    static TOUCH_SKIN: std::sync::Mutex<Option<(u32, Vec<(&'static str, [f32; 4], f32)>)>> =
        std::sync::Mutex::new(None);
}

thread_local! {
    /// Artwork of the temporally-named labels: the atlas texture id and, per label text, its UV
    /// rectangle and aspect.
    static TOUCH_LABELS: std::sync::Mutex<Option<(u32, Vec<(String, [f32; 4], f32)>)>> =
        std::sync::Mutex::new(None);
}

/// UVs and aspect of a label's artwork, if the label atlas has been loaded.
fn touch_label_art(text: &str) -> Option<([f32; 4], f32)> {
    TOUCH_LABELS.with(|cell| cell.lock().unwrap().as_ref().and_then(|(_, slots)| {
        slots.iter().find(|(known, _, _)| known == text).map(|(_, uv, aspect)| (*uv, *aspect))
    }))
}

/// UVs and aspect of a control's artwork, if the skin has been loaded.
fn touch_art(name: &str) -> Option<([f32; 4], f32)> {
    TOUCH_SKIN
        .with(|cell| cell.lock().unwrap().as_ref().and_then(|(_, slots)| {
            slots.iter().find(|(known, _, _)| *known == name).map(|(_, uv, aspect)| (*uv, *aspect))
        }))
}

pub struct RendererImpl {
    device: wgpu::Device,
    queue: wgpu::Queue,
    surface: wgpu::Surface<'static>,
    config: wgpu::SurfaceConfiguration,
    /// Instancia y adaptador, para poder recrear la surface si Android reemplaza la ventana.
    instance: wgpu::Instance,
    adapter: wgpu::Adapter,
    /// `ANativeWindow*` con el que se creó la surface (solo Android). Android recrea la ventana al
    /// volver de segundo plano o al girar; la surface vieja apunta a una ventana muerta, así que
    /// hay que detectarlo y rehacerla o la pantalla queda en negro.
    #[cfg(target_os = "android")]
    window_ptr: *mut std::ffi::c_void,
    /// Tamaño del framebuffer del juego (el lienzo), no el de la ventana: `present` lo usa para
    /// decidir el rectángulo donde cae la imagen.
    fb_size: (u32, u32),
    /// La última adquisición fue `Suboptimal`: hay que reconfigurar la surface en cuanto se suelte
    /// la textura (reconfigurar con una viva hace panic).
    surface_dirty: bool,
    pipeline: pipeline::SpritePipeline,
    textures: atlas::TextureStore,
    /// Sprite del píxel blanco 1x1: atlas de las primitivas sin textura.
    white_pixel: AtlasRef,
    clear_colour: Colour,
    vsync: bool,
    max_texture_size: u32,
    frame: FrameState,
    primitive_2d: PrimitiveBuilder,
    primitive_3d: PrimitiveBuilder,
    /// Lienzo a resolución del juego; `present` dibuja en él y lo escala a la ventana.
    canvas: Option<Canvas>,
    /// Pipeline del volcado del lienzo a la surface.
    blit: BlitPipeline,
    /// Pipeline del overlay de depuración (píxeles de ventana).
    ui: UiPipeline,
    /// Id de la textura con el arte de los controles táctiles, una vez cargada.
    touch_skin: Option<u32>,
    /// Id de la textura con el arte de las etiquetas, una vez cargada.
    touch_labels: Option<u32>,
    /// Textura del equipo para la rueda, y la lista con la que se subió.
    party_texture: Option<u32>,
    party_species: Option<[i32; crate::touch_controls::PICKER_SLOTS]>,
    ui_uniform: wgpu::Buffer,
    ui_uniform_group: wgpu::BindGroup,
    ui_layout: wgpu::BindGroupLayout,
    /// Datos del adaptador elegido, para el overlay de diagnóstico.
    adapter_name: String,
    adapter_backend: String,
    adapter_driver: String,
    adapter_api: String,
}

impl RendererImpl {
    pub fn new(
        options: &RendererOptions,
        connection: &Connection,
        window: &Window,
        clear_colour: Colour,
    ) -> Result<Self, String> {
        let _ = connection;

        // `wgpu` entrega futuros que en el backend nativo (`wgpu-core`) resuelven de forma
        // síncrona (`ready`). Aun así los sondeamos con un waker noop para no añadir una
        // dependencia de runtime asíncrono.
        fn block_on<F: std::future::Future>(fut: F) -> F::Output {
            let mut fut = std::pin::pin!(fut);
            let waker = std::task::Waker::noop();
            let mut cx = std::task::Context::from_waker(waker);
            loop {
                match fut.as_mut().poll(&mut cx) {
                    std::task::Poll::Ready(value) => return value,
                    std::task::Poll::Pending => std::thread::yield_now(),
                }
            }
        }

        // La instancia necesita el display handle: wgpu lo usa para crear superficies y para
        // elegir las extensiones de instancia correctas (X11 en Linux). Sin él, `create_surface`
        // falla con "No `DisplayHandle` is available".
        // `InstanceDescriptor::display` es lo que permite crear la surface después; sin él,
        // `create_surface` devuelve "No `DisplayHandle` is available". wgpu pide un
        // `Box<dyn WgpuHasDisplayHandle>`, así que se envuelve el handle crudo en un tipo propio
        // que lo posee (y por tanto puede ser 'static).
        let raw_display = {
            use raw_window_handle::HasDisplayHandle;
            window
                .display_handle()
                .map_err(|e| format!("wgpu: la ventana no expone un display handle: {e}"))?
                .as_raw()
        };
        let display = OwnedDisplay { raw: raw_display };

        let mut desc = wgpu::InstanceDescriptor::new_without_display_handle();
        // One backend list for every platform: Vulkan and GL everywhere, DX12 on Windows. wgpu
        // picks the best one the machine actually has.
        //
        // `OPENGMK_WGPU_BACKEND` narrows it down for testing (dx12, vulkan or gl): without it,
        // forcing a backend on a machine that lacks it was impossible.
        desc.backends = match std::env::var("OPENGMK_WGPU_BACKEND").unwrap_or_default().trim().to_ascii_lowercase().as_str() {
            "dx12" | "d3d12" | "directx" => wgpu::Backends::DX12,
            "vulkan" | "vk" => wgpu::Backends::VULKAN,
            "gl" | "gles" | "opengl" => wgpu::Backends::GL,
            _ => wgpu::Backends::VULKAN | wgpu::Backends::GL | wgpu::Backends::DX12,
        };
        desc.display = Some(Box::new(display));
        let instance = wgpu::Instance::new(desc);

        // `ramen::window::Window` no implementa `Clone`, así que no podemos pasarla por valor
        // (que es lo que haría que wgpu la mantuviera viva y la surface fuese `'static`).
        // En su lugar construimos la surface desde los handles crudos y reclamamos `'static`.
        //
        // SAFETY: `SurfaceTargetUnsafe::from_window` solo lee el handle nativo; la ventana
        // (y su conexión de display) sigue siendo dueña del mismo. En el motor la `Window`
        // se crea antes que el `Renderer` y se destruye después (en `Game`, el campo
        // `renderer` se declara antes que `window`), de modo que la surface nunca sobrevive
        // a la ventana. Todo acceso a la surface ocurre desde el hilo principal.
        let surface = create_surface(&instance, window)?;

        let adapter = block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            force_fallback_adapter: false,
            compatible_surface: Some(&surface),
            ..Default::default()
        }))
        .map_err(|e| format!("wgpu: no hay adaptador compatible: {e}"))?;

        let info = adapter.get_info();
        // `AdapterInfo` de wgpu 30 ya no expone `api_version`; el driver y su `driver_info`
        // (donde Vulkan/GLES incluyen la versión de API) son el equivalente disponible.
        eprintln!(
            "wgpu: backend {:?} | dispositivo: {} | tipo: {:?} | driver: {} | API: {}",
            info.backend, info.name, info.device_type, info.driver, info.driver_info
        );
        let adapter_name = info.name.clone();
        let adapter_backend = format!("{:?}", info.backend);
        let adapter_driver = info.driver.clone();
        let adapter_api = info.driver_info.clone();

        let (device, queue) = block_on(adapter.request_device(&wgpu::DeviceDescriptor {
            label: Some("OpenGMK wgpu device"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults().using_resolution(adapter.limits()),
            ..Default::default()
        }))
        .map_err(|e| format!("wgpu: no se pudo crear el dispositivo: {e}"))?;

        let caps = surface.get_capabilities(&adapter);
        if caps.formats.is_empty() {
            return Err("wgpu: la surface no soporta ningún formato de textura".into());
        }

        // Preferimos formatos no-sRGB: el motor ya trabaja en espacio linear y no queremos
        // que el swapchain aplique una conversión sRGB extra a los colores.
        let format = caps
            .formats
            .iter()
            .copied()
            .find(|f| *f == wgpu::TextureFormat::Bgra8Unorm)
            .or_else(|| caps.formats.iter().copied().find(|f| *f == wgpu::TextureFormat::Rgba8Unorm))
            .or_else(|| caps.formats.iter().copied().find(|f| !f.is_srgb()))
            .unwrap_or(caps.formats[0]);

        // `AutoVsync`/`AutoNoVsync` son los únicos modos con fallback garantizado, así que
        // no hace falta comprobarlos contra `caps.present_modes`.
        let present_mode = if options.vsync { wgpu::PresentMode::AutoVsync } else { wgpu::PresentMode::AutoNoVsync };

        let alpha_mode = caps.alpha_modes.first().copied().unwrap_or(wgpu::CompositeAlphaMode::Auto);

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            color_space: wgpu::SurfaceColorSpace::Auto,
            width: options.size.0.max(1),
            height: options.size.1.max(1),
            desired_maximum_frame_latency: 2,
            present_mode,
            alpha_mode,
            view_formats: vec![],
        };
        surface.configure(&device, &config);

        eprintln!(
            "wgpu: surface {}x{} | formato {:?} | present {:?} | alpha {:?}",
            config.width, config.height, config.format, config.present_mode, config.alpha_mode
        );

        let max_texture_size = device.limits().max_texture_dimension_2d;
        let pipeline = pipeline::SpritePipeline::new(&device, config.format);
        let mut textures = atlas::TextureStore::new(&pipeline.texture_bind_group_layout);

        // Píxel blanco con el que se dibujan las primitivas sin textura (el motor marca
        // `tex_coord = -1`). Se registra en el store como un sprite más para que su bind group
        // salga del mismo sitio que los demás.
        let white_pixel = textures
            .create_colour(&device, &queue, 255, 255, 255, 255)
            .map_err(|e| format!("wgpu: no se pudo crear el píxel blanco: {e}"))?;

        let fb_size = (config.width, config.height);

        let blit = BlitPipeline::new(&device, format);
        let ui = UiPipeline::new(&device, format);
        let ui_uniform_buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("gm8emulator ui uniform"),
            size: 16,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        let ui_uniform_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator ui uniform layout group"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let ui_uniform_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("gm8emulator ui uniform group"),
            layout: &ui_uniform_layout,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: ui_uniform_buffer.as_entire_binding() }],
        });

        #[cfg(target_os = "android")]
        let window_ptr = window.xid();

        Ok(Self {
            device,
            queue,
            surface,
            config,
            fb_size,
            instance,
            adapter,
            #[cfg(target_os = "android")]
            window_ptr,
            surface_dirty: false,
            pipeline,
            textures,
            white_pixel,
            clear_colour,
            vsync: options.vsync,
            max_texture_size,
            frame: FrameState::new(clear_colour),
            primitive_2d: PrimitiveBuilder::new(Default::default(), PrimitiveType::PointList, false),
            primitive_3d: PrimitiveBuilder::new(Default::default(), PrimitiveType::PointList, false),
            canvas: None,
            blit,
            ui,
            touch_skin: None,
            touch_labels: None,
            party_texture: None,
            party_species: None,
            ui_uniform: ui_uniform_buffer,
            ui_uniform_group,
            ui_layout: ui_uniform_layout,
            adapter_name,
            adapter_backend,
            adapter_driver,
            adapter_api,
        })
    }

    /// Crea (o recrea, si cambió de tamaño) el lienzo a resolución del juego.
    fn ensure_canvas(&mut self) {
        let (width, height) = (self.fb_size.0.max(1), self.fb_size.1.max(1));
        if let Some(canvas) = &self.canvas {
            if canvas.width == width && canvas.height == height && canvas.format == self.config.format {
                return
            }
        }
        let texture = self.device.create_texture(&wgpu::TextureDescriptor {
            label: Some("gm8emulator canvas"),
            size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: self.config.format,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT
                | wgpu::TextureUsages::TEXTURE_BINDING
                | wgpu::TextureUsages::COPY_SRC,
            view_formats: &[],
        });
        let view = texture.create_view(&wgpu::TextureViewDescriptor {
            label: Some("gm8emulator canvas"),
            ..Default::default()
        });
        let bind_group = self.device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("gm8emulator canvas bind group"),
            layout: &self.blit.texture_layout,
            entries: &[
                wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(&view) },
                wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(&self.blit.sampler) },
            ],
        });
        self.canvas = Some(Canvas { texture, view, bind_group, width, height, format: self.config.format });
    }

    /// Saves the canvas as a PNG when `OPENGMK_WGPU_DUMP` has a path, to compare against the
    /// OpenGL backend without depending on screen capture. Runs every 60 frames so it does not
    /// fill the disk.
    fn dump_canvas(&mut self) {
        let Some(path) = std::env::var_os("OPENGMK_WGPU_DUMP") else { return };
        static COUNTER: std::sync::atomic::AtomicU32 = std::sync::atomic::AtomicU32::new(0);
        let n = COUNTER.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        if n % 60 != 0 {
            return;
        }
        let Some(canvas) = self.canvas.as_ref() else { return };
        let (width, height) = (canvas.width, canvas.height);
        let unpadded = (width * 4) as usize;
        let padded = unpadded.div_ceil(wgpu::COPY_BYTES_PER_ROW_ALIGNMENT as usize)
            * wgpu::COPY_BYTES_PER_ROW_ALIGNMENT as usize;
        let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("gm8emulator canvas dump"),
            size: (padded * height as usize) as u64,
            usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
            mapped_at_creation: false,
        });
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator canvas dump") });
        encoder.copy_texture_to_buffer(
            wgpu::TexelCopyTextureInfo {
                texture: &canvas.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::TexelCopyBufferInfo {
                buffer: &buffer,
                layout: wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(padded as u32),
                    rows_per_image: Some(height),
                },
            },
            wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        );
        self.queue.submit(Some(encoder.finish()));

        let slice = buffer.slice(..);
        let (tx, rx) = std::sync::mpsc::channel();
        slice.map_async(wgpu::MapMode::Read, move |result| {
            let _ = tx.send(result);
        });
        let _ = self.device.poll(wgpu::PollType::Wait { submission_index: None, timeout: None });
        if !rx.recv().map(|result| result.is_ok()).unwrap_or(false) {
            return;
        }
        let Ok(view) = slice.get_mapped_range() else { return };
        let data: &[u8] = &view;
        let mut rgba = Vec::with_capacity(unpadded * height as usize);
        for row in 0..height as usize {
            let start = row * padded;
            let end = start + unpadded;
            if end > data.len() {
                break;
            }
            let mut line = data[start..end].to_vec();
            if self.config.format == wgpu::TextureFormat::Bgra8Unorm {
                crate::util::bgra2rgba(&mut line);
            }
            rgba.extend_from_slice(&line);
        }
        drop(view);
        buffer.unmap();
        if let Some(image) = image::RgbaImage::from_raw(width, height, rgba) {
            let file = format!("{}-{n}.png", path.to_string_lossy());
            let _ = image.save(&file);
            eprintln!("[wgpu-dump] {file}");
        }
    }

    /// Vuelca el lienzo a la surface: negro en toda la ventana (letterbox) y el lienzo escalado
    /// con nearest en su rectángulo, igual que el `glClear` + `glBlitFramebuffer` de OpenGL.
    fn blit_canvas(&mut self, target: &wgpu::TextureView, game: (f32, f32, f32, f32)) {
        let (Some(canvas), window_width, window_height) =
            (self.canvas.as_ref(), self.config.width as f32, self.config.height as f32)
        else {
            return
        };
        let (gx, gy, gw, gh) = game;
        // Rectángulo destino en NDC: (izquierda, abajo, ancho, alto).
        let left = if window_width > 0.0 { gx / window_width * 2.0 - 1.0 } else { -1.0 };
        let top = if window_height > 0.0 { 1.0 - gy / window_height * 2.0 } else { 1.0 };
        let width = if window_width > 0.0 { gw / window_width * 2.0 } else { 2.0 };
        let height = if window_height > 0.0 { gh / window_height * 2.0 } else { 2.0 };
        let rect = [left, top - height, width, height];
        self.queue.write_buffer(&self.blit.uniform_buffer, 0, bytemuck::cast_slice(&rect));

        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator blit encoder") });
        {
            // El negro del letterbox cubre toda la ventana; `LoadOp::Clear` lo hace de una vez.
            let _black = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("gm8emulator blit clear"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: target,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color::BLACK),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
        }
        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("gm8emulator blit pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: target,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations { load: wgpu::LoadOp::Load, store: wgpu::StoreOp::Store },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            pass.set_pipeline(&self.blit.pipeline);
            pass.set_bind_group(0, &self.blit.uniform_group, &[]);
            pass.set_bind_group(1, &canvas.bind_group, &[]);
            pass.draw(0..6, 0..1);
        }
        self.queue.submit(Some(encoder.finish()));
    }

    /// Draws the debug overlay on top of the presented frame, in window pixels.
    ///
    /// The canvas already scaled the game to the window; drawing the HUD here, after that, is what
    /// keeps its text at a readable size instead of magnifying it with the game.
    fn draw_ui(&mut self, target: &wgpu::TextureView, window_width: u32, window_height: u32, quads: &[crate::render::UiQuad]) {
        if quads.is_empty() || window_width == 0 || window_height == 0 {
            return
        }
        // Each quad samples one texture; flat ones sample the 1x1 white pixel and stay coloured.
        let mut batches: Vec<(u32, Vec<[f32; 8]>)> = Vec::new();
        for quad in quads {
            let texture = quad
                .atlas
                .and_then(|atlas_ref| self.textures.texture_id(atlas_ref))
                .unwrap_or_else(|| self.resolve_atlas_id(None));
            let rect = quad.atlas.and_then(|atlas_ref| self.textures.rect(atlas_ref).copied());
            let (u0, v0, u1, v1) = match rect {
                // Un sprite se muestrea con sus UVs dentro de su celda del atlas.
                Some(rect) => {
                    let (w, h) = self
                        .textures
                        .size(rect.atlas_id)
                        .map(|(w, h)| (w.max(1) as f32, h.max(1) as f32))
                        .unwrap_or((1.0, 1.0));
                    (
                        rect.x as f32 / w,
                        rect.y as f32 / h,
                        (rect.x + rect.w) as f32 / w,
                        (rect.y + rect.h) as f32 / h,
                    )
                },
                // Un quad plano muestrea el píxel blanco del atlas, no el atlas entero: hay que
                // apuntar al centro de su celda de 1x1. Con UV 0..1 se vería todo el sprite sheet
                // detrás del texto (el patrón de basura del fondo del panel).
                None => {
                    let white = self.white_pixel;
                    match self.textures.rect(white).copied().and_then(|rect| {
                        self.textures.size(rect.atlas_id()).map(|(w, h)| {
                            let (x, y, rw, rh) = rect.cell();
                            (
                                (x as f32 + rw as f32 * 0.5) / w.max(1) as f32,
                                (y as f32 + rh as f32 * 0.5) / h.max(1) as f32,
                            )
                        })
                    }) {
                        Some((u, v)) => (u, v, u, v),
                        None => (0.0, 0.0, 1.0, 1.0),
                    }
                },
            };
            let (x, y, w, h) = quad.rect;
            let colour = quad.colour;
            let mut push = |px: f32, py: f32, u: f32, v: f32, out: &mut Vec<[f32; 8]>| {
                out.push([px, py, colour[0], colour[1], colour[2], colour[3], u, v]);
            };
            let mut vertices = Vec::with_capacity(6);
            push(x, y, u0, v0, &mut vertices);
            push(x + w, y, u1, v0, &mut vertices);
            push(x + w, y + h, u1, v1, &mut vertices);
            push(x, y, u0, v0, &mut vertices);
            push(x + w, y + h, u1, v1, &mut vertices);
            push(x, y + h, u0, v1, &mut vertices);

            match batches.iter_mut().find(|(id, _)| *id == texture) {
                Some((_, list)) => list.extend_from_slice(&vertices),
                None => batches.push((texture, vertices)),
            }
        }

        let size = [window_width as f32, window_height as f32, 0.0, 0.0];
        self.queue.write_buffer(&self.ui_uniform, 0, bytemuck::cast_slice(&size));

        let mut encoder =
            self.device.create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator ui encoder") });
        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("gm8emulator ui pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: target,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations { load: wgpu::LoadOp::Load, store: wgpu::StoreOp::Store },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            pass.set_pipeline(&self.ui.pipeline);
            pass.set_bind_group(0, &self.ui_uniform_group, &[]);
            for (texture, vertices) in &batches {
                let Some(atlas) = self.textures.bind_group(*texture) else { continue };
                let bytes = bytemuck::cast_slice(vertices);
                let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
                    label: Some("gm8emulator ui vertices"),
                    size: bytes.len().max(32) as wgpu::BufferAddress,
                    usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                    mapped_at_creation: false,
                });
                self.queue.write_buffer(&buffer, 0, bytes);
                pass.set_bind_group(1, atlas, &[]);
                pass.set_vertex_buffer(0, buffer.slice(..));
                pass.draw(0..vertices.len() as u32, 0..1);
            }
        }
        self.queue.submit(Some(encoder.finish()));
    }

    /// Rebuilds the surface if Android replaced the window underneath it.
    ///
    /// Android hands out a new `ANativeWindow` when the activity comes back from the background or
    /// the device rotates, and the old one is destroyed. A surface still pointing at it accepts
    /// frames that go nowhere, which shows up as a black screen with no error at all: the renderer
    /// keeps drawing happily into the dead window. Recreating it is the only fix.
    ///
    /// Returns whether the surface was rebuilt.
    #[cfg(target_os = "android")]
    fn refresh_android_window(&mut self) -> bool {
        let current = ramen::platform::android::live_native_window();
        if current.is_null() || std::ptr::eq(current, self.window_ptr) {
            return false
        }
        let handle = raw_window_handle::AndroidNdkWindowHandle::new(
            // SAFETY: `current` is the live ANativeWindow the activity published, which outlives
            // this call; the surface keeps its own reference to the window from here on.
            match std::ptr::NonNull::new(current) {
                Some(ptr) => ptr,
                None => return false,
            },
        );
        let target = wgpu::SurfaceTargetUnsafe::RawHandle {
            raw_display_handle: Some(raw_window_handle::RawDisplayHandle::Android(
                raw_window_handle::AndroidDisplayHandle::new(),
            )),
            raw_window_handle: raw_window_handle::RawWindowHandle::AndroidNdk(handle),
        };
        let surface = match unsafe { self.instance.create_surface_unsafe(target) } {
            Ok(surface) => surface,
            Err(e) => {
                eprintln!("wgpu: no se pudo recrear la surface de Android: {e}");
                return false
            },
        };
        self.surface = surface;
        self.surface.configure(&self.device, &self.config);
        self.window_ptr = current;
        eprintln!("wgpu: ventana nueva de Android, surface recreada");
        true
    }

    /// Loads the pixel-art control skin, once, as one texture of its own.
    ///
    /// The classic controls are images the launcher drops next to the game (`<name>.png`). The
    /// OpenGL backend packs them into a single atlas at startup; wgpu is on Android and has to offer
    /// the same look, so it does the same here and keeps the atlas id for the overlay.
    #[cfg(target_os = "android")]
    fn touch_skin(&mut self) -> Option<u32> {
        if let Some(id) = self.touch_skin {
            return Some(id)
        }
        use crate::touch_controls;
        let dir = crate::android_main::storage_dir().to_string_lossy().into_owned();
        let load = |name: &str, fallback: &str| {
            image::open(format!("{}/touch-sprites-active/{}.png", dir, name))
                .ok()
                .or_else(|| image::open(format!("{}/{}", dir, fallback)).ok())
                .map(|img| img.to_rgba8())
        };

        let mut images: Vec<(&'static str, image::RgbaImage)> = Vec::new();
        for (name, file) in [
            ("joystick", "joystick-base.png"),
            ("joystick-knob", "joystick-knob.png"),
            ("selector", "seleccionarpokemon.png"),
            ("selection", "bordeseleccionar.png"),
            ("field-selector", "field-selector.png"),
        ] {
            if let Some(img) = load(name, file) {
                images.push((name, img));
            }
        }
        for control in touch_controls::controls() {
            if let Some(img) = load(control.name, &format!("{}.png", control.name)) {
                let mut img = img;
                if let Some(tint) = crate::touch_appearance::settings().tint {
                    for pixel in img.pixels_mut() {
                        crate::touch_appearance::tint_pixel(&mut pixel.0, tint);
                    }
                }
                images.push((control.name, img));
            }
        }
        if images.is_empty() {
            eprintln!("[overlay] sin arte pixel para los controles; se usan las formas dibujadas");
            return None
        }

        let sizes: Vec<(u32, u32)> = images.iter().map(|(_, img)| (img.width(), img.height())).collect();
        let limit = self.max_texture_size.min(2048);
        let Some((width, height, positions)) = crate::touch_skin::atlas_layout(&sizes, limit) else {
            eprintln!("[overlay] arte demasiado grande; se usan las formas dibujadas");
            return None
        };
        let mut pixels = vec![0u8; (width * height * 4) as usize];
        let mut slots: Vec<(&'static str, [f32; 4], f32)> = Vec::with_capacity(images.len());
        for (index, (name, img)) in images.iter().enumerate() {
            let (x, top) = positions[index];
            let row = (img.width() * 4) as usize;
            for y in 0..img.height() as usize {
                let src = y * row;
                let dst = (((top + y as u32) * width + x) * 4) as usize;
                if src + row <= img.as_raw().len() && dst + row <= pixels.len() {
                    pixels[dst..dst + row].copy_from_slice(&img.as_raw()[src..src + row]);
                }
            }
            slots.push((
                name,
                [
                    x as f32 / width as f32,
                    top as f32 / height as f32,
                    (x + img.width()) as f32 / width as f32,
                    (top + img.height()) as f32 / height as f32,
                ],
                img.width() as f32 / img.height().max(1) as f32,
            ));
        }
        let id = self.textures.alloc_texture_id();
        if let Err(e) = self.textures.upload(&self.device, &self.queue, id, width, height, &pixels) {
            eprintln!("[overlay] no se pudo subir el arte de los controles: {e}");
            return None
        }
        // The overlay resolves a control's artwork through this table.
        TOUCH_SKIN.with(|cell| *cell.lock().unwrap() = Some((id, slots)));
        self.touch_skin = Some(id);
        Some(id)
    }

    /// Loads the label artwork (the clean text images the launcher ships), if there is any.
    ///
    /// It goes in a texture of its own so it can be sampled with LINEAR, like the OpenGL backend
    /// does, instead of the crisp nearest the rest of the overlay uses.
    #[cfg(target_os = "android")]
    fn touch_labels(&mut self) -> Option<u32> {
        if let Some(id) = self.touch_labels {
            return Some(id)
        }
        use crate::touch_controls;
        let dir = crate::android_main::storage_dir().join("touch-labels");
        let mut texts: Vec<&'static str> = Vec::new();
        for control in touch_controls::CLASSIC_CONTROLS {
            if let touch_controls::Label::Text(text) = control.label {
                if !texts.contains(&text) {
                    texts.push(text);
                }
            }
        }
        let mut images: Vec<(&'static str, image::RgbaImage)> = Vec::new();
        for text in texts {
            if let Ok(img) = image::open(dir.join(crate::render::opengl::label_file(text))) {
                images.push((text, img.to_rgba8()));
            }
        }
        if images.is_empty() {
            return None
        }
        let height = images.iter().map(|(_, img)| img.height()).max().unwrap_or(1).max(1);
        let width: u32 = images.iter().map(|(_, img)| img.width()).sum::<u32>().max(1);
        let mut pixels = vec![0u8; (width * height * 4) as usize];
        let mut slots: Vec<(String, [f32; 4], f32)> = Vec::new();
        let mut x = 0u32;
        for (text, img) in &images {
            let row = (img.width() * 4) as usize;
            for y in 0..img.height() as usize {
                let src = y * row;
                let dst = (((y as u32) * width + x) * 4) as usize;
                if src + row <= img.as_raw().len() && dst + row <= pixels.len() {
                    pixels[dst..dst + row].copy_from_slice(&img.as_raw()[src..src + row]);
                }
            }
            slots.push((
                (*text).to_string(),
                [
                    x as f32 / width as f32,
                    0.0,
                    (x + img.width()) as f32 / width as f32,
                    img.height() as f32 / height as f32,
                ],
                img.width() as f32 / img.height().max(1) as f32,
            ));
            x += img.width();
        }
        let id = self.textures.alloc_texture_id();
        if let Err(e) = self.textures.upload(&self.device, &self.queue, id, width, height, &pixels) {
            eprintln!("[overlay] no se pudieron subir las etiquetas: {e}");
            return None
        }
        TOUCH_LABELS.with(|cell| *cell.lock().unwrap() = Some((id, slots)));
        self.touch_labels = Some(id);
        Some(id)
    }

    /// Uploads the party's overworld sprites into a strip texture, one cell per slot.
    ///
    /// The wheel shows the team with the game's own artwork, so this reads the same files the
    /// OpenGL backend does. Re-uploaded only when the team changes.
    #[cfg(target_os = "android")]
    fn party_strip(&mut self, species: &[i32; crate::touch_controls::PICKER_SLOTS]) -> Option<u32> {
        const CELL: u32 = 64;
        if self.party_species.as_ref() == Some(species) {
            return self.party_texture
        }

        use crate::compat::f3r;
        let dir = crate::android_main::storage_dir().join("game").join(f3r::save::ROOT);
        let cells = crate::touch_controls::PICKER_SLOTS;
        let cell = CELL as usize;
        let stride = cell * cells;
        let mut strip = vec![0u8; stride * cell * 4];
        for (slot, &value) in species.iter().enumerate() {
            if value == 0 {
                continue
            }
            let shiny = f3r::world::is_shiny_sprite(value);
            let folder =
                if shiny { f3r::world::SHINY_OVERWORLD_SPRITE_DIR } else { f3r::world::OVERWORLD_SPRITE_DIR };
            let Ok(image) = image::open(format!("{}/{}/{}.{}", dir.display(), folder, value, f3r::world::SPRITE_EXT))
            else {
                continue
            };
            let image = image.to_rgba8();
            let (iw, ih) = (image.width() as usize, image.height() as usize);
            if iw == 0 || ih == 0 {
                continue
            }
            // Scale to fit the cell, square and centred, sampling nearest so it stays crisp.
            let scale = (cell as f32 / iw as f32).min(cell as f32 / ih as f32);
            let (dw, dh) = (((iw as f32 * scale) as usize).max(1), ((ih as f32 * scale) as usize).max(1));
            let (ox, oy) = ((cell - dw) / 2, (cell - dh) / 2);
            for y in 0..dh {
                for x in 0..dw {
                    let pixel = image.get_pixel((x * iw / dw) as u32, (y * ih / dh) as u32).0;
                    let index = ((oy + y) * stride + slot * cell + ox + x) * 4;
                    strip[index..index + 4].copy_from_slice(&pixel);
                }
            }
        }

        let id = match self.party_texture {
            Some(id) => id,
            None => self.textures.alloc_texture_id(),
        };
        if let Err(e) = self.textures.upload(&self.device, &self.queue, id, stride as u32, CELL, &strip) {
            eprintln!("[overlay] no se pudo subir el equipo: {e}");
            return None
        }
        self.party_texture = Some(id);
        self.party_species = Some(*species);
        Some(id)
    }

    /// Draws the on-screen touch controls over the presented frame.
    ///
    /// On Android the game is unplayable without them, and they have to be drawn by whichever
    /// backend is presenting: they used to live only in the OpenGL path, so switching to wgpu made
    /// every button disappear. The shapes come from the same geometry the OpenGL backend uses, so
    /// both look identical.
    #[cfg(target_os = "android")]
    fn draw_touch_overlay(&mut self, target: &wgpu::TextureView, window_width: u32, window_height: u32) {
        use crate::touch_controls;
        if window_width == 0 || window_height == 0 {
            return
        }
        let (ww, wh) = (window_width as f32, window_height as f32);
        let appearance = crate::touch_appearance::settings();

        // The classic look is the pixel-art skin the launcher ships; load it the first time the
        // overlay is drawn. Without artwork the drawn shapes below are used instead.
        self.touch_skin();

        // Vertices in the format the UI pipeline expects: x, y, r, g, b, a, u, v. The shapes are
        // generated in NDC by the shared helpers, so they are converted back to window pixels here.
        let mut ndc: Vec<f32> = Vec::new();
        // Textured quads: same layout, but with the control's own UVs (they sample the skin).
        let mut art: Vec<[f32; 8]> = Vec::new();
        // Party sprites, which live in a texture of their own.
        let mut party_art: Vec<[f32; 8]> = Vec::new();
        // Clean label artwork, sampled with LINEAR.
        let mut label_art: Vec<[f32; 8]> = Vec::new();
        self.touch_labels();
        for (i, c) in touch_controls::controls().iter().enumerate() {
            if !touch_controls::is_active(i) {
                continue
            }
            if touch_controls::wheel_open() && c.name == touch_controls::PARTY_NAME {
                continue
            }
            let pressed = touch_controls::is_pressed(i);
            let (visual_scale, brightness) = crate::touch_appearance::visual(i);
            let start = ndc.len();
            let center = match touch_controls::window_shape_for(i, ww, wh) {
                touch_controls::Shape::Circle { cx, cy, .. } | touch_controls::Shape::Rect { cx, cy, .. } => (cx, cy),
            };
            // With artwork, the button is that picture; the drawn disc is only the fallback.
            if let Some((uv, aspect)) = touch_art(c.name) {
                let (cx, cy, half) = match touch_controls::window_shape_for(i, ww, wh) {
                    touch_controls::Shape::Circle { cx, cy, r } => (cx * ww, cy * wh, r * ww),
                    touch_controls::Shape::Rect { cx, cy, w, h } => (cx * ww, cy * wh, (w * ww + h * wh) / 4.0),
                };
                let (half_w, half_h) = (half * aspect, half);
                let alpha = touch_controls::sprite_opacity();
                let mut corner = |x: f32, y: f32, u: f32, v: f32| {
                    art.push([x, y, 1.0, 1.0, 1.0, alpha, u, v]);
                };
                let (x1, y1, x2, y2) = (cx - half_w, cy - half_h, cx + half_w, cy + half_h);
                corner(x1, y1, uv[0], uv[1]);
                corner(x2, y1, uv[2], uv[1]);
                corner(x2, y2, uv[2], uv[3]);
                corner(x1, y1, uv[0], uv[1]);
                corner(x2, y2, uv[2], uv[3]);
                corner(x1, y2, uv[0], uv[3]);
                continue
            }
            let (lcx, lcy, lr) = crate::render::opengl::push_modern_control(
                &mut ndc,
                ww,
                wh,
                touch_controls::window_shape_for(i, ww, wh),
                pressed,
                touch_controls::control_art_aspect(c.name),
                appearance.tint,
            );
            match c.label {
                touch_controls::Label::Text(text) => {
                    if let Some((uv, aspect)) = touch_label_art(text) {
                        // Capped so a wide label still fits inside its button.
                        let half_h = lr * 0.42;
                        let max_w = half_h * 3.0;
                        let mut hh = half_h;
                        let mut hw = hh * aspect;
                        if hw > max_w {
                            hh *= max_w / hw;
                            hw = max_w;
                        }
                        let alpha = touch_controls::sprite_opacity();
                        let mut corner = |px: f32, py: f32, u: f32, v: f32| {
                            label_art.push([px, py, 1.0, 1.0, 1.0, alpha, u, v]);
                        };
                        corner(lcx - hw, lcy - hh, uv[0], uv[1]);
                        corner(lcx + hw, lcy - hh, uv[2], uv[1]);
                        corner(lcx + hw, lcy + hh, uv[2], uv[3]);
                        corner(lcx - hw, lcy - hh, uv[0], uv[1]);
                        corner(lcx + hw, lcy + hh, uv[2], uv[3]);
                        corner(lcx - hw, lcy + hh, uv[0], uv[3]);
                    } else {
                        crate::render::opengl::push_text(
                            &mut ndc,
                            ww,
                            wh,
                            text,
                            lcx,
                            lcy,
                            lr * 0.28,
                            [0.72, 0.72, 0.76, 0.92],
                        );
                    }
                },
                touch_controls::Label::Arrow(dir) => crate::render::opengl::push_arrow(
                    &mut ndc,
                    ww,
                    wh,
                    lcx,
                    lcy,
                    lr * 0.5,
                    dir,
                    [0.72, 0.72, 0.76, 0.92],
                ),
                touch_controls::Label::None => {},
            }
            crate::render::opengl::transform_touch_vertices(&mut ndc[start..], center, visual_scale, brightness);
        }
        // Field-move selector: the picture that stands in for the numeric menu.
        if crate::field_controls::open() {
            if let Some((uv, _)) = touch_art("field-selector") {
                let (x, y, w, h) = crate::field_controls::rect(ww, wh);
                let alpha = touch_controls::sprite_opacity();
                let mut corner = |px: f32, py: f32, u: f32, v: f32| {
                    art.push([px, py, 1.0, 1.0, 1.0, alpha, u, v]);
                };
                corner(x, y, uv[0], uv[1]);
                corner(x + w, y, uv[2], uv[1]);
                corner(x + w, y + h, uv[2], uv[3]);
                corner(x, y, uv[0], uv[1]);
                corner(x + w, y + h, uv[2], uv[3]);
                corner(x, y + h, uv[0], uv[3]);
            }
        }

        // Party wheel: the honeycomb, each Pokémon of the team in its hexagon and the border on the
        // one the finger points at. The sprites come from the game's own files.
        if touch_controls::wheel_open() {
            let species = touch_controls::party_species();
            let (cx, cy) = touch_controls::wheel_center(ww, wh);
            let radius = touch_controls::wheel_radius(ww, wh);
            let selected = touch_controls::wheel_selected();
            let alpha = touch_controls::sprite_opacity();
            if let Some((uv, _)) = touch_art("selector") {
                let half = radius / touch_controls::SELECTOR_SLOT_DISTANCE;
                let tall = half * touch_controls::SELECTOR_ASPECT;
                let mut corner = |px: f32, py: f32, u: f32, v: f32| {
                    art.push([px, py, 1.0, 1.0, 1.0, alpha, u, v]);
                };
                corner(cx - half, cy - tall, uv[0], uv[1]);
                corner(cx + half, cy - tall, uv[2], uv[1]);
                corner(cx + half, cy + tall, uv[2], uv[3]);
                corner(cx - half, cy - tall, uv[0], uv[1]);
                corner(cx + half, cy + tall, uv[2], uv[3]);
                corner(cx - half, cy + tall, uv[0], uv[3]);
            }
            // The party sprites live in their own strip, one cell per slot.
            if let Some(strip) = self.party_strip(&species) {
                let cell = 1.0 / touch_controls::PICKER_SLOTS as f32;
                for (index, &value) in species.iter().enumerate() {
                    if value == 0 {
                        continue
                    }
                    let (ix, iy) = touch_controls::wheel_item_center(index, ww, wh);
                    let inner = radius * 0.42;
                    let x0 = index as f32 * cell;
                    let mut corner = |px: f32, py: f32, u: f32, v: f32| {
                        party_art.push([px, py, 1.0, 1.0, 1.0, 1.0, u, v]);
                    };
                    corner(ix - inner, iy - inner, x0, 0.0);
                    corner(ix + inner, iy - inner, x0 + cell, 0.0);
                    corner(ix + inner, iy + inner, x0 + cell, 1.0);
                    corner(ix - inner, iy - inner, x0, 0.0);
                    corner(ix + inner, iy + inner, x0 + cell, 1.0);
                    corner(ix - inner, iy + inner, x0, 1.0);
                }
                let _ = strip;
            }
            if let Some((uv, _)) = touch_art("selection") {
                let (ix, iy) = touch_controls::wheel_item_center(selected.max(0) as usize, ww, wh);
                let wide = radius * 0.55;
                let tall = wide * 1.155;
                let mut corner = |px: f32, py: f32, u: f32, v: f32| {
                    art.push([px, py, 1.0, 1.0, 1.0, alpha, u, v]);
                };
                corner(ix - wide, iy - tall, uv[0], uv[1]);
                corner(ix + wide, iy - tall, uv[2], uv[1]);
                corner(ix + wide, iy + tall, uv[2], uv[3]);
                corner(ix - wide, iy - tall, uv[0], uv[1]);
                corner(ix + wide, iy + tall, uv[2], uv[3]);
                corner(ix - wide, iy + tall, uv[0], uv[3]);
            }
        }

        // The movement control, drawn the same way.
        if touch_controls::movement_active() {
            let (jcx, jcy) = touch_controls::joystick_center_in_window(ww, wh);
            let (cx, cy) = (jcx * ww, jcy * wh);
            let r = touch_controls::joystick_radius() * wh;
            let (ox, oy) = touch_controls::movement_offset();
            // With artwork the joystick is the base picture with the knob on top; otherwise the
            // drawn ring.
            let base = touch_art("joystick");
            let knob = touch_art("joystick-knob");
            if let (Some((base_uv, base_aspect)), Some((knob_uv, _))) = (base, knob) {
                let alpha = touch_controls::sprite_opacity();
                let half = r * 1.05 * base_aspect.max(1.0);
                let mut push_quad = |x1: f32, y1: f32, x2: f32, y2: f32, uv: [f32; 4]| {
                    let mut corner = |x: f32, y: f32, u: f32, v: f32| {
                        art.push([x, y, 1.0, 1.0, 1.0, alpha, u, v]);
                    };
                    corner(x1, y1, uv[0], uv[1]);
                    corner(x2, y1, uv[2], uv[1]);
                    corner(x2, y2, uv[2], uv[3]);
                    corner(x1, y1, uv[0], uv[1]);
                    corner(x2, y2, uv[2], uv[3]);
                    corner(x1, y2, uv[0], uv[3]);
                };
                push_quad(cx - half, cy - half, cx + half, cy + half, base_uv);
                let (kx, ky) = (cx + ox * r * 0.72, cy + oy * r * 0.72);
                let kh = r * 0.88;
                push_quad(kx - kh, ky - kh, kx + kh, ky + kh, knob_uv);
            } else {
            crate::render::opengl::push_modern_movement(
                &mut ndc,
                ww,
                wh,
                cx,
                cy,
                r,
                ox,
                oy,
                touch_controls::move_mode() == touch_controls::MoveMode::Dpad,
                appearance.tint,
            );
            }
        }
        let opacity = touch_controls::sprite_opacity();
        if !ndc.is_empty() && opacity < 1.0 {
            for chunk in ndc.chunks_exact_mut(8) {
                chunk[5] *= opacity;
            }
        }
        if ndc.is_empty() {
            return
        }

        // The shared shapes leave the UV at (0,0), which in OpenGL samples its dedicated 1x1 white
        // texture. Here the texture bound is the game's atlas, and its corner is transparent: the
        // whole overlay would draw as nothing. Flat shapes have to sample the centre of the white
        // pixel's own cell instead.
        let (white_u, white_v) = {
            let white = self.white_pixel;
            match self.textures.rect(white).copied().and_then(|rect| {
                self.textures.size(rect.atlas_id()).map(|(w, h)| {
                    let (x, y, rw, rh) = rect.cell();
                    (
                        (x as f32 + rw as f32 * 0.5) / w.max(1) as f32,
                        (y as f32 + rh as f32 * 0.5) / h.max(1) as f32,
                    )
                })
            }) {
                Some(uv) => uv,
                None => (0.0, 0.0),
            }
        };

        if ndc.is_empty() && art.is_empty() && party_art.is_empty() && label_art.is_empty() {
            return
        }

        // NDC back to window pixels for the UI pipeline, which works in screen coordinates.
        let mut vertices: Vec<[f32; 8]> = Vec::with_capacity(ndc.len() / 8);
        for chunk in ndc.chunks_exact(8) {
            let x = (chunk[0] + 1.0) * 0.5 * ww;
            let y = (1.0 - chunk[1]) * 0.5 * wh;
            vertices.push([x, y, chunk[2], chunk[3], chunk[4], chunk[5], white_u, white_v]);
        }

        let size = [ww, wh, 0.0, 0.0];
        self.queue.write_buffer(&self.ui_uniform, 0, bytemuck::cast_slice(&size));
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator touch encoder") });
        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("gm8emulator touch pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: target,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations { load: wgpu::LoadOp::Load, store: wgpu::StoreOp::Store },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
                multiview_mask: None,
            });
            pass.set_pipeline(&self.ui.pipeline);
            pass.set_bind_group(0, &self.ui_uniform_group, &[]);

            // The artwork samples the skin atlas; the flat shapes sample the game atlas' white pixel.
            if !art.is_empty() {
                if let Some(id) = self.touch_skin {
                    if let Some(atlas) = self.textures.bind_group(id) {
                        let bytes = bytemuck::cast_slice(&art);
                        let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
                            label: Some("gm8emulator touch art"),
                            size: bytes.len() as wgpu::BufferAddress,
                            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                            mapped_at_creation: false,
                        });
                        self.queue.write_buffer(&buffer, 0, bytes);
                        pass.set_bind_group(1, atlas, &[]);
                        pass.set_vertex_buffer(0, buffer.slice(..));
                        pass.draw(0..art.len() as u32, 0..1);
                    }
                }
            }
            if !label_art.is_empty() {
                if let Some(id) = self.touch_labels {
                    if let Some(atlas) = self.textures.bind_group(id) {
                        let bytes = bytemuck::cast_slice(&label_art);
                        let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
                            label: Some("gm8emulator touch labels"),
                            size: bytes.len() as wgpu::BufferAddress,
                            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                            mapped_at_creation: false,
                        });
                        self.queue.write_buffer(&buffer, 0, bytes);
                        pass.set_bind_group(1, atlas, &[]);
                        pass.set_vertex_buffer(0, buffer.slice(..));
                        pass.draw(0..label_art.len() as u32, 0..1);
                    }
                }
            }
            if !party_art.is_empty() {
                if let Some(id) = self.party_texture {
                    if let Some(atlas) = self.textures.bind_group(id) {
                        let bytes = bytemuck::cast_slice(&party_art);
                        let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
                            label: Some("gm8emulator party wheel"),
                            size: bytes.len() as wgpu::BufferAddress,
                            usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                            mapped_at_creation: false,
                        });
                        self.queue.write_buffer(&buffer, 0, bytes);
                        pass.set_bind_group(1, atlas, &[]);
                        pass.set_vertex_buffer(0, buffer.slice(..));
                        pass.draw(0..party_art.len() as u32, 0..1);
                    }
                }
            }
            if !vertices.is_empty() {
                let white = self.resolve_atlas_id(None);
                if let Some(atlas) = self.textures.bind_group(white) {
                    let bytes = bytemuck::cast_slice(&vertices);
                    let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
                        label: Some("gm8emulator touch vertices"),
                        size: bytes.len() as wgpu::BufferAddress,
                        usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
                        mapped_at_creation: false,
                    });
                    self.queue.write_buffer(&buffer, 0, bytes);
                    pass.set_bind_group(1, atlas, &[]);
                    pass.set_vertex_buffer(0, buffer.slice(..));
                    pass.draw(0..vertices.len() as u32, 0..1);
                }
            }
        }
        self.queue.submit(Some(encoder.finish()));
    }

    /// Reconfigura la surface (tamaño nuevo o tras `Lost`/`Outdated`).
    fn reconfigure_surface(&mut self) {
        self.config.width = self.config.width.max(1);
        self.config.height = self.config.height.max(1);
        self.surface.configure(&self.device, &self.config);
    }

    /// Id de textura de GPU que contiene un sprite, o el del píxel blanco si no se conoce.
    ///
    /// `AtlasRef` indexa la lista de sprites; `TextureStore` lo traduce al id de la textura que lo
    /// empaqueta. El píxel blanco cubre las primitivas sin textura y los refs que aún no existen.
    fn resolve_atlas_id(&self, atlas_ref: Option<AtlasRef>) -> u32 {
        atlas_ref
            .and_then(|r| self.textures.texture_id(r))
            .or_else(|| self.textures.texture_id(self.white_pixel))
            .unwrap_or(0)
    }

    /// Bind group del atlas pedido (o del píxel blanco si ese id no tiene textura).
    fn atlas_group(&self, atlas_id: u32) -> &wgpu::BindGroup {
        self.textures
            .bind_group(atlas_id)
            .or_else(|| self.textures.texture_id(self.white_pixel).and_then(|id| self.textures.bind_group(id)))
            .expect("el píxel blanco siempre tiene bind group")
    }

    /// Bind group de uniforms propio de un lote, con la proyección, el modelo y la interpolación
    /// que estaban activos cuando se encoló.
    fn uniform_group(&self, projection: &[[f32; 4]; 4], model: &[[f32; 4]; 4], interp_amount: f32) -> wgpu::BindGroup {
        use wgpu::util::DeviceExt as _;
        let uniforms = pipeline::SpriteUniforms {
            projection: *projection,
            model: *model,
            interp_amount: interp_amount.clamp(0.0, 1.0),
            ..pipeline::SpriteUniforms::default()
        };
        let buffer = self.device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("gm8emulator batch uniforms"),
            contents: bytemuck::bytes_of(&uniforms),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });
        self.device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("gm8emulator batch uniforms"),
            layout: &self.pipeline.uniform_bind_group_layout,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: buffer.as_entire_binding() }],
        })
    }

    /// Convierte los vértices pendientes en buffers de GPU, listos para que `present` los dibuje.
    ///
    /// Los lotes ya preparados se conservan: un frame puede cambiar de vista a mitad de dibujo y lo
    /// anterior sigue formando parte de la imagen. La proyección, el modelo y el puerto se congelan
    /// aquí, igual que OpenGL dibuja con el estado vigente en el flush.
    fn prepare_batches(&mut self) {
        if self.frame.draw_list.is_empty() {
            return;
        }

        let projection = self.frame.projection;
        let model = columns_from_mat4(self.frame.model_matrix);
        let interp_amount = self.frame.interp_amount;
        let port = self.frame.port;
        let full = (0, 0, self.fb_size.0 as i32, self.fb_size.1 as i32);
        let port = if port.2 > 0 && port.3 > 0 { port } else { full };

        let mut prepared = Vec::with_capacity(self.frame.draw_list.len());
        for batch in self.frame.draw_list.batches() {
            let (buffer, vertices) = draw::build_vertex_buffer(&self.device, &self.queue, &batch.vertices);
            let uniform_group = self.uniform_group(&projection, &model, interp_amount);
            let atlas_group = self.atlas_group(batch.atlas_id).clone();

            prepared.push(PreparedBatch {
                buffer,
                vertices,
                topology: batch.topology,
                port,
                uniform_group,
                atlas_group,
            });
        }

        self.frame.prepared.extend(prepared);
        self.frame.draw_list.clear();
    }

    /// Vértices de un quad que cubre todo el NDC, para rellenar un puerto con un color plano. Con
    /// la proyección identidad el shader los deja pasar tal cual.
    /// Volcado de la cola de dibujo. `pub(crate)` para que los módulos separados del trait
    /// puedan usarlo; el método del trait delega aquí.
    pub(crate) fn flush_now(&mut self) {
        self.prepare_batches();
    }

    /// Guarda el puerto de render. `pub(crate)` por la misma razón.
    pub(crate) fn apply_view(&mut self, x: i32, y: i32, w: i32, h: i32, _angle: f64, xport: i32, yport: i32, wport: i32, hport: i32) {
        self.frame.port = (xport, yport, wport.max(1), hport.max(1));
        let _ = (x, y, w, h);
    }

    fn clear_quad_vertices(colour: wgpu::Color) -> [Vertex; 6] {
        // El fragment multiplica la textura (blanca) por el color de vértice y devuelve el
        // resultado premultiplicado. El alpha va a 1 para que el quad reemplace lo de debajo en vez
        // de mezclarse: la surface es opaca y `glClear` tampoco mezcla.
        let blend = [colour.r as f32, colour.g as f32, colour.b as f32, 1.0];
        let vertex = |x: f32, y: f32| Vertex {
            pos: [x, y, 0.0],
            interp: [0.0, 0.0],
            tex_coord: [0.0, 0.0],
            blend,
            atlas_xywh: [0.0, 0.0, 1.0, 1.0],
            normal: [0.0, 0.0, 1.0],
        };
        [
            vertex(-1.0, -1.0),
            vertex(1.0, -1.0),
            vertex(1.0, 1.0),
            vertex(-1.0, -1.0),
            vertex(1.0, 1.0),
            vertex(-1.0, 1.0),
        ]
    }

    /// Prepara el draw de una limpieza: su buffer de quad y los bind groups (proyección identidad
    /// y píxel blanco).
    fn build_clear_quad(&self, colour: wgpu::Color) -> (wgpu::Buffer, u32, wgpu::BindGroup, wgpu::BindGroup) {
        let vertices = Self::clear_quad_vertices(colour);
        let (buffer, count) = draw::build_vertex_buffer(&self.device, &self.queue, &vertices);
        let uniform = self.uniform_group(&identity_columns(), &identity_columns(), 0.0);
        let atlas = self.atlas_group(self.resolve_atlas_id(None)).clone();
        (buffer, count, uniform, atlas)
    }

    /// Convierte un viewport en píxeles del lienzo a un scissor válido para wgpu: enteros, dentro
    /// del target y no vacío. `None` si no queda nada que recortar.
    fn scissor_for(&self, viewport: (f32, f32, f32, f32)) -> Option<(u32, u32, u32, u32)> {
        let (vx, vy, vw, vh) = viewport;
        let target_w = self.fb_size.0.max(1);
        let target_h = self.fb_size.1.max(1);
        let x = vx.floor().max(0.0).min(target_w as f32) as u32;
        let y = vy.floor().max(0.0).min(target_h as f32) as u32;
        let right = (vx + vw).ceil().max(0.0).min(target_w as f32) as u32;
        let bottom = (vy + vh).ceil().max(0.0).min(target_h as f32) as u32;
        let w = right.saturating_sub(x);
        let h = bottom.saturating_sub(y);
        if w == 0 || h == 0 { None } else { Some((x, y, w, h)) }
    }

    /// Traduce el puerto de una vista (píxeles del framebuffer del juego) al viewport dentro del
    /// lienzo, recortado a su tamaño. `None` si no queda nada visible.
    ///
    /// El lienzo mide exactamente el framebuffer del juego, así que el puerto se usa tal cual: es
    /// lo mismo que hacía OpenGL con `glViewport` sobre su FBO.
    fn viewport_for(&self, port: (i32, i32, i32, i32)) -> Option<(f32, f32, f32, f32)> {
        let (fb_w, fb_h) = (self.fb_size.0.max(1) as f32, self.fb_size.1.max(1) as f32);
        let (px, py, pw, ph) = port;
        let (px, py, pw, ph) = if pw > 0 && ph > 0 { (px, py, pw, ph) } else { (0, 0, fb_w as i32, fb_h as i32) };
        // wgpu exige el viewport dentro del target y con coordenadas positivas: se recorta a lo
        // visible en vez de dejar que la validación rechace el draw.
        let vx = (px as f32).clamp(0.0, fb_w);
        let vy = (py as f32).clamp(0.0, fb_h);
        let vw = (pw.max(0) as f32).min(fb_w - vx);
        let vh = (ph.max(0) as f32).min(fb_h - vy);
        if vw <= 0.0 || vh <= 0.0 { None } else { Some((vx, vy, vw, vh)) }
    }

    /// Emite el frame al lienzo del juego.
    ///
    /// El lienzo se limpia con `LoadOp::Clear` al empezar, que es lo que hace el `glClear` de
    /// `setup_frame` en OpenGL: el color de clear del juego cubre todo el framebuffer y no queda
    /// nada del frame anterior. Después van las limpiezas que pida el juego y los lotes, en orden.
    ///
    /// Ojo: una limpieza no puede ser un quad, porque un quad se mezcla con lo de debajo y los
    /// píxeles que el juego no repinta se quedan con el contenido viejo (fantasmas sobre el suelo).
    fn render_frame(&mut self, target: &wgpu::TextureView) {
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator frame encoder") });

        if std::env::var_os("OPENGMK_WGPU_DEBUG").is_some() {
            eprintln!(
                "[wgpu] frame: {} lotes, {} limpiezas, lienzo {}x{}",
                self.frame.prepared.len(),
                self.frame.clears.len(),
                self.fb_size.0,
                self.fb_size.1
            );
        }

        let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("gm8emulator frame pass"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: target,
                depth_slice: None,
                resolve_target: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(self.frame.wgpu_clear_colour()),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: None,
            timestamp_writes: None,
            occlusion_query_set: None,
            multiview_mask: None,
        });
        pass.set_pipeline(&self.pipeline.pipeline);

        // Dibuja una limpieza del juego: su quad cubre su puerto, como `glClear` con el scissor
        // puesto. Solo se usa para los `clear_view` del juego, no para el clear inicial del lienzo.
        let draw_clear = |pass: &mut wgpu::RenderPass<'_>,
                          port: (i32, i32, i32, i32),
                          colour: wgpu::Color| {
            let Some((vx, vy, vw, vh)) = self.viewport_for(port) else { return };
            let (buffer, count, uniform, atlas) = self.build_clear_quad(colour);
            pass.set_viewport(vx, vy, vw, vh, 0.0, 1.0);
            if let Some((sx, sy, sw, sh)) = self.scissor_for((vx, vy, vw, vh)) {
                pass.set_scissor_rect(sx, sy, sw, sh);
            }
            pass.set_bind_group(0, &uniform, &[]);
            pass.set_bind_group(1, &atlas, &[]);
            pass.set_vertex_buffer(0, buffer.slice(..));
            pass.draw(0..count, 0..1);
        };

        for (index, batch) in self.frame.prepared.iter().enumerate() {
            // Limpiezas que el juego pidió justo antes de este lote.
            for clear in self.frame.clears.iter().filter(|c| c.after == index) {
                draw_clear(&mut pass, clear.port, clear.colour);
            }

            if batch.vertices == 0 {
                continue;
            }
            // TODO(wgpu): en wgpu la topología va en el pipeline y `SpritePipeline` solo expone el
            // de triángulos. Puntos y líneas necesitan su propio pipeline.
            if batch.topology != wgpu::PrimitiveTopology::TriangleList {
                continue;
            }
            let Some((vx, vy, vw, vh)) = self.viewport_for(batch.port) else { continue };
            pass.set_viewport(vx, vy, vw, vh, 0.0, 1.0);
            if let Some((sx, sy, sw, sh)) = self.scissor_for((vx, vy, vw, vh)) {
                pass.set_scissor_rect(sx, sy, sw, sh);
            }
            pass.set_bind_group(0, &batch.uniform_group, &[]);
            pass.set_bind_group(1, &batch.atlas_group, &[]);
            pass.set_vertex_buffer(0, batch.buffer.slice(..));
            pass.draw(0..batch.vertices, 0..1);
        }
        // Limpiezas pedidas después del último lote.
        let total = self.frame.prepared.len();
        for clear in self.frame.clears.iter().filter(|c| c.after >= total) {
            draw_clear(&mut pass, clear.port, clear.colour);
        }
        drop(pass);

        self.queue.submit(Some(encoder.finish()));
    }

    /// Descarta lo dibujado pero todavía no presentado (frame perdido, tapado o fuera de tiempo).
    fn drop_frame(&mut self) {
        self.frame.prepared.clear();
        self.frame.clears.clear();
        self.frame.draw_list.clear();
    }
}

// El trait se reparte en varios módulos para poder trabajar en paralelo.
// Cada uno contiene `impl RendererImpl` con su grupo de métodos.
// El trait se implementa aquí delegando en módulos separados (trait_*.rs),
// para poder desarrollarlos en paralelo sin conflictos.
mod trait_state;
mod trait_textures;
mod trait_geometry;
mod trait_surfaces;

include!("trait_impl.rs");
