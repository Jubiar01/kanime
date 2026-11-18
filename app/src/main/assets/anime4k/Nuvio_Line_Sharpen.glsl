//!DESC KAnime-Adaptive-Line-Sharpen
//!HOOK MAIN
//!BIND HOOKED
#define NUVIO_SHARPEN_STRENGTH 4.2
#define NUVIO_DARK_OVERSHOOT 0.28
#define NUVIO_BRIGHT_OVERSHOOT 0.12
#define NUVIO_LINE_DARKEN 0.14
#define NUVIO_EDGE_FLOOR 0.012
#define NUVIO_EDGE_CEIL 0.065

float nuvio_luma(vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

float nuvio_tap(float x, float y) {
    return nuvio_luma(HOOKED_texOff(vec2(x, y)).rgb);
}

vec4 hook() {
    vec4 center = HOOKED_texOff(vec2(0.0, 0.0));
    float c = nuvio_luma(center.rgb);
    float n = nuvio_tap(0.0, -1.0);
    float s = nuvio_tap(0.0, 1.0);
    float w = nuvio_tap(-1.0, 0.0);
    float e = nuvio_tap(1.0, 0.0);
    float nw = nuvio_tap(-1.0, -1.0);
    float ne = nuvio_tap(1.0, -1.0);
    float sw = nuvio_tap(-1.0, 1.0);
    float se = nuvio_tap(1.0, 1.0);
    float n2 = nuvio_tap(0.0, -2.0);
    float s2 = nuvio_tap(0.0, 2.0);
    float w2 = nuvio_tap(-2.0, 0.0);
    float e2 = nuvio_tap(2.0, 0.0);

    // Sobel edge magnitude: gates out flat skies, skin gradients & compression grain
    float gx = (ne + 2.0 * e + se) - (nw + 2.0 * w + sw);
    float gy = (sw + 2.0 * s + se) - (nw + 2.0 * n + ne);
    float edgeMag = sqrt(gx * gx + gy * gy) * 0.25;
    float edgeMask = smoothstep(NUVIO_EDGE_FLOOR, NUVIO_EDGE_CEIL, edgeMag);

    // Multi-scale Gaussian blur for fine + medium anime ink lines
    float blur = (
        36.0 * c +
        24.0 * (n + s + w + e) +
        16.0 * (nw + ne + sw + se) +
        6.0 * (n2 + s2 + w2 + e2)
    ) / 220.0;

    float lo = min(c, min(min(min(n, s), min(w, e)), min(min(nw, ne), min(sw, se))));
    float hi = max(c, max(max(max(n, s), max(w, e)), max(max(nw, ne), max(sw, se))));
    float range = hi - lo;

    // CAS-inspired local contrast adaptation: protects already-hard edges & subtitles
    float casWeight = clamp(1.0 - range * 0.35, 0.55, 1.0);

    // Extra definition on dark anime ink lines (pixels darker than surrounding fill)
    float valley = max(blur - c, 0.0);
    float darkLineBoost = 1.0 + NUVIO_LINE_DARKEN * 4.0 * smoothstep(0.01, 0.14, valley);

    float delta = NUVIO_SHARPEN_STRENGTH * casWeight * edgeMask * darkLineBoost * (c - blur);

    // Asymmetric anti-halo clamping: crisp deep ink lines without glowing white halos
    float minAllowed = max(0.0, lo - NUVIO_DARK_OVERSHOOT * range);
    float maxAllowed = min(1.0, hi + NUVIO_BRIGHT_OVERSHOOT * range);
    float sharpened = clamp(c + delta, minAllowed, maxAllowed);

    // Chroma-preserving luma reconstruction
    float lumaDiff = sharpened - c;
    vec3 additive = clamp(center.rgb + lumaDiff, 0.0, 1.0);
    vec3 scaled = clamp(center.rgb * (sharpened / max(c, 0.0001)), 0.0, 1.0);
    float ratioMix = smoothstep(0.03, 0.18, c) * (lumaDiff >= 0.0 ? 0.55 : 0.85);
    vec3 finalRgb = mix(additive, scaled, ratioMix);
    return vec4(finalRgb, center.a);
}
