function onMoudReady(callback) {
    if (typeof Moud !== 'undefined' && Moud.camera) {
        callback();
    } else {
        setTimeout(() => onMoudReady(callback), 50);
    }
}

onMoudReady(() => {
    console.log("Moud API ready, initializing flashlight system");

    // Flashlight toggle with F key
    Moud.input.onKey('key.keyboard.f', (pressed) => {
        if (pressed) {
            console.log("F key pressed - toggling flashlight");
            Moud.network.sendToServer('flashlight.toggle', {});
        }
    });

    console.log("Flashlight system initialized - Press F to toggle");
});
