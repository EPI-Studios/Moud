float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// hash but 2 values
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// smooth value noise, 0 to 1
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
    mix(hash12(i),                 hash12(i + vec2(1.0, 0.0)), f.x),
    mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x),
    f.y);
}

// cellular noise, lower = closer to a cell center
float worley(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float md = 8.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 cell = i + vec2(float(x), float(y));
            vec2 pt   = vec2(float(x), float(y)) + hash22(cell);
            vec2 dv   = pt - f;
            md        = min(md, dot(dv, dv));
        }
    }
    return sqrt(md);
}

// slight rotation applied between fbm octaves so things dont line up on a grid
const mat2 NOISE_ROT = mat2( 0.86602540, -0.5,
0.5,         0.86602540);

// fbm with 2 octaves, 0 to 1
float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float total = 0.0;
    for (int i = 0; i < 2; i++) {
        v     += a * vnoise(p);
        total += a;
        p      = NOISE_ROT * p * 2.03;
        a     *= 0.5;
    }
    return v / total;
}

// fbm with 3 octaves, 0 to 1
float fbm3(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float total = 0.0;
    for (int i = 0; i < 3; i++) {
        v     += a * vnoise(p);
        total += a;
        p      = NOISE_ROT * p * 2.03;
        a     *= 0.5;
    }
    return v / total;
}

// fbm with 4 octaves, 0 to 1
float fbm4(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        v     += a * vnoise(p);
        total += a;
        p      = NOISE_ROT * p * 2.03;
        a     *= 0.5;
    }
    return v / total;
}

// warps the sample point so edges curl around instead of sitting flat on the noise
// use it like:
//   p += 0.6 * domainWarp(p * 0.5);
//   float n = fbm3(p);
vec2 domainWarp(vec2 p) {
    return vec2(vnoise(p + vec2(1.7, 9.2)),
    vnoise(p + vec2(8.3, 2.8))) - 0.5;
}