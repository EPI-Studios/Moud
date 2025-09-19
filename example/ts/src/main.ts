api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

const VectorMath = {
    cross: (a, b) => new Vector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x),
    normalize: (vec) => {
        const length = Math.sqrt(vec.x * vec.x + vec.y * vec.y + vec.z * vec.z);
        return length === 0 ? new Vector3(0, 0, 0) : new Vector3(vec.x / length, vec.y / length, vec.z / length);
    }
};

const CURSOR_PLANE_DISTANCE = 10;
const MOUSE_SENSITIVITY = 0.02;
const MOVEMENT_THRESHOLD = 0.01;
const STABILIZATION_SAMPLES = 3;
const playerState = new Map();
let isExperienceActive = false;

api.on('player.join', (player) => {
    try {
        console.log(`[DEBUG] First player joined: ${player.getName()}. Initializing custom camera experience.`);

        const playerPos = player.getPosition();
        const playerYaw = player.getYaw();
        const camPos = new Vector3(playerPos.x, playerPos.y + 2, playerPos.z);

        player.setVanished(true);
        player.getCamera().lock(camPos, { yaw: playerYaw, pitch: 0 });
        console.log('[DEBUG] Camera locked.');

        const yawRad = (playerYaw * Math.PI) / 180;
        const cameraDirection = VectorMath.normalize(new Vector3(Math.sin(yawRad), 0, -Math.cos(yawRad)));
        const worldUp = new Vector3(0, 1, 0);
        const screenRight = VectorMath.normalize(VectorMath.cross(worldUp, cameraDirection));

        const initialCursorPos = camPos.add(cameraDirection.multiply(CURSOR_PLANE_DISTANCE));

        const cursor = api.getWorld().createText({
            content: '+',
            position: initialCursorPos,
            billboard: 'fixed'
        });

        if (!cursor) throw new Error("api.getWorld().createText() returned null!");

        playerState.set(player.getUuid(), {
            cursor: cursor,
            cursorWorldPos: initialCursorPos,
            cameraVectors: { up: worldUp, right: screenRight },
            lastMovementTime: 0,
            deltaHistory: [],
            isStabilized: true
        });
        console.log('[DEBUG] Player state has been set successfully.');

    } catch (e) {
        console.error(`[CRITICAL ERROR] Failed to initialize player ${player.getName()}:`, e);
        player.sendMessage(`§cScript Error: Could not initialize custom cursor.`);
        isExperienceActive = false;
    }
});

api.on('player.mousemove', (player, data) => {
    const state = playerState.get(player.getUuid());
    if (!state) return;

    const deltaX = data.deltaX;
    const deltaY = data.deltaY;
    const currentTime = Date.now();

    const deltaHistoryEntry = { deltaX, deltaY, time: currentTime };
    state.deltaHistory.push(deltaHistoryEntry);

    if (state.deltaHistory.length > STABILIZATION_SAMPLES) {
        state.deltaHistory.shift();
    }

    if (Math.abs(deltaX) < MOVEMENT_THRESHOLD && Math.abs(deltaY) < MOVEMENT_THRESHOLD) {
        if (!state.isStabilized) {
            console.log('[CURSOR DEBUG] Movement stabilized, ignoring micro-deltas');
            state.isStabilized = true;
        }
        return;
    }

    if (currentTime - state.lastMovementTime < 16) {
        return;
    }

    const avgDeltaX = state.deltaHistory.reduce((sum, entry) => sum + entry.deltaX, 0) / state.deltaHistory.length;
    const avgDeltaY = state.deltaHistory.reduce((sum, entry) => sum + entry.deltaY, 0) / state.deltaHistory.length;

    if (Math.abs(avgDeltaX) < MOVEMENT_THRESHOLD && Math.abs(avgDeltaY) < MOVEMENT_THRESHOLD) {
        return;
    }

    state.isStabilized = false;
    state.lastMovementTime = currentTime;

    const moveRight = state.cameraVectors.right.multiply(deltaX * MOUSE_SENSITIVITY);
    const moveUp = new Vector3(0, deltaY * MOUSE_SENSITIVITY, 0);

    state.cursorWorldPos = state.cursorWorldPos.add(moveRight).add(moveUp);
    state.cursor.setPosition(state.cursorWorldPos);

    const entityPos = state.cursor.getPosition();
    console.log(`[CURSOR DEBUG] Delta: (${deltaX.toFixed(2)}, ${deltaY.toFixed(2)}) | Calculated: X=${state.cursorWorldPos.x.toFixed(2)}, Y=${state.cursorWorldPos.y.toFixed(2)}, Z=${state.cursorWorldPos.z.toFixed(2)} | Entity: X=${entityPos.x.toFixed(2)}, Y=${entityPos.y.toFixed(2)}, Z=${entityPos.z.toFixed(2)}`);

    setTimeout(() => {
        const delayedEntityPos = state.cursor.getPosition();
        console.log(`[CURSOR DELAYED] Entity after 50ms: X=${delayedEntityPos.x.toFixed(2)}, Y=${delayedEntityPos.y.toFixed(2)}, Z=${delayedEntityPos.z.toFixed(2)}`);
    }, 50);
});

api.on('player.chat', (event) => {
    const player = event.getPlayer();
    const message = event.getMessage();

    console.log(`[${player.getName()}]: ${message}`);

    if (message.toLowerCase() === '/spawn') {
        event.cancel();
        player.teleport(0, 64, 0);
        player.sendMessage('Teleported to spawn!');
    }

    if (message.toLowerCase() === '!tp') {
        event.cancel();
        const state = playerState.get(player.getUuid());
        console.log(`[DEBUG] Player ${player.getName()} used !tp command`);
        console.log(`[DEBUG] Player state exists: ${!!state}`);
        if (state) {
            console.log(`[DEBUG] Cursor world pos exists: ${!!state.cursorWorldPos}`);
            console.log(`[DEBUG] Current cursor position: X=${state.cursorWorldPos?.x}, Y=${state.cursorWorldPos?.y}, Z=${state.cursorWorldPos?.z}`);
        }

        if (state && state.cursorWorldPos) {
            player.teleport(state.cursorWorldPos.x, state.cursorWorldPos.y, state.cursorWorldPos.z);
            player.sendMessage(`Teleported to cursor position: ${state.cursorWorldPos.x.toFixed(2)}, ${state.cursorWorldPos.y.toFixed(2)}, ${state.cursorWorldPos.z.toFixed(2)}`);
        } else {
            player.sendMessage('No cursor position found!');
        }
    }
});

api.on('player.leave', (player) => {
    console.log(`${player.getName()} has left. Cleaning up their resources.`);
    const state = playerState.get(player.getUuid());
    if (state) {
        if (state.cursor) state.cursor.remove();
        playerState.delete(player.getUuid());
    }
    isExperienceActive = false;
});