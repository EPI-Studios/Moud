

const world: World = api.createWorld();
world.setFlatGenerator().setSpawn(0, 65, 0);

api.on('player.join', (player: Player) => {
    console.log(`Player joined: ${player.getName()}`);
    player.sendMessage('§6Welcome to the server!');

    const server: Server = api.getServer();
    server.broadcast(`§7${player.getName()} joined the server`);
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
        } else if (message === '!info') {
            player.sendMessage('§bThis server runs on moud with ts');
        }
    }
});

console.log('Project loaded successfully');