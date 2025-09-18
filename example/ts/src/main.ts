api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);


const CAMERA_POSITION = new Vector3(0, 66, 10);
const CAMERA_ROTATION = { yaw: 180, pitch: 10 };
const CURSOR_DISTANCE = 15;

const playerStates = new Map();
const testNpcs = []; // Liste séparée pour les PNJ créés avec !npc

function cleanupDiorama(player) {
    const state = playerStates.get(player.getUuid());
    if (state) {
        console.log(`Nettoyage du diorama pour ${player.getName()}`);
        state.cursor?.remove();
        state.models?.forEach(model => model.remove());
        playerStates.delete(player.getUuid());
    }
}

function startDiorama(player) {
    cleanupDiorama(player);
    console.log(`Démarrage du diorama pour ${player.getName()}`);

    player.getUi().hide({ hideHand: true, hideHotbar: true, hideExperience: true });
    player.getCamera().lock(CAMERA_POSITION, CAMERA_ROTATION);

    const cursor = api.getWorld().createText({
        position: new Vector3(0, 0, 0),
        content: "§e+",
        billboard: "center",
    });

    const models = [];
    const clickableModel = api.getWorld().createPlayerModel({
        position: new Vector3(2, 64, 0),
        skinUrl: "http://textures.minecraft.net/texture/62a74f0d30a0651a27f67d71952a2205565e3153dce1843bde64a5d3013824"
    });
    clickableModel.setRotation({ yaw: 135, pitch: 0 });
    clickableModel.onClick((clicker) => {
        clicker.sendMessage("§a[Modèle Cliquable] §fBravo ! Vous m'avez trouvé.");
    });
    models.push(clickableModel);

    const staticModel = api.getWorld().createPlayerModel({
        position: new Vector3(-2, 64, 0),
        skinUrl: "http://textures.minecraft.net/texture/443ddc331a1825838c11575231c6c59b2e65b162a4a37e19391d4e41a17961"
    });
    staticModel.setRotation({ yaw: -135, pitch: 0 });
    models.push(staticModel);

    playerStates.set(player.getUuid(), { cursor, models, isInDiorama: true });
    player.sendMessage("§eMode Diorama activé. Bougez votre souris et cliquez sur le bon personnage !");
}

function stopDiorama(player) {
    if (!player.isOnline()) return;
    const state = playerStates.get(player.getUuid());
    if (!state || !state.isInDiorama) {
        player.sendMessage("§cVous n'êtes pas en mode diorama.");
        return;
    }

    console.log(`Arrêt du diorama pour ${player.getName()}`);
    cleanupDiorama(player);
    player.getCamera().release();
    player.getUi().show();
    playerStates.set(player.getUuid(), { cursor: null, models: [], isInDiorama: false });
    player.sendMessage("§aMode Diorama désactivé.");
}

// --- GESTION DES ÉVÉNEMENTS ---

api.on('player.chat', (event) => {
    const message = event.getMessage();
    const player = event.getPlayer();

    if (message === '!start') {
        event.cancel();
        startDiorama(player);
        player.sendMessage("§aDiorama démarré !");
    } else if (message === '!stop') {
        event.cancel();
        stopDiorama(player);
    } else if (message === '!npc') {
        event.cancel();
        const playerPos = player.getPosition();
        const playerDir = player.getCameraDirection();
        const npcPosition = playerPos.add(playerDir.multiply(3)).add(new Vector3(0, 1, 0));
        const skinUrl = "http://textures.minecraft.net/texture/443ddc331a1825838c11575231c6c59b2e65b162a4a37e19391d4e41a17961";

        const staticModel = api.getWorld().createPlayerModel({
            position: npcPosition,
            skinUrl: skinUrl
        });

        // CORRECTION : Utilisation de getHeadRotation()
        const playerRotation = player.getHeadRotation();
        staticModel.setRotation({ yaw: playerRotation.yaw + 180, pitch: 0 });

        testNpcs.push(staticModel);
        player.sendMessage(`§ePNJ de test créé devant vous !`);
    } else if (message === '!clearnpcs') {
        event.cancel();
        testNpcs.forEach(model => model.remove());
        testNpcs.length = 0;
        player.sendMessage("§bTous les PNJ de test ont été supprimés.");
    }
});

// CORRECTION : Un seul gestionnaire pour mousemove
api.on('player.mousemove', (player) => {
    const state = playerStates.get(player.getUuid());
    if (!state?.isInDiorama || !state.cursor) return;

    const cameraDirection = player.getCameraDirection();
    const newCursorPos = CAMERA_POSITION.add(cameraDirection.multiply(CURSOR_DISTANCE));
    state.cursor.setPosition(newCursorPos);

    // Ce log fonctionnera maintenant après "!start"
    console.log(`Curseur déplacé : X=${newCursorPos.x.toFixed(2)}, Y=${newCursorPos.y.toFixed(2)}, Z=${newCursorPos.z.toFixed(2)}`);
});

api.on('player.click', (player) => {
    const state = playerStates.get(player.getUuid());
    if (!state?.isInDiorama) return;

    const clickIndicatorPos = state.cursor.getEntity().getPosition();
    const clickIndicator = api.getWorld().createText({
        position: clickIndicatorPos,
        content: "§c*",
        billboard: "center"
    });
    setTimeout(() => clickIndicator.remove(), 250);
});

api.on('player.join', (player) => {
    player.sendMessage("§bBienvenue ! Tapez §a!start §bpour commencer le diorama.");
    cleanupDiorama(player);
});

api.on('player.leave', (player) => {
    cleanupDiorama(player);
});

console.log("--- Script de Diorama Interactif Chargé (v3 - Corrigé) ---");