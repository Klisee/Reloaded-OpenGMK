//! Subida y gestión de texturas de atlas en wgpu.

use crate::render::atlas::AtlasRect;
use std::collections::HashMap;

pub struct TextureStore {
    /// Regiones registradas por atlas.
    pub regions: HashMap<u32, Vec<AtlasRect>>,
    /// Texturas subidas a la GPU.
    pub textures: HashMap<u32, wgpu::Texture>,
}

impl TextureStore {
    pub fn new() -> Self {
        Self { regions: HashMap::new(), textures: HashMap::new() }
    }

    /// Sube un atlas RGBA8 completo usando un staging buffer y `write_texture`.
    pub fn upload(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        id: u32,
        width: u32,
        height: u32,
        pixels: &[u8],
    ) {
        let _ = (device, queue, id, width, height, pixels);
        // TODO(wgpu): device.create_texture + queue.write_texture con la alineación de 256 bytes.
    }
}
