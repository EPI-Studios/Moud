try {
    console.log("Attempting to load shader...");
    const myShader = assets.loadShader('shaders/my_shader.glsl');
    console.log(`Shader loaded successfully! ID: ${myShader.getId()}`);
} catch (e) {
    console.error("Failed to load shader:", e);
}

const world: World = api.createWorld();
world.setFlatGenerator().setSpawn(0, 65, 0);

const playerSmoothCameraStatus = new Map<string, boolean>();

api.on('player.join', (player: Player) => {
    console.log(`Player joined: ${player.getName()}`);
    player.sendMessage('§6Welcome to the Moud TypeScript server!');
    playerSmoothCameraStatus.set(player.getUuid(), false);

    const server: Server = api.getServer();
    server.broadcast(`§7${player.getName()} joined the server`);

    player.getClient().send('hud:welcome', {
        playerName: player.getName(),
        serverInfo: 'Moud Test Server',
        timestamp: Date.now()
    });
});

api.on('player.chat', (chatEvent: ChatEvent) => {
    const player: Player = chatEvent.getPlayer();
    const message: string = chatEvent.getMessage();

    if (message.startsWith('!')) {
        chatEvent.cancel();

        if (message === '!smoothcamera') {
            const currentStatus = playerSmoothCameraStatus.get(player.getUuid()) || false;
            const newStatus = !currentStatus;
            playerSmoothCameraStatus.set(player.getUuid(), newStatus);

            player.getClient().send('camera:toggle_smooth', { enabled: newStatus });
            player.sendMessage(`§eCinematic camera: §${newStatus ? 'aENABLED' : 'cDISABLED'}`);
        }
        else if (message === '!effect') {
            player.sendMessage('§eToggling screen invert effect...');
            player.getClient().send('rendering:toggle_invert', {});
        }
        else if (message === '!players') {
            const playerCount: number = api.getServer().getPlayerCount();
            player.sendMessage(`§eOnline players: ${playerCount}`);
        } else if (message.startsWith('!say ')) {
            const text: string = message.substring(5);
            api.getServer().broadcast(`§c[BROADCAST] §f${text}`);
        } else if (message === '!test') {
            player.getClient().send('test:notification', {
                type: 'success',
                message: 'Client-server communication working!',
                duration: 5000
            });
            player.sendMessage('§aTest event sent to client');
        }
    }
});

api.on('test:clientResponse', (data: any, player: Player) => {
    console.log(`Received client response from ${player.getName()}:`, data);
    player.sendMessage('§bClient response received!');
});

console.log('Moud TypeScript project loaded successfully');