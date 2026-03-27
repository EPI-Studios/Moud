#version 330 core
in vec2 vUv;
in vec4 vFillColor;
in vec4 vStrokeColor;
in vec4 vParams;
in vec4 vGradColor;
in vec4 vGradParams;
in vec4 vExtra;

uniform sampler2D uTexture;

out vec4 FragColor;

float sdRoundBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - (halfSize - vec2(radius));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float sdCircle(vec2 p, float r) {
    return length(p) - r;
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = 0.0;
    float denom = dot(ba, ba);
    if (denom > 1e-6) {
        h = clamp(dot(pa, ba) / denom, 0.0, 1.0);
    }
    return length(pa - ba * h);
}

void main() {
    float mode = vGradParams.x;
    if (mode < -1.5) {
        float a = texture(uTexture, vUv).a;
        FragColor = vec4(vFillColor.rgb, vFillColor.a * a);
        return;
    }

    if (mode < -0.5) {
        float sdf = texture(uTexture, vUv).a;
        float w = max(fwidth(sdf), 1e-4) * 0.75;
        float a = smoothstep(0.5 - w, 0.5 + w, sdf);
        FragColor = vec4(vFillColor.rgb, vFillColor.a * a);
        return;
    }

    float shapeGroupF = floor(mode * 0.1 + 1e-4); // 0=box, 1=circle, 2=capsule
    float gradMode = mode - shapeGroupF * 10.0;   // 0=solid, 1=linear, 2=radial
    vec4 fillColor = vFillColor;
    if (gradMode > 0.5) {
        if (gradMode < 1.5) {
            vec2 dir = normalize(vec2(vGradParams.y, vGradParams.z));
            float t = clamp(dot(vUv, dir) + vGradParams.w, 0.0, 1.0);
            fillColor = mix(vFillColor, vGradColor, t);
        } else {
            vec2 c = vec2(vGradParams.y, vGradParams.z);
            float r = max(1e-4, vGradParams.w);
            float t = clamp(length(vUv - c) / r, 0.0, 1.0);
            fillColor = mix(vFillColor, vGradColor, t);
        }
    }
    vec4 fillBase = texture(uTexture, vUv) * fillColor;

    float borderPx = vParams.w;
    if (shapeGroupF < 0.5 && vParams.z <= 0.01 && borderPx <= 0.01) {
        FragColor = fillBase;
        return;
    }

    vec2 sizePx = max(vParams.xy, vec2(0.0));
    vec2 halfSize = sizePx * 0.5;
    vec2 p = vUv * sizePx - halfSize;

    float d = 0.0;
    if (shapeGroupF < 0.5) {
        float r = clamp(vParams.z, 0.0, min(halfSize.x, halfSize.y));
        d = sdRoundBox(p, halfSize, r);
    } else if (shapeGroupF < 1.5) {
        float r = clamp(vParams.z, 0.0, min(halfSize.x, halfSize.y));
        d = sdCircle(p, r);
    } else {
        float r = max(0.0, vParams.z);
        d = sdSegment(p, vExtra.xy, vExtra.zw) - r;
    }
    float aa = max(fwidth(d), 1e-4);

    float fillMask = 1.0 - smoothstep(0.0, aa, d);
    vec4 fill = vec4(fillBase.rgb, fillBase.a * fillMask);

    float strokeMask = 0.0;
    if (borderPx > 0.01) {
        float t = max(0.0, borderPx);
        float outer = fillMask;
        float inner = 1.0 - smoothstep(-t, -t + aa, d);
        strokeMask = clamp(outer - inner, 0.0, 1.0);
    }
    vec4 strokeBase = vStrokeColor;
    vec4 stroke = vec4(strokeBase.rgb, strokeBase.a * strokeMask);
    float outA = stroke.a + fill.a * (1.0 - stroke.a);
    vec3 outRGB = (stroke.rgb * stroke.a + fill.rgb * fill.a * (1.0 - stroke.a)) / max(outA, 1e-6);
    FragColor = vec4(outRGB, outA);
}

