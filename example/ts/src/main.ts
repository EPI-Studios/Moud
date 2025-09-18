api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

api.on('player.join', (player) => {
});

api.on('player.chat', (event) => {
    const player = event.getPlayer();
    const message = event.getMessage();

    event.cancel();

    if (message === '!lock') {
        const spawnPos = new Vector3(0, 64, 0);
        const rotation = { yaw: 0, pitch: -30 };
        player.getCamera().lock(spawnPos, rotation);
        player.sendMessage("Camera locked at spawn looking down");

    } else if (message === '!unlock') {
        player.getCamera().release();
        player.sendMessage("Camera unlocked");

    } else if (message === '!hideui') {
        player.getUi().hide();
        player.sendMessage("UI hidden (using mixins to hide elements)");

    } else if (message === '!showui') {
        player.getUi().show();
        player.sendMessage("UI restored");

    } else if (message === '!spectate') {
        const playerPos = player.getPosition();
        const spectatePos = new Vector3(playerPos.x, playerPos.y + 20, playerPos.z);
        const rotation = { yaw: 0, pitch: -90 };
        player.getCamera().lock(spectatePos, rotation);
        player.getUi().hide({ hideHotbar: true, hideHand: true, hideExperience: false });
        player.sendMessage("Spectating from above with partial UI hidden");

    } else {
        player.sendMessage(`Unknown command: ${message}`);
    }
});