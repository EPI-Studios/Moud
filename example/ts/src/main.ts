class Guardian {
    private self: Entity;
    private target: Player | null = null;
    private speed: number = 0.05;

    onSpawn(entity: Entity) {
        this.self = entity;
        console.log(`Guardian spawned with ID: ${this.self.getId()}`);
    }

    onTick() {
        if (!this.target || !this.target.isOnline()) {
            return;
        }

        const targetPos = this.target.getPosition();
        const myPos = this.self.getPosition();
        const dirX = targetPos.x - myPos.x;
        const dirY = targetPos.y - myPos.y;
        const dirZ = targetPos.z - myPos.z;
        const distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);

        if (distance < 2.0) {
            this.self.lookAt(targetPos.x, targetPos.y + 1.6, targetPos.z);
            return;
        }

        const newX = myPos.x + (dirX / distance) * this.speed;
        const newY = myPos.y + (dirY / distance) * this.speed;
        const newZ = myPos.z + (dirZ / distance) * this.speed;

        this.self.teleport(newX, newY, newZ);
        this.self.lookAt(targetPos.x, targetPos.y + 1.6, targetPos.z);
    }

    setTarget(player: Player) {
        this.target = player;
        console.log(`Guardian's new target: ${player.getName()}`);
    }
}

console.log('Moud Guardian Example loaded successfully.');

const world = api.getWorld();
world.setFlatGenerator();
let guardianInstance: Guardian | null = null;
const playerSmoothCameraStatus = new Map<string, boolean>();

api.on('player.join', (player: Player) => {
    player.sendMessage('§6Welcome! Type §e!spawn§6 to create a guardian.');
    playerSmoothCameraStatus.set(player.getUuid(), false);
});

api.on('player.chat', (event) => {
    const player = event.getPlayer();
    const message = event.getMessage();

    if (message === '!spawn') {
        event.cancel();
        if (guardianInstance) {
            player.sendMessage('§cA guardian already exists. Type §e!targetme§c.');
            return;
        }

        const pos = player.getPosition();
        const logic = new Guardian();
        const entityProxy = world.spawnScriptedEntity("minecraft:iron_golem", pos.x + 2, pos.y, pos.z, logic);
        logic.onSpawn(entityProxy);
        guardianInstance = logic;
        api.getServer().broadcast('§aA Guardian has been spawned!');
    }

    if (message === '!targetme') {
        event.cancel();
        if (guardianInstance) {
            guardianInstance.setTarget(player);
            player.sendMessage('§aThe Guardian is now following you.');
        } else {
            player.sendMessage('§cNo guardian exists. Type §e!spawn§c first.');
        }
    }

    if (message === '!smooth_camera') {
        event.cancel();
        const currentStatus = playerSmoothCameraStatus.get(player.getUuid()) || false;
        const newStatus = !currentStatus;
        playerSmoothCameraStatus.set(player.getUuid(), newStatus);

        player.getClient().send('camera:toggle_smooth', { enabled: newStatus });
        player.sendMessage(`§eCinematic camera: §${newStatus ? 'aENABLED' : 'cDISABLED'}`);
    }
});