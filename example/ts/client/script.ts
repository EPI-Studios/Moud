function onMoudReady(callback) {
    if (typeof Moud !== 'undefined' && Moud.camera) {
        callback();
    } else {
        setTimeout(() => onMoudReady(callback), 50);
    }
}

onMoudReady(() => {
    console.log("Moud API ready, initializing test systems");

    const coordsDisplay = Moud.ui.createText('X: 0, Y: 0, Z: 0');
    coordsDisplay.setPos(10, 10);
    coordsDisplay.setSize(200, 20);
    coordsDisplay.setTextColor('#00FF00');
    coordsDisplay.setBackgroundColor('#00000080');
    coordsDisplay.setPadding(5);
    coordsDisplay.showAsOverlay();

    const cursorDisplay = Moud.ui.createText('Cursor: No target');
    cursorDisplay.setPos(10, 40);
    cursorDisplay.setSize(300, 20);
    cursorDisplay.setTextColor('#FFFF00');
    cursorDisplay.setBackgroundColor('#00000080');
    cursorDisplay.setPadding(5);
    cursorDisplay.showAsOverlay();

    const fpsDisplay = Moud.ui.createText('FPS: 0');
    fpsDisplay.setPos(10, 70);
    fpsDisplay.setSize(100, 20);
    fpsDisplay.setTextColor('#FF00FF');
    fpsDisplay.setBackgroundColor('#00000080');
    fpsDisplay.setPadding(5);
    fpsDisplay.showAsOverlay();

    let frameCount = 0;
    let lastFpsTime = performance.now();

    function updateDisplays() {
        if (Moud?.camera) {
            const x = Moud.camera.getX().toFixed(2);
            const y = Moud.camera.getY().toFixed(2);
            const z = Moud.camera.getZ().toFixed(2);
            const pitch = Moud.camera.getPitch().toFixed(1);
            const yaw = Moud.camera.getYaw().toFixed(1);

            coordsDisplay.setText(`Pos: ${x}, ${y}, ${z} | Rot: ${yaw}°, ${pitch}°`);
        }

        frameCount++;
        const now = performance.now();
        if (now - lastFpsTime >= 1000) {
            fpsDisplay.setText(`FPS: ${frameCount}`);
            frameCount = 0;
            lastFpsTime = now;
        }
    }

    setInterval(updateDisplays, 16);

    Moud.input.onKey('key.keyboard.f', (pressed) => {
        if (pressed) {
            console.log("F key pressed - toggling cursor visibility");
        }
    });

    Moud.input.onKey('key.keyboard.g', (pressed) => {
        if (pressed) {
            console.log("G key pressed - triggering camera shake");
        }
    });

    Moud.input.onMouseMove((deltaX, deltaY) => {
        cursorDisplay.setText(`Mouse: Δ${deltaX.toFixed(1)}, ${deltaY.toFixed(1)}`);
    });

    Moud.network.on('cursor.target', (data) => {
        if (data.entity) {
            cursorDisplay.setText(`Target: Entity ${data.entity.type}`);
        } else if (data.block) {
            cursorDisplay.setText(`Target: Block ${data.block.type} at ${data.block.x}, ${data.block.y}, ${data.block.z}`);
        } else {
            cursorDisplay.setText('Target: Air');
        }
    });

    console.log("Test script initialization complete");
});