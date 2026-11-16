// Grupo `geometry`: implementación real. Las funciones del trait en mod.rs delegan aquí.
//
// La geometría replica la del backend OpenGL (`render/opengl.rs`) vértice a vértice: los mismos
// quads, el mismo orden de triangulación, los mismos UVs normalizados y los mismos colores de
// esquina. La única diferencia es el destino: en vez de una cola que termina en un draw call de
// OpenGL, aquí los vértices van al `DrawList` del frame, que los agrupa por atlas y topología.
//
// El motor es monohilo y cada `RendererImpl` tiene su propio estado: las primitivas 2D/3D viven en
// los campos `primitive_2d`/`primitive_3d` de mod.rs, igual que en OpenGL, así que no hace falta
// ningún buffer `thread_local`.
use super::*;
use std::f64::consts::PI;

/// Igual que `render::opengl::split_colour`: RGB empaquetado (0xBBGGRR) y alfa a 0..1.
fn split_colour(rgb: i32, alpha: f64) -> [f32; 4] {
    [
        ((rgb & 0xFF) as f32) / 255.0,
        (((rgb >> 8) & 0xFF) as f32) / 255.0,
        (((rgb >> 16) & 0xFF) as f32) / 255.0,
        alpha.max(0.0).min(1.0) as f32,
    ]
}

/// Rectángulo del píxel blanco 1x1, el atlas de las primitivas sin textura.
fn white_rect(r: &RendererImpl) -> AtlasRect {
    r.textures.rect(r.white_pixel).copied().unwrap_or(AtlasRect {
        atlas_id: 0,
        x: 0,
        y: 0,
        w: 1,
        h: 1,
        origin_x: 0.0,
        origin_y: 0.0,
    })
}

/// Topología de wgpu que corresponde a la forma con la que el builder ya trianguló la primitiva.
fn topology_for(shape: PrimitiveShape) -> wgpu::PrimitiveTopology {
    match shape {
        PrimitiveShape::Point => wgpu::PrimitiveTopology::PointList,
        PrimitiveShape::Line => wgpu::PrimitiveTopology::LineList,
        PrimitiveShape::Triangle => wgpu::PrimitiveTopology::TriangleList,
    }
}

/// Espejo de `render::opengl::ShapeBuilder`: construye las figuras básicas sin textura.
struct ShapeBuilder {
    primitive: PrimitiveBuilder,
    outline: bool,
    depth: f32,
    alpha: f64,
}

impl ShapeBuilder {
    fn new(outline: bool, atlas_ref: AtlasRect, alpha: f64, depth: f32) -> Self {
        Self {
            primitive: PrimitiveBuilder::new(
                atlas_ref,
                if outline { PrimitiveType::LineStrip } else { PrimitiveType::TriFan },
                false,
            ),
            outline,
            depth,
            alpha,
        }
    }

    /// Atajo para las figuras básicas, idéntico al del backend OpenGL.
    fn push_point(&mut self, x: f64, y: f64, colour: i32) -> &mut Self {
        self.primitive.push_vertex(
            [x as f32, y as f32, self.depth],
            [0.0, 0.0],
            split_colour(colour, self.alpha),
            [0.0, 0.0, 0.0],
        );
        self
    }

    /// Cierra el contorno (solo para las figuras huecas) y devuelve la primitiva terminada.
    fn build(&mut self) -> &PrimitiveBuilder {
        if self.outline {
            let vertices = self.primitive.get_vertices();
            if vertices.len() > 2 {
                let vertex = vertices[0];
                self.primitive.push_vertex_raw(vertex);
            }
        }
        &self.primitive
    }
}

/// Equivalente a `push_primitive` de OpenGL: marca el desplazamiento de interpolación (solo cuando
/// el frame interpola; en modo nativo el uniforme es cero y el atributo da igual) y añade los
/// vértices ya triangulados a la lista de dibujo del frame.
fn push_primitive(r: &mut RendererImpl, builder: &PrimitiveBuilder) {
    let vertices = builder.get_vertices();
    if vertices.is_empty() {
        return;
    }
    let mut vertices = vertices.to_vec();
    if r.frame.interp_amount != 0.0 {
        let offset = r.frame.draw_offset;
        for vertex in &mut vertices {
            vertex.interp = offset;
        }
    }
    let topology = topology_for(builder.get_shape());
    r.frame.draw_list.push(builder.get_atlas_id(), topology, &vertices);
}

/// Igual que `draw_primitive_2d`/`draw_primitive_3d` de OpenGL: el builder se vuelca tal cual, sin
/// tocar la interpolación (sus vértices ya traen `interp` a cero).
fn push_raw_primitive(r: &mut RendererImpl, use_3d: bool) {
    // Los préstamos son de campos distintos de `r`, así que el compilador los acepta a la vez.
    let (builder, draw_list) = if use_3d {
        (&r.primitive_3d, &mut r.frame.draw_list)
    } else {
        (&r.primitive_2d, &mut r.frame.draw_list)
    };
    let vertices = builder.get_vertices();
    if vertices.is_empty() {
        return;
    }
    draw_list.push(builder.get_atlas_id(), topology_for(builder.get_shape()), vertices);
}

/// Profundidad de dibujo, que vive en el grupo `state` (trait_state.rs), como en OpenGL.
fn depth(r: &RendererImpl) -> f32 {
    super::trait_state::get_depth(r)
}

/// Precisión del círculo, acotada por `set_circle_precision` en el grupo `state`.
fn circle_precision(r: &RendererImpl) -> i32 {
    super::trait_state::get_circle_precision(r)
}

pub(crate) fn draw_sprite_general(r: &mut RendererImpl, texture: AtlasRef,         part_x: f64,         part_y: f64,         part_w: f64,         part_h: f64,         x: f64,         y: f64,         xscale: f64,         yscale: f64,         angle: f64,         col1: i32,         col2: i32,         col3: i32,         col4: i32,         alpha: f64,         use_origin: bool,) {
        let atlas_ref = match r.textures.rect(texture) {
                Some(rect) => *rect,
                None => return,
        };

        // get angle (GM8 gira en sentido contrario al matemático, de ahí el signo)
        let angle = -angle.to_radians();
        let angle_sin = angle.sin();
        let angle_cos = angle.cos();

        // get real width of drawn sprite
        let width: f64 = xscale * part_w;
        let height: f64 = yscale * part_h;
        // calculate pre-rotation corner offsets from sprite origin
        // incl. subtraction 0.5 from left and top (GM does this in an attempt to combat the DX half-pixel offset)
        let (left, top): (f64, f64) = if use_origin {
                (-width * f64::from(atlas_ref.origin_x) - 0.5, -height * f64::from(atlas_ref.origin_y) - 0.5)
        } else {
                (-0.5, -0.5)
        };
        let right: f64 = left + width;
        let bottom: f64 = top + height;

        // get texture corners.
        // A negative part_w/part_h means the part is mirrored: its size is still |part_w|/|part_h|,
        // but using the signed value put the UVs *outside* the sprite's atlas cell (0 .. -1), so the
        // draw sampled whatever sat next to it in the atlas. The flip itself is already handled by
        // the destination geometry (width/height carry the scale sign), so take the source extent as
        // positive and just swap the corners each vertex samples.
        let tex_x0 = part_x / f64::from(atlas_ref.w);
        let tex_y0 = part_y / f64::from(atlas_ref.h);
        let tex_x1 = tex_x0 + part_w.abs() / f64::from(atlas_ref.w);
        let tex_y1 = tex_y0 + part_h.abs() / f64::from(atlas_ref.h);
        let (tex_left, tex_right) = if part_w < 0.0 { (tex_x1, tex_x0) } else { (tex_x0, tex_x1) };
        let (tex_top, tex_bottom) = if part_h < 0.0 { (tex_y1, tex_y0) } else { (tex_y0, tex_y1) };

        let (tex_left, tex_top, tex_right, tex_bottom) =
                (tex_left as f32, tex_top as f32, tex_right as f32, tex_bottom as f32);

        let normal = [0.0, 0.0, 0.0];
        let depth = depth(r);

        // rotate around draw origin
        let rotate = |xoff, yoff| {
                [(x + xoff * angle_cos - yoff * angle_sin) as f32, (y + yoff * angle_cos + xoff * angle_sin) as f32, depth]
        };

        // push the vertices (TriFan: el builder los expande al mismo triángulo doble que OpenGL)
        let mut primitive = PrimitiveBuilder::new(atlas_ref, PrimitiveType::TriFan, true);
        primitive
                .push_vertex(rotate(left, top), [tex_left, tex_top], split_colour(col1, alpha), normal)
                .push_vertex(rotate(right, top), [tex_right, tex_top], split_colour(col2, alpha), normal)
                .push_vertex(rotate(right, bottom), [tex_right, tex_bottom], split_colour(col3, alpha), normal)
                .push_vertex(rotate(left, bottom), [tex_left, tex_bottom], split_colour(col4, alpha), normal);
        push_primitive(r, &primitive);
}

pub(crate) fn draw_sprite_pos(r: &mut RendererImpl, texture: AtlasRef,         x1: f64,         y1: f64,         x2: f64,         y2: f64,         x3: f64,         y3: f64,         x4: f64,         y4: f64,         alpha: f64,) {
        let atlas_ref = match r.textures.rect(texture) {
                Some(rect) => *rect,
                None => return,
        };

        // get texture corners: the whole sprite cell, in 0..1
        let (tex_left, tex_top, tex_right, tex_bottom) = (0.0_f32, 0.0_f32, 1.0_f32, 1.0_f32);

        let normal = [0.0, 0.0, 0.0];
        let depth = depth(r);

        // correct for gm offset
        let correct = |xoff, yoff| {
                [(xoff-0.5) as f32, (yoff-0.5) as f32, depth]
        };

        let mut primitive = PrimitiveBuilder::new(atlas_ref, PrimitiveType::TriFan, true);
        primitive
                .push_vertex(correct(x1, y1), [tex_left, tex_top], split_colour(0xffffff, alpha), normal)
                .push_vertex(correct(x2, y2), [tex_right, tex_top], split_colour(0xffffff, alpha), normal)
                .push_vertex(correct(x3, y3), [tex_right, tex_bottom], split_colour(0xffffff, alpha), normal)
                .push_vertex(correct(x4, y4), [tex_left, tex_bottom], split_colour(0xffffff, alpha), normal);
        push_primitive(r, &primitive);
}

pub(crate) fn draw_rectangle(r: &mut RendererImpl, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        let (x1, x2) = if x2 < x1 { (x2, x1) } else { (x1, x2) };
        let (y1, y2) = if y2 < y1 { (y2, y1) } else { (y1, y2) };
        let x2 = if x2 == x2.floor() { x2 + 0.01 } else { x2 };
        let y2 = if y2 == y2.floor() { y2 + 0.01 } else { y2 };
        let mut shape = ShapeBuilder::new(false, white_rect(r), alpha, depth(r));
        shape
                .push_point(x1, y1, colour)
                .push_point(x2, y1, colour)
                .push_point(x2, y2, colour)
                .push_point(x1, y2, colour);
        push_primitive(r, shape.build());
}

pub(crate) fn draw_rectangle_outline(r: &mut RendererImpl, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        let (x1, x2) = if x2 < x1 { (x2, x1) } else { (x1, x2) };
        let (y1, y2) = if y2 < y1 { (y2, y1) } else { (y1, y2) };
        let x2 = if x2 == x2.floor() { x2 + 0.01 } else { x2 };
        let y2 = if y2 == y2.floor() { y2 + 0.01 } else { y2 };
        let mut shape = ShapeBuilder::new(true, white_rect(r), alpha, depth(r));
        shape
                .push_point(x1, y1, colour)
                .push_point(x2, y1, colour)
                .push_point(x2, y2, colour)
                .push_point(x1, y2, colour);
        push_primitive(r, shape.build());
}

pub(crate) fn draw_rectangle_gradient(r: &mut RendererImpl, x1: f64,         y1: f64,         x2: f64,         y2: f64,         c1: i32,         c2: i32,         c3: i32,         c4: i32,         alpha: f64,         outline: bool,) {
        let (x1, x2) = if x2 < x1 { (x2, x1) } else { (x1, x2) };
        let (y1, y2) = if y2 < y1 { (y2, y1) } else { (y1, y2) };
        let x2 = if x2 == x2.floor() { x2 + 0.01 } else { x2 };
        let y2 = if y2 == y2.floor() { y2 + 0.01 } else { y2 };
        let mut shape = ShapeBuilder::new(outline, white_rect(r), alpha, depth(r));
        shape
                .push_point(x1, y1, c1)
                .push_point(x2, y1, c2)
                .push_point(x2, y2, c3)
                .push_point(x1, y2, c4);
        push_primitive(r, shape.build());
}

pub(crate) fn draw_point(r: &mut RendererImpl, x: f64, y: f64, colour: i32, alpha: f64) {
        // OpenGL empuja el vértice directo a la cola con `tex_coord` NaN; aquí `-1` es la marca de
        // primitiva sin textura que entiende el shader de wgpu.
        let white = white_rect(r);
        let mut vertex = Vertex {
                pos: [x as f32, y as f32, depth(r)],
                interp: [0.0, 0.0],
                tex_coord: [-1.0; 2],
                blend: split_colour(colour, alpha),
                atlas_xywh: white.into(),
                normal: [0.0, 0.0, 0.0],
        };
        if r.frame.interp_amount != 0.0 {
                vertex.interp = r.frame.draw_offset;
        }
        r.frame.draw_list.push(white.atlas_id, wgpu::PrimitiveTopology::PointList, &[vertex]);
}

pub(crate) fn draw_line(r: &mut RendererImpl, x1: f64, y1: f64, x2: f64, y2: f64, width: Option<f64>, c1: i32, c2: i32, alpha: f64) {
        if let Some(width) = width {
                let length = (x2 - x1).hypot(y2 - y1);
                // on the off chance that they're in different points but the length is still somehow 0, check length
                if length != 0.0 {
                        // calculate corners
                        let width_x = (y2 - y1) * (width / 2.0) / length;
                        let width_y = (x2 - x1) * (width / 2.0) / length;
                        // actually push the rectangle
                        let mut shape = ShapeBuilder::new(false, white_rect(r), alpha, depth(r));
                        shape
                                .push_point(x1 - width_x, y1 + width_y, c1)
                                .push_point(x1 + width_x, y1 - width_y, c1)
                                .push_point(x2 + width_x, y2 - width_y, c2)
                                .push_point(x2 - width_x, y2 + width_y, c2);
                        push_primitive(r, shape.build());
                }
        } else {
                let mut shape = ShapeBuilder::new(true, white_rect(r), alpha, depth(r));
                shape.push_point(x1, y1, c1).push_point(x2, y2, c2);
                push_primitive(r, shape.build());
        }
}

pub(crate) fn draw_triangle(r: &mut RendererImpl, x1: f64,         y1: f64,         x2: f64,         y2: f64,         x3: f64,         y3: f64,         c1: i32,         c2: i32,         c3: i32,         alpha: f64,         outline: bool,) {
        let mut shape = ShapeBuilder::new(outline, white_rect(r), alpha, depth(r));
        shape.push_point(x1, y1, c1).push_point(x2, y2, c2).push_point(x3, y3, c3);
        push_primitive(r, shape.build());
}

pub(crate) fn draw_ellipse(r: &mut RendererImpl, x: f64, y: f64, rad_x: f64, rad_y: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        let precision = circle_precision(r);
        let mut shape = ShapeBuilder::new(outline, white_rect(r), alpha, depth(r));
        if !outline {
                shape.push_point(x, y, c1);
        }
        for i in 0..=precision {
                let angle = f64::from(i) * 2.0 * PI / f64::from(precision);
                shape.push_point(x + rad_x * angle.cos(), y + rad_y * angle.sin(), c2);
        }
        push_primitive(r, shape.build());
}

pub(crate) fn draw_roundrect(r: &mut RendererImpl, x1: f64, y1: f64, x2: f64, y2: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        let x2 = if x2 == x2.floor() { x2 + 0.01 } else { x2 };
        let y2 = if y2 == y2.floor() { y2 + 0.01 } else { y2 };
        let xcenter = (x1 + x2) / 2.0;
        let ycenter = (y1 + y2) / 2.0;
        let width = (x2 - x1).abs();
        let height = (y2 - y1).abs();
        let rad_x = width.min(10.0) / 2.0;
        let rad_y = height.min(10.0) / 2.0;
        let rect_half_w = (width / 2.0 - rad_x).max(0.0);
        let rect_half_h = (height / 2.0 - rad_y).max(0.0);
        let precision = circle_precision(r);
        let mut shape = ShapeBuilder::new(outline, white_rect(r), alpha, depth(r));
        if !outline {
                shape.push_point(xcenter, ycenter, c1);
        }
        let quarter_circle = precision / 4;
        for quad in 0..4 {
                let circle_x = xcenter + if quad == 0 || quad == 3 { rect_half_w } else { -rect_half_w };
                let circle_y = ycenter + if quad < 2 { rect_half_h } else { -rect_half_h };
                for i in quarter_circle * quad..=quarter_circle * (quad + 1) {
                        let angle = f64::from(i) * 2.0 * PI / f64::from(precision);
                        shape.push_point(circle_x + rad_x * angle.cos(), circle_y + rad_y * angle.sin(), c2);
                }
        }
        shape.push_point(xcenter + rect_half_w + rad_x, ycenter + rect_half_h, c2);
        push_primitive(r, shape.build());
}

pub(crate) fn reset_primitive_2d(r: &mut RendererImpl, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        // El estado de las primitivas vive en `RendererImpl` (mod.rs), como en OpenGL: el motor es
        // monohilo y cada renderer tiene el suyo, así que no hace falta ningún buffer aparte.
        let ar = atlas_ref.and_then(|ar| r.textures.rect(ar).copied());
        let white = white_rect(r);
        r.primitive_2d = PrimitiveBuilder::new(ar.unwrap_or(white), ptype, ar.is_some());
}

pub(crate) fn vertex_2d(r: &mut RendererImpl, x: f64, y: f64, xtex: f64, ytex: f64, col: i32, alpha: f64) {
        r.primitive_2d.push_vertex(
                [x as f32, y as f32, depth(r)],
                [xtex as f32, ytex as f32],
                split_colour(col, alpha),
                [0.0, 0.0, 0.0],
        );
}

pub(crate) fn draw_primitive_2d(r: &mut RendererImpl) {
        push_raw_primitive(r, false);
}

pub(crate) fn get_primitive_2d(r: &RendererImpl)-> PrimitiveBuilder {
        r.primitive_2d.clone()
}

pub(crate) fn set_primitive_2d(r: &mut RendererImpl, prim: PrimitiveBuilder) {
        r.primitive_2d = prim;
}

pub(crate) fn reset_primitive_3d(r: &mut RendererImpl, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        let ar = atlas_ref.and_then(|ar| r.textures.rect(ar).copied());
        let white = white_rect(r);
        r.primitive_3d = PrimitiveBuilder::new(ar.unwrap_or(white), ptype, ar.is_some());
}

pub(crate) fn vertex_3d(r: &mut RendererImpl, x: f64,         y: f64,         z: f64,         nx: f64,         ny: f64,         nz: f64,         xtex: f64,         ytex: f64,         col: i32,         alpha: f64,) {
        r.primitive_3d.push_vertex(
                [x as f32, y as f32, z as f32],
                [xtex as f32, ytex as f32],
                split_colour(col, alpha),
                [nx as f32, ny as f32, nz as f32],
        );
}

pub(crate) fn draw_primitive_3d(r: &mut RendererImpl) {
        push_raw_primitive(r, true);
}

pub(crate) fn get_primitive_3d(r: &RendererImpl)-> PrimitiveBuilder {
        r.primitive_3d.clone()
}

pub(crate) fn set_primitive_3d(r: &mut RendererImpl, prim: PrimitiveBuilder) {
        r.primitive_3d = prim;
}

pub(crate) fn extend_buffers(r: &RendererImpl, buf: &mut VertexBuffer) {
        // Igual que OpenGL: el builder ya triangula strips y abanicos, así que aquí solo hay que
        // separar por topología y recortar lo que no complete una primitiva.
        let verts = r.primitive_3d.get_vertices();
        match r.primitive_3d.get_shape() {
            PrimitiveShape::Point => buf.points.extend_from_slice(verts),
            PrimitiveShape::Line => buf.lines.extend_from_slice(&verts[..verts.len() / 2 * 2]),
            PrimitiveShape::Triangle => buf.tris.extend_from_slice(&verts[..verts.len() / 3 * 3]),
        }
}

pub(crate) fn draw_buffers(r: &mut RendererImpl, atlas_ref: Option<AtlasRef>, buf: &VertexBuffer) {
        // Un modelo cacheado de GM8.1: se vuelca a la lista con el atlas pedido (o el píxel blanco
        // cuando el modelo no lleva textura).
        r.flush_now();
        let atlas_id = r.resolve_atlas_id(atlas_ref);
        let (points, lines, tris) = buf.slices();
        r.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::PointList, points);
        r.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::LineList, lines);
        r.frame.draw_list.push(atlas_id, wgpu::PrimitiveTopology::TriangleList, tris);
}

#[cfg(test)]
mod tests {
    use super::*;

    fn white() -> AtlasRect {
        AtlasRect { atlas_id: 1, x: 0, y: 0, w: 1, h: 1, origin_x: 0.0, origin_y: 0.0 }
    }

    #[test]
    fn split_colour_unpacks_bgr_and_clamps_alpha() {
        // 0xBBGGRR, igual que el empaquetado de GM8.
        assert_eq!(split_colour(0x3322FF, 0.5), [1.0, 0x22 as f32 / 255.0, 0x33 as f32 / 255.0, 0.5]);
        assert_eq!(split_colour(0xFFFFFF, 2.0)[3], 1.0);
        assert_eq!(split_colour(0xFFFFFF, -1.0)[3], 0.0);
    }

    #[test]
    fn filled_rect_triangulates_like_opengl() {
        // A=(10,20) B=(30,20) C=(30,40) D=(10,40). El builder de TriFan de `render.rs`
        // (compartido con OpenGL) emite el abanico BCA y CDA alrededor de A.
        let mut shape = ShapeBuilder::new(false, white(), 1.0, 0.0);
        shape.push_point(10.0, 20.0, 0xFFFFFF);
        shape.push_point(30.0, 20.0, 0xFFFFFF);
        shape.push_point(30.0, 40.0, 0xFFFFFF);
        shape.push_point(10.0, 40.0, 0xFFFFFF);
        let verts = shape.build().get_vertices();

        let expected = [[30.0, 20.0], [30.0, 40.0], [10.0, 20.0], [30.0, 40.0], [10.0, 40.0], [10.0, 20.0]];
        assert_eq!(verts.len(), expected.len());
        for (vertex, [x, y]) in verts.iter().zip(expected) {
            assert_eq!([vertex.pos[0], vertex.pos[1]], [x, y]);
            assert_eq!(vertex.tex_coord, [-1.0; 2], "un relleno sin textura muestrea el píxel blanco");
        }
    }

    #[test]
    fn outlined_rect_closes_the_strip_like_opengl() {
        let mut shape = ShapeBuilder::new(true, white(), 1.0, 0.0);
        shape.push_point(10.0, 20.0, 0xFFFFFF);
        shape.push_point(30.0, 20.0, 0xFFFFFF);
        shape.push_point(30.0, 40.0, 0xFFFFFF);
        shape.push_point(10.0, 40.0, 0xFFFFFF);
        let verts = shape.build().get_vertices();

        // Cuatro segmentos (8 vértices) y el cierre vuelve al primero.
        assert_eq!(verts.len(), 8);
        assert_eq!(verts[6].pos[..2], [10.0, 40.0]);
        assert_eq!(verts[7].pos[..2], [10.0, 20.0]);
    }

    #[test]
    fn ellipse_vertex_counts_follow_circle_precision() {
        // El mismo bucle que OpenGL: una precisión de 24 da 25 puntos de borde (0..=24).
        let precision = 24;
        let mut filled = ShapeBuilder::new(false, white(), 1.0, 0.0);
        filled.push_point(0.0, 0.0, 0xFFFFFF); // centro
        let mut outline = ShapeBuilder::new(true, white(), 1.0, 0.0);
        for i in 0..=precision {
            let angle = f64::from(i) * 2.0 * PI / f64::from(precision);
            let (x, y) = (10.0 * angle.cos(), 5.0 * angle.sin());
            filled.push_point(x, y, 0xFFFFFF);
            outline.push_point(x, y, 0xFFFFFF);
        }
        // TriFan con 26 puntos: 3 vértices por triángulo, 24 triángulos.
        assert_eq!(filled.build().get_vertices().len(), 24 * 3);
        // LineStrip: 25 puntos dan 48 vértices más el cierre del contorno (+2).
        assert_eq!(outline.build().get_vertices().len(), 50);
    }
}
