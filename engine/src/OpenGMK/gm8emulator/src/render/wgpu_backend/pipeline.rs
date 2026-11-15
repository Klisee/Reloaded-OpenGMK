//! Pipeline de sprites para wgpu: vértices, blending premultiplicado y sampler nearest.

/// Espejo de `crate::render::Vertex`; debe conservar el mismo layout (72 bytes).
#[repr(C)]
#[derive(Clone, Copy, Debug, bytemuck::Pod, bytemuck::Zeroable)]
pub struct GpuVertex {
    pub pos: [f32; 3],
    pub interp: [f32; 2],
    pub tex_coord: [f32; 2],
    pub blend: [f32; 4],
    pub atlas_xywh: [f32; 4],
    pub normal: [f32; 3],
}

pub struct SpritePipeline {
    pub pipeline: wgpu::RenderPipeline,
    pub sampler: wgpu::Sampler,
}

impl SpritePipeline {
    pub fn new(
        device: &wgpu::Device,
        format: wgpu::TextureFormat,
        bind_group_layout: &wgpu::BindGroupLayout,
    ) -> Self {
        // blending premultiplicado: src + dst*(1-src.a)
        let blend = wgpu::BlendState::PREMULTIPLIED_ALPHA_BLENDING;
        let _ = (device, format, bind_group_layout, blend);
        // TODO(wgpu): crear el shader module desde shaders.wgsl y el pipeline.
        todo!("SpritePipeline::new pendiente")
    }
}
