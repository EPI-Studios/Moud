
console.log("Moud client script loaded.");


Moud.input.onKey("key.keyboard.f", (pressed) => {
    if (pressed) {
        Moud.network.sendToServer("flashlight.toggle", {});
    }
});