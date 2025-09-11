api.on('player.join', (player) => {
    const stats = player.getShared().getStore('stats');
    const inventory = player.getShared().getStore('inventory');

    stats.set('health', 100, 'immediate', 'hybrid');
    stats.set('mana', 50, 'batched', 'hybrid');
    stats.set('level', 1, 'batched', 'server-only');
    stats.set('score', 0, 'immediate', 'hybrid');

    inventory.set('coins', 100, 'batched', 'hybrid');
    inventory.set('items', ['sword', 'potion'], 'batched', 'hybrid');

    stats.onChange('health', (newHealth, oldHealth) => {
        console.log(`${player.getName()} health changed: ${oldHealth} -> ${newHealth}`);
        if (newHealth <= 0) {
            player.sendMessage('You died!');
            stats.set('health', 100, 'immediate', 'hybrid');
        }
    });

    inventory.onChange('coins', (newCoins, oldCoins) => {
        console.log(`${player.getName()} coins: ${oldCoins} -> ${newCoins}`);
    });

    player.sendMessage(`Welcome! Health: ${stats.get('health')}, Coins: ${inventory.get('coins')}`);
});

api.on('player.chat', (event) => {
    const player = event.getPlayer();
    const message = event.getMessage();

    if (message.startsWith('!damage')) {
        const stats = player.getShared().getStore('stats');
        const currentHealth = stats.get('health');
        stats.set('health', currentHealth - 10, 'immediate', 'hybrid');
        player.sendMessage(`Took 10 damage! Health: ${currentHealth - 10}`);
        event.cancel();
    }

    if (message.startsWith('!coins')) {
        const inventory = player.getShared().getStore('inventory');
        const currentCoins = inventory.get('coins');
        inventory.set('coins', currentCoins + 50, 'batched', 'hybrid');
        player.sendMessage('Added 50 coins!');
        event.cancel();
    }

    if (message.startsWith('!levelup')) {
        const stats = player.getShared().getStore('stats');
        const currentLevel = stats.get('level');
        stats.set('level', currentLevel + 1, 'batched', 'server-only');
        player.sendMessage(`Level up! Now level ${currentLevel + 1}`);

        player.getClient().send('level_up_effect', {
            newLevel: currentLevel + 1,
            timestamp: Date.now()
        });

        event.cancel();
    }

    if (message.startsWith('!heal')) {
        const stats = player.getShared().getStore('stats');
        const currentHealth = stats.get('health');
        const newHealth = Math.min(100, currentHealth + 25);
        stats.set('health', newHealth, 'immediate', 'hybrid');
        player.sendMessage(`Healed! Health: ${newHealth}`);
        event.cancel();
    }

    if (message.startsWith('!status')) {
        const stats = player.getShared().getStore('stats');
        const inventory = player.getShared().getStore('inventory');

        player.sendMessage(`Health: ${stats.get('health')}, Mana: ${stats.get('mana')}, Level: ${stats.get('level')}, Coins: ${inventory.get('coins')}`);
        event.cancel();
    }
});