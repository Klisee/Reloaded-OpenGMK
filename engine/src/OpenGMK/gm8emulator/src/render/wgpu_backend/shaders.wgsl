// Traducción de render/glsl/vertex.glsl y fragment.glsl al backend wgpu.
//
// Diferencias con el GLSL, todas forzadas por la API:
//  * El clip space de wgpu/Vulkan tiene z en [0, 1]; el de GL en [-1, 1]. Por eso el vertex aplica
//    `(z + w) * 0.5`, que deja el resultado equivalente a la proyección de GL sin tocar la matriz.
//    y sigue la convención de GL: la matriz de proyección ya incluye el volteo vertical.
//  * WGSL no admite `bool` en un uniform buffer: los flags viajan como u32 (0 = off, 1 = on).
//  * El fragment devuelve color premultiplicado porque el pipeline usa un blend premultiplicado;
//    con eso el resultado es el mismo que el glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) de GL.
//  * `textureSampleLevel(..., 0.0)` en vez de `texture(...)`: el atlas no tiene mips y así el
//    muestreo no depende de derivadas (que cambian con el escalado del sprite).

struct Light {
    // Direccional: xyz = dirección. Puntual: xyz = posición. w sin usar.
    pos: vec4<f32>,
    // rgb = color; a sin usar.
    colour: vec4<f32>,
    // x = enabled, y = is_point, z = range (solo puntual), w sin usar.
    params: vec4<f32>,
};

// Debe coincidir byte a byte con `pipeline::SpriteUniforms` (608 bytes, ver su documentación).
struct Uniforms {
    // Matriz view-projection (lo que el GLSL llama `viewproj`).
    projection: mat4x4<f32>,
    // Matriz de modelo; el GLSL la aplica antes de `projection` y a las normales.
    model: mat4x4<f32>,
    ambient_colour: vec4<f32>,
    lights: array<Light, 8>,
    // x = lighting_enabled, y = gouraud_shading, z = colour_blending, w = alpha_test.
    flags: vec4<u32>,
    // x = repeat, y = lerp, z = fog_enabled, w = gm81_normalize.
    flags2: vec4<u32>,
    // x = fog_begin, y = fog_end.
    fog_range: vec4<f32>,
    fog_colour: vec4<f32>,
    // 0 dibuja el frame actual; 1 devuelve cada vértice a donde estaba en el paso anterior.
    interp_amount: f32,
    interp_pad0: f32,
    interp_pad1: f32,
    interp_pad2: f32,
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
    @location(1) atlas_xywh: vec4<f32>,
    @location(2) blend: vec4<f32>,
    // Equivalente al `flat out vec4 frag_blend_flat` de GLSL: un solo valor por primitiva.
    @location(3) @interpolate(flat) blend_flat: vec4<f32>,
    @location(4) fog_z: f32,
};

@vertex
fn vs_main(input: VertexIn) -> VertexOut {
    var out: VertexOut;

    let world_pos = uniforms.model * vec4<f32>(input.pos, 1.0);
    out.atlas_xywh = input.atlas_xywh;
    out.blend_flat = vec4<f32>(1.0);

    // -1 marca una primitiva sin textura: sus vértices muestrean el píxel blanco del atlas y
    // conservan su color. NaN no sirve como marca porque algunas GPUs lo aplanan a 0.
    if (input.tex_coord.x < 0.0) {
        out.uv = vec2<f32>(0.0);
        out.blend = input.blend;
    } else {
        out.uv = input.tex_coord;
        // Sin colour_blending el color viene solo de la textura (glTexEnv GL_REPLACE).
        out.blend = select(vec4<f32>(1.0), input.blend, uniforms.flags.z != 0u);
    }

    if (uniforms.flags.x != 0u) {
        var light_col = vec3<f32>(0.0);
        // El GLSL niega la normal transformada; se mantiene para no cambiar el sombreado.
        var new_normal = -(uniforms.model * vec4<f32>(input.normal, 0.0)).xyz;
        if (uniforms.flags2.w != 0u) {
            new_normal = normalize(new_normal);
        }
        for (var i = 0u; i < 8u; i = i + 1u) {
            let light = uniforms.lights[i];
            if (light.params.x != 0.0) {
                var this_light_col = light.colour.rgb;
                var ray = light.pos.xyz;
                if (light.params.y != 0.0) {
                    ray = world_pos.xyz - ray;
                    let dist = length(ray);
                    if (dist < light.params.z) {
                        this_light_col = this_light_col / vec3<f32>(1.0 + (4.0 / light.params.z) * dist);
                    } else {
                        this_light_col = vec3<f32>(0.0);
                    }
                }
                let lambert = clamp(dot(normalize(ray), new_normal), 0.0, 1.0);
                light_col = light_col + this_light_col * vec3<f32>(lambert);
            }
        }
        if (uniforms.flags.y != 0u) {
            // Gouraud: la luz va en el color de vértice y se interpola.
            out.blend = vec4<f32>(out.blend.rgb * light_col + uniforms.ambient_colour.rgb, out.blend.a);
        } else {
            // Flat: la luz se aplica una vez por primitiva, en el fragment.
            out.blend_flat =
                vec4<f32>(out.blend_flat.rgb * light_col + uniforms.ambient_colour.rgb, out.blend_flat.a);
        }
    }

    // Se aplica después de la luz para que las posiciones de las luces sigan en coordenadas del
    // juego. `interp` guarda cuánto se movió el vértice desde el frame anterior (actual -
    // anterior) y se resta: con `interp_amount` en 1 el vértice cae donde estaba en el paso
    // anterior, y en 0 se queda en el actual. `set_draw_offset` del backend wgpu debe guardar el
    // desplazamiento en ese sentido (el backend GL usa el opuesto y suma).
    let interpolated = world_pos.xy - input.interp * uniforms.interp_amount;
    let clip = uniforms.projection * vec4<f32>(interpolated, world_pos.z, world_pos.w);

    // GL: z en [-1, 1]. Vulkan/wgpu: z en [0, 1]. (z + w) * 0.5 lleva el clip space de GL al de
    // Vulkan; con w = 1 equivale a (z + 1) * 0.5.
    out.clip_pos = vec4<f32>(clip.xy, (clip.z + clip.w) * 0.5, clip.w);
    // El GLSL usa gl_Position.z (clip space, no NDC) para la niebla.
    out.fog_z = clip.z;
    return out;
}

// `mod` de GLSL: siempre positivo con divisor positivo. El `%` de WGSL conserva el signo del
// dividendo, y aquí el dividendo puede ser negativo (floor(coord - 0.5) con coord < 1).
fn glsl_mod(x: vec2<f32>, y: vec2<f32>) -> vec2<f32> {
    return x - y * floor(x / y);
}

@fragment
fn fs_main(input: VertexOut) -> @location(0) vec4<f32> {
    // `atlas_xywh` viene en píxeles del atlas (x, y, w, h), no normalizado. `uv` es la posición
    // dentro del sprite en 0..1, igual que en el backend GL.
    let tex_size = vec2<f32>(textureDimensions(atlas, 0));
    let origin = input.atlas_xywh.xy;
    let span = input.atlas_xywh.zw;

    var tex_col: vec4<f32>;
    if (uniforms.flags2.x != 0u) {
        // repeat: da la vuelta a la coordenada dentro del sprite.
        let wrapped = fract(input.uv) * span;
        if (uniforms.flags2.y != 0u) {
            // lerp: mezcla a mano los cuatro texeles vecinos (el sampler es NEAREST).
            let floor_coord = floor(wrapped - vec2<f32>(0.5));
            let topleft = (origin + glsl_mod(floor_coord + vec2<f32>(0.5), span)) / tex_size;
            let botright = (origin + glsl_mod(floor_coord + vec2<f32>(1.5), span)) / tex_size;

            let sample_tl = textureSampleLevel(atlas, atlas_sampler, topleft, 0.0);
            let sample_tr =
                textureSampleLevel(atlas, atlas_sampler, vec2<f32>(botright.x, topleft.y), 0.0);
            let sample_bl =
                textureSampleLevel(atlas, atlas_sampler, vec2<f32>(topleft.x, botright.y), 0.0);
            let sample_br = textureSampleLevel(atlas, atlas_sampler, botright, 0.0);

            let factor = fract(wrapped + vec2<f32>(0.5));
            let mix_top = mix(sample_tl, sample_tr, factor.x);
            let mix_bot = mix(sample_bl, sample_br, factor.x);
            tex_col = mix(mix_top, mix_bot, factor.y);
        } else {
            // El wrap ya está hecho; se limita al centro de los píxeles del borde.
            let clamped = clamp(wrapped, vec2<f32>(0.5), span - vec2<f32>(0.5));
            tex_col = textureSampleLevel(atlas, atlas_sampler, (origin + clamped) / tex_size, 0.0);
        }
    } else {
        // Centro del píxel del borde: mantiene el nearest sin sangrar del sprite vecino.
        let clamped = clamp(input.uv * span, vec2<f32>(0.5), span - vec2<f32>(0.5));
        tex_col = textureSampleLevel(atlas, atlas_sampler, (origin + clamped) / tex_size, 0.0);
    }

    var colour = tex_col * input.blend * input.blend_flat;

    if (uniforms.flags2.z != 0u) {
        let f = clamp(
            (uniforms.fog_range.y - input.fog_z) / (uniforms.fog_range.y - uniforms.fog_range.x),
            0.0,
            1.0,
        );
        colour = vec4<f32>(mix(uniforms.fog_colour.rgb, colour.rgb, f), colour.a);
    }

    if (uniforms.flags.w != 0u && colour.a <= 0.0) {
        // El alpha test no puede ser una máscara de escritura: los modos de blend lo necesitan.
        discard;
    }

    // Blend premultiplicado: el pipeline multiplica el destino por (1 - a) y suma este color.
    return vec4<f32>(colour.rgb * colour.a, colour.a);
}
