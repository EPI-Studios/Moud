const world = api.getWorld();
world.setFlatGenerator();
world.setSpawn(0, 65, 0);

const playerStates = new Map();
const activeLights = new Map();

function getPlayerLightId(player) {
    return parseInt(player.getUuid().substring(0, 8), 16);
}

function getPlayerSpotlightId(player) {
    return parseInt(player.getUuid().substring(8, 16), 16);
}

function removePlayerFlashlight(player) {
    const lightInfo = activeLights.get(player.getUuid());
    if (lightInfo) {
        api.getLighting().removeLight(lightInfo.mainLight);
        api.getLighting().removeLight(lightInfo.spotlight);
        activeLights.delete(player.getUuid());
    }
}

function createPlayerFlashlight(player) {
    removePlayerFlashlight(player);

    const mainLightId = getPlayerLightId(player);
    const spotlightId = getPlayerSpotlightId(player);
    const lightColor = new Vector3(1.0, 0.95, 0.8);

    api.getLighting().createAreaLight(mainLightId, player.getPosition(), player.getCameraDirection(), lightColor, 0, 0, 1.0);
    api.getLighting().createAreaLight(spotlightId, player.getPosition(), player.getCameraDirection(), lightColor, 0, 0, 1.0);

    activeLights.set(player.getUuid(), {
        mainLight: mainLightId,
        spotlight: spotlightId
    });
}

function updatePlayerFlashlight(player) {
    const state = playerStates.get(player.getUuid());
    const lightInfo = activeLights.get(player.getUuid());

    if (!state || !state.flashlightOn || !lightInfo) {
        return;
    }

    const playerPos = player.getPosition();
    const eyePos = new Vector3(playerPos.x, playerPos.y + 1.62, playerPos.z);
    const direction = player.getCameraDirection();

    api.getLighting().updateLight(lightInfo.mainLight, {
        x: eyePos.x, y: eyePos.y, z: eyePos.z,
        dirX: direction.x, dirY: direction.y, dirZ: direction.z,
        brightness: 5.0,
        distance: 40.0,
        width: 0.1,
        height: 0.1,
        angle: 0.4
    });

    api.getLighting().updateLight(lightInfo.spotlight, {
        x: eyePos.x, y: eyePos.y, z: eyePos.z,
        dirX: direction.x, dirY: direction.y, dirZ: direction.z,
        brightness: 1.0,
        distance: 15.0,
        width: 0.0,
        height: 0.0,
        angle: 1.5
    });
}

api.on('player.join', (player) => {
    playerStates.set(player.getUuid(), { flashlightOn: false });
});

api.on('player.leave', (player) => {
    removePlayerFlashlight(player);
    playerStates.delete(player.getUuid());
});

api.on('flashlight.toggle', (player) => {
    const state = playerStates.get(player.getUuid());
    if (!state) return;

    state.flashlightOn = !state.flashlightOn;

    if (state.flashlightOn) {
        player.sendMessage("Flashlight ON");
        createPlayerFlashlight(player);
    } else {
        player.sendMessage("Flashlight OFF");
        removePlayerFlashlight(player);
    }
});
api.on('player.chat', (chatEvent) => {
    const player = chatEvent.getPlayer();
    const message = chatEvent.getMessage().toLowerCase();

    if (message.startsWith('!wall')) {
        chatEvent.cancel();

        const WALL_WIDTH = 11;
        const WALL_HEIGHT = 5;
        const DISTANCE_FROM_PLAYER = 5;

        const pos = player.getPosition();
        const dir = player.getDirection();

        const right = new Vector3(-dir.z, 0, dir.x);

        const wallCenterBase = pos.add(dir.multiply(DISTANCE_FROM_PLAYER));

        const startPos = wallCenterBase.add(right.multiply(-(WALL_WIDTH - 1) / 2));

        player.sendMessage("Building a wall...");

        for (let y = 0; y < WALL_HEIGHT; y++) {
            for (let x = 0; x < WALL_WIDTH; x++) {
                const blockPos = startPos.add(right.multiply(x));
                api.getWorld().setBlock(
                    Math.floor(blockPos.x),
                    Math.floor(wallCenterBase.y) + y,
                    Math.floor(blockPos.z),
                    "minecraft:stone_bricks"
                );
            }
        }
    }
});

setInterval(() => {
    const players = api.getServer().getPlayers();
    for (const player of players) {
        updatePlayerFlashlight(player);
    }
}, 16);