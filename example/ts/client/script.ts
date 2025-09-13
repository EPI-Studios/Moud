console.log("Moud client script loaded.");

Moud.input.onKey("key.keyboard.f", (pressed) => {
        Moud.network.sendToServer("flashlight.toggle", {});
    }
});