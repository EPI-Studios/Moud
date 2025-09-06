let isInverted = false;

Moud.network.on('rendering:toggle_invert', () => {
    isInverted = !isInverted;

    if (isInverted) {
        Moud.rendering.applyPostEffect("moud:invert_colors");
    } else {
        console.log("Removing invert colors effect...");
        Moud.rendering.removePostEffect("moud:invert_colors");
    }
});

Moud.network.on('hud:welcome', (data: any) => {
    console.log('Welcome message received:', data);
});

Moud.network.on('test:notification', (data: any) => {
    console.log('Test notification received:', data);
    console.log('Type:', data.type, 'Message:', data.message);
});

console.log('Client HUD script ready for rendering tests.');