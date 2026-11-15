//! Backend de renderizado basado en wgpu.
//!
//! wgpu selecciona la API gráfica en tiempo de ejecución: Vulkan en Linux/Windows/Android,
//! y GLES como alternativa cuando Vulkan no está disponible. Eso permite un único
//! `RendererTrait` para las tres plataformas en lugar de tres backends separados.
//!
//! Licencia: wgpu es `MIT OR Apache-2.0`, compatible con la GPL-2.0-only del motor.

mod atlas;
mod pipeline;

use crate::render::{
    atlas::{AtlasBuilder as AtlasBuilderAny, AtlasRect, AtlasRef},
    BlendType, CrtSettings, Fog, GbSettings, Light, PrimitiveBuilder, PrimitiveType,
    RendererOptions, RendererTrait, SavedTexture, Scaling, VertexBuffer,
};
use crate::types::Colour;
use ramen::{connection::Connection, window::Window};
use std::any::Any;

pub struct RendererImpl {
    device: wgpu::Device,
    queue: wgpu::Queue,
    surface: wgpu::Surface<'static>,
    config: wgpu::SurfaceConfiguration,
    pipeline: pipeline::SpritePipeline,
    textures: atlas::TextureStore,
    clear_colour: Colour,
    vsync: bool,
    max_texture_size: u32,
}

impl RendererImpl {
    pub fn new(
        options: &RendererOptions,
        connection: &Connection,
        window: &Window,
        clear_colour: Colour,
    ) -> Result<Self, String> {
        let _ = (connection, window);

        // TODO(wgpu): crear la instancia con las extensiones de superficie adecuadas:
        //   let instance = wgpu::Instance::new(&wgpu::InstanceDescriptor {
        //       backends: wgpu::Backends::VULKAN | wgpu::Backends::GL,
        //       ..Default::default()
        //   });
        // Después: `instance.create_surface(window)` aprovechando que `ramen` implementa
        // `HasWindowHandle`/`HasDisplayHandle` con la feature `rwh-06`.
        let _ = options;
        Err("El backend wgpu todavía no está implementado.".into())
    }
}

impl RendererTrait for RendererImpl {
    fn as_any(&self) -> &dyn Any {
        todo!("wgpu: as_any")
    }

    fn max_texture_size(&self) -> u32 {
        self.max_texture_size
    }

    fn push_atlases(&mut self, atl: AtlasBuilderAny) -> Result<(), String> {
        todo!("wgpu: push_atlases")
    }

    fn upload_sprite(
        &mut self,
        data: Box<[u8]>,
        width: i32,
        height: i32,
        origin_x: i32,
        origin_y: i32,
    ) -> Result<AtlasRef, String> {
        todo!("wgpu: upload_sprite")
    }

    fn duplicate_sprite(&mut self, atlas_ref: AtlasRef) -> Result<AtlasRef, String> {
        todo!("wgpu: duplicate_sprite")
    }

    fn delete_sprite(&mut self, atlas_ref: AtlasRef) {
        todo!("wgpu: delete_sprite")
    }

    fn resize_framebuffer(&mut self, width: u32, height: u32, store: bool) {
        todo!("wgpu: resize_framebuffer")
    }

    fn set_vsync(&self, vsync: bool) {
        todo!("wgpu: set_vsync")
    }

    fn get_vsync(&self) -> bool {
        self.vsync
    }

    fn wait_vsync(&self) {
        // wgpu sincroniza en `present`; nada que esperar aquí.
    }

    fn set_draw_offset(&mut self, x: f64, y: f64) {
        todo!("wgpu: set_draw_offset")
    }

    fn set_interp_amount(&mut self, amount: f32) {
        todo!("wgpu: set_interp_amount")
    }

    fn get_rect(&self, id: AtlasRef) -> Option<&AtlasRect> {
        todo!("wgpu: get_rect")
    }

    fn set_sprite_origin(&mut self, id: AtlasRef, origin_x: f32, origin_y: f32) {
        todo!("wgpu: set_sprite_origin")
    }

    fn draw_sprite_general(
        &mut self,
        texture: AtlasRef,
        part_x: f64,
        part_y: f64,
        part_w: f64,
        part_h: f64,
        x: f64,
        y: f64,
        xscale: f64,
        yscale: f64,
        angle: f64,
        col1: i32,
        col2: i32,
        col3: i32,
        col4: i32,
        alpha: f64,
        use_origin: bool,
    ) {
        todo!("wgpu: draw_sprite_general")
    }

    fn draw_sprite_pos(
        &mut self,
        texture: AtlasRef,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        x3: f64,
        y3: f64,
        x4: f64,
        y4: f64,
        alpha: f64
    ) {
        todo!("wgpu: draw_sprite_pos")
    }

    fn set_view_matrix(&mut self, view: [f32; 16]) {
        todo!("wgpu: set_view_matrix")
    }

    fn set_viewproj_matrix(&mut self, view: [f32; 16], proj: [f32; 16]) {
        todo!("wgpu: set_viewproj_matrix")
    }

    fn get_model_matrix(&self) -> [f32; 16] {
        todo!("wgpu: get_model_matrix")
    }

    fn set_model_matrix(&mut self, model: [f32; 16]) {
        todo!("wgpu: set_model_matrix")
    }

    fn mult_model_matrix(&mut self, model: [f32; 16]) {
        todo!("wgpu: mult_model_matrix")
    }

    fn set_projection_ortho(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        todo!("wgpu: set_projection_ortho")
    }

    fn set_projection_perspective(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) {
        todo!("wgpu: set_projection_perspective")
    }

    fn set_view(
        &mut self,
        src_x: i32,
        src_y: i32,
        src_w: i32,
        src_h: i32,
        src_angle: f64,
        port_x: i32,
        port_y: i32,
        port_w: i32,
        port_h: i32,
    ) {
        todo!("wgpu: set_view")
    }

    fn flush_queue(&mut self) {
        todo!("wgpu: flush_queue")
    }

    fn set_crt(&mut self, _settings: Option<CrtSettings>) {}
    fn set_gb(&mut self, _settings: Option<GbSettings>) {}
    fn set_gb_inverted(&mut self, _inverted: bool) {}
    fn present(&mut self, window_width: u32, window_height: u32, scaling: Scaling) {
        todo!("wgpu: set_crt")
    }

    fn present_surface(&mut self, surface: AtlasRef, window_width: u32, window_height: u32) {
        todo!("wgpu: present_surface")
    }

    fn draw_stored(&mut self, target_x: i32, target_y: i32, width: u32, height: u32) {
        todo!("wgpu: draw_stored")
    }

    fn stored_size(&self) -> (u32, u32) {
        todo!("wgpu: stored_size")
    }

    fn finish(&mut self, window_width: u32, window_height: u32, clear_colour: Colour) {
        todo!("wgpu: finish")
    }

    fn dump_sprite_part(&self, texture: AtlasRef, part_x: i32, part_y: i32, part_w: i32, part_h: i32) -> Box<[u8]> {
        todo!("wgpu: dump_sprite_part")
    }

    fn get_alpha_blending(&self) -> bool {
        todo!("wgpu: get_alpha_blending")
    }

    fn set_alpha_blending(&mut self, alphablend: bool) {
        todo!("wgpu: set_alpha_blending")
    }

    fn get_colour_blending(&self) -> bool {
        todo!("wgpu: get_colour_blending")
    }

    fn set_colour_blending(&mut self, modulate: bool) {
        todo!("wgpu: set_colour_blending")
    }

    fn get_blend_mode(&self) -> (BlendType, BlendType) {
        todo!("wgpu: get_blend_mode")
    }

    fn set_blend_mode(&mut self, src: BlendType, dst: BlendType) {
        todo!("wgpu: set_blend_mode")
    }

    fn get_pixel_interpolation(&self) -> bool {
        todo!("wgpu: get_pixel_interpolation")
    }

    fn set_pixel_interpolation(&mut self, lerping: bool) {
        todo!("wgpu: set_pixel_interpolation")
    }

    fn get_texture_repeat(&self) -> bool {
        todo!("wgpu: get_texture_repeat")
    }

    fn set_texture_repeat(&mut self, repeat: bool) {
        todo!("wgpu: set_texture_repeat")
    }

    fn get_pixels(&self, x: i32, y: i32, w: i32, h: i32) -> Box<[u8]> {
        todo!("wgpu: get_pixels")
    }

    fn stored_pixels(&self) -> Box<[u8]> {
        todo!("wgpu: stored_pixels")
    }

    fn stored_zbuffer(&self) -> Box<[f32]> {
        todo!("wgpu: stored_zbuffer")
    }

    fn set_stored(&mut self, rgba: Box<[u8]>, zbuf: Box<[f32]>, fb_w: u32, fb_h: u32) {
        todo!("wgpu: set_stored")
    }

    fn dump_dynamic_textures(&self) -> Vec<Option<SavedTexture>> {
        todo!("wgpu: dump_dynamic_textures")
    }

    fn upload_dynamic_textures(&mut self, textures: &[Option<SavedTexture>]) {
        todo!("wgpu: upload_dynamic_textures")
    }

    fn create_sprite_colour(&mut self, width: i32, height: i32, col: Colour) -> Result<AtlasRef, String> {
        todo!("wgpu: create_sprite_colour")
    }

    fn create_surface(&mut self, w: i32, h: i32, has_zbuffer: bool) -> Result<AtlasRef, String> {
        todo!("wgpu: create_surface")
    }

    fn set_target(&mut self, atlas_ref: AtlasRef) {
        todo!("wgpu: set_target")
    }

    fn reset_target(&mut self) {
        todo!("wgpu: reset_target")
    }

    fn copy_surface(
        &mut self,
        dest: AtlasRef,
        dest_x: i32,
        dest_y: i32,
        src: AtlasRef,
        src_x: i32,
        src_y: i32,
        width: i32,
        height: i32,
    ) {
        todo!("wgpu: copy_surface")
    }

    fn set_zbuf_trashed(&mut self, trashed: bool) {
        todo!("wgpu: set_zbuf_trashed")
    }

    fn get_zbuf_trashed(&self) -> bool {
        todo!("wgpu: get_zbuf_trashed")
    }

    fn get_texture_id(&mut self, atl_ref: AtlasRef) -> i32 {
        todo!("wgpu: get_texture_id")
    }

    fn get_texture_from_id(&self, id: i32) -> Option<AtlasRef> {
        todo!("wgpu: get_texture_from_id")
    }

    fn get_texture_rects(&self) -> Vec<Option<AtlasRect>> {
        todo!("wgpu: get_texture_rects")
    }

    fn set_texture_rects(&mut self, rects: &[Option<AtlasRect>]) {
        todo!("wgpu: set_texture_rects")
    }

    fn draw_rectangle(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_rectangle")
    }

    fn draw_rectangle_outline(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_rectangle_outline")
    }

    fn draw_rectangle_gradient(
        &mut self,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        c1: i32,
        c2: i32,
        c3: i32,
        c4: i32,
        alpha: f64,
        outline: bool,
    ) {
        todo!("wgpu: draw_rectangle_gradient")
    }

    fn draw_point(&mut self, x: f64, y: f64, colour: i32, alpha: f64) {
        todo!("wgpu: draw_point")
    }

    fn draw_line(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, width: Option<f64>, c1: i32, c2: i32, alpha: f64) {
        todo!("wgpu: draw_line")
    }

    fn draw_triangle(
        &mut self,
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        x3: f64,
        y3: f64,
        c1: i32,
        c2: i32,
        c3: i32,
        alpha: f64,
        outline: bool,
    ) {
        todo!("wgpu: draw_triangle")
    }

    fn draw_ellipse(&mut self, x: f64, y: f64, rad_x: f64, rad_y: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        todo!("wgpu: draw_ellipse")
    }

    fn draw_roundrect(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, c1: i32, c2: i32, alpha: f64, outline: bool) {
        todo!("wgpu: draw_roundrect")
    }

    fn set_circle_precision(&mut self, prec: i32) {
        todo!("wgpu: set_circle_precision")
    }

    fn get_circle_precision(&self) -> i32 {
        todo!("wgpu: get_circle_precision")
    }

    fn reset_primitive_2d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        todo!("wgpu: reset_primitive_2d")
    }

    fn vertex_2d(&mut self, x: f64, y: f64, xtex: f64, ytex: f64, col: i32, alpha: f64) {
        todo!("wgpu: vertex_2d")
    }

    fn draw_primitive_2d(&mut self) {
        todo!("wgpu: draw_primitive_2d")
    }

    fn get_primitive_2d(&self) -> PrimitiveBuilder {
        todo!("wgpu: get_primitive_2d")
    }

    fn set_primitive_2d(&mut self, prim: PrimitiveBuilder) {
        todo!("wgpu: set_primitive_2d")
    }

    fn reset_primitive_3d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) {
        todo!("wgpu: reset_primitive_3d")
    }

    fn vertex_3d(
        &mut self,
        x: f64,
        y: f64,
        z: f64,
        nx: f64,
        ny: f64,
        nz: f64,
        xtex: f64,
        ytex: f64,
        col: i32,
        alpha: f64,
    ) {
        todo!("wgpu: vertex_3d")
    }

    fn draw_primitive_3d(&mut self) {
        todo!("wgpu: draw_primitive_3d")
    }

    fn get_primitive_3d(&self) -> PrimitiveBuilder {
        todo!("wgpu: get_primitive_3d")
    }

    fn set_primitive_3d(&mut self, prim: PrimitiveBuilder) {
        todo!("wgpu: set_primitive_3d")
    }

    fn extend_buffers(&self, buf: &mut VertexBuffer) {
        todo!("wgpu: extend_buffers")
    }

    fn draw_buffers(&mut self, atlas_ref: Option<AtlasRef>, buf: &VertexBuffer) {
        todo!("wgpu: draw_buffers")
    }

    fn clear_view(&mut self, colour: Colour, alpha: f64) {
        todo!("wgpu: clear_view")
    }

    fn clear_view_no_zbuf(&mut self, colour: Colour, alpha: f64) {
        todo!("wgpu: clear_view_no_zbuf")
    }

    fn clear_zbuf(&mut self) {
        todo!("wgpu: clear_zbuf")
    }

    fn get_3d(&self) -> bool {
        todo!("wgpu: get_3d")
    }

    fn set_3d(&mut self, use_3d: bool) {
        todo!("wgpu: set_3d")
    }

    fn get_depth(&self) -> f32 {
        todo!("wgpu: get_depth")
    }

    fn set_depth(&mut self, depth: f32) {
        todo!("wgpu: set_depth")
    }

    fn get_depth_test(&self) -> bool {
        todo!("wgpu: get_depth_test")
    }

    fn set_depth_test(&mut self, depth_test: bool) {
        todo!("wgpu: set_depth_test")
    }

    fn get_write_depth(&self) -> bool {
        todo!("wgpu: get_write_depth")
    }

    fn set_write_depth(&mut self, write_depth: bool) {
        todo!("wgpu: set_write_depth")
    }

    fn get_culling(&self) -> bool {
        todo!("wgpu: get_culling")
    }

    fn set_culling(&mut self, culling: bool) {
        todo!("wgpu: set_culling")
    }

    fn get_perspective(&self) -> bool {
        todo!("wgpu: get_perspective")
    }

    fn set_perspective(&mut self, perspective: bool) {
        todo!("wgpu: set_perspective")
    }

    fn get_fog(&self) -> Option<Fog> {
        todo!("wgpu: get_fog")
    }

    fn set_fog(&mut self, fog: Option<Fog>) {
        todo!("wgpu: set_fog")
    }

    fn get_gouraud(&self) -> bool {
        todo!("wgpu: get_gouraud")
    }

    fn set_gouraud(&mut self, gouraud: bool) {
        todo!("wgpu: set_gouraud")
    }

    fn get_lighting_enabled(&self) -> bool {
        todo!("wgpu: get_lighting_enabled")
    }

    fn set_lighting_enabled(&mut self, enabled: bool) {
        todo!("wgpu: set_lighting_enabled")
    }

    fn get_ambient_colour(&self) -> i32 {
        todo!("wgpu: get_ambient_colour")
    }

    fn set_ambient_colour(&mut self, colour: i32) {
        todo!("wgpu: set_ambient_colour")
    }

    fn get_lights(&self) -> [(bool, Light); 8] {
        todo!("wgpu: get_lights")
    }

    fn set_lights(&mut self, lights: [(bool, Light); 8]) {
        todo!("wgpu: set_lights")
    }

    fn set_light_enabled(&mut self, id: usize, enabled: bool) {
        todo!("wgpu: set_light_enabled")
    }

    fn set_light(&mut self, id: usize, light: Light) {
        todo!("wgpu: set_light")
    }

}