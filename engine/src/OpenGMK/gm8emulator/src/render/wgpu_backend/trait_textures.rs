// Grupo `textures`: implementación real. Las funciones del trait en mod.rs delegan aquí.
use super::*;

/// Sube los atlas estáticos del juego y registra sus rectángulos.
///
/// Igual que el backend OpenGL, añade un píxel blanco al `AtlasBuilder` (lo usan las primitivas
/// sin textura), compone cada atlas en una sola subida y guarda los rectángulos empaquetados por
/// id de textura (`set_atlas_rects`) y por id de sprite (`set_rects`).
pub(crate) fn push_atlases(r: &mut RendererImpl, mut atl: AtlasBuilderAny) -> Result<(), String> {
    assert!(r.textures.stock_count() == 0, "atlases should be initialized only once");

    let white_pixel_ref =
        atl.texture(1, 1, 0, 0, Box::new([0xFF, 0xFF, 0xFF, 0xFF])).ok_or("Couldn't pack white_pixel")?;
    let (packers, sprites) = atl.into_inner();
    let white_rect = sprites.get(white_pixel_ref.0 as usize).map(|(rect, _)| *rect).unwrap_or_default();

    // Los índices de `sprites` son los ids de sprite (en OpenGL, el índice de `texture_rects`).
    // El píxel blanco que creó `RendererImpl::new` se sustituye por el del atlas.
    r.white_pixel = white_pixel_ref;

    // Las primitivas sin textura muestrean el píxel blanco del atlas, así que los constructores
    // pendientes se reinician con él, como hace `push_atlases` en el backend OpenGL.
    r.primitive_2d = PrimitiveBuilder::new(white_rect, PrimitiveType::PointList, false);
    r.primitive_3d = PrimitiveBuilder::new(white_rect, PrimitiveType::PointList, false);

    // Los píxeles del `AtlasBuilder` están en BGRA, como los lee el backend OpenGL en escritorio;
    // wgpu usa RGBA8, así que se intercambian los canales al componer cada atlas.
    for (atlas_idx, packer) in packers.iter().enumerate() {
        let (atlas_w, atlas_h) = packer.size();
        if atlas_w <= 0 || atlas_h <= 0 {
            continue
        }
        let mut atlas_buf = vec![0u8; (atlas_w as usize) * (atlas_h as usize) * 4];

        for (rect, pixels) in &sprites {
            if rect.atlas_id as usize != atlas_idx {
                continue
            }
            let sw = rect.w.max(0) as usize;
            let sh = rect.h.max(0) as usize;
            let sx = rect.x.max(0) as usize;
            let sy = rect.y.max(0) as usize;
            let aw = atlas_w as usize;

            for row in 0..sh {
                let src_start = row * sw * 4;
                let src_end = src_start + sw * 4;
                let dst_start = ((sy + row) * aw + sx) * 4;
                let dst_end = dst_start + sw * 4;
                if src_end <= pixels.len() && dst_end <= atlas_buf.len() {
                    atlas_buf[dst_start..dst_end].copy_from_slice(&pixels[src_start..src_end]);
                }
            }
        }

        crate::util::bgra2rgba(&mut atlas_buf);

        r.textures
            .upload(&r.device, &r.queue, atlas_idx as u32, atlas_w as u32, atlas_h as u32, &atlas_buf)
            .map_err(|e| format!("wgpu: no se pudo subir el atlas {atlas_idx}: {e}"))?;

        let rects: Vec<AtlasRect> =
            sprites.iter().filter(|(rect, _)| rect.atlas_id as usize == atlas_idx).map(|(rect, _)| *rect).collect();
        r.textures.set_atlas_rects(atlas_idx as u32, rects);
    }

    for (index, (rect, _)) in sprites.iter().enumerate() {
        r.textures.set_rects(index as u32, vec![*rect]);
    }

    r.textures.set_stock_count(packers.len() as u32);
    r.textures.set_stock_regions(sprites.len() as u32);
    Ok(())
}

/// Sube un sprite dinámico como textura propia (sin empaquetar) y devuelve su id de sprite.
///
/// Es el equivalente a `create_surface` + `TexSubImage2D` del backend OpenGL: `data` ya viene en
/// RGBA, así que no hay que intercambiar canales. Los ids de textura y de sprite son espacios
/// distintos: la textura sale de `alloc_texture_id` (reutiliza huecos) y el sprite de
/// `alloc_sprite_id` (nunca se reutiliza).
pub(crate) fn upload_sprite(
    r: &mut RendererImpl,
    data: Box<[u8]>,
    width: i32,
    height: i32,
    origin_x: i32,
    origin_y: i32,
) -> Result<AtlasRef, String> {
    if width <= 0 || height <= 0 {
        return Ok(AtlasRef(-1))
    }

    let needed = (width as usize) * (height as usize) * 4;
    let mut pixels = data.into_vec();
    if pixels.len() < needed {
        pixels.resize(needed, 0);
    }

    let texture_id = r.textures.alloc_texture_id();
    r.textures
        .upload(&r.device, &r.queue, texture_id, width as u32, height as u32, &pixels)
        .map_err(|e| format!("wgpu: no se pudo subir el sprite: {e}"))?;

    let rect = AtlasRect {
        atlas_id: texture_id,
        x: 0,
        y: 0,
        w: width,
        h: height,
        origin_x: origin_x as f32 / width as f32,
        origin_y: origin_y as f32 / height as f32,
    };
    r.textures.set_atlas_rects(texture_id, vec![rect]);

    let sprite_id = r.textures.alloc_sprite_id();
    r.textures.set_rects(sprite_id, vec![rect]);
    Ok(AtlasRef(sprite_id as i32))
}

/// Duplica un sprite en una textura nueva, copiando los píxeles desde la copia en CPU del atlas.
///
/// El backend OpenGL usa `CopyTexImage2D` desde el framebuffer del atlas; aquí no se puede leer de
/// la GPU sin un readback, pero `TextureStore` conserva los píxeles con los que se subió el atlas,
/// que es lo mismo salvo que la textura se haya usado como destino de render.
pub(crate) fn duplicate_sprite(r: &mut RendererImpl, atlas_ref: AtlasRef) -> Result<AtlasRef, String> {
    let Some(rect) = r.textures.rect(atlas_ref).copied() else { return Ok(AtlasRef(-1)) };
    if rect.w <= 0 || rect.h <= 0 {
        return Ok(AtlasRef(-1))
    }

    let (src_w, src_h) = match r.textures.textures.get(&rect.atlas_id) {
        Some(atlas) => (atlas.width as i32, atlas.height as i32),
        None => return Ok(AtlasRef(-1)),
    };

    // Recorta la región pedida contra los límites del atlas, como haría la GPU al copiar.
    let sx = rect.x.max(0);
    let sy = rect.y.max(0);
    let copy_w = (rect.w - (sx - rect.x)).min(src_w - sx).max(0) as usize;
    let copy_h = (rect.h - (sy - rect.y)).min(src_h - sy).max(0) as usize;
    let dst_x = (sx - rect.x) as usize;
    let dst_y = (sy - rect.y) as usize;

    let mut pixels = vec![0u8; (rect.w as usize) * (rect.h as usize) * 4];
    if let Some(src) = r.textures.pixels(rect.atlas_id) {
        let stride = src_w.max(0) as usize * 4;
        let dst_stride = rect.w as usize * 4;
        for row in 0..copy_h {
            let src_start = (sy as usize + row) * stride + sx as usize * 4;
            let src_end = src_start + copy_w * 4;
            let dst_start = (dst_y + row) * dst_stride + dst_x * 4;
            let dst_end = dst_start + copy_w * 4;
            if src_end <= src.len() && dst_end <= pixels.len() {
                pixels[dst_start..dst_end].copy_from_slice(&src[src_start..src_end]);
            }
        }
    }

    let texture_id = r.textures.alloc_texture_id();
    r.textures
        .upload(&r.device, &r.queue, texture_id, rect.w as u32, rect.h as u32, &pixels)
        .map_err(|e| format!("wgpu: no se pudo duplicar el sprite: {e}"))?;

    let new_rect = AtlasRect {
        atlas_id: texture_id,
        x: 0,
        y: 0,
        w: rect.w,
        h: rect.h,
        origin_x: rect.origin_x,
        origin_y: rect.origin_y,
    };
    r.textures.set_atlas_rects(texture_id, vec![new_rect]);

    let sprite_id = r.textures.alloc_sprite_id();
    r.textures.set_rects(sprite_id, vec![new_rect]);
    Ok(AtlasRef(sprite_id as i32))
}

/// Borra un sprite. Si su textura es dinámica y ya no la usa nadie, la libera también.
pub(crate) fn delete_sprite(r: &mut RendererImpl, atlas_ref: AtlasRef) {
    // Se vacía la cola antes de borrar, como hace OpenGL: los lotes pendientes ya capturaron su
    // bind group (que mantiene la textura viva hasta que se presenten).
    r.flush_now();
    if let Ok(id) = u32::try_from(atlas_ref.0) {
        r.textures.remove_sprite(id);
    }
}

pub(crate) fn get_rect(r: &RendererImpl, id: AtlasRef) -> Option<&AtlasRect> {
    r.textures.rect(id)
}

pub(crate) fn set_sprite_origin(r: &mut RendererImpl, id: AtlasRef, origin_x: f32, origin_y: f32) {
    if let Some(rect) = r.textures.rect_mut(id) {
        if rect.w != 0 {
            rect.origin_x = origin_x / rect.w as f32;
        }
        if rect.h != 0 {
            rect.origin_y = origin_y / rect.h as f32;
        }
    }
}

/// El id de textura que ve GML es el id del sprite: en el backend OpenGL ambos son el mismo
/// índice (`texture_rects`), así que se mantiene esa correspondencia.
pub(crate) fn get_texture_id(_r: &mut RendererImpl, atl_ref: AtlasRef) -> i32 {
    atl_ref.0
}

pub(crate) fn get_texture_from_id(_r: &RendererImpl, id: i32) -> Option<AtlasRef> {
    Some(AtlasRef(id))
}

/// Rectángulos de los sprites dinámicos, indexados a partir de `stock_regions` y con los huecos de
/// los sprites borrados, igual que `texture_rects[stock_texture_count..]` en OpenGL.
pub(crate) fn get_texture_rects(r: &RendererImpl) -> Vec<Option<AtlasRect>> {
    r.textures.sprite_rects()
}

pub(crate) fn set_texture_rects(r: &mut RendererImpl, rects: &[Option<AtlasRect>]) {
    r.textures.set_sprite_rects(rects);
}

pub(crate) fn dump_dynamic_textures(r: &RendererImpl) -> Vec<Option<SavedTexture>> {
    r.textures.dump()
}

pub(crate) fn upload_dynamic_textures(r: &mut RendererImpl, textures: &[Option<SavedTexture>]) {
    if let Err(e) = r.textures.restore(&r.device, &r.queue, textures) {
        eprintln!("wgpu: no se pudieron restaurar las texturas dinámicas: {e}");
    }
}

/// Crea un sprite de `width`x`height` relleno de `col` con alpha 1 (como el `glClear` de OpenGL).
pub(crate) fn create_sprite_colour(
    r: &mut RendererImpl,
    width: i32,
    height: i32,
    col: Colour,
) -> Result<AtlasRef, String> {
    if width <= 0 || height <= 0 {
        return Ok(AtlasRef(-1))
    }
    let (r8, g8, b8) = col.as_rgb();
    r.textures.create_colour_sized(&r.device, &r.queue, width as u32, height as u32, r8, g8, b8, 255)
}
