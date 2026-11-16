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
    /// A diferencia de `update_matrix` del backend OpenGL, aquí no hay volteo vertical extra: allí
    /// compensa que la imagen se dibuja en una textura que luego se presenta con la Y invertida,
    /// pero aquí se dibuja directamente en la surface. La matriz de proyección ya trae su propio
    /// `-2/h`, suficiente para que la Y del juego (que crece hacia abajo) acabe arriba en pantalla.
    fn update_projection(&mut self) {
        self.projection = columns_from_mat4(mat4mult(self.view_matrix, self.proj_matrix));
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

pub struct RendererImpl {
    device: wgpu::Device,
    queue: wgpu::Queue,
    surface: wgpu::Surface<'static>,
    config: wgpu::SurfaceConfiguration,
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

        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::VULKAN | wgpu::Backends::GL,
            ..wgpu::InstanceDescriptor::new_without_display_handle()
        });

        // `ramen::window::Window` no implementa `Clone`, así que no podemos pasarla por valor
        // (que es lo que haría que wgpu la mantuviera viva y la surface fuese `'static`).
        // En su lugar construimos la surface desde los handles crudos y reclamamos `'static`.
        //
        // SAFETY: `SurfaceTargetUnsafe::from_window` solo lee el handle nativo; la ventana
        // (y su conexión de display) sigue siendo dueña del mismo. En el motor la `Window`
        // se crea antes que el `Renderer` y se destruye después (en `Game`, el campo
        // `renderer` se declara antes que `window`), de modo que la surface nunca sobrevive
        // a la ventana. Todo acceso a la surface ocurre desde el hilo principal.
        let surface: wgpu::Surface<'static> = unsafe {
            let target = wgpu::SurfaceTargetUnsafe::from_window(window)
                .map_err(|e| format!("wgpu: no se pudo obtener el handle nativo de la ventana: {e}"))?;
            instance.create_surface_unsafe(target).map_err(|e| format!("wgpu: no se pudo crear la surface: {e}"))?
        };

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

        Ok(Self {
            device,
            queue,
            surface,
            config,
            fb_size,
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
        })
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

    /// Convierte un viewport en píxeles de ventana a un scissor válido para wgpu: enteros, dentro
    /// del target y no vacío. `None` si no queda nada que recortar.
    fn scissor_for(&self, viewport: (f32, f32, f32, f32)) -> Option<(u32, u32, u32, u32)> {
        let (vx, vy, vw, vh) = viewport;
        let target_w = self.config.width;
        let target_h = self.config.height;
        let x = vx.floor().max(0.0).min(target_w as f32) as u32;
        let y = vy.floor().max(0.0).min(target_h as f32) as u32;
        let right = (vx + vw).ceil().max(0.0).min(target_w as f32) as u32;
        let bottom = (vy + vh).ceil().max(0.0).min(target_h as f32) as u32;
        let w = right.saturating_sub(x);
        let h = bottom.saturating_sub(y);
        if w == 0 || h == 0 { None } else { Some((x, y, w, h)) }
    }

    /// Traduce el puerto de una vista (píxeles del framebuffer) a píxeles de ventana y lo recorta al
    /// target. Devuelve `(x, y, w, h)` o `None` si no queda nada visible.
    fn viewport_for(&self, port: (i32, i32, i32, i32), game: (f32, f32, f32, f32)) -> Option<(f32, f32, f32, f32)> {
        let (gx, gy, gw, gh) = game;
        let (target_w, target_h) = (self.config.width as f32, self.config.height as f32);
        let (fb_w, fb_h) = (self.fb_size.0.max(1) as f32, self.fb_size.1.max(1) as f32);
        let (px, py, pw, ph) = port;
        let (px, py, pw, ph) = if pw > 0 && ph > 0 { (px, py, pw, ph) } else { (0, 0, fb_w as i32, fb_h as i32) };
        let sx = gw / fb_w;
        let sy = gh / fb_h;
        // wgpu exige el viewport dentro del target y con coordenadas positivas: se recorta a lo
        // visible en vez de dejar que la validación rechace el draw.
        let vx = (gx + px as f32 * sx).clamp(0.0, target_w);
        let vy = (gy + py as f32 * sy).clamp(0.0, target_h);
        let vw = (pw.max(0) as f32 * sx).min(target_w - vx);
        let vh = (ph.max(0) as f32 * sy).min(target_h - vy);
        if vw <= 0.0 || vh <= 0.0 { None } else { Some((vx, vy, vw, vh)) }
    }

    /// Emite el frame en la surface.
    ///
    /// Primero deja toda la ventana en negro -las bandas del letterbox, como el `glClear` negro que
    /// OpenGL hace antes de blitear- y después dibuja las limpiezas y los lotes en el orden en que
    /// el juego los pidió. `game` es el rectángulo de la ventana donde cae la imagen
    /// (`present_rect`). Las dos pasadas son necesarias porque `LoadOp::Clear` ignora viewport y
    /// scissor: el negro tiene que cubrir todo el target, pero lo demás solo su rectángulo.
    fn render_frame(&mut self, target: &wgpu::TextureView, game: (f32, f32, f32, f32)) {
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: Some("gm8emulator frame encoder") });

        // Pasada 1: la ventana entera a negro.
        {
            let _pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("gm8emulator black pass"),
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

        let game_visible = self.viewport_for((0, 0, 0, 0), game).is_some();
        if !game_visible {
            self.queue.submit(Some(encoder.finish()));
            return;
        }

        // Si el juego no pidió ninguna limpieza, al menos se rellena el lienzo con el color de clear
        // del frame, para que no quede el negro del letterbox dentro del rectángulo del juego.
        let fallback_clear = if self.frame.clears.is_empty() {
            let (buffer, count, uniform, atlas) = self.build_clear_quad(self.frame.wgpu_clear_colour());
            Some((0, (buffer, count, uniform, atlas)))
        } else {
            None
        };

        let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("gm8emulator frame pass"),
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
        pass.set_pipeline(&self.pipeline.pipeline);

        // Dibuja una limpieza: su quad cubre su puerto, como `glClear` con el scissor puesto.
        let draw_clear =
            |pass: &mut wgpu::RenderPass<'_>,
             port: (i32, i32, i32, i32),
             colour: wgpu::Color,
             prepared: Option<(wgpu::Buffer, u32, wgpu::BindGroup, wgpu::BindGroup)>| {
                let Some((vx, vy, vw, vh)) = self.viewport_for(port, game) else { return };
                let (buffer, count, uniform, atlas) = match prepared {
                    Some(prepared) => prepared,
                    None => self.build_clear_quad(colour),
                };
                pass.set_viewport(vx, vy, vw, vh, 0.0, 1.0);
                if let Some((sx, sy, sw, sh)) = self.scissor_for((vx, vy, vw, vh)) {
                    pass.set_scissor_rect(sx, sy, sw, sh);
                }
                pass.set_bind_group(0, &uniform, &[]);
                pass.set_bind_group(1, &atlas, &[]);
                pass.set_vertex_buffer(0, buffer.slice(..));
                pass.draw(0..count, 0..1);
            };

        if let Some((after, prepared)) = fallback_clear {
            let _ = after;
            draw_clear(&mut pass, (0, 0, 0, 0), self.frame.wgpu_clear_colour(), Some(prepared));
        }

        for (index, batch) in self.frame.prepared.iter().enumerate() {
            // Limpiezas que el juego pidió justo antes de este lote.
            for clear in self.frame.clears.iter().filter(|c| c.after == index) {
                draw_clear(&mut pass, clear.port, clear.colour, None);
            }

            if batch.vertices == 0 {
                continue;
            }
            // TODO(wgpu): en wgpu la topología va en el pipeline y `SpritePipeline` solo expone el
            // de triángulos. Puntos y líneas necesitan su propio pipeline.
            if batch.topology != wgpu::PrimitiveTopology::TriangleList {
                continue;
            }
            let Some((vx, vy, vw, vh)) = self.viewport_for(batch.port, game) else { continue };
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
            draw_clear(&mut pass, clear.port, clear.colour, None);
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

impl RendererTrait for RendererImpl {
    fn as_any(&self) -> &dyn Any {
        todo!("wgpu: as_any")
    }

    fn max_texture_size(&self) -> u32 {
        self.max_texture_size
    }

    fn push_atlases(&mut self, atl: AtlasBuilderAny) -> Result<(), String> {
        todo!("wgpu: push_atlases")
    }

    fn upload_sprite(
        &mut self,
        data: Box<[u8]>,
        width: i32,
        height: i32,
        origin_x: i32,
        origin_y: i32,
    ) -> Result<AtlasRef, String> {
        todo!("wgpu: upload_sprite")
    }

    fn duplicate_sprite(&mut self, atlas_ref: AtlasRef) -> Result<AtlasRef, String> {
        todo!("wgpu: duplicate_sprite")
    }

    fn delete_sprite(&mut self, atlas_ref: AtlasRef) {
        todo!("wgpu: delete_sprite")
    }

    fn resize_framebuffer(&mut self, width: u32, height: u32, store: bool) {
        // `width`/`height` son el tamaño del lienzo del juego, no el de la ventana: el motor los
        // deriva del tamaño de la room. La surface no se puede configurar con ellos porque su
        // tamaño lo manda la ventana (y reconfigurarla cada frame bloquearía la GPU); en su lugar
        // `present` reconfigura la surface cuando la ventana cambia y el lienzo se escala con
        // `present_rect`.
        //
        // Lo ya dibujado se conserva en `prepared` y se vuelve a presentar con la proyección
        // nueva, como el blit del framebuffer viejo al nuevo que hace OpenGL. `store` solo
        // importaría para guardar una copia aparte, que este backend todavía no conserva.
        let _ = store;
        self.flush_queue();
        self.fb_size = (width.max(1), height.max(1));
    }

    fn set_vsync(&self, vsync: bool) {
        todo!("wgpu: set_vsync")
    }

    fn get_vsync(&self) -> bool {
        self.vsync
    }

    fn wait_vsync(&self) {
        // wgpu sincroniza en `present`; nada que esperar aquí.
    }

    fn set_draw_offset(&mut self, x: f64, y: f64) {
        // Un NaN o infinito del juego acabaría en el shader como `NaN * 0`; se neutraliza aquí,
        // igual que hace el backend OpenGL.
        let finite = |v: f64| {
            let v = v as f32;
            if v.is_finite() { v } else { 0.0 }
        };
        self.frame.draw_offset = [finite(x), finite(y)];
    }

    fn set_interp_amount(&mut self, amount: f32) {
        self.frame.interp_amount = amount.clamp(0.0, 1.0);
    }

    fn get_rect(&self, id: AtlasRef) -> Option<&AtlasRect> {
        todo!("wgpu: get_rect")
    }

    fn set_sprite_origin(&mut self, id: AtlasRef, origin_x: f32, origin_y: f32) {
        todo!("wgpu: set_sprite_origin")
    }

    fn draw_sprite_general(
        &mut self,
        texture: AtlasRef,
        part_x: f64,
        part_y: f64,
        part_w: f64,
        part_h: f64,
        x: f64,
        y: f64,
        xscale: f64,
        yscale: f64,
        angle: f64,
        col1: i32,
        col2: i32,
        col3: i32,
        col4: i32,
        alpha: f64,
        use_origin: bool,
    ) {
        todo!("wgpu: draw_sprite_general")
    }

    fn draw_sprite_pos(
        &mut self,
        texture: AtlasRef,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        x3: f64,
        y3: f64,
        x4: f64,
        y4: f64,
        alpha: f64,
    ) {
        todo!("wgpu: draw_sprite_pos")
    }

    fn set_view_matrix(&mut self, view: [f32; 16]) {
        self.frame.view_matrix = view;
        self.frame.update_projection();
    }

    fn set_viewproj_matrix(&mut self, view: [f32; 16], proj: [f32; 16]) {
        self.frame.view_matrix = view;
        self.frame.proj_matrix = proj;
        self.frame.update_projection();
    }

    fn get_model_matrix(&self) -> [f32; 16] {
        self.frame.model_matrix
    }

    fn set_model_matrix(&mut self, model: [f32; 16]) {
        self.frame.model_matrix = model;
    }

    fn mult_model_matrix(&mut self, model: [f32; 16]) {
        self.frame.model_matrix = mat4mult(self.frame.model_matrix, model);
    }

    fn set_projection_ortho(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        #[rustfmt::skip]
        let proj_matrix: [f32; 16] = [
            // Comprime a pantalla, voltea verticalmente y limita z a 1..32000 (igual que OpenGL).
            2.0 / w as f32, 0.0,             0.0,            0.0,
            0.0,            -2.0 / h as f32, 0.0,            0.0,
            0.0,            0.0,             1.0 / 31999.0,  0.0,
            0.0,            0.0,             -1.0 / 31999.0, 1.0,
        ];

        self.set_viewproj_matrix(make_view_matrix(x, y, -16000.0, w, h, angle), proj_matrix);
    }

    fn set_projection_perspective(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        #[rustfmt::skip]
        let proj_matrix: [f32; 16] = [
            // Comprime a pantalla, voltea verticalmente y limita z a 1..32000 (igual que OpenGL).
            2.0, 0.0,                  0.0,                0.0,
            0.0, 2.0 * (w / h) as f32, 0.0,                0.0,
            0.0, 0.0,                  32000.0 / 31999.0,  1.0,
            0.0, 0.0,                  -32000.0 / 31999.0, 0.0,
        ];

        self.set_viewproj_matrix(make_view_matrix(x, y, -w, w, h, angle), proj_matrix);
    }

    fn set_view(
        &mut self,
        src_x: i32,
        src_y: i32,
        src_w: i32,
        src_h: i32,
        src_angle: f64,
        port_x: i32,
        port_y: i32,
        port_w: i32,
        port_h: i32,
    ) {
        self.flush_queue();
        if port_x >= 0 && port_y >= 0 && port_w >= 0 && port_h >= 0 {
            self.frame.port = (port_x, port_y, port_w, port_h);
        }
        // TODO(wgpu): falta el estado `using_3d`/`perspective` de OpenGL; mientras no exista, toda
        // vista es ortográfica. `set_perspective` lo activará cuando se implemente.
        self.set_projection_ortho(src_x.into(), src_y.into(), src_w.into(), src_h.into(), src_angle);
    }

    fn flush_queue(&mut self) {
        self.prepare_batches();
    }

    fn set_crt(&mut self, _settings: Option<CrtSettings>) {}
    fn set_gb(&mut self, _settings: Option<GbSettings>) {}
    fn set_gb_inverted(&mut self, _inverted: bool) {}
    fn present(&mut self, window_width: u32, window_height: u32, scaling: Scaling) {
        // Una ventana de tamaño cero no tiene imagen que presentar (y pedir la textura fallaría).
        if window_width == 0 || window_height == 0 {
            return;
        }

        // Termina el dibujo pendiente antes de tocar la surface.
        self.flush_queue();

        // La surface ES la ventana: si cambió de tamaño desde el último frame, hay que
        // reconfigurarla antes de pedir la textura.
        if self.config.width != window_width || self.config.height != window_height {
            self.config.width = window_width;
            self.config.height = window_height;
            self.reconfigure_surface();
        } else if self.surface_dirty {
            self.surface_dirty = false;
            self.reconfigure_surface();
        }

        let (fb_width, fb_height) = self.fb_size;
        let (rx, ry, rw, rh) = draw::present_rect(window_width, window_height, fb_width, fb_height, scaling);
        let viewport =
            (rx * window_width as f32, ry * window_height as f32, rw * window_width as f32, rh * window_height as f32);

        #[cfg(target_os = "android")]
        {
            // Los controles táctiles que van sobre el juego necesitan saber dónde cayó la imagen.
            crate::touch_controls::set_viewport(rx, ry, rw, rh);
            crate::game_ready::poll(crate::game::audio::sound_started());
        }

        let surface_texture = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(texture) => texture,
            wgpu::CurrentSurfaceTexture::Suboptimal(texture) => {
                // Se puede dibujar con ella, pero la configuración quedó desfasada: se rehace en
                // cuanto se presente y se suelte la textura (reconfigurar antes hace panic).
                self.surface_dirty = true;
                texture
            },
            wgpu::CurrentSurfaceTexture::Timeout => {
                // El driver no entregó frame a tiempo; se salta este, no es un error.
                self.drop_frame();
                return;
            },
            wgpu::CurrentSurfaceTexture::Occluded => {
                // Ventana minimizada o tapada: no hay nada que presentar.
                self.drop_frame();
                return;
            },
            wgpu::CurrentSurfaceTexture::Outdated => {
                // La surface cambió (tamaño, formato, pantalla): reconfigurar y reintentar luego.
                self.drop_frame();
                self.reconfigure_surface();
                return;
            },
            wgpu::CurrentSurfaceTexture::Lost => {
                // La surface se perdió: reconfigurarla la recrea sobre la ventana actual.
                self.drop_frame();
                self.reconfigure_surface();
                return;
            },
            wgpu::CurrentSurfaceTexture::Validation => {
                // Error de validación de wgpu al adquirir: se registra y se salta el frame para no
                // dibujar sobre una textura inválida.
                eprintln!("wgpu: error de validación al adquirir la textura de la surface");
                self.drop_frame();
                return;
            },
        };

        let view = surface_texture.texture.create_view(&wgpu::TextureViewDescriptor::default());
        self.render_frame(&view, viewport);
        // Lo dibujado ya está enviado a la cola; soltar los buffers y las limpiezas deja que la GPU
        // los recicle para el frame siguiente.
        self.frame.prepared.clear();
        self.frame.clears.clear();
        self.queue.present(surface_texture);
    }

    fn present_surface(&mut self, surface: AtlasRef, window_width: u32, window_height: u32) {
        todo!("wgpu: present_surface")
    }

    fn draw_stored(&mut self, target_x: i32, target_y: i32, width: u32, height: u32) {
        todo!("wgpu: draw_stored")
    }

    fn stored_size(&self) -> (u32, u32) {
        todo!("wgpu: stored_size")
    }

    fn finish(&mut self, window_width: u32, window_height: u32, clear_colour: Colour) {
        // Presenta tal cual está el lienzo (OpenGL usa `Fixed(1.0)` aquí) y arranca el frame
        // siguiente igual que su `setup_frame`: vista completa del lienzo y su color de clear.
        self.present(window_width, window_height, Scaling::Fixed(1.0));
        self.clear_colour = clear_colour;
        let (fb_w, fb_h) = (self.fb_size.0 as i32, self.fb_size.1 as i32);
        self.set_view(0, 0, fb_w, fb_h, 0.0, 0, 0, fb_w, fb_h);
        self.clear_view(clear_colour, 1.0);
    }

    fn dump_sprite_part(&self, texture: AtlasRef, part_x: i32, part_y: i32, part_w: i32, part_h: i32) -> Box<[u8]> {
        todo!("wgpu: dump_sprite_part")
    }

    fn get_alpha_blending(&self) -> bool {
        todo!("wgpu: get_alpha_blending")
    }

    fn set_alpha_blending(&mut self, alphablend: bool) {
        todo!("wgpu: set_alpha_blending")
    }

    fn get_colour_blending(&self) -> bool {
        todo!("wgpu: get_colour_blending")
    }

    fn set_colour_blending(&mut self, modulate: bool) {
        todo!("wgpu: set_colour_blending")
    }

    fn get_blend_mode(&self) -> (BlendType, BlendType) {
        todo!("wgpu: get_blend_mode")
    }

    fn set_blend_mode(&mut self, src: BlendType, dst: BlendType) {
        todo!("wgpu: set_blend_mode")
    }

    fn get_pixel_interpolation(&self) -> bool {
        todo!("wgpu: get_pixel_interpolation")
    }

    fn set_pixel_interpolation(&mut self, lerping: bool) {
        todo!("wgpu: set_pixel_interpolation")
    }

    fn get_texture_repeat(&self) -> bool {
        todo!("wgpu: get_texture_repeat")
    }

    fn set_texture_repeat(&mut self, repeat: bool) {
        todo!("wgpu: set_texture_repeat")
    }

    fn get_pixels(&self, x: i32, y: i32, w: i32, h: i32) -> Box<[u8]> {
        todo!("wgpu: get_pixels")
    }

    fn stored_pixels(&self) -> Box<[u8]> {
        todo!("wgpu: stored_pixels")
    }

    fn stored_zbuffer(&self) -> Box<[f32]> {
        todo!("wgpu: stored_zbuffer")
    }

    fn set_stored(&mut self, rgba: Box<[u8]>, zbuf: Box<[f32]>, fb_w: u32, fb_h: u32) {
        todo!("wgpu: set_stored")
    }

    fn dump_dynamic_textures(&self) -> Vec<Option<SavedTexture>> {
        todo!("wgpu: dump_dynamic_textures")
    }

    fn upload_dynamic_textures(&mut self, textures: &[Option<SavedTexture>]) {
        todo!("wgpu: upload_dynamic_textures")
    }

    fn create_sprite_colour(&mut self, width: i32, height: i32, col: Colour) -> Result<AtlasRef, String> {
        todo!("wgpu: create_sprite_colour")
    }

    fn create_surface(&mut self, w: i32, h: i32, has_zbuffer: bool) -> Result<AtlasRef, String> {
        todo!("wgpu: create_surface")
    }

    fn set_target(&mut self, atlas_ref: AtlasRef) {
        todo!("wgpu: set_target")
    }

    fn reset_target(&mut self) {
        todo!("wgpu: reset_target")
    }

    fn copy_surface(
        &mut self,
        dest: AtlasRef,
        dest_x: i32,
        dest_y: i32,
        src: AtlasRef,
        src_x: i32,
        src_y: i32,
        width: i32,
        height: i32,
    ) {
        todo!("wgpu: copy_surface")
    }

    fn set_zbuf_trashed(&mut self, trashed: bool) {
        todo!("wgpu: set_zbuf_trashed")
    }

    fn get_zbuf_trashed(&self) -> bool {
        todo!("wgpu: get_zbuf_trashed")
    }

    fn get_texture_id(&mut self, atl_ref: AtlasRef) -> i32 {
        todo!("wgpu: get_texture_id")
    }

    fn get_texture_from_id(&self, id: i32) -> Option<AtlasRef> {
        todo!("wgpu: get_texture_from_id")
    }

    fn get_texture_rects(&self) -> Vec<Option<AtlasRect>> {
        todo!("wgpu: get_texture_rects")
    }

    fn set_texture_rects(&mut self, rects: &[Option<AtlasRect>]) {
        todo!("wgpu: set_texture_rects")
    }

    fn draw_rectangle(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_rectangle")
    }

    fn draw_rectangle_outline(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_rectangle_outline")
    }

    fn draw_rectangle_gradient(
        &mut self,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        c1: i32,
        c2: i32,
        c3: i32,
        c4: i32,
        alpha: f64,
        outline: bool,
    ) {
        todo!("wgpu: draw_rectangle_gradient")
    }

    fn draw_point(&mut self, x: f64, y: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_point")
    }

    fn draw_line(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, width: Option<f64>, c1: i32, c2: i32, alpha: f64) {
        todo!("wgpu: draw_line")
    }

    fn draw_triangle(
        &mut self,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        x3: f64,
        y3: f64,
        c1: i32,
        c2: i32,
        c3: i32,
        alpha: f64,
        outline: bool,
    ) {
        todo!("wgpu: draw_triangle")
    }

    fn draw_ellipse(&mut self, x: f64, y: f64, rad_x: f64, rad_y: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        todo!("wgpu: draw_ellipse")
    }

    fn draw_roundrect(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        todo!("wgpu: draw_roundrect")
    }

    fn set_circle_precision(&mut self, prec: i32) {
        todo!("wgpu: set_circle_precision")
    }

    fn get_circle_precision(&self) -> i32 {
        todo!("wgpu: get_circle_precision")
    }

    fn reset_primitive_2d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        todo!("wgpu: reset_primitive_2d")
    }

    fn vertex_2d(&mut self, x: f64, y: f64, xtex: f64, ytex: f64, col: i32, alpha: f64) {
        todo!("wgpu: vertex_2d")
    }

    fn draw_primitive_2d(&mut self) {
        todo!("wgpu: draw_primitive_2d")
    }

    fn get_primitive_2d(&self) -> PrimitiveBuilder {
        todo!("wgpu: get_primitive_2d")
    }

    fn set_primitive_2d(&mut self, prim: PrimitiveBuilder) {
        todo!("wgpu: set_primitive_2d")
    }

    fn reset_primitive_3d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        todo!("wgpu: reset_primitive_3d")
    }

    fn vertex_3d(
        &mut self,
        x: f64,
        y: f64,
        z: f64,
        nx: f64,
        ny: f64,
        nz: f64,
        xtex: f64,
        ytex: f64,
        col: i32,
        alpha: f64,
    ) {
        todo!("wgpu: vertex_3d")
    }

    fn draw_primitive_3d(&mut self) {
        todo!("wgpu: draw_primitive_3d")
    }

    fn get_primitive_3d(&self) -> PrimitiveBuilder {
        todo!("wgpu: get_primitive_3d")
    }

    fn set_primitive_3d(&mut self, prim: PrimitiveBuilder) {
        todo!("wgpu: set_primitive_3d")
    }

    fn extend_buffers(&self, buf: &mut VertexBuffer) {
        // Igual que OpenGL: el builder ya triangula strips y abanicos, así que aquí solo hay que
        // separar por topología y recortar lo que no complete una primitiva.
        let verts = self.primitive_3d.get_vertices();
        match self.primitive_3d.get_shape() {
            PrimitiveShape::Point => buf.points.extend_from_slice(verts),
            PrimitiveShape::Line => buf.lines.extend_from_slice(&verts[..verts.len() / 2 * 2]),
            PrimitiveShape::Triangle => buf.tris.extend_from_slice(&verts[..verts.len() / 3 * 3]),
        }
    }

    fn draw_buffers(&mut self, atlas_ref: Option<AtlasRef>, buf: &VertexBuffer) {
        // Un modelo cacheado de GM8.1: se vuelca a la lista con el atlas pedido (o el píxel blanco
        // cuando el modelo no lleva textura).
        self.flush_queue();
        let atlas_id = self.resolve_atlas_id(atlas_ref);
        let (points, lines, tris) = buf.slices();
        self.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::PointList, points);
        self.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::LineList, lines);
        self.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::TriangleList, tris);
    }

    fn clear_view(&mut self, colour: Colour, alpha: f64) {
        // El clear no puede ejecutarse aquí (el frame aún no tiene textura de surface), así que se
        // encola en orden con lo ya dibujado: `present` lo dibuja como un quad que cubre el puerto
        // de la vista. Equivale al `glClear` de OpenGL, incluido el z-buffer, que aún no existe en
        // este backend (por eso `clear_view_no_zbuf` hace lo mismo).
        self.flush_queue();
        self.frame.clear_colour = colour;
        self.frame.clear_alpha = alpha;
        self.frame.clears.push(ClearCommand {
            after: self.frame.prepared.len(),
            colour: wgpu::Color { r: colour.r, g: colour.g, b: colour.b, a: alpha },
            port: self.frame.port,
        });
    }

    fn clear_view_no_zbuf(&mut self, colour: Colour, alpha: f64) {
        // Misma semántica que `clear_view`: sin z-buffer implementado, el resultado coincide.
        self.clear_view(colour, alpha);
    }

    fn clear_zbuf(&mut self) {
        // Sin z-buffer en este backend todavía; no hay nada que limpiar. Los vértices pendientes se
        // conservan: `clear_zbuf` nunca debe descartar el color ya dibujado.
    }

    fn get_3d(&self) -> bool {
        todo!("wgpu: get_3d")
    }

    fn set_3d(&mut self, use_3d: bool) {
        todo!("wgpu: set_3d")
    }

    fn get_depth(&self) -> f32 {
        todo!("wgpu: get_depth")
    }

    fn set_depth(&mut self, depth: f32) {
        todo!("wgpu: set_depth")
    }

    fn get_depth_test(&self) -> bool {
        todo!("wgpu: get_depth_test")
    }

    fn set_depth_test(&mut self, depth_test: bool) {
        todo!("wgpu: set_depth_test")
    }

    fn get_write_depth(&self) -> bool {
        todo!("wgpu: get_write_depth")
    }

    fn set_write_depth(&mut self, write_depth: bool) {
        todo!("wgpu: set_write_depth")
    }

    fn get_culling(&self) -> bool {
        todo!("wgpu: get_culling")
    }

    fn set_culling(&mut self, culling: bool) {
        todo!("wgpu: set_culling")
    }

    fn get_perspective(&self) -> bool {
        todo!("wgpu: get_perspective")
    }

    fn set_perspective(&mut self, perspective: bool) {
        todo!("wgpu: set_perspective")
    }

    fn get_fog(&self) -> Option<Fog> {
        todo!("wgpu: get_fog")
    }

    fn set_fog(&mut self, fog: Option<Fog>) {
        todo!("wgpu: set_fog")
    }

    fn get_gouraud(&self) -> bool {
        todo!("wgpu: get_gouraud")
    }

    fn set_gouraud(&mut self, gouraud: bool) {
        todo!("wgpu: set_gouraud")
    }

    fn get_lighting_enabled(&self) -> bool {
        todo!("wgpu: get_lighting_enabled")
    }

    fn set_lighting_enabled(&mut self, enabled: bool) {
        todo!("wgpu: set_lighting_enabled")
    }

    fn get_ambient_colour(&self) -> i32 {
        todo!("wgpu: get_ambient_colour")
    }

    fn set_ambient_colour(&mut self, colour: i32) {
        todo!("wgpu: set_ambient_colour")
    }

    fn get_lights(&self) -> [(bool, Light); 8] {
        todo!("wgpu: get_lights")
    }

    fn set_lights(&mut self, lights: [(bool, Light); 8]) {
        todo!("wgpu: set_lights")
    }

    fn set_light_enabled(&mut self, id: usize, enabled: bool) {
        todo!("wgpu: set_light_enabled")
    }

    fn set_light(&mut self, id: usize, light: Light) {
        todo!("wgpu: set_light")
    }
}
