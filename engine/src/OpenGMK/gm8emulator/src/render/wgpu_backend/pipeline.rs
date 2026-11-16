//! Pipeline de sprites para wgpu: vértices, blending premultiplicado y sampler nearest.

use wgpu::util::DeviceExt as _;

/// Espejo de `crate::render::Vertex`; debe conservar el mismo layout (72 bytes).
///
/// Los offsets de los campos son los que espera `GpuVertex::layout()`: 0 (pos), 12 (interp),
/// 20 (tex_coord), 28 (blend), 44 (atlas_xywh), 60 (normal). `draw.rs` sube los vértices como
/// `VertexPod`, que tiene este mismo layout, así que el pipeline puede leerlos sin conversión.
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

impl GpuVertex {
    /// El layout del buffer de vértices para `SpritePipeline`.
    ///
    /// Se construye a mano en vez de con `vertex_attr_array!` porque este último calcula los
    /// offsets sumando tamaños y nuestro `pos`/`normal` son `vec3` de 12 bytes: el macro los
    /// encadenaría igual, pero dejando los offsets explícitos se ve de un vistazo que coinciden
    /// con `Vertex::POS_OFFSET` y compañía.
    pub fn layout() -> wgpu::VertexBufferLayout<'static> {
        const ATTRS: [wgpu::VertexAttribute; 6] = [
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x3, offset: 0, shader_location: 0 },
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 12, shader_location: 1 },
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x2, offset: 20, shader_location: 2 },
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x4, offset: 28, shader_location: 3 },
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x4, offset: 44, shader_location: 4 },
            wgpu::VertexAttribute { format: wgpu::VertexFormat::Float32x3, offset: 60, shader_location: 5 },
        ];
        wgpu::VertexBufferLayout {
            array_stride: std::mem::size_of::<GpuVertex>() as wgpu::BufferAddress,
            step_mode: wgpu::VertexStepMode::Vertex,
            attributes: &ATTRS,
        }
    }
}

/// Un `Light` del shader, con los mismos campos que el `GLLight` del backend OpenGL.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, bytemuck::Pod, bytemuck::Zeroable)]
pub struct SpriteLight {
    /// Direccional: xyz = dirección. Puntual: xyz = posición. w no se usa.
    pub pos: [f32; 4],
    /// rgb = color; a no se usa.
    pub colour: [f32; 4],
    /// x = enabled, y = is_point, z = range, w sin usar. Los dos primeros son 0.0/1.0 porque
    /// WGSL no admite `bool` en un uniform buffer.
    pub params: [f32; 4],
}

/// Estado de render que el shader necesita, calcado del `RenderState` del backend OpenGL.
///
/// **Tamaño: 608 bytes.** El layout está pensado para que coincida con el `struct Uniforms` de
/// `shaders.wgsl`, que usa las mismas reglas de alineación de WGSL (matriz 16 B, vec4 16 B, array
/// de `Light` con stride 48 B):
///
/// | offset | campo              | bytes |
/// |--------|--------------------|-------|
/// | 0      | projection         | 64    |
/// | 64     | model              | 64    |
/// | 128    | ambient_colour     | 16    |
/// | 144    | lights[8]          | 384   |
/// | 528    | flags (u32 x4)     | 16    |
/// | 544    | flags2 (u32 x4)    | 16    |
/// | 560    | fog_range          | 16    |
/// | 576    | fog_colour         | 16    |
/// | 592    | interp_amount      | 4     |
/// | 596    | interp_pad         | 12    |
///
/// `interp_amount` va al final con tres floats de relleno para que el struct mida un múltiplo de
/// 16 (lo que exige `wgpu::Limits::min_uniform_buffer_offset_alignment`).
///
/// `flags` = (lighting_enabled, gouraud_shading, colour_blending, alpha_test) y
/// `flags2` = (repeat, lerp, fog_enabled, gm81_normalize); 0 = off, 1 = on.
#[repr(C)]
#[derive(Clone, Copy, Debug, bytemuck::Pod, bytemuck::Zeroable)]
pub struct SpriteUniforms {
    pub projection: [[f32; 4]; 4],
    pub model: [[f32; 4]; 4],
    pub ambient_colour: [f32; 4],
    pub lights: [SpriteLight; 8],
    pub flags: [u32; 4],
    pub flags2: [u32; 4],
    pub fog_range: [f32; 4],
    pub fog_colour: [f32; 4],
    pub interp_amount: f32,
    pub interp_pad: [f32; 3],
}

impl Default for SpriteUniforms {
    fn default() -> Self {
        Self {
            projection: [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0], [0.0, 0.0, 1.0, 0.0], [0.0, 0.0, 0.0, 1.0]],
            model: [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0], [0.0, 0.0, 1.0, 0.0], [0.0, 0.0, 0.0, 1.0]],
            ambient_colour: [0.0; 4],
            lights: [SpriteLight::default(); 8],
            // colour_blending arranca en 1: es el `texture_blend: GLBool::True` del backend GL.
            flags: [0, 1, 1, 0],
            flags2: [0; 4],
            fog_range: [0.0; 4],
            fog_colour: [0.0; 4],
            interp_amount: 0.0,
            interp_pad: [0.0; 3],
        }
    }
}

const _: () = {
    assert!(std::mem::size_of::<SpriteUniforms>() == 608);
    assert!(std::mem::offset_of!(SpriteUniforms, model) == 64);
    assert!(std::mem::offset_of!(SpriteUniforms, ambient_colour) == 128);
    assert!(std::mem::offset_of!(SpriteUniforms, lights) == 144);
    assert!(std::mem::offset_of!(SpriteUniforms, flags) == 528);
    assert!(std::mem::offset_of!(SpriteUniforms, flags2) == 544);
    assert!(std::mem::offset_of!(SpriteUniforms, fog_range) == 560);
    assert!(std::mem::offset_of!(SpriteUniforms, fog_colour) == 576);
    assert!(std::mem::offset_of!(SpriteUniforms, interp_amount) == 592);
};

pub struct SpritePipeline {
    pub pipeline: wgpu::RenderPipeline,
    /// Nearest + ClampToEdge, sin mips: los sprites son pixel-art y no deben interpolarse al
    /// escalar. El `lerp` opcional del shader lo hace a mano.
    pub sampler: wgpu::Sampler,
    pub uniform_buffer: wgpu::Buffer,
    pub uniform_bind_group: wgpu::BindGroup,
    /// `texture_2d<f32>` + sampler en el grupo 1.
    pub texture_bind_group_layout: wgpu::BindGroupLayout,
    /// El uniform buffer en el grupo 0.
    pub uniform_bind_group_layout: wgpu::BindGroupLayout,
}

impl SpritePipeline {
    /// Crea sampler, bind groups y pipeline de sprites.
    ///
    /// El pipeline dibuja triángulos con blending premultiplicado, sin culling (igual que GL:
    /// los sprites pueden venir con el orden de vértices invertido según la escala) y sin
    /// depth-stencil: el estado de profundidad lo gestiona el backend GL con la ViewMatrix, no
    /// el pipeline.
    pub fn new(device: &wgpu::Device, format: wgpu::TextureFormat) -> Self {
        let uniform_bind_group_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator sprite uniform layout"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::VERTEX_FRAGMENT,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: wgpu::BufferSize::new(std::mem::size_of::<SpriteUniforms>() as u64),
                },
                count: None,
            }],
        });

        let texture_bind_group_layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("gm8emulator sprite texture layout"),
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

        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("gm8emulator sprite sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            address_mode_w: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Nearest,
            min_filter: wgpu::FilterMode::Nearest,
            mipmap_filter: wgpu::MipmapFilterMode::Nearest,
            lod_min_clamp: 0.0,
            lod_max_clamp: 0.0,
            compare: None,
            anisotropy_clamp: 1,
            border_color: None,
        });

        // El buffer se crea ya relleno con `SpriteUniforms::default()`. Sin esto arrancaría a
        // ceros, `model` sería la matriz nula y todo sprite colapsaría al origen hasta que
        // alguien llamase a `write_state`; `write_uniforms` solo toca proyección e interpolación
        // y no puede arreglarlo.
        let uniform_buffer = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("gm8emulator sprite uniforms"),
            contents: bytemuck::bytes_of(&SpriteUniforms::default()),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });

        let uniform_bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("gm8emulator sprite uniform bind group"),
            layout: &uniform_bind_group_layout,
            entries: &[wgpu::BindGroupEntry { binding: 0, resource: uniform_buffer.as_entire_binding() }],
        });

        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("gm8emulator sprite shader"),
            source: wgpu::ShaderSource::Wgsl(include_str!("shaders.wgsl").into()),
        });

        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("gm8emulator sprite pipeline layout"),
            // El grupo 0 es el uniform; el 1, el atlas. El orden debe coincidir con los
            // `@group(...)` de shaders.wgsl.
            bind_group_layouts: &[Some(&uniform_bind_group_layout), Some(&texture_bind_group_layout)],
            immediate_size: 0,
        });

        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("gm8emulator sprite pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: Some("vs_main"),
                compilation_options: wgpu::PipelineCompilationOptions::default(),
                buffers: &[Some(GpuVertex::layout())],
            },
            // Los sprites se dibujan con `draw_buffers`, que fija la topología por lote; el
            // pipeline se crea para triángulos y el backend la cambia con `set_pipeline` +
            // draw calls por topología. Ver `draw.rs`.
            //
            // Sin culling: GL lo tiene desactivado (`gl.Disable(gl::CULL_FACE)`) y los sprites
            // llegan con el orden de vértices invertido cuando la escala es negativa. Sin
            // depth-stencil: el pipeline no recibe el formato del z-buffer y GL gestiona la
            // profundidad con la matriz de vista; con `None` el depth test queda desactivado.
            primitive: wgpu::PrimitiveState {
                topology: wgpu::PrimitiveTopology::TriangleList,
                cull_mode: None,
                ..Default::default()
            },
            depth_stencil: None,
            multisample: wgpu::MultisampleState::default(),
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: Some("fs_main"),
                compilation_options: wgpu::PipelineCompilationOptions::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format,
                    // El fragment devuelve color premultiplicado (rgb * a); con `ONE` en el
                    // origen el resultado es equivalente al SRC_ALPHA/ONE_MINUS_SRC_ALPHA de GL.
                    blend: Some(wgpu::BlendState::PREMULTIPLIED_ALPHA_BLENDING),
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            multiview_mask: None,
            cache: None,
        });

        Self {
            pipeline,
            sampler,
            uniform_buffer,
            uniform_bind_group,
            texture_bind_group_layout,
            uniform_bind_group_layout,
        }
    }

    /// Sube la proyección (en el mismo formato de 16 floats que `set_viewproj_matrix`) y la
    /// cantidad de interpolación.
    ///
    /// Solo escribe esas dos regiones del uniform buffer, no el struct entero: así no pisa las
    /// luces, la niebla ni los flags que haya dejado `write_state`. Las dos escrituras empiezan en
    /// un múltiplo de 4, que es lo que exige `Queue::write_buffer`.
    pub fn write_uniforms(&self, queue: &wgpu::Queue, projection: &[[f32; 4]; 4], interp_amount: f32) {
        queue.write_buffer(&self.uniform_buffer, 0, bytemuck::bytes_of(projection));
        let interp_offset = std::mem::offset_of!(SpriteUniforms, interp_amount) as wgpu::BufferAddress;
        let interp_amount = interp_amount.clamp(0.0, 1.0);
        queue.write_buffer(&self.uniform_buffer, interp_offset, bytemuck::bytes_of(&interp_amount));
    }

    /// Sube el estado completo (luces, niebla, flags y matrices).
    pub fn write_state(&self, queue: &wgpu::Queue, uniforms: &SpriteUniforms) {
        queue.write_buffer(&self.uniform_buffer, 0, bytemuck::bytes_of(uniforms));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gpu_vertex_matches_cpu_vertex_layout() {
        use crate::render::Vertex;
        assert_eq!(std::mem::size_of::<GpuVertex>(), Vertex::STRIDE);
        assert_eq!(std::mem::size_of::<GpuVertex>(), 72);
        assert_eq!(std::mem::offset_of!(GpuVertex, pos), Vertex::POS_OFFSET);
        assert_eq!(std::mem::offset_of!(GpuVertex, interp), Vertex::INTERP_OFFSET);
        assert_eq!(std::mem::offset_of!(GpuVertex, tex_coord), Vertex::TEX_OFFSET);
        assert_eq!(std::mem::offset_of!(GpuVertex, blend), Vertex::BLEND_OFFSET);
        assert_eq!(std::mem::offset_of!(GpuVertex, atlas_xywh), Vertex::ATLAS_OFFSET);
        assert_eq!(std::mem::offset_of!(GpuVertex, normal), Vertex::NORMAL_OFFSET);
    }

    #[test]
    fn uniform_layout_matches_the_shader() {
        assert_eq!(std::mem::size_of::<SpriteLight>(), 48);
        assert_eq!(std::mem::size_of::<SpriteUniforms>(), 608);
        assert_eq!(std::mem::align_of::<SpriteUniforms>(), 4);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, projection), 0);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, model), 64);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, ambient_colour), 128);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, lights), 144);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, flags), 528);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, flags2), 544);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, fog_range), 560);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, fog_colour), 576);
        assert_eq!(std::mem::offset_of!(SpriteUniforms, interp_amount), 592);
    }

    #[test]
    fn default_uniforms_are_usable_without_write_state() {
        // El buffer se crea con estos valores; si `model` no fuese la identidad, todo sprite
        // colapsaría al origen cuando el backend solo llame a `write_uniforms`.
        let uniforms = SpriteUniforms::default();
        assert_eq!(uniforms.model[0], [1.0, 0.0, 0.0, 0.0]);
        assert_eq!(uniforms.model[1], [0.0, 1.0, 0.0, 0.0]);
        assert_eq!(uniforms.model[2], [0.0, 0.0, 1.0, 0.0]);
        assert_eq!(uniforms.model[3], [0.0, 0.0, 0.0, 1.0]);
        assert_eq!(uniforms.projection, uniforms.model);
        assert_eq!(uniforms.interp_amount, 0.0);
        // lighting off, gouraud on, colour_blending on, alpha_test off: el mismo
        // `RenderState::default` del backend OpenGL.
        assert_eq!(uniforms.flags, [0, 1, 1, 0]);
        assert_eq!(uniforms.flags2, [0, 0, 0, 0]);
    }

    #[test]
    fn vertex_layout_uses_the_documented_offsets() {
        let layout = GpuVertex::layout();
        assert_eq!(layout.array_stride, 72);
        assert_eq!(layout.step_mode, wgpu::VertexStepMode::Vertex);
        let offsets: Vec<u64> = layout.attributes.iter().map(|a| a.offset).collect();
        assert_eq!(offsets, [0, 12, 20, 28, 44, 60]);
    }
}
