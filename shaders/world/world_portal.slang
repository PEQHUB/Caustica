// Literal port of vanilla Minecraft 26.2 rendertype_end_portal.fsh to Slang.
// Reproduces the exact End portal / End gateway fragment shader: 16 color coefficients,
// per-layer rotation/scale/translate matrix, end_sky for layer 0, end_portal texture
// for layers 1..layerCount, projected clip UVs, frac() for repeat.
//
// The caller must provide texProj0 as the post-projection_from_position coordinate:
//   texProj0 = projection_from_position(clipPosition)
// where projection_from_position is:
//   vec4 projection = position * 0.5;
//   projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
//   projection.zw = position.zw;
//
// The return value is in the SAMPLED/ENCODED domain (sRGB). The caller must apply
// sRGB-to-linear BT.709 and then BT.709-to-BT.2020 conversion.
module world_portal;

import world_common;

static const float3 END_PORTAL_COLORS[16] = {
    float3(0.022087, 0.098399, 0.110818),
    float3(0.011892, 0.095924, 0.089485),
    float3(0.027636, 0.101689, 0.100326),
    float3(0.046564, 0.109883, 0.114838),
    float3(0.064901, 0.117696, 0.097189),
    float3(0.063761, 0.086895, 0.123646),
    float3(0.084817, 0.111994, 0.166380),
    float3(0.097489, 0.154120, 0.091064),
    float3(0.106152, 0.131144, 0.195191),
    float3(0.097721, 0.110188, 0.187229),
    float3(0.133516, 0.138278, 0.148582),
    float3(0.070006, 0.243332, 0.235792),
    float3(0.196766, 0.142899, 0.214696),
    float3(0.047281, 0.315338, 0.321970),
    float3(0.204675, 0.390010, 0.302066),
    float3(0.080955, 0.314821, 0.661491)
};

// Vanilla SCALE_TRANSLATE matrix from matrix.glsl.
// GLSL mat4 constructor fills COLUMN-BY-COLUMN:
//   Column 0: (0.5, 0.0, 0.0, 0.25)
//   Column 1: (0.0, 0.5, 0.0, 0.25)
//   Column 2: (0.0, 0.0, 1.0, 0.0)
//   Column 3: (0.0, 0.0, 0.0, 1.0)
// Slang float4x4 constructor fills ROW-BY-ROW, so the equivalent is the transpose:
//   Row 0: (0.5, 0.0, 0.0, 0.0)
//   Row 1: (0.0, 0.5, 0.0, 0.0)
//   Row 2: (0.0, 0.0, 1.0, 0.0)
//   Row 3: (0.25, 0.25, 0.0, 1.0)
static const float4x4 END_PORTAL_SCALE_TRANSLATE = float4x4(
    0.5, 0.0, 0.0, 0.0,
    0.0, 0.5, 0.0, 0.0,
    0.0, 0.0, 1.0, 0.0,
    0.25, 0.25, 0.0, 1.0
);

// GLSL 2D rotation matrix constructor equivalent: mat2(a, b, c, d) fills
// column 0 = (a, c), column 1 = (b, d), i.e. [[a, c], [b, d]].
// The vanilla shader does: mat2_rotate_z(radians(angle))
// which produces: mat2(cos, -sin, sin, cos) = [[cos, sin], [-sin, cos]]
float2x2 mat2_rotate_z(float a) {
    float c = cos(a);
    float s = sin(a);
    return float2x2(c, -s, s, c);
}

// Compute the per-layer UV transform for a given portal layer.
// layer is 1-based (1.0 for the first animated layer, matching vanilla's float(i + 1)).
// texProj0 is the post-projection_from_position screen-projected coordinate.
// Returns the transformed 2D texture coordinate (already divided by safe W, frac'd).
//
// Vanilla GLSL: textureProj(Sampler1, texProj0 * end_portal_layer(layer))
// where end_portal_layer returns a mat4 and the multiplication is row-vector * column-major-matrix.
//
// This is a faithful scalar port of that matrix multiplication, matching the exact vanilla
// computation without constructing intermediate float4x4 objects.
float2 end_portal_layer_uv(float layer, float2 texProj0_xy, float texProj0_w, float gameTime) {
    float angle = radians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
    float c = cos(angle);
    float s = sin(angle);
    float sc = (4.5 - layer / 4.0) * 2.0;

    float x = sc * (c * texProj0_xy.x - s * texProj0_xy.y);
    float y = sc * (s * texProj0_xy.x + c * texProj0_xy.y);

    // Translation: vanilla translate column 3 = (17.0/layer, (2.0+layer/1.5)*(gameTime*1.5), 0, 1)
    x += texProj0_w * (17.0 / layer);
    y += texProj0_w * ((2.0 + layer / 1.5) * (gameTime * 1.5));

    // SCALE_TRANSLATE: M[0][0]=0.5, M[1][1]=0.5, M[3][0]=0.25, M[3][1]=0.25
    x = x * 0.5 + texProj0_w * 0.25;
    y = y * 0.5 + texProj0_w * 0.25;

    // Safe divide: avoid division by near-zero w
    float safeW = abs(texProj0_w) >= 1.0e-6
            ? texProj0_w
            : (texProj0_w < 0.0 ? -1.0e-6 : 1.0e-6);

    return frac(float2(x, y) / safeW);
}

// Sample the vanilla End portal effect.
//
// texProj0: post-projection_from_position screen-projected coordinate (float4).
// endSkySlot: bindless entityAlbedoTex slot for the end_sky texture.
// portalSlot: bindless entityAlbedoTex slot for the end_portal texture.
// gameTime: shader game time for animation (WorldPush.portalGameTime).
// layerCount: number of animated portal layers (15 for End portal, 16 for End gateway).
// textures: the entityAlbedoTex bindless texture array.
//
// Returns the accumulated color in sRGB/BT.709 (sampled/encoded domain).
// The caller must apply sRGB-to-linear BT.709 and then BT.709-to-BT.2020.
public float3 sampleVanillaEndPortal(
        float4 texProj0,
        uint endSkySlot,
        uint portalSlot,
        float gameTime,
        uint layerCount,
        Sampler2D textures[]
) {
    // Layer 0: end_sky background * COLORS[0]
    float3 color = textures[NonUniformResourceIndex(endSkySlot)]
            .SampleLevel(texProj0.xy / texProj0.w, 0.0).rgb
            * END_PORTAL_COLORS[0];

    // Layers 1..layerCount: animated portal layers with COLORS[0..layerCount-1]
    for (uint i = 0u; i < layerCount; i++) {
        float fi = float(i + 1);  // 1-based layer parameter

        float2 layerUv = end_portal_layer_uv(fi, texProj0.xy, texProj0.w, gameTime);

        float3 layerColor = textures[NonUniformResourceIndex(portalSlot)]
                .SampleLevel(layerUv, 0.0).rgb;

        // COLORS[i] where i = 0..layerCount-1
        color += layerColor * END_PORTAL_COLORS[i];
    }

    return color;
}
