// Traducción de render/glsl/vertex.glsl y fragment.glsl.
// Mantener las mismas uniforms que el backend OpenGL.

struct Uniforms {
    projection: mat4x4<f32>,
    // TODO(wgpu): completar con fog, luces y estado que usa el shader GLSL.
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(0) var atlas: texture_2d<f32>;
@group(1) @binding(1) var atlas_sampler: sampler;

struct VertexIn {
    @location(0) pos: vec3<f32>,
    @location(1) interp: vec2<f32>,
    @location(2) tex_coord: vec2<f32>,
    @location(3) blend: vec4<f32>,
    @location(4) atlas_xywh: vec4<f32>,
    @location(5) normal: vec3<f32>,
};

struct VertexOut {
    @builtin(position) clip_pos: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) colour: vec4<f32>,
};

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
    var out: VertexOut;
    let world = uniforms.projection * vec4<f32>(input.pos, 1.0);
    // Vulkan tiene Z en [0,1]; GL en [-1,1].
    out.clip_pos = vec4<f32>(world.xy, (world.z + world.w) * 0.5, world.w);
    // Las UV del atlas vienen normalizadas por el propio vértice en el backend GL.
    out.uv = input.tex_coord;
    out.colour = input.blend;
    return out;
}

@fragment
fn fs_main(input: VertexOut) -> @location(0) vec4<f32> {
    let tex = textureSample(atlas, atlas_sampler, input.uv);
    return tex * input.colour;
}
