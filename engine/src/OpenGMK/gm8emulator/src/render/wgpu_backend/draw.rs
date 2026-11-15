//! Lista de dibujo por frame y subida de vértices a la GPU.
//!
//! El motor entrega los vértices agrupados por forma (puntos, líneas, triángulos) en un
//! `VertexBuffer`, pero wgpu necesita un draw call por cada combinación de atlas y topología. Esta
//! lista acumula los vértices de todas las llamadas de un frame y los fusiona cuando comparten
//! atlas y topología, de modo que dibujar muchos sprites seguidos del mismo atlas sigue siendo una
//! sola llamada.

use crate::render::{display_rect, Scaling, Vertex};

/// Un draw call pendiente: los vértices ya concatenados y el estado con el que se dibujan.
pub(crate) struct FrameBatch {
    pub vertices: Vec<Vertex>,
    pub atlas_id: u32,
    pub topology: wgpu::PrimitiveTopology,
}

/// Acumulador de batches para un frame.
#[derive(Default)]
pub(crate) struct DrawList {
    batches: Vec<FrameBatch>,
}

impl DrawList {
    pub fn new() -> Self {
        Self::default()
    }

    /// Añade vértices al final de la lista, fusionándolos con el último batch cuando el atlas y la
    /// topología coinciden. Un lote vacío no añade nada: no hay nada que dibujar ni por lo que
    /// romper la fusión.
    pub fn push(&mut self, atlas_id: u32, topology: wgpu::PrimitiveTopology, verts: &[Vertex]) {
        if verts.is_empty() {
            return
        }
        if let Some(last) = self.batches.last_mut() {
            if last.atlas_id == atlas_id && last.topology == topology {
                last.vertices.extend_from_slice(verts);
                return
            }
        }
        self.batches.push(FrameBatch { vertices: verts.to_vec(), atlas_id, topology });
    }

    /// Vacía la lista conservando la memoria reservada para el frame siguiente.
    pub fn clear(&mut self) {
        self.batches.clear();
    }

    pub fn batches(&self) -> &[FrameBatch] {
        &self.batches
    }

    pub fn len(&self) -> usize {
        self.batches.len()
    }

    pub fn is_empty(&self) -> bool {
        self.batches.is_empty()
    }
}

/// Espejo de `crate::render::Vertex` con `Pod`, que es lo que `bytemuck` necesita para ver los
/// vértices como bytes. `Vertex` no puede implementar `Pod` desde este crate, así que se copia
/// campo a campo; las aserciones de abajo atan el espejo al layout original.
#[repr(C)]
#[derive(Clone, Copy, Debug, bytemuck::Pod, bytemuck::Zeroable)]
struct VertexPod {
    pos: [f32; 3],
    interp: [f32; 2],
    tex_coord: [f32; 2],
    blend: [f32; 4],
    atlas_xywh: [f32; 4],
    normal: [f32; 3],
}

const _: () = {
    assert!(std::mem::size_of::<VertexPod>() == std::mem::size_of::<Vertex>());
    assert!(std::mem::align_of::<VertexPod>() == std::mem::align_of::<Vertex>());
    assert!(std::mem::offset_of!(VertexPod, pos) == Vertex::POS_OFFSET);
    assert!(std::mem::offset_of!(VertexPod, interp) == Vertex::INTERP_OFFSET);
    assert!(std::mem::offset_of!(VertexPod, tex_coord) == Vertex::TEX_OFFSET);
    assert!(std::mem::offset_of!(VertexPod, blend) == Vertex::BLEND_OFFSET);
    assert!(std::mem::offset_of!(VertexPod, atlas_xywh) == Vertex::ATLAS_OFFSET);
    assert!(std::mem::offset_of!(VertexPod, normal) == Vertex::NORMAL_OFFSET);
};

impl From<Vertex> for VertexPod {
    fn from(v: Vertex) -> Self {
        Self {
            pos: v.pos,
            interp: v.interp,
            tex_coord: v.tex_coord,
            blend: v.blend,
            atlas_xywh: v.atlas_xywh,
            normal: v.normal,
        }
    }
}

/// Crea un buffer de vértices en la GPU con `vertices` ya subidos.
///
/// Devuelve el buffer y el número de vértices que contiene. Con una lista vacía se sube un vértice
/// a cero y se devuelve 0: algunos backends rechazan enlazar buffers de tamaño cero, y el llamante
/// ya no dibuja nada cuando el conteo es cero.
pub(crate) fn build_vertex_buffer(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    vertices: &[Vertex],
) -> (wgpu::Buffer, u32) {
    let gpu: Vec<VertexPod> = vertices.iter().copied().map(VertexPod::from).collect();
    let placeholder = [VertexPod {
        pos: [0.0; 3],
        interp: [0.0; 2],
        tex_coord: [0.0; 2],
        blend: [0.0; 4],
        atlas_xywh: [0.0; 4],
        normal: [0.0; 3],
    }];
    let contents: &[VertexPod] = if gpu.is_empty() { &placeholder } else { &gpu };
    let bytes = bytemuck::cast_slice(contents);
    let buffer = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("gm8emulator vertex buffer"),
        size: bytes.len() as wgpu::BufferAddress,
        usage: wgpu::BufferUsages::VERTEX | wgpu::BufferUsages::COPY_DST,
        mapped_at_creation: false,
    });
    queue.write_buffer(&buffer, 0, bytes);
    (buffer, u32::try_from(vertices.len()).unwrap_or(u32::MAX))
}

/// Rectángulo de destino del juego normalizado a la ventana: `(x, y, w, h)` en el rango 0..=1, con
/// el origen arriba a la izquierda (el mismo que usa wgpu al fijar el viewport).
///
/// La decisión de dónde cae la imagen la toma `crate::render::display_rect`, que es la misma que
/// usan el mapeo de entrada y los controles táctiles; aquí solo se convierte a fracciones para que
/// el llamante lo escale al tamaño real del destino.
pub(crate) fn present_rect(
    window_w: u32,
    window_h: u32,
    fb_w: u32,
    fb_h: u32,
    scaling: Scaling,
) -> (f32, f32, f32, f32) {
    if window_w == 0 || window_h == 0 {
        return (0.0, 0.0, 0.0, 0.0)
    }
    let (x, y, w, h) = display_rect(window_w, window_h, fb_w, fb_h, scaling);
    let window_w = window_w as f32;
    let window_h = window_h as f32;
    (x as f32 / window_w, y as f32 / window_h, w as f32 / window_w, h as f32 / window_h)
}

#[cfg(all(test, feature = "backend-wgpu"))]
mod draw_list_tests {
    use super::*;
    use wgpu::PrimitiveTopology;

    fn vertex(x: f32) -> Vertex {
        Vertex {
            pos: [x, 0.0, 0.0],
            interp: [0.0, 0.0],
            tex_coord: [0.0, 0.0],
            blend: [1.0, 1.0, 1.0, 1.0],
            atlas_xywh: [0.0, 0.0, 1.0, 1.0],
            normal: [0.0, 0.0, 1.0],
        }
    }

    #[test]
    fn push_merges_same_atlas_and_topology() {
        let mut list = DrawList::new();
        list.push(7, PrimitiveTopology::TriangleList, &[vertex(0.0), vertex(1.0), vertex(2.0)]);
        list.push(7, PrimitiveTopology::TriangleList, &[vertex(3.0), vertex(4.0), vertex(5.0)]);

        assert_eq!(list.len(), 1);
        let batch = &list.batches()[0];
        assert_eq!(batch.atlas_id, 7);
        assert_eq!(batch.topology, PrimitiveTopology::TriangleList);
        assert_eq!(batch.vertices.len(), 6);
        assert_eq!(batch.vertices[3].pos[0], 3.0);
    }

    #[test]
    fn push_separates_on_atlas_change() {
        let mut list = DrawList::new();
        list.push(1, PrimitiveTopology::TriangleList, &[vertex(0.0)]);
        list.push(2, PrimitiveTopology::TriangleList, &[vertex(1.0)]);
        list.push(1, PrimitiveTopology::TriangleList, &[vertex(2.0)]);

        assert_eq!(list.len(), 3);
        assert_eq!(list.batches()[0].vertices.len(), 1);
        assert_eq!(list.batches()[1].atlas_id, 2);
        assert_eq!(list.batches()[2].atlas_id, 1);
    }

    #[test]
    fn push_separates_on_topology_change() {
        let mut list = DrawList::new();
        list.push(5, PrimitiveTopology::PointList, &[vertex(0.0)]);
        list.push(5, PrimitiveTopology::LineList, &[vertex(1.0), vertex(2.0)]);
        list.push(5, PrimitiveTopology::TriangleList, &[vertex(3.0)]);

        assert_eq!(list.len(), 3);
        assert_eq!(list.batches()[1].topology, PrimitiveTopology::LineList);
    }

    #[test]
    fn push_ignores_empty_slices() {
        let mut list = DrawList::new();
        list.push(1, PrimitiveTopology::TriangleList, &[]);
        assert!(list.is_empty());

        list.push(1, PrimitiveTopology::TriangleList, &[vertex(0.0)]);
        list.push(2, PrimitiveTopology::TriangleList, &[]);
        list.push(1, PrimitiveTopology::TriangleList, &[vertex(1.0)]);

        // The empty push must not break the merge of the two non-empty ones.
        assert_eq!(list.len(), 1);
        assert_eq!(list.batches()[0].vertices.len(), 2);
    }

    #[test]
    fn clear_empties_the_list() {
        let mut list = DrawList::new();
        list.push(1, PrimitiveTopology::TriangleList, &[vertex(0.0)]);
        list.clear();
        assert!(list.is_empty());
        assert_eq!(list.batches().len(), 0);
    }
}

#[cfg(test)]
mod present_rect_tests {
    use super::*;

    fn approx(actual: f32, expected: f32) {
        assert!((actual - expected).abs() < 1e-5, "expected {expected}, got {actual}");
    }

    #[test]
    fn fixed_scaling_centres_the_image() {
        let (x, y, w, h) = present_rect(800, 600, 320, 240, Scaling::Fixed(2.0));
        approx(x, 0.1);
        approx(y, 0.1);
        approx(w, 0.8);
        approx(h, 0.8);
    }

    #[test]
    fn aspect_wide_window_leaves_side_bands() {
        let (x, y, w, h) = present_rect(1000, 500, 320, 240, Scaling::Aspect(-1.0));
        // The 4:3 image cannot fill a 2:1 window, so it is pillarboxed.
        approx(y, 0.0);
        approx(h, 1.0);
        assert!(x > 0.0, "x = {x}");
        approx(x, 167.0 / 1000.0);
        approx(w, 666.0 / 1000.0);
    }

    #[test]
    fn aspect_tall_window_leaves_top_and_bottom_bands() {
        let (x, y, w, h) = present_rect(500, 1000, 320, 240, Scaling::Aspect(-1.0));
        approx(x, 0.0);
        approx(w, 1.0);
        assert!(y > 0.0, "y = {y}");
        approx(y, 312.0 / 1000.0);
        approx(h, 375.0 / 1000.0);
    }

    #[test]
    fn full_scaling_fills_the_window() {
        let (x, y, w, h) = present_rect(800, 600, 320, 240, Scaling::Full);
        approx(x, 0.0);
        approx(y, 0.0);
        approx(w, 1.0);
        approx(h, 1.0);
    }

    #[test]
    fn zero_window_returns_empty_rect() {
        assert_eq!(present_rect(0, 600, 320, 240, Scaling::Full), (0.0, 0.0, 0.0, 0.0));
        assert_eq!(present_rect(800, 0, 320, 240, Scaling::Aspect(-1.0)), (0.0, 0.0, 0.0, 0.0));
    }
}
