api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

console.log("Moud server script loaded!");

api.on("player.join", (player) => {
    console.log(`${player.getName()} has joined the server.`);

    const cursor = player.getCursor();

    cursor.setVisible(true);
    cursor.setVisibleToAll();
    cursor.setTexture("moud:textures/gui/custom_cursor.png");
    cursor.setColor(1.0, 1.0, 1.0);
    cursor.setScale(0.5);

    player.sendMessage("Your custom cursor has been set!");
});

api.on("player.leave", (player) => {
    console.log(`${player.getName()} has left the server.`);
});

api.on("player.click", (player, button) => {
    const cursor = player.getCursor();

    // Scale animation: grow then shrink back
    cursor.setScale(parseFloat(0.8)); // Grow larger

    setTimeout(() => {
        cursor.setScale(parseFloat(0.5)); // Return to normal
    }, 150);

    console.log(`${player.getName()} clicked with button ${button}`);
});

// Alternative version with more dramatic animation
api.on("player.rightClick", (player) => {
    const cursor = player.getCursor();

    // Double pulse animation
    cursor.setScale(parseFloat(0.9));

    setTimeout(() => {
        cursor.setScale(parseFloat(0.3));
    }, 100);

    setTimeout(() => {
        cursor.setScale(parseFloat(0.7));
    }, 200);

    setTimeout(() => {
        cursor.setScale(parseFloat(0.5));
    }, 300);
});