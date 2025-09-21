// Set up a flat world with a spawn point at (0, 64, 0)
api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

// Fired when a player joins the server
api.on('player.join', (player) => {
    console.log(`Player ${player.getName()} has joined. Locking camera and setting up cursor.`);

    // Lock the player's camera to a fixed position for a better view.
    // We create a new Vector3 object for the camera's position.
    try {

        player.sendMessage("§eYour camera has been locked.");
    } catch (e) {
        console.error("Failed to lock camera:", e);
        player.sendMessage("§cCould not lock your camera.");
    }

    // A short delay to ensure the client is ready to handle cursor updates
    setTimeout(() => {
        try {
            const cursor = player.getCursor();

            // Make the cursor visible and set its initial size
            cursor.setVisible(true);
            cursor.setScale(0.8); // Set a smaller initial size for the cursor

            player.sendMessage("§aYour cursor is now active!");
            console.log(`Successfully set up cursor for ${player.getName()}`);

        } catch (e) {
            console.error("Error during cursor setup:", e);
            player.sendMessage("§cAn error occurred while setting up your cursor: " + e.message);
        }
    }, 1000); // 1-second delay
});

// Fired when a player clicks
api.on('player.click', (player, data) => {
    try {
        const cursor = player.getCursor();
        const pos = cursor.getPosition();
        const isHittingBlock = cursor.isHittingBlock();

        player.sendMessage(`§bClicked at: X=${pos.x.toFixed(2)}, Y=${pos.y.toFixed(2)}, Z=${pos.z.toFixed(2)} | Hitting Block: ${isHittingBlock}`);

        // --- Cursor Animation on Click ---
        const originalScale = 0.8;
        const shrinkScale = 0.4;
        const animationDuration = 100; // in milliseconds

        // 1. Immediately shrink the cursor
        cursor.setScale(shrinkScale);

        // 2. After a short delay, return it to its original size
        setTimeout(() => {
            try {
                const currentCursor = player.getCursor();
                if (currentCursor) {
                    currentCursor.setScale(originalScale);
                }
            } catch (e) {
                // Ignore errors here, as the player might have disconnected.
            }
        }, animationDuration);

    } catch (e) {
        console.error("Error in player.click event handler:", e);
        player.sendMessage("§cAn error occurred during click: " + e.message);
    }
});