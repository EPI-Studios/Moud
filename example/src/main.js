console.log('loading');

const world = api.createWorld();
world.setFlatGenerator().setSpawn(0, 65, 0);

api.on('player.join', function(player) {
    console.log('Player joined:', player.getName());
    player.sendMessage('Welcome !');

    api.getServer().broadcast(player.getName() + ' joined the server');
});

api.on('player.chat', function(chatEvent) {
    const player = chatEvent.getPlayer();
    const message = chatEvent.getMessage();
    
    if (message.startsWith('!')) {
        chatEvent.cancel();
        
        if (message === '!players') {
            player.sendMessage('Online: ' + api.getServer().getPlayerCount());
        } else if (message.startsWith('!say ')) {
            const text = message.substring(5);
            api.getServer().broadcast('[SERVER] ' + text);
        }
    }
});

console.log('Moud project loaded successfully');