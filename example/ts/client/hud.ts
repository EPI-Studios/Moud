// --- State Variables for Camera Smoothing ---

// These variables store the camera's rotation from the previous frame.
// They are the starting point for our smoothing calculation.
let smoothPitch = 0.0;
let smoothYaw = 0.0;

// This controls how fast the camera catches up to the mouse.
// A smaller value (e.g., 0.05) is very smooth and "floaty".
// A larger value (e.g., 0.5) is very responsive and closer to vanilla.
let smoothingAmount = 0.1;

// This flag is controlled by the server command.
let smoothCameraEnabled = false;


// --- Math Helper Function ---

/**
 * A simple linear interpolation function for angles.
 * It correctly handles the "wrap-around" from -180 to 180 degrees.
 */
function lerpAngle(start: number, end: number, amount: number): number {
    let diff = end - start;
    while (diff < -180) diff += 360;
    while (diff > 180) diff -= 360;
    return start + diff * amount;
}


// --- Event Handlers ---

/**
 * Handles the command from the server to toggle the smooth camera effect on or off.
 */
Moud.network.on('camera:toggle_smooth', (data: any) => {
    smoothCameraEnabled = data.enabled;
    const clientCamera = Moud.camera;

    if (smoothCameraEnabled) {
        smoothYaw = clientCamera.getYaw();
        smoothPitch = clientCamera.getPitch();
        clientCamera.enableCustomCamera();
        // --- LOUD DEBUG LOG ---
        Moud.console.log("========================================");
        Moud.console.log(">>> SMOOTH CAMERA ENABLED <<<");
        Moud.console.log(`>>> Initial Yaw: ${smoothYaw.toFixed(4)}`);
        Moud.console.log("========================================");
    } else {
        clientCamera.disableCustomCamera();
        Moud.console.log(">>> SMOOTH CAMERA DISABLED <<<");
    }
});

Moud.rendering.on('beforeWorldRender', (deltaTime: number) => {
    // This is now our main loop. If this log doesn't appear, the event is not firing.
    // Moud.console.log(`[SCRIPT TICK] beforeWorldRender fired. Enabled: ${smoothCameraEnabled}`);

    if (!smoothCameraEnabled) {
        Moud.camera.clearRenderOverrides();
        return;
    };

    const clientCamera = Moud.camera;

    const targetYaw = clientCamera.getYaw();
    const targetPitch = clientCamera.getPitch();

    // The core calculation
    smoothYaw = lerpAngle(smoothYaw, targetYaw, smoothingAmount);
    smoothPitch = lerpAngle(smoothPitch, targetPitch, smoothingAmount);

    // --- LOUD DEBUG LOG ---
    const yawDifference = targetYaw - smoothYaw;
    Moud.console.log(`[SCRIPT CALC] Target: ${targetYaw.toFixed(4)}, Smoothed: ${smoothYaw.toFixed(4)}, Diff: ${yawDifference.toFixed(4)}`);

    clientCamera.setRenderYawOverride(smoothYaw);
    clientCamera.setRenderPitchOverride(smoothPitch);
});

// --- Other existing event handlers for your mod ---

let isInverted = false;
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

Moud.console.log('Client HUD script ready.');