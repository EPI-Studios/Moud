api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

console.log("Debug script loaded");

let playerModel = null;

api.on('player.join', (player) => {
    console.log(`Player joined: ${player.getName()}`);

    playerModel = api.getWorld().createPlayerModel({
        position: new Vector3(5, 64, 5),
        skinUrl: ""
    });

    console.log("Created player model at (5, 64, 5)");

    playerModel.onClick((clicker, data) => {
        console.log(`Player ${clicker.getName()} clicked model at mouse (${data.mouseX}, ${data.mouseY})`);
    });
});

api.on('player.mousemove', (player, data) => {
    if (Math.abs(data.deltaX) > 5 || Math.abs(data.deltaY) > 5) {
        console.log(`Significant mouse move from ${player.getName()}: dx=${data.deltaX}, dy=${data.deltaY}`);
    }
});

api.on('player.click', (player, data) => {
    console.log(`Player ${player.getName()} clicked: button=${data.button}`);
});

api.on('player.chat', (event) => {
    const message = event.getMessage().toLowerCase();
    const player = event.getPlayer();

    console.log(`Chat from ${player.getName()}: ${message}`);

    if (message === '!test') {
        player.sendMessage("Testing systems...");

        if (playerModel) {
            const currentPos = playerModel.getPosition();
            const newPos = new Vector3(currentPos.x + 1, currentPos.y, currentPos.z);
            playerModel.setPosition(newPos);
            player.sendMessage(`Moved model to (${newPos.x}, ${newPos.y}, ${newPos.z})`);
        }
    }

    if (message === '!rotate') {
        if (playerModel) {
            playerModel.setRotation({ yaw: 45, pitch: 0 });
            player.sendMessage("Rotated model 45 degrees");
        }
    }

    if (message === '!remove') {
        if (playerModel) {
            playerModel.remove();
            playerModel = null;
            player.sendMessage("Removed player model");
        }
    }

    if (message === '!create') {
        const pos = player.getPosition();
        playerModel = api.getWorld().createPlayerModel({
            position: new Vector3(pos.x + 2, pos.y, pos.z + 2),
            skinUrl: ""
        });

        playerModel.onClick((clicker, data) => {
            clicker.sendMessage(`You clicked the model! Button: ${data.button}`);
        });

        player.sendMessage("Created new player model near you");
    }
});

console.log("Debug event handlers registered");