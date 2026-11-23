#version 150

// The void: a fixed starfield picked by the world direction you look along (like the sky), so void liquid looks
// like a window onto one still background. Near-black with purple specks in big square "pixels".

uniform float GameTime;

in vec3 viewDir;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Cube-map face and its coordinates in 0..1.
vec3 cube(vec3 d) {
    vec3 a = abs(d);
    if (a.x >= a.y && a.x >= a.z) return vec3(d.zy / a.x * 0.5 + 0.5, d.x > 0.0 ? 0.0 : 1.0);
    if (a.y >= a.z) return vec3(d.xz / a.y * 0.5 + 0.5, d.y > 0.0 ? 2.0 : 3.0);
    return vec3(d.xy / a.z * 0.5 + 0.5, d.z > 0.0 ? 4.0 : 5.0);
}

// Smooth value noise on a face, for faint purple haze.
float haze(vec2 uv, float face, float cells) {
    vec2 g = uv * cells;
    vec2 i = floor(g), f = fract(g);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(vec3(i, face));
    float b = hash(vec3(i + vec2(1.0, 0.0), face));
    float c = hash(vec3(i + vec2(0.0, 1.0), face));
    float e = hash(vec3(i + vec2(1.0, 1.0), face));
    return mix(mix(a, b, f.x), mix(c, e, f.x), f.y);
}

void main() {
    vec3 d = normalize(viewDir);
    vec3 c = cube(d);
    vec2 uv = c.xy;
    float face = c.z;
    float ticks = GameTime * 24000.0;

    vec3 col = vec3(0.016, 0.008, 0.03);
    col += vec3(0.05, 0.015, 0.09) * pow(haze(uv, face, 5.0), 3.0);

    // Small specks: one in ~60 cells of a 160-cell grid.
    vec2 cell = floor(uv * 160.0);
    float h = hash(vec3(cell, face + 11.0));
    if (h > 0.983) {
        float tw = 0.65 + 0.35 * sin(ticks * 0.06 + h * 400.0);
        col = mix(vec3(0.30, 0.13, 0.50), vec3(0.62, 0.38, 0.95), fract(h * 57.0)) * tw;
    }
    // Bigger, brighter spots: 2x2 "pixels" on an 80-cell grid, rarer.
    vec2 big = floor(uv * 80.0);
    float hb = hash(vec3(big, face + 37.0));
    if (hb > 0.993) {
        float tw = 0.8 + 0.2 * sin(ticks * 0.035 + hb * 900.0);
        col = mix(vec3(0.55, 0.28, 0.88), vec3(0.85, 0.66, 1.0), fract(hb * 91.0)) * tw;
    }
    fragColor = vec4(col, 1.0);
}
