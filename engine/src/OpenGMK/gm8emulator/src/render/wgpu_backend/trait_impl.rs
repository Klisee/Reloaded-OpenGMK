// Implementación de `RendererTrait` para el backend wgpu.
impl RendererTrait for RendererImpl {
    fn as_any(&self) -> &dyn Any { trait_state::as_any(self) }
    fn max_texture_size(&self) -> u32 { trait_state::max_texture_size(self) }
    fn debug_info(&self) -> crate::render::DebugInfo { trait_state::debug_info(self) }
    fn owns_surface(&self) -> bool { true }
    fn push_atlases(&mut self, atl: AtlasBuilderAny) -> Result<(), String> { trait_textures::push_atlases(self, atl) }
    fn upload_sprite( &mut self, data: Box<[u8]>, width: i32, height: i32, origin_x: i32, origin_y: i32, ) -> Result<AtlasRef, String> { trait_textures::upload_sprite(self, data, width, height, origin_x, origin_y) }
    fn duplicate_sprite(&mut self, atlas_ref: AtlasRef) -> Result<AtlasRef, String> { trait_textures::duplicate_sprite(self, atlas_ref) }
    fn delete_sprite(&mut self, atlas_ref: AtlasRef) { trait_textures::delete_sprite(self, atlas_ref) }
    fn resize_framebuffer(&mut self, width: u32, height: u32, store: bool) { trait_surfaces::resize_framebuffer(self, width, height, store) }
    fn set_vsync(&self, vsync: bool) { trait_state::set_vsync(self, vsync) }
    fn get_vsync(&self) -> bool { trait_state::get_vsync(self) }
    fn wait_vsync(&self) { trait_state::wait_vsync(self) }
    fn set_ui_quads(&mut self, quads: Vec<crate::render::UiQuad>) { trait_surfaces::set_ui_quads(self, quads) }
    fn set_draw_offset(&mut self, x: f64, y: f64) { trait_state::set_draw_offset(self, x, y) }
    fn set_interp_amount(&mut self, amount: f32) { trait_state::set_interp_amount(self, amount) }
    fn get_rect(&self, id: AtlasRef) -> Option<&AtlasRect> { trait_textures::get_rect(self, id) }
    fn atlas_size(&self, atlas_id: u32) -> Option<(u32, u32)> { self.textures.size(atlas_id) }
    fn set_sprite_origin(&mut self, id: AtlasRef, origin_x: f32, origin_y: f32) { trait_textures::set_sprite_origin(self, id, origin_x, origin_y) }
    fn draw_sprite_general( &mut self, texture: AtlasRef, part_x: f64, part_y: f64, part_w: f64, part_h: f64, x: f64, y: f64, xscale: f64, yscale: f64, angle: f64, col1: i32, col2: i32, col3: i32, col4: i32, alpha: f64, use_origin: bool, ) { trait_geometry::draw_sprite_general(self, texture, part_x, part_y, part_w, part_h, x, y, xscale, yscale, angle, col1, col2, col3, col4, alpha, use_origin) }
    fn draw_sprite_pos( &mut self, texture: AtlasRef, x1: f64, y1: f64, x2: f64, y2: f64, x3: f64, y3: f64, x4: f64, y4: f64, alpha: f64 ) { trait_geometry::draw_sprite_pos(self, texture, x1, y1, x2, y2, x3, y3, x4, y4, alpha) }
    fn set_view_matrix(&mut self, view: [f32; 16]) { trait_state::set_view_matrix(self, view) }
    fn set_viewproj_matrix(&mut self, view: [f32; 16], proj: [f32; 16]) { trait_state::set_viewproj_matrix(self, view, proj) }
    fn get_model_matrix(&self) -> [f32; 16] { trait_state::get_model_matrix(self) }
    fn set_model_matrix(&mut self, model: [f32; 16]) { trait_state::set_model_matrix(self, model) }
    fn mult_model_matrix(&mut self, model: [f32; 16]) { trait_state::mult_model_matrix(self, model) }
    fn set_projection_ortho(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) { trait_state::set_projection_ortho(self, x, y, w, h, angle) }
    fn set_projection_perspective(&mut self, x: f64, y: f64, w: f64, h: f64, angle: f64) { trait_state::set_projection_perspective(self, x, y, w, h, angle) }
    fn set_view( &mut self, src_x: i32, src_y: i32, src_w: i32, src_h: i32, src_angle: f64, port_x: i32, port_y: i32, port_w: i32, port_h: i32, ) { trait_state::set_view(self, src_x, src_y, src_w, src_h, src_angle, port_x, port_y, port_w, port_h) }
    fn flush_queue(&mut self) { trait_surfaces::flush_queue(self) }
    fn set_crt(&mut self, _settings: Option<CrtSettings>) { trait_state::set_crt(self, _settings) }
    fn set_gb(&mut self, _settings: Option<GbSettings>) { trait_state::set_gb(self, _settings) }
    fn set_gb_inverted(&mut self, _inverted: bool) { trait_state::set_gb_inverted(self, _inverted) }
    fn present(&mut self, window_width: u32, window_height: u32, scaling: Scaling) { trait_surfaces::present(self, window_width, window_height, scaling) }
    fn present_surface(&mut self, surface: AtlasRef, window_width: u32, window_height: u32) { trait_surfaces::present_surface(self, surface, window_width, window_height) }
    fn draw_stored(&mut self, target_x: i32, target_y: i32, width: u32, height: u32) { trait_surfaces::draw_stored(self, target_x, target_y, width, height) }
    fn stored_size(&self) -> (u32, u32) { trait_surfaces::stored_size(self) }
    fn finish(&mut self, window_width: u32, window_height: u32, clear_colour: Colour) { trait_surfaces::finish(self, window_width, window_height, clear_colour) }
    fn dump_sprite_part(&self, texture: AtlasRef, part_x: i32, part_y: i32, part_w: i32, part_h: i32) -> Box<[u8]> { trait_surfaces::dump_sprite_part(self, texture, part_x, part_y, part_w, part_h) }
    fn get_alpha_blending(&self) -> bool { trait_state::get_alpha_blending(self) }
    fn set_alpha_blending(&mut self, alphablend: bool) { trait_state::set_alpha_blending(self, alphablend) }
    fn get_colour_blending(&self) -> bool { trait_state::get_colour_blending(self) }
    fn set_colour_blending(&mut self, modulate: bool) { trait_state::set_colour_blending(self, modulate) }
    fn get_blend_mode(&self) -> (BlendType, BlendType) { trait_state::get_blend_mode(self) }
    fn set_blend_mode(&mut self, src: BlendType, dst: BlendType) { trait_state::set_blend_mode(self, src, dst) }
    fn get_pixel_interpolation(&self) -> bool { trait_state::get_pixel_interpolation(self) }
    fn set_pixel_interpolation(&mut self, lerping: bool) { trait_state::set_pixel_interpolation(self, lerping) }
    fn get_texture_repeat(&self) -> bool { trait_state::get_texture_repeat(self) }
    fn set_texture_repeat(&mut self, repeat: bool) { trait_state::set_texture_repeat(self, repeat) }
    fn get_pixels(&self, x: i32, y: i32, w: i32, h: i32) -> Box<[u8]> { trait_surfaces::get_pixels(self, x, y, w, h) }
    fn stored_pixels(&self) -> Box<[u8]> { trait_surfaces::stored_pixels(self) }
    fn stored_zbuffer(&self) -> Box<[f32]> { trait_surfaces::stored_zbuffer(self) }
    fn set_stored(&mut self, rgba: Box<[u8]>, zbuf: Box<[f32]>, fb_w: u32, fb_h: u32) { trait_surfaces::set_stored(self, rgba, zbuf, fb_w, fb_h) }
    fn dump_dynamic_textures(&self) -> Vec<Option<SavedTexture>> { trait_textures::dump_dynamic_textures(self) }
    fn upload_dynamic_textures(&mut self, textures: &[Option<SavedTexture>]) { trait_textures::upload_dynamic_textures(self, textures) }
    fn create_sprite_colour(&mut self, width: i32, height: i32, col: Colour) -> Result<AtlasRef, String> { trait_textures::create_sprite_colour(self, width, height, col) }
    fn create_surface(&mut self, w: i32, h: i32, has_zbuffer: bool) -> Result<AtlasRef, String> { trait_surfaces::create_surface(self, w, h, has_zbuffer) }
    fn set_target(&mut self, atlas_ref: AtlasRef) { trait_surfaces::set_target(self, atlas_ref) }
    fn reset_target(&mut self) { trait_surfaces::reset_target(self) }
    fn copy_surface( &mut self, dest: AtlasRef, dest_x: i32, dest_y: i32, src: AtlasRef, src_x: i32, src_y: i32, width: i32, height: i32, ) { trait_surfaces::copy_surface(self, dest, dest_x, dest_y, src, src_x, src_y, width, height) }
    fn set_zbuf_trashed(&mut self, trashed: bool) { trait_state::set_zbuf_trashed(self, trashed) }
    fn get_zbuf_trashed(&self) -> bool { trait_state::get_zbuf_trashed(self) }
    fn get_texture_id(&mut self, atl_ref: AtlasRef) -> i32 { trait_textures::get_texture_id(self, atl_ref) }
    fn get_texture_from_id(&self, id: i32) -> Option<AtlasRef> { trait_textures::get_texture_from_id(self, id) }
    fn get_texture_rects(&self) -> Vec<Option<AtlasRect>> { trait_textures::get_texture_rects(self) }
    fn set_texture_rects(&mut self, rects: &[Option<AtlasRect>]) { trait_textures::set_texture_rects(self, rects) }
    fn draw_rectangle(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) { trait_geometry::draw_rectangle(self, x1, y1, x2, y2, colour, alpha) }
    fn draw_rectangle_outline(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, colour: i32, alpha: f64) { trait_geometry::draw_rectangle_outline(self, x1, y1, x2, y2, colour, alpha) }
    fn draw_rectangle_gradient( &mut self, x1: f64, y1: f64, x2: f64, y2: f64, c1: i32, c2: i32, c3: i32, c4: i32, alpha: f64, outline: bool, ) { trait_geometry::draw_rectangle_gradient(self, x1, y1, x2, y2, c1, c2, c3, c4, alpha, outline) }
    fn draw_point(&mut self, x: f64, y: f64, colour: i32, alpha: f64) { trait_geometry::draw_point(self, x, y, colour, alpha) }
    fn draw_line(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, width: Option<f64>, c1: i32, c2: i32, alpha: f64) { trait_geometry::draw_line(self, x1, y1, x2, y2, width, c1, c2, alpha) }
    fn draw_triangle( &mut self, x1: f64, y1: f64, x2: f64, y2: f64, x3: f64, y3: f64, c1: i32, c2: i32, c3: i32, alpha: f64, outline: bool, ) { trait_geometry::draw_triangle(self, x1, y1, x2, y2, x3, y3, c1, c2, c3, alpha, outline) }
    fn draw_ellipse(&mut self, x: f64, y: f64, rad_x: f64, rad_y: f64, c1: i32, c2: i32, alpha: f64, outline: bool) { trait_geometry::draw_ellipse(self, x, y, rad_x, rad_y, c1, c2, alpha, outline) }
    fn draw_roundrect(&mut self, x1: f64, y1: f64, x2: f64, y2: f64, c1: i32, c2: i32, alpha: f64, outline: bool) { trait_geometry::draw_roundrect(self, x1, y1, x2, y2, c1, c2, alpha, outline) }
    fn set_circle_precision(&mut self, prec: i32) { trait_state::set_circle_precision(self, prec) }
    fn get_circle_precision(&self) -> i32 { trait_state::get_circle_precision(self) }
    fn reset_primitive_2d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) { trait_geometry::reset_primitive_2d(self, ptype, atlas_ref) }
    fn vertex_2d(&mut self, x: f64, y: f64, xtex: f64, ytex: f64, col: i32, alpha: f64) { trait_geometry::vertex_2d(self, x, y, xtex, ytex, col, alpha) }
    fn draw_primitive_2d(&mut self) { trait_geometry::draw_primitive_2d(self) }
    fn get_primitive_2d(&self) -> PrimitiveBuilder { trait_geometry::get_primitive_2d(self) }
    fn set_primitive_2d(&mut self, prim: PrimitiveBuilder) { trait_geometry::set_primitive_2d(self, prim) }
    fn reset_primitive_3d(&mut self, ptype: PrimitiveType, atlas_ref: Option<AtlasRef>) { trait_geometry::reset_primitive_3d(self, ptype, atlas_ref) }
    fn vertex_3d( &mut self, x: f64, y: f64, z: f64, nx: f64, ny: f64, nz: f64, xtex: f64, ytex: f64, col: i32, alpha: f64, ) { trait_geometry::vertex_3d(self, x, y, z, nx, ny, nz, xtex, ytex, col, alpha) }
    fn draw_primitive_3d(&mut self) { trait_geometry::draw_primitive_3d(self) }
    fn get_primitive_3d(&self) -> PrimitiveBuilder { trait_geometry::get_primitive_3d(self) }
    fn set_primitive_3d(&mut self, prim: PrimitiveBuilder) { trait_geometry::set_primitive_3d(self, prim) }
    fn extend_buffers(&self, buf: &mut VertexBuffer) { trait_geometry::extend_buffers(self, buf) }
    fn draw_buffers(&mut self, atlas_ref: Option<AtlasRef>, buf: &VertexBuffer) { trait_geometry::draw_buffers(self, atlas_ref, buf) }
    fn clear_view(&mut self, colour: Colour, alpha: f64) { trait_surfaces::clear_view(self, colour, alpha) }
    fn clear_view_no_zbuf(&mut self, colour: Colour, alpha: f64) { trait_surfaces::clear_view_no_zbuf(self, colour, alpha) }
    fn clear_zbuf(&mut self) { trait_surfaces::clear_zbuf(self) }
    fn get_3d(&self) -> bool { trait_state::get_3d(self) }
    fn set_3d(&mut self, use_3d: bool) { trait_state::set_3d(self, use_3d) }
    fn get_depth(&self) -> f32 { trait_state::get_depth(self) }
    fn set_depth(&mut self, depth: f32) { trait_state::set_depth(self, depth) }
    fn get_depth_test(&self) -> bool { trait_state::get_depth_test(self) }
    fn set_depth_test(&mut self, depth_test: bool) { trait_state::set_depth_test(self, depth_test) }
    fn get_write_depth(&self) -> bool { trait_state::get_write_depth(self) }
    fn set_write_depth(&mut self, write_depth: bool) { trait_state::set_write_depth(self, write_depth) }
    fn get_culling(&self) -> bool { trait_state::get_culling(self) }
    fn set_culling(&mut self, culling: bool) { trait_state::set_culling(self, culling) }
    fn get_perspective(&self) -> bool { trait_state::get_perspective(self) }
    fn set_perspective(&mut self, perspective: bool) { trait_state::set_perspective(self, perspective) }
    fn get_fog(&self) -> Option<Fog> { trait_state::get_fog(self) }
    fn set_fog(&mut self, fog: Option<Fog>) { trait_state::set_fog(self, fog) }
    fn get_gouraud(&self) -> bool { trait_state::get_gouraud(self) }
    fn set_gouraud(&mut self, gouraud: bool) { trait_state::set_gouraud(self, gouraud) }
    fn get_lighting_enabled(&self) -> bool { trait_state::get_lighting_enabled(self) }
    fn set_lighting_enabled(&mut self, enabled: bool) { trait_state::set_lighting_enabled(self, enabled) }
    fn get_ambient_colour(&self) -> i32 { trait_state::get_ambient_colour(self) }
    fn set_ambient_colour(&mut self, colour: i32) { trait_state::set_ambient_colour(self, colour) }
    fn get_lights(&self) -> [(bool, Light); 8] { trait_state::get_lights(self) }
    fn set_lights(&mut self, lights: [(bool, Light); 8]) { trait_state::set_lights(self, lights) }
    fn set_light_enabled(&mut self, id: usize, enabled: bool) { trait_state::set_light_enabled(self, id, enabled) }
    fn set_light(&mut self, id: usize, light: Light) { trait_state::set_light(self, id, light) }
}
