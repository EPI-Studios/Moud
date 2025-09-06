const world: World = api.createWorld();
world.setFlatGenerator().setSpawn(0, 65, 0);

api.on('player.join', (player: Player) => {
    console.log(`Player joined: ${player.getName()}`);
    player.sendMessage('§6Welcome to the Moud TypeScript server!');

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

        if (message === '!players') {
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
        } else if (message === '!effect') {
            player.getClient().send('render:applyEffect', {
                effect: 'damage_overlay',
                intensity: 0.5,
                duration: 2000
            });
            player.sendMessage('§cApplied damage effect');
        }
    }
});

api.on('test:clientResponse', (data: any, player: Player) => {
    console.log(`Received client response from ${player.getName()}:`, data);
    player.sendMessage('§bClient response received!');
});

console.log('Moud TypeScript project loaded successfully');