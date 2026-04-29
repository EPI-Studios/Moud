#define FXAA_EDGE_THRESHOLD     0.125
#define FXAA_EDGE_THRESHOLD_MIN 0.04166667
#define FXAA_SUBPIX             0.75
#define FXAA_SEARCH_STEPS       12

float fxaa_luma(vec3 rgb) {
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 inv = TexelSize;
    vec3 rgbM = texture(screen, texCoord).rgb;
    vec3 rgbN = texture(screen, texCoord + vec2(0.0, -inv.y)).rgb;
    vec3 rgbS = texture(screen, texCoord + vec2(0.0,  inv.y)).rgb;
    vec3 rgbE = texture(screen, texCoord + vec2( inv.x, 0.0)).rgb;
    vec3 rgbW = texture(screen, texCoord + vec2(-inv.x, 0.0)).rgb;

    float lumaM = fxaa_luma(rgbM);
    float lumaN = fxaa_luma(rgbN);
    float lumaS = fxaa_luma(rgbS);
    float lumaE = fxaa_luma(rgbE);
    float lumaW = fxaa_luma(rgbW);

    float lumaMin = min(lumaM, min(min(lumaN, lumaS), min(lumaE, lumaW)));
    float lumaMax = max(lumaM, max(max(lumaN, lumaS), max(lumaE, lumaW)));
    float lumaRange = lumaMax - lumaMin;

    if (lumaRange < max(FXAA_EDGE_THRESHOLD_MIN, lumaMax * FXAA_EDGE_THRESHOLD)) {
        fragColor = vec4(rgbM, 1.0);
        return;
    }

    vec3 rgbNW = texture(screen, texCoord + vec2(-inv.x, -inv.y)).rgb;
    vec3 rgbNE = texture(screen, texCoord + vec2( inv.x, -inv.y)).rgb;
    vec3 rgbSW = texture(screen, texCoord + vec2(-inv.x,  inv.y)).rgb;
    vec3 rgbSE = texture(screen, texCoord + vec2( inv.x,  inv.y)).rgb;
    float lumaNW = fxaa_luma(rgbNW);
    float lumaNE = fxaa_luma(rgbNE);
    float lumaSW = fxaa_luma(rgbSW);
    float lumaSE = fxaa_luma(rgbSE);

    float edgeHorz = abs(lumaNW + lumaNE - 2.0 * lumaN)
                   + abs(lumaW  + lumaE  - 2.0 * lumaM) * 2.0
                   + abs(lumaSW + lumaSE - 2.0 * lumaS);
    float edgeVert = abs(lumaNW + lumaSW - 2.0 * lumaW)
                   + abs(lumaN  + lumaS  - 2.0 * lumaM) * 2.0
                   + abs(lumaNE + lumaSE - 2.0 * lumaE);
    float horzSpan = step(edgeVert, edgeHorz);

    float lumaUp   = mix(lumaW, lumaN, horzSpan);
    float lumaDown = mix(lumaE, lumaS, horzSpan);
    float gradientUp   = abs(lumaUp   - lumaM);
    float gradientDown = abs(lumaDown - lumaM);
    float steepest = step(gradientUp, gradientDown);

    float gradient = max(gradientUp, gradientDown) * 0.25;
    float lumaLocalAvg = 0.5 * (lumaM + mix(lumaUp, lumaDown, steepest));

    vec2 stepDir = mix(vec2(inv.x, 0.0), vec2(0.0, inv.y), horzSpan);
    stepDir *= mix(-1.0, 1.0, steepest);

    vec2 currentUv = texCoord + stepDir * 0.5;
    vec2 offset = mix(vec2(0.0, inv.y), vec2(inv.x, 0.0), horzSpan);

    float lumaEnd1 = lumaLocalAvg;
    float lumaEnd2 = lumaLocalAvg;
    float reached1 = 0.0;
    float reached2 = 0.0;
    vec2 uv1 = currentUv - offset;
    vec2 uv2 = currentUv + offset;
    for (int i = 0; i < FXAA_SEARCH_STEPS; i++) {
        if (reached1 < 0.5) {
            lumaEnd1 = fxaa_luma(texture(screen, uv1).rgb) - lumaLocalAvg;
            reached1 = step(gradient, abs(lumaEnd1));
        }
        if (reached2 < 0.5) {
            lumaEnd2 = fxaa_luma(texture(screen, uv2).rgb) - lumaLocalAvg;
            reached2 = step(gradient, abs(lumaEnd2));
        }
        if (reached1 + reached2 > 1.5) break;
        uv1 -= offset * (1.0 - reached1);
        uv2 += offset * (1.0 - reached2);
    }

    vec2 deltaUv1 = texCoord - uv1;
    vec2 deltaUv2 = uv2 - texCoord;
    float distance1 = mix(deltaUv1.y, deltaUv1.x, horzSpan);
    float distance2 = mix(deltaUv2.y, deltaUv2.x, horzSpan);
    float isDirection1 = step(distance1, distance2);
    float distanceFinal = min(distance1, distance2);
    float edgeLength = distance1 + distance2;
    float pixelOffset = -distanceFinal / max(edgeLength, 1e-6) + 0.5;

    float lumaCenterSmaller = step(lumaLocalAvg, lumaM) * -1.0 + 1.0;
    float endChosen = mix(lumaEnd2, lumaEnd1, isDirection1);
    float endNegative = step(0.0, -endChosen);
    float correctVariation = abs(endNegative - lumaCenterSmaller);
    float finalOffset = mix(0.0, pixelOffset, correctVariation);

    float lumaAvg = (1.0 / 12.0) * (2.0 * (lumaN + lumaS + lumaE + lumaW)
                                  + (lumaNW + lumaNE + lumaSW + lumaSE));
    float subPixOffset1 = clamp(abs(lumaAvg - lumaM) / max(lumaRange, 1e-6), 0.0, 1.0);
    float subPixOffset2 = (-2.0 * subPixOffset1 + 3.0) * subPixOffset1 * subPixOffset1;
    float subPixOffsetFinal = subPixOffset2 * subPixOffset2 * FXAA_SUBPIX;
    finalOffset = max(finalOffset, subPixOffsetFinal);

    vec2 finalUv = texCoord + stepDir * finalOffset * mix(0.0, 1.0, 1.0);
    fragColor = vec4(texture(screen, finalUv).rgb, 1.0);
}
