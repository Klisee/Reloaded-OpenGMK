use crate::render::{BlendType, Scaling, Vertex, display_rect};

const ALL_BLEND_TYPES: [BlendType; 11] = [
    BlendType::Zero,
    BlendType::One,
    BlendType::SrcColour,
    BlendType::InvSrcColour,
    BlendType::SrcAlpha,
    BlendType::InvSrcAlpha,
    BlendType::DestAlpha,
    BlendType::InvDestAlpha,
    BlendType::DestColour,
    BlendType::InvDestColour,
    BlendType::SrcAlphaSaturate,
];

fn vulkan_mapping_must_cover_every_blend_type(blend: BlendType) -> usize {
    match blend {
        BlendType::Zero => 0,
        BlendType::One => 1,
        BlendType::SrcColour => 2,
        BlendType::InvSrcColour => 3,
        BlendType::SrcAlpha => 4,
        BlendType::InvSrcAlpha => 5,
        BlendType::DestAlpha => 6,
        BlendType::InvDestAlpha => 7,
        BlendType::DestColour => 8,
        BlendType::InvDestColour => 9,
        BlendType::SrcAlphaSaturate => 10,
    }
}

fn clear_touch_state() {
    crate::touch_controls::set_text_entry(false);
    crate::touch_controls::set_ime_inset(0);
}

#[test]
fn vertex_layout_matches_wgpu() {
    assert_eq!(
        Vertex::STRIDE,
        72,
        "Vertex::STRIDE changed: wgpu's layout would read past every vertex; update layout and shader together"
    );
    assert_eq!(Vertex::POS_OFFSET, 0, "Vertex::POS_OFFSET changed: wgpu would read pos from the wrong place");
    assert_eq!(Vertex::INTERP_OFFSET, 12, "Vertex::INTERP_OFFSET changed: wgpu would read interp from the wrong place");
    assert_eq!(Vertex::TEX_OFFSET, 20, "Vertex::TEX_OFFSET changed: wgpu would read tex_coord from the wrong place");
    assert_eq!(Vertex::BLEND_OFFSET, 28, "Vertex::BLEND_OFFSET changed: wgpu would read blend from the wrong place");
    assert_eq!(
        Vertex::ATLAS_OFFSET,
        44,
        "Vertex::ATLAS_OFFSET changed: wgpu would read atlas_xywh from the wrong place"
    );
    assert_eq!(Vertex::NORMAL_OFFSET, 60, "Vertex::NORMAL_OFFSET changed: wgpu would read normal from the wrong place");
    assert_eq!(std::mem::size_of::<Vertex>(), Vertex::STRIDE, "Vertex::STRIDE must stay size_of::<Vertex>()");
}

#[test]
fn vertex_is_pod_safe() {
    assert_eq!(
        std::mem::align_of::<Vertex>(),
        4,
        "Vertex alignment changed: it must stay aligned like f32 to be uploaded as a POD buffer"
    );
}

#[test]
fn blend_types_are_exhaustive() {
    assert_eq!(ALL_BLEND_TYPES.len(), 11, "BlendType gained or lost variants: update the Vulkan blend-factor mapping");
    for (index, blend) in ALL_BLEND_TYPES.iter().enumerate() {
        assert_eq!(
            vulkan_mapping_must_cover_every_blend_type(*blend),
            index,
            "BlendType variant order changed: update the Vulkan blend-factor mapping"
        );
        assert_eq!(
            *blend as usize, index,
            "BlendType discriminants are no longer contiguous: update the Vulkan blend-factor mapping"
        );
    }
    for (index, blend) in ALL_BLEND_TYPES.iter().enumerate() {
        for other in &ALL_BLEND_TYPES[index + 1..] {
            assert_ne!(blend, other, "BlendType variants must be pairwise distinct");
        }
    }
}

#[test]
fn display_rect_full_fills_window() {
    clear_touch_state();
    assert_eq!(
        display_rect(1920, 1080, 320, 240, Scaling::Full),
        (0, 0, 1920, 1080),
        "Scaling::Full must cover the whole window"
    );
    assert_eq!(
        display_rect(800, 600, 640, 480, Scaling::Full),
        (0, 0, 800, 600),
        "Scaling::Full must ignore the framebuffer size"
    );
}

#[test]
fn display_rect_aspect_letterboxes() {
    clear_touch_state();
    let (x, y, w, h) = display_rect(1920, 1080, 320, 240, Scaling::Aspect(-1.0));
    assert!(w < 1920, "Scaling::Aspect on a wide window must keep the game narrower than the window (got {w})");
    assert_eq!(h, 1080, "Scaling::Aspect should fill the window height here");
    assert_eq!(x, (1920 - w) / 2, "the picture must stay horizontally centred");
    assert_eq!(y, 0, "the picture must stay at the top when only width is letterboxed");
    assert_eq!(w, 1440, "the exact width for 320x240 in a 1920x1080 window");
    assert_eq!(w as u64 * 240, h as u64 * 320, "the picture must preserve the framebuffer aspect ratio");

    let (px, py, pw, ph) = display_rect(1080, 1920, 320, 240, Scaling::Aspect(-1.0));
    assert!(ph < 1920, "Scaling::Aspect on a tall window must keep the game shorter than the window (got {ph})");
    assert_eq!(pw, 1080, "Scaling::Aspect should fill the window width here");
    assert_eq!(px, 0, "the picture must stay at the left when only height is letterboxed");
    assert_eq!(py, (1920 - ph) / 2, "the picture must stay vertically centred");
    assert_eq!(ph, 810, "the exact height for 320x240 in a 1080x1920 window");
    assert_eq!(pw as u64 * 240, ph as u64 * 320, "the picture must preserve the framebuffer aspect ratio");
}
