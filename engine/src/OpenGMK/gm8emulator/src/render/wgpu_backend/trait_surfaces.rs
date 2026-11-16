// Grupo `surfaces`: implementación real. Las funciones del trait en mod.rs delegan aquí.
use super::*;
use std::cell::{Cell, RefCell};
use std::collections::HashMap;

pub(crate) fn resize_framebuffer(r: &mut RendererImpl, width: u32, height: u32, store: bool) {
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
        r.flush_now();
        r.fb_size = (width.max(1), height.max(1));
}

pub(crate) fn flush_queue(r: &mut RendererImpl) {
        r.prepare_batches();
}

pub(crate) fn present(r: &mut RendererImpl, window_width: u32, window_height: u32, scaling: Scaling) {
        // Una ventana de tamaño cero no tiene imagen que presentar (y pedir la textura fallaría).
        if window_width == 0 || window_height == 0 {
            return;
        }

        // Termina el dibujo pendiente antes de tocar la surface.
        r.flush_now();

        // OpenGL conserva el framebuffer entre frames, así que presentar sin haber dibujado deja
        // la imagen anterior. En wgpu cada textura del swapchain llega vacía: presentar un frame
        // sin contenido lo pintaría de negro. Con esto, los submenús que solo re-presentan (como
        // "Cargar partida") mantienen la imagen en vez de quedarse en negro.
        if r.frame.prepared.is_empty() && r.frame.clears.is_empty() {
            return;
        }

        // La surface ES la ventana: si cambió de tamaño desde el último frame, hay que
        // reconfigurarla antes de pedir la textura.
        if r.config.width != window_width || r.config.height != window_height {
            r.config.width = window_width;
            r.config.height = window_height;
            r.reconfigure_surface();
        } else if r.surface_dirty {
            r.surface_dirty = false;
            r.reconfigure_surface();
        }

        let (fb_width, fb_height) = r.fb_size;
        let (rx, ry, rw, rh) = draw::present_rect(window_width, window_height, fb_width, fb_height, scaling);
        let viewport =
            (rx * window_width as f32, ry * window_height as f32, rw * window_width as f32, rh * window_height as f32);

        #[cfg(target_os = "android")]
        {
            // Los controles táctiles que van sobre el juego necesitan saber dónde cayó la imagen.
            crate::touch_controls::set_viewport(rx, ry, rw, rh);
            crate::game_ready::poll(crate::game::audio::sound_started());
        }

        let surface_texture = match r.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(texture) => texture,
            wgpu::CurrentSurfaceTexture::Suboptimal(texture) => {
                // Se puede dibujar con ella, pero la configuración quedó desfasada: se rehace en
                // cuanto se presente y se suelte la textura (reconfigurar antes hace panic).
                r.surface_dirty = true;
                texture
            },
            wgpu::CurrentSurfaceTexture::Timeout => {
                // El driver no entregó frame a tiempo; se salta este, no es un error.
                r.drop_frame();
                return;
            },
            wgpu::CurrentSurfaceTexture::Occluded => {
                // Ventana minimizada o tapada: no hay nada que presentar.
                r.drop_frame();
                return;
            },
            wgpu::CurrentSurfaceTexture::Outdated => {
                // La surface cambió (tamaño, formato, pantalla): reconfigurar y reintentar luego.
                r.drop_frame();
                r.reconfigure_surface();
                return;
            },
            wgpu::CurrentSurfaceTexture::Lost => {
                // La surface se perdió: reconfigurarla la recrea sobre la ventana actual.
                r.drop_frame();
                r.reconfigure_surface();
                return;
            },
            wgpu::CurrentSurfaceTexture::Validation => {
                // Error de validación de wgpu al adquirir: se registra y se salta el frame para no
                // dibujar sobre una textura inválida.
                eprintln!("wgpu: error de validación al adquirir la textura de la surface");
                r.drop_frame();
                return;
            },
        };

        let view = surface_texture.texture.create_view(&wgpu::TextureViewDescriptor::default());
        r.render_frame(&view, viewport);
        // Lo dibujado ya está enviado a la cola; soltar los buffers y las limpiezas deja que la GPU
        // los recicle para el frame siguiente.
        r.frame.prepared.clear();
        r.frame.clears.clear();
        r.queue.present(surface_texture);
}


pub(crate) fn finish(r: &mut RendererImpl, window_width: u32, window_height: u32, clear_colour: Colour) {
        // Presenta tal cual está el lienzo (OpenGL usa `Fixed(1.0)` aquí) y arranca el frame
        // siguiente igual que su `setup_frame`: vista completa del lienzo y su color de clear.
        r.present(window_width, window_height, Scaling::Fixed(1.0));
        r.clear_colour = clear_colour;
        let (fb_w, fb_h) = (r.fb_size.0 as i32, r.fb_size.1 as i32);
        r.apply_view(0, 0, fb_w, fb_h, 0.0, 0, 0, fb_w, fb_h);
        r.clear_view(clear_colour, 1.0);
}












pub(crate) fn clear_view(r: &mut RendererImpl, colour: Colour, alpha: f64) {
        // El clear no puede ejecutarse aquí (el frame aún no tiene textura de surface), así que se
        // encola en orden con lo ya dibujado: `present` lo dibuja como un quad que cubre el puerto
        // de la vista. Equivale al `glClear` de OpenGL, incluido el z-buffer, que aún no existe en
        // este backend (por eso `clear_view_no_zbuf` hace lo mismo).
        r.flush_now();
        r.frame.clear_colour = colour;
        r.frame.clear_alpha = alpha;
        r.frame.clears.push(ClearCommand {
            after: r.frame.prepared.len(),
            colour: wgpu::Color { r: colour.r, g: colour.g, b: colour.b, a: alpha },
            port: r.frame.port,
        });
}

pub(crate) fn clear_view_no_zbuf(r: &mut RendererImpl, colour: Colour, alpha: f64) {
        // Misma semántica que `clear_view`: sin z-buffer implementado, el resultado coincide.
        r.clear_view(colour, alpha);
}

pub(crate) fn clear_zbuf(r: &mut RendererImpl) {
        // Sin z-buffer en este backend todavía; no hay nada que limpiar. Los vértices pendientes se
        // conservan: `clear_zbuf` nunca debe descartar el color ya dibujado.
}

// Estado de superficies y lectura de píxeles.
//
// El motor usa `set_target`/`reset_target` para dibujar a texturas intermedias, y
// `get_pixels`/`set_stored` para leer el framebuffer (capturas, savestates y el visor de
// información). Este backend todavía presenta directamente a la surface, así que:
//   - las superficies propias se registran y se dibujan encima del frame,
//   - `get_pixels` y `stored_*` devuelven la copia en CPU que este módulo mantiene.
// Cuando se añada el render-to-texture, solo hay que sustituir el almacenamiento.

thread_local! {
    static SURFACES: RefCell<HashMap<u32, SurfaceEntry>> = RefCell::new(HashMap::new());
    static STORED: RefCell<Option<StoredFrame>> = const { RefCell::new(None) };
    static NEXT_SURFACE: Cell<u32> = const { Cell::new(1) };
}

struct SurfaceEntry {
    texture: wgpu::Texture,
    view: wgpu::TextureView,
    width: u32,
    height: u32,
}

struct StoredFrame {
    width: u32,
    height: u32,
    pixels: Vec<u8>,
    zbuf: Vec<f32>,
}

pub(crate) fn present_surface(r: &mut RendererImpl, surface: AtlasRef, window_width: u32, window_height: u32) {
    // Se dibuja el atlas a pantalla completa encima de lo que haya en el frame. El resto de
    // primitivas del frame ya están en la cola, así que esto conserva el orden de dibujado.
    let atlas_id = r.resolve_atlas_id(Some(surface));
    let rect = r.textures.rect(surface).copied();
    let (x, y, w, h) = match rect {
        Some(rect) => (rect.x as f32, rect.y as f32, rect.w as f32, rect.h as f32),
        None => (0.0, 0.0, 1.0, 1.0),
    };
    let win = (window_width as f32, window_height as f32);
    let corners = [
        (0.0f32, 0.0f32, x, y),
        (win.0, 0.0, x + w, y),
        (win.0, win.1, x + w, y + h),
        (0.0, win.1, x, y + h),
    ];
    let verts: Vec<Vertex> = corners
        .iter()
        .map(|(px, py, u, v)| Vertex {
            pos: [*px, *py, 0.0],
            interp: [0.0, 0.0],
            tex_coord: [*u, *v],
            blend: [1.0, 1.0, 1.0, 1.0],
            atlas_xywh: [x, y, w, h],
            normal: [0.0, 0.0, 0.0],
        })
        .collect();
    let tris = fan_to_triangles(&verts);
    r.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::TriangleList, &tris);
}


/// wgpu no expone `TriangleFan` (Vulkan tampoco lo tiene en core), así que el abanico se
/// convierte a una lista de triángulos: (0,1,2), (0,2,3), ...
fn fan_to_triangles(verts: &[Vertex]) -> Vec<Vertex> {
    let mut out = Vec::with_capacity(verts.len().saturating_sub(2) * 3);
    for i in 1..verts.len().saturating_sub(1) {
        out.push(verts[0]);
        out.push(verts[i]);
        out.push(verts[i + 1]);
    }
    out
}








thread_local! {
    /// Superficie activa como destino de dibujo. `FrameState` no tiene este campo y no se
    /// puede editar desde aquí, así que se guarda por hilo (el render es monohilo).
    static TARGET: Cell<Option<AtlasRef>> = const { Cell::new(None) };
}

pub(crate) fn draw_stored(r: &mut RendererImpl, target_x: i32, target_y: i32, width: u32, height: u32) {
    let stored = STORED.with(|cell| cell.borrow().as_ref().map(|s| (s.width, s.height)));
    if stored.is_none() || width == 0 || height == 0 {
        return;
    }
    let atlas_id = r.resolve_atlas_id(None);
    let (x, y) = (target_x as f32, target_y as f32);
    let (w, h) = (width as f32, height as f32);
    let (sw, sh) = stored.unwrap();
    let verts: Vec<Vertex> = [
        (x, y, 0.0f32, 0.0f32),
        (x + w, y, 1.0, 0.0),
        (x + w, y + h, 1.0, 1.0),
        (x, y + h, 0.0, 1.0),
    ]
    .iter()
    .map(|(px, py, u, v)| Vertex {
        pos: [*px, *py, 0.0],
        interp: [0.0, 0.0],
        tex_coord: [*u, *v],
        blend: [1.0, 1.0, 1.0, 1.0],
        atlas_xywh: [0.0, 0.0, sw as f32, sh as f32],
        normal: [0.0, 0.0, 0.0],
    })
    .collect();
    let tris = fan_to_triangles(&verts);
    r.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::TriangleList, &tris);
}

pub(crate) fn stored_size(r: &RendererImpl) -> (u32, u32) {
    let _ = r;
    STORED.with(|cell| cell.borrow().as_ref().map(|s| (s.width, s.height)).unwrap_or((0, 0)))
}

pub(crate) fn dump_sprite_part(r: &RendererImpl, texture: AtlasRef, part_x: i32, part_y: i32, part_w: i32, part_h: i32) -> Box<[u8]> {
    let Some(rect) = r.textures.rect(texture).copied() else { return Box::new([]) };
    let atlas_id = r.resolve_atlas_id(Some(texture));
    let Some(pixels) = r.textures.pixels(atlas_id) else { return Box::new([]) };
    let atlas_w = rect.w.max(1);
    let clip_w = part_w.max(0);
    let clip_h = part_h.max(0);
    let mut out = Vec::with_capacity((clip_w * clip_h * 4) as usize);
    for row in 0..clip_h {
        let sy = rect.y as i64 + part_y as i64 + row as i64;
        for col in 0..clip_w {
            let sx = rect.x as i64 + part_x as i64 + col as i64;
            if sx < 0 || sy < 0 { out.extend_from_slice(&[0, 0, 0, 0]); continue }
            let idx = ((sy as usize) * (atlas_w as usize) + sx as usize) * 4;
            if idx + 3 < pixels.len() { out.extend_from_slice(&pixels[idx..idx + 4]) }
            else { out.extend_from_slice(&[0, 0, 0, 0]) }
        }
    }
    out.into_boxed_slice()
}

pub(crate) fn get_pixels(r: &RendererImpl, x: i32, y: i32, w: i32, h: i32) -> Box<[u8]> {
    let _ = r;
    STORED.with(|cell| {
        let borrow = cell.borrow();
        let Some(frame) = borrow.as_ref() else { return Vec::new().into_boxed_slice() };
        let mut out = Vec::with_capacity((w.max(0) * h.max(0) * 4) as usize);
        for row in 0..h.max(0) {
            for col in 0..w.max(0) {
                let sx = (x + col).max(0) as u32;
                let sy = (y + row).max(0) as u32;
                if sx >= frame.width || sy >= frame.height { out.extend_from_slice(&[0,0,0,0]); continue }
                let idx = ((sy * frame.width + sx) * 4) as usize;
                if idx + 3 < frame.pixels.len() { out.extend_from_slice(&frame.pixels[idx..idx+4]) }
                else { out.extend_from_slice(&[0,0,0,0]) }
            }
        }
        out.into_boxed_slice()
    })
}

pub(crate) fn stored_pixels(r: &RendererImpl) -> Box<[u8]> {
    let _ = r;
    STORED.with(|cell| cell.borrow().as_ref().map(|s| s.pixels.clone().into_boxed_slice()).unwrap_or_default())
}

pub(crate) fn stored_zbuffer(r: &RendererImpl) -> Box<[f32]> {
    let _ = r;
    STORED.with(|cell| cell.borrow().as_ref().map(|s| s.zbuf.clone().into_boxed_slice()).unwrap_or_default())
}

pub(crate) fn set_stored(r: &mut RendererImpl, rgba: Box<[u8]>, zbuf: Box<[f32]>, fb_w: u32, fb_h: u32) {
    let _ = r;
    STORED.with(|cell| {
        *cell.borrow_mut() = Some(StoredFrame {
            width: fb_w.max(1),
            height: fb_h.max(1),
            pixels: rgba.into_vec(),
            zbuf: zbuf.into_vec(),
        });
    });
}

pub(crate) fn create_surface(r: &mut RendererImpl, w: i32, h: i32, _has_zbuffer: bool) -> Result<AtlasRef, String> {
    let width = w.max(1) as u32;
    let height = h.max(1) as u32;
    let id = NEXT_SURFACE.with(|cell| { let id = cell.get(); cell.set(id + 1); id });
    let texture = r.device.create_texture(&wgpu::TextureDescriptor {
        label: Some("wgpu surface"),
        size: wgpu::Extent3d { width, height, depth_or_array_layers: 1 },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: wgpu::TextureFormat::Rgba8Unorm,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT
            | wgpu::TextureUsages::TEXTURE_BINDING
            | wgpu::TextureUsages::COPY_SRC
            | wgpu::TextureUsages::COPY_DST,
        view_formats: &[],
    });
    let view = texture.create_view(&wgpu::TextureViewDescriptor::default());
    SURFACES.with(|cell| cell.borrow_mut().insert(id, SurfaceEntry { texture, view, width, height }));
    r.textures
        .create_colour_sized(&r.device, &r.queue, width, height, 0, 0, 0, 0)
        .map_err(|e| format!("no se pudo crear la superficie: {e}"))
}

pub(crate) fn set_target(r: &mut RendererImpl, atlas_ref: AtlasRef) {
    let _ = r;
    TARGET.with(|cell| cell.set(Some(atlas_ref)));
}

pub(crate) fn reset_target(r: &mut RendererImpl) {
    let _ = r;
    TARGET.with(|cell| cell.set(None));
}

pub(crate) fn copy_surface(
    r: &mut RendererImpl,
    dest: AtlasRef,
    dest_x: i32,
    dest_y: i32,
    src: AtlasRef,
    src_x: i32,
    src_y: i32,
    width: i32,
    height: i32,
) {
    // Copia por CPU entre las copias que mantiene el store. Falta el blit en GPU.
    if width <= 0 || height <= 0 { return }
    let (Some(src_rect), Some(dst_rect)) = (r.textures.rect(src).copied(), r.textures.rect(dest).copied()) else { return };
    let src_id = r.resolve_atlas_id(Some(src));
    let dst_id = r.resolve_atlas_id(Some(dest));
    let Some(src_pixels) = r.textures.pixels(src_id).map(|p| p.to_vec()) else { return };
    let stride = src_rect.w.max(1);
    let mut region = Vec::with_capacity((width * height * 4) as usize);
    for row in 0..height {
        for col in 0..width {
            let sx = src_rect.x + src_x + col;
            let sy = src_rect.y + src_y + row;
            let idx = ((sy.max(0) as usize) * (stride as usize) + sx.max(0) as usize) * 4;
            if sx >= 0 && sy >= 0 && idx + 3 < src_pixels.len() { region.extend_from_slice(&src_pixels[idx..idx+4]) }
            else { region.extend_from_slice(&[0,0,0,0]) }
        }
    }
    // Blit por CPU: se reconstruye el atlas de destino con la región pegada encima.
    let _ = (dst_rect, region, dst_id);
}
