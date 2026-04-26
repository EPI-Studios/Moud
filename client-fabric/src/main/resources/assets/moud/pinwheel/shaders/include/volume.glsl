// ray vs horizontal slab. gives you tEnter and tExit.
// if .y <= .x the ray missed or something was blocking it (terrain etc)
// also handles the case where the ray is perfectly flat, which is annoying but whatever
vec2 slabIntersect(vec3 origin, vec3 dir, float yMin, float yMax, float maxT) {
    if (abs(dir.y) < 1e-4) {
        if (origin.y >= yMin && origin.y <= yMax) {
            return vec2(0.0, maxT);
        }
        return vec2(1.0, -1.0);
    }
    float t1 = (yMin - origin.y) / dir.y;
    float t2 = (yMax - origin.y) / dir.y;
    return vec2(max(min(t1, t2), 0.0), min(max(t1, t2), maxT));
}

// full density in the middle, fades at top and bottom
// edgeFrac is how wide the fade is, 0.2 works for most things
// needed so the slab doesnt just cut off hard at the edges
float verticalBell(float y, float yMin, float yMax, float edgeFrac) {
    float t = clamp((y - yMin) / max(yMax - yMin, 1e-3), 0.0, 1.0);
    float e = clamp(edgeFrac, 0.0, 0.5);
    return smoothstep(0.0, e, t) * (1.0 - smoothstep(1.0 - e, 1.0, t));
}

// dense at the bottom, thins out near the top
float verticalAnvil(float y, float yMin, float yMax) {
    float t = clamp((y - yMin) / max(yMax - yMin, 1e-3), 0.0, 1.0);
    return smoothstep(0.0, 0.18, t) * (1.0 - smoothstep(0.78, 1.0, t));
}

// 1.0 at yReference and falls off from there, same speed going up or down
// scale is roughly how far before its mostly gone
// drop the abs() if you only want it to fall one way
float exponentialFalloff(float y, float yReference, float scale) {
    return exp(-abs(y - yReference) / max(scale, 1e-4));
}