let isInverted = false;
let smoothPitch = 0.0;
let smoothYaw = 0.0;
let targetPitch = 0.0;
let targetYaw = 0.0;
let smoothingFactor = 0.7;
let mouseSensitivity = 0.1;
let lastTime = Date.now();
let smoothCameraEnabled = false;

function framerate_independent_lerp(source: number, destination: number, smoothing: number, delta: number): number {
    return source + (1.0 - Math.pow(smoothing, delta)) * (destination - source);
}

Moud.input.onMouseMove((deltaX: number, deltaY: number) => {
    if (smoothCameraEnabled) {
        targetYaw += deltaX * mouseSensitivity;
        targetPitch -= deltaY * mouseSensitivity;
        targetPitch = Math.max(-90, Math.min(90, targetPitch));
    }
});

Moud.network.on('camera:toggle_smooth', (data: any) => {
    smoothCameraEnabled = data.enabled;

    if (smoothCameraEnabled) {
        targetYaw = Moud.camera.getYaw();
        targetPitch = Moud.camera.getPitch();
        smoothYaw = targetYaw;
        smoothPitch = targetPitch;
        Moud.console.log('Cinematic camera enabled');
    } else {
        Moud.console.log('Cinematic camera disabled');
    }
});

Moud.rendering.on('beforeWorldRender', () => {
    if (!smoothCameraEnabled) return;

    const currentTime = Date.now();
    const deltaTime = (currentTime - lastTime) / 1000.0;
    lastTime = currentTime;

    smoothYaw = framerate_independent_lerp(smoothYaw, targetYaw, smoothingFactor, deltaTime);
    smoothPitch = framerate_independent_lerp(smoothPitch, targetPitch, smoothingFactor, deltaTime);

    Moud.camera.setYaw(smoothYaw);
    Moud.camera.setPitch(smoothPitch);
});

Moud.network.on('rendering:toggle_invert', () => {
    isInverted = !isInverted;

    if (isInverted) {
        Moud.rendering.applyPostEffect("moud:invert_colors");
    } else {
        Moud.rendering.removePostEffect("moud:invert_colors");
    }
});

Moud.network.on('hud:welcome', (data: any) => {
    Moud.console.log('Welcome message received:', data);
});

Moud.network.on('test:notification', (data: any) => {
    Moud.console.log('Test notification received:', data);
    Moud.console.log('Type:', data.type, 'Message:', data.message);
});

Moud.console.log('Client HUD script ready for rendering tests.');