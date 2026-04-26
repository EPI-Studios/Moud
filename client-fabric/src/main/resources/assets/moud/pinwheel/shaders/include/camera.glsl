// pulls camera data straight from veil's UBO so the math always lines up
// with what was actually rendered, no fov mismatch or anything like that
// #include moud:include/camera

#veil:buffer veil:camera VeilCamera

// world position of whatever is at this uv + depth
vec3 worldFromUv(vec2 uv, float depth) {
    vec4 clip   = vec4(uv, depth, 1.0) * 2.0 - 1.0;
    vec4 view   = VeilCamera.IProjMat * clip;
    vec4 local  = VeilCamera.IViewMat * (view / view.w);
    return VeilCamera.CameraPosition + local.xyz;
}

// direction the camera is looking toward this uv
vec3 viewDirFromUv(vec2 uv) {
    vec4 clip   = vec4(uv, 1.0, 1.0) * 2.0 - 1.0;
    vec4 view   = VeilCamera.IProjMat * clip;
    vec4 local  = VeilCamera.IViewMat * (view / view.w);
    return normalize(local.xyz);
}

// gives you both at once, saves doing the matrix math twice
void cameraRayFromUv(vec2 uv, float depth, out vec3 origin, out vec3 dir) {
    vec4 clip      = vec4(uv, depth, 1.0) * 2.0 - 1.0;
    vec4 view      = VeilCamera.IProjMat * clip;
    vec4 local     = VeilCamera.IViewMat * (view / view.w);
    origin         = VeilCamera.CameraPosition;
    vec3 worldHit  = origin + local.xyz;
    dir            = normalize(worldHit - origin);
}