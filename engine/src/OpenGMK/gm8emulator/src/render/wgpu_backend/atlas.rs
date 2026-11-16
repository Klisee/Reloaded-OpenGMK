//! Subida y gestión de texturas de atlas en wgpu.
//!
//! El backend OpenGL guarda cada atlas en una textura y los sprites como rectángulos dentro de
//! ella. Este módulo replica esa idea para wgpu: `textures` contiene las texturas de GPU indexadas
//! por el id de textura que usa el motor, y `regions` indexa los rectángulos por el id del sprite
//! (`AtlasRef`). `TextureStore` conserva además una copia en CPU de los píxeles de cada textura,
//! porque los savestates necesitan volver a subirlos sin leer de la GPU.

use crate::render::{
    SavedTexture,
    atlas::{AtlasRect, AtlasRef},
};
use std::collections::HashMap;

/// Una textura de atlas ya creada en la GPU, junto con su vista, su bind group y una copia en CPU
/// de los píxeles originales.
pub struct AtlasTexture {
    pub texture: wgpu::Texture,
    pub view: wgpu::TextureView,
    pub bind_group: wgpu::BindGroup,
    /// Rectángulos empaquetados en esta textura. El llamante los rellena con [`TextureStore::set_rects`]
    /// o [`TextureStore::set_atlas_rects`]; `upload` deja la lista vacía porque no conoce el empaquetado.
    pub rects: Vec<AtlasRect>,
    pub width: u32,
    pub height: u32,
    /// Píxeles RGBA8 con las filas apretadas (sin el relleno de 256 bytes), copiados tal cual se
    /// subieron. Es la fuente de verdad para [`TextureStore::dump`] y los savestates.
    pixels: Vec<u8>,
    /// Etiqueta de depuración, reutilizada al reconstruir el bind group.
    label: String,
}

/// Almacén de texturas de atlas y de los rectángulos que las componen.
pub struct TextureStore {
    /// Texturas de GPU indexadas por el id de textura del motor.
    pub textures: HashMap<u32, AtlasTexture>,
    /// Rectángulos indexados por el id del sprite (`AtlasRef.0`). Normalmente hay uno por sprite;
    /// una lista con varios permite que un mismo id agrupe varias regiones.
    pub regions: HashMap<u32, Vec<AtlasRect>>,
    /// Layout de los bind groups de las texturas (grupo 1: textura + sampler en `shaders.wgsl`).
    layout: wgpu::BindGroupLayout,
    /// Sampler compartido por todos los bind groups. Si no se fija uno, se crea el primero con los
    /// valores por defecto del backend OpenGL: filtro nearest y direccionamiento repeat.
    sampler: Option<wgpu::Sampler>,
    /// Número de texturas de atlas que pertenecen a los recursos estáticos del juego. Los ids a
    /// partir de este valor son texturas dinámicas (superficies y sprites subidos en ejecución) y
    /// son las únicas que entran en [`TextureStore::dump`]. Se fija desde el renderer con
    /// [`TextureStore::set_stock_count`] después de subir los atlas estáticos.
    stock_count: u32,
    /// Número de sprites (regiones) estáticos creados por `push_atlases`. Los ids de sprite a partir
    /// de este valor son dinámicos y son los que exporta [`TextureStore::sprite_rects`], igual que
    /// `texture_rects[stock_texture_count..]` en el backend OpenGL.
    stock_regions: u32,
    /// Siguiente id de textura que no se ha usado nunca. Los ids liberados por debajo de este valor
    /// se reutilizan, igual que hacía el backend OpenGL con su vector de texturas.
    next_texture: u32,
    /// Siguiente id de sprite (`AtlasRef`). Los ids de sprite nunca se reutilizan: un sprite
    /// borrado deja un hueco permanente en el índice, como `texture_rects` en el backend OpenGL.
    next_region: u32,
}

impl TextureStore {
    /// Crea el almacén. `bind_group_layout` debe describir el grupo que usa el shader para la
    /// textura del atlas: binding 0 = `texture_2d<f32>`, binding 1 = `sampler`.
    pub fn new(bind_group_layout: &wgpu::BindGroupLayout) -> Self {
        Self {
            textures: HashMap::new(),
            regions: HashMap::new(),
            layout: bind_group_layout.clone(),
            sampler: None,
            stock_count: 0,
            stock_regions: 0,
            next_texture: 0,
            next_region: 0,
        }
    }

    /// Fija el sampler que usarán los bind groups nuevos. Llamar antes de subir las texturas; si
    /// ya hay texturas creadas, [`TextureStore::rebind`] las reconstruye con este sampler.
    pub fn set_sampler(&mut self, sampler: wgpu::Sampler) {
        self.sampler = Some(sampler);
    }

    /// Devuelve el sampler configurado, creando uno nearest/repeat la primera vez.
    fn ensure_sampler(&mut self, device: &wgpu::Device) -> wgpu::Sampler {
        self.sampler
            .get_or_insert_with(|| {
                device.create_sampler(&wgpu::SamplerDescriptor {
                    label: Some("gm8emulator atlas sampler"),
                    address_mode_u: wgpu::AddressMode::Repeat,
                    address_mode_v: wgpu::AddressMode::Repeat,
                    address_mode_w: wgpu::AddressMode::Repeat,
                    mag_filter: wgpu::FilterMode::Nearest,
                    min_filter: wgpu::FilterMode::Nearest,
                    mipmap_filter: wgpu::MipmapFilterMode::Nearest,
                    ..Default::default()
                })
            })
            .clone()
    }

    /// Reconstruye los bind groups de todas las texturas con el sampler actual. Necesario si el
    /// sampler se fija después de subir texturas, porque cada bind group captura el sampler en el
    /// momento de crearse.
    pub fn rebind(&mut self, device: &wgpu::Device) {
        let sampler = self.ensure_sampler(device);
        for atlas in self.textures.values_mut() {
            atlas.bind_group = create_bind_group(device, &self.layout, &atlas.view, &sampler, Some(&atlas.label));
        }
    }

    /// Sube una textura RGBA8 completa.
    ///
    /// `pixels` debe contener `width * height * 4` bytes con las filas apretadas.
    ///
    /// # Alineación de 256 bytes
    ///
    /// Las copias de buffer a textura de wgpu exigen que `bytes_per_row` sea múltiplo de
    /// [`wgpu::COPY_BYTES_PER_ROW_ALIGNMENT`] (256 bytes). Una fila mide `width * 4`, que solo es
    /// múltiplo de 256 cuando `width` lo es de 64. Si no lo es, se crea un buffer temporal con cada
    /// fila rellenada hasta el siguiente múltiplo de 256 y se copia fila a fila; el relleno nunca se
    /// escribe en la textura porque `copy_size` solo abarca las columnas reales. `write_texture`
    /// acepta layouts sin alinear, pero hacer el relleno explícito mantiene el camino válido también
    /// para `copy_buffer_to_texture` y para drivers estrictos.
    pub fn upload(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        id: u32,
        width: u32,
        height: u32,
        pixels: &[u8],
    ) -> Result<(), String> {
        if width == 0 || height == 0 {
            return Err(format!("wgpu: textura {id} con tamaño inválido {width}x{height}"));
        }

        let max = device.limits().max_texture_dimension_2d;
        if width > max || height > max {
            return Err(format!("wgpu: textura {id} de {width}x{height} supera el máximo del dispositivo ({max})"));
        }

        let row_bytes = (width as usize).checked_mul(4).ok_or_else(|| format!("wgpu: textura {id} demasiado ancha"))?;
        let needed =
            row_bytes.checked_mul(height as usize).ok_or_else(|| format!("wgpu: textura {id} demasiado grande"))?;
        if pixels.len() < needed {
            return Err(format!("wgpu: textura {id} esperaba {needed} bytes RGBA8 y recibió {}", pixels.len()));
        }
        let pixels = &pixels[..needed];

        let size = wgpu::Extent3d { width, height, depth_or_array_layers: 1 };
        let label = format!("gm8emulator atlas texture {id}");
        let texture = device.create_texture(&wgpu::TextureDescriptor {
            label: Some(&label),
            size,
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });

        let padded_row = padded_row_bytes(width);
        let copy_layout = wgpu::TexelCopyBufferLayout {
            offset: 0,
            bytes_per_row: Some(padded_row as u32),
            rows_per_image: Some(height),
        };
        if padded_row == row_bytes {
            queue.write_texture(texture.as_image_copy(), pixels, copy_layout, size);
        } else {
            let mut padded = vec![0u8; padded_row * height as usize];
            for row in 0..height as usize {
                let src = row * row_bytes;
                let dst = row * padded_row;
                padded[dst..dst + row_bytes].copy_from_slice(&pixels[src..src + row_bytes]);
            }
            queue.write_texture(texture.as_image_copy(), &padded, copy_layout, size);
        }

        let view = texture.create_view(&wgpu::TextureViewDescriptor { label: Some(&label), ..Default::default() });
        let sampler = self.ensure_sampler(device);
        let bind_group = create_bind_group(device, &self.layout, &view, &sampler, Some(&label));

        let rects = self.textures.get(&id).map(|atlas| atlas.rects.clone()).unwrap_or_default();
        self.textures.insert(
            id,
            AtlasTexture { texture, view, bind_group, rects, width, height, pixels: pixels.to_vec(), label },
        );
        self.next_texture = self.next_texture.max(id.saturating_add(1));
        Ok(())
    }

    /// Rectángulo asociado a un sprite.
    pub fn rect(&self, id: AtlasRef) -> Option<&AtlasRect> {
        let id = u32::try_from(id.0).ok()?;
        self.regions.get(&id)?.first()
    }

    /// Rectángulo asociado a un sprite, mutable. Lo necesita `set_sprite_origin`.
    pub fn rect_mut(&mut self, id: AtlasRef) -> Option<&mut AtlasRect> {
        let id = u32::try_from(id.0).ok()?;
        self.regions.get_mut(&id)?.first_mut()
    }

    /// Id de la textura que contiene un sprite, para buscar su bind group con
    /// [`TextureStore::bind_group`].
    pub fn texture_id(&self, id: AtlasRef) -> Option<u32> {
        self.rect(id).map(|rect| rect.atlas_id)
    }

    /// Reemplaza los rectángulos de un id de sprite. No toca los rectángulos empaquetados de las
    /// texturas: los ids de sprite y los de textura son espacios distintos y pueden coincidir
    /// numéricamente (los primeros ids de ambos son pequeños). Para el empaquetado de un atlas usa
    /// [`TextureStore::set_atlas_rects`].
    pub fn set_rects(&mut self, id: u32, rects: Vec<AtlasRect>) {
        self.next_region = self.next_region.max(id.saturating_add(1));
        self.regions.insert(id, rects);
    }

    /// Reemplaza solo los rectángulos empaquetados en una textura de atlas, sin tocar el índice de
    /// sprites. Lo usa `push_atlases` para reflejar el empaquetado de `AtlasBuilder`.
    pub fn set_atlas_rects(&mut self, texture_id: u32, rects: Vec<AtlasRect>) {
        if let Some(atlas) = self.textures.get_mut(&texture_id) {
            atlas.rects = rects;
        }
    }

    /// Bind group (textura + sampler) de una textura.
    pub fn bind_group(&self, id: u32) -> Option<&wgpu::BindGroup> {
        self.textures.get(&id).map(|atlas| &atlas.bind_group)
    }

    /// Copia en CPU de los píxeles de una textura, con las filas apretadas. Es la que se subió, no
    /// el contenido actual de la GPU: una superficie usada como destino de render puede diferir.
    pub fn pixels(&self, id: u32) -> Option<&[u8]> {
        self.textures.get(&id).map(|atlas| atlas.pixels.as_slice())
    }

    /// Marca cuántas texturas iniciales son estáticas. Los ids `>= count` se consideran dinámicos y
    /// son los únicos que `dump` exporta y `restore` reemplaza.
    pub fn set_stock_count(&mut self, count: u32) {
        self.stock_count = count;
    }

    pub fn stock_count(&self) -> u32 {
        self.stock_count
    }

    /// Marca cuántos sprites iniciales son estáticos. Son los que crea `push_atlases`; los ids a
    /// partir de este valor son los dinámicos que exporta [`TextureStore::sprite_rects`].
    pub fn set_stock_regions(&mut self, count: u32) {
        self.stock_regions = count;
        self.next_region = self.next_region.max(count);
    }

    /// Rectángulos de los sprites dinámicos, indexados por id a partir de `stock_regions` y
    /// conservando los huecos de los sprites borrados. Es el equivalente de
    /// `texture_rects[stock_texture_count..]` del backend OpenGL y lo que guarda un savestate.
    pub fn sprite_rects(&self) -> Vec<Option<AtlasRect>> {
        let start = self.stock_regions;
        let end = self.next_region.max(start);
        (start..end).map(|id| self.regions.get(&id).and_then(|rects| rects.first().copied())).collect()
    }

    /// Restaura los rectángulos de [`TextureStore::sprite_rects`]: descarta los sprites dinámicos
    /// actuales y asigna la lista a los ids `stock_regions + índice`. Las texturas no se tocan.
    pub fn set_sprite_rects(&mut self, rects: &[Option<AtlasRect>]) {
        let start = self.stock_regions;
        let end = start.saturating_add(rects.len() as u32);
        let dynamic: Vec<u32> = self.regions.keys().copied().filter(|id| *id >= start).collect();
        for id in dynamic {
            self.regions.remove(&id);
        }
        for (index, rect) in rects.iter().enumerate() {
            if let Some(rect) = rect {
                self.regions.insert(start.saturating_add(index as u32), vec![*rect]);
            }
        }
        self.next_region = end;
    }

    /// Siguiente id libre para una textura nueva, reutilizando los huecos que dejaron las texturas
    /// dinámicas liberadas (igual que hacía el backend OpenGL con su vector de texturas).
    pub fn alloc_texture_id(&mut self) -> u32 {
        let mut id = self.stock_count;
        while self.textures.contains_key(&id) {
            id = id.saturating_add(1);
        }
        self.next_texture = self.next_texture.max(id.saturating_add(1));
        id
    }

    /// Siguiente id libre para un sprite. Los ids no se reutilizan: un sprite borrado deja un hueco
    /// permanente, como `texture_rects` en el backend OpenGL.
    pub fn alloc_sprite_id(&mut self) -> u32 {
        let id = self.next_region;
        self.next_region = self.next_region.saturating_add(1);
        id
    }

    /// Marca como usados todos los ids de sprite menores que `next`. `set_texture_rects` la llama
    /// tras restaurar una lista de rectángulos para que los sprites nuevos no pisen los existentes.
    pub fn reserve_sprite_ids(&mut self, next: u32) {
        self.next_region = self.next_region.max(next);
    }

    /// Elimina una textura de GPU. No toca `regions`: los ids de textura y de sprite son espacios
    /// distintos. Para borrar un sprite usa [`TextureStore::remove_sprite`].
    pub fn remove(&mut self, id: u32) -> Option<AtlasTexture> {
        self.textures.remove(&id)
    }

    /// Elimina una región de sprite. Si la textura a la que pertenece es dinámica y ya no queda
    /// ninguna otra región suya, también libera la textura.
    pub fn remove_sprite(&mut self, id: u32) -> Option<AtlasRect> {
        let rects = self.regions.remove(&id)?;
        let first = rects.first().copied();
        if let Some(rect) = first {
            if rect.atlas_id >= self.stock_count {
                let in_use = self.regions.values().flatten().any(|other| other.atlas_id == rect.atlas_id);
                if !in_use {
                    self.textures.remove(&rect.atlas_id);
                }
            }
        }
        first
    }

    /// Exporta las texturas dinámicas para un savestate, en orden de id y conservando los huecos
    /// (`None`) de las superficies liberadas. Las texturas con id menor que `stock_count` no se
    /// incluyen: se reconstruyen al cargar el juego.
    pub fn dump(&self) -> Vec<Option<SavedTexture>> {
        let start = self.stock_count;
        let end = self.next_texture.max(start);
        (start..end)
            .map(|id| {
                self.textures.get(&id).map(|atlas| SavedTexture {
                    width: atlas.width as i32,
                    height: atlas.height as i32,
                    pixels: atlas.pixels.clone().into_boxed_slice(),
                    zbuf: None,
                })
            })
            .collect()
    }

    /// Restaura las texturas dinámicas exportadas por [`TextureStore::dump`]. La lista se asigna a
    /// los ids `stock_count + índice`, igual que hacía el backend OpenGL; los huecos eliminan la
    /// textura correspondiente y cualquier textura dinámica fuera de la lista se descarta. Las
    /// regiones no se tocan: `set_texture_rects` las restaura después.
    pub fn restore(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        textures: &[Option<SavedTexture>],
    ) -> Result<(), String> {
        let start = self.stock_count;
        let end = start.saturating_add(textures.len() as u32);
        for (index, saved) in textures.iter().enumerate() {
            let id = start.saturating_add(index as u32);
            match saved {
                Some(saved) => {
                    if saved.width <= 0 || saved.height <= 0 {
                        return Err(format!(
                            "wgpu: textura guardada {id} con tamaño inválido {}x{}",
                            saved.width, saved.height
                        ));
                    }
                    self.upload(device, queue, id, saved.width as u32, saved.height as u32, &saved.pixels)?;
                },
                None => {
                    self.textures.remove(&id);
                },
            }
        }
        let stale: Vec<u32> = self.textures.keys().copied().filter(|id| *id >= start && *id >= end).collect();
        for id in stale {
            self.textures.remove(&id);
        }
        self.next_texture = end;
        Ok(())
    }

    /// Crea una textura de 1x1 con el color RGBA indicado y devuelve su referencia.
    pub fn create_colour(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        r: u8,
        g: u8,
        b: u8,
        a: u8,
    ) -> Result<AtlasRef, String> {
        self.create_colour_sized(device, queue, 1, 1, r, g, b, a)
    }

    /// Crea una textura de `width`x`height` rellena del color RGBA indicado. Es lo que necesita
    /// `create_sprite_colour` para los fondos creados por GML, que no son de 1x1.
    pub fn create_colour_sized(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        width: u32,
        height: u32,
        r: u8,
        g: u8,
        b: u8,
        a: u8,
    ) -> Result<AtlasRef, String> {
        if width == 0 || height == 0 {
            return Err(format!("wgpu: color {width}x{height} con tamaño inválido"));
        }
        let count = (width as usize)
            .checked_mul(height as usize)
            .ok_or_else(|| format!("wgpu: color {width}x{height} demasiado grande"))?;
        let pixels = [r, g, b, a].repeat(count);

        let texture_id = self.alloc_texture_id();
        self.upload(device, queue, texture_id, width, height, &pixels)?;

        let sprite_id = self.alloc_sprite_id();
        let rect = AtlasRect {
            atlas_id: texture_id,
            x: 0,
            y: 0,
            w: width as i32,
            h: height as i32,
            origin_x: 0.0,
            origin_y: 0.0,
        };
        self.set_atlas_rects(texture_id, vec![rect]);
        self.set_rects(sprite_id, vec![rect]);
        Ok(AtlasRef(sprite_id as i32))
    }
}

fn create_bind_group(
    device: &wgpu::Device,
    layout: &wgpu::BindGroupLayout,
    view: &wgpu::TextureView,
    sampler: &wgpu::Sampler,
    label: Option<&str>,
) -> wgpu::BindGroup {
    device.create_bind_group(&wgpu::BindGroupDescriptor {
        label,
        layout,
        entries: &[
            wgpu::BindGroupEntry { binding: 0, resource: wgpu::BindingResource::TextureView(view) },
            wgpu::BindGroupEntry { binding: 1, resource: wgpu::BindingResource::Sampler(sampler) },
        ],
    })
}

/// Bytes por fila redondeados al múltiplo de 256 que exige la copia a texturas.
fn padded_row_bytes(width: u32) -> usize {
    let row = width as usize * 4;
    let align = wgpu::COPY_BYTES_PER_ROW_ALIGNMENT as usize;
    row.div_ceil(align) * align
}

#[cfg(test)]
mod tests {
    use super::padded_row_bytes;

    #[test]
    fn rows_are_padded_to_256_bytes() {
        assert_eq!(padded_row_bytes(1), 256);
        assert_eq!(padded_row_bytes(64), 256);
        assert_eq!(padded_row_bytes(65), 512);
        assert_eq!(padded_row_bytes(4096), 16384);
    }
}
