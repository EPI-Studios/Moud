/**
 * Moud Engine - Server-Side API Definitions
**/

// --- Core & Global Types ---

/**
 * Represents a 3D vector with x, y, and z components.
 * This class can be instantiated in scripts.
 */
interface Vector3 {
    readonly x: number;
    readonly y: number;
    readonly z: number;
    add(other: Vector3): Vector3;
}

declare global {
    /**
     * The main entry point to all server-side Moud APIs.
     */
    const api: MoudAPI;

    /**
     * A secure console API for logging messages to the server console.
     */
    const console: {
        log(...args: any[]): void;
        warn(...args: any[]): void;
        error(...args: any[]): void;
        debug(...args: any[]): void;
    };

    /**
     * The constructor for the Vector3 class, allowing instantiation in scripts.
     * @example const position = new Vector3(10, 20, 30);
     */
    const Vector3: {
        new(x: number, y: number, z: number): Vector3;
    };
}


// --- Main API Interfaces ---

/**
 * The root interface for all server-side Moud APIs.
 */
interface MoudAPI {
    /**
     * Registers a callback function to be executed when a specific game event occurs.
     * @param eventName The name of the event (e.g., 'player.join', 'player.chat').
     * @param callback The function to execute when the event is triggered.
     */
    on(eventName: 'player.join', callback: (player: Player) => void): void;
    on(eventName: 'player.leave', callback: (player: Player) => void): void;
    on(eventName: 'player.chat', callback: (event: ChatEvent) => void): void;
    on(eventName: string, callback: (...args: any[]) => void): void;

    /**
     * Accesses the server-wide API for managing players and broadcasting messages.
     * @returns The Server API proxy.
     */
    getServer(): Server;

    /**
     * Accesses the main world/instance API for block manipulation and entity spawning.
     * @returns The World API proxy.
     */
    getWorld(): World;

    /**
     * Accesses the lighting API for creating and managing dynamic lights.
     * @returns The Lighting API proxy.
     */
    getLighting(): LightingAPI;

    /**
     * Accesses the asynchronous task manager for running long-running, non-blocking operations.
     * @returns The AsyncManager API proxy.
     */
    getAsync(): AsyncManager;
}

/**
 * API for managing server-level operations.
 */
interface Server {
    /**
     * Sends a message to every online player.
     * @param message The message to broadcast.
     */
    broadcast(message: string): void;

    /**
     * Gets the current number of online players.
     * @returns The number of players.
     */
    getPlayerCount(): number;

    /**
     * Gets an array of all currently online players.
     * @returns An array of Player objects.
     */
    getPlayers(): Player[];
}

/**
 * API for managing the main game world.
 */
interface World {
    /**
     * Sets the world generator to a flat grass plain.
     * @returns The World object for chaining.
     */
    setFlatGenerator(): World;

    /**
     * Sets the world generator to a completely empty void.
     * @returns The World object for chaining.
     */
    setVoidGenerator(): World;

    /**
     * Sets the default spawn point for new players.
     * @returns The World object for chaining.
     */
    setSpawn(x: number, y: number, z: number): World;

    /**
     * Gets the namespaced ID of a block at a specific coordinate.
     * @returns The block ID (e.g., 'minecraft:stone').
     */
    getBlock(x: number, y: number, z: number): string;

    /**
     * Places a block at a specific coordinate.
     * @param blockId The namespaced ID of the block to place.
     */
    setBlock(x: number, y: number, z: number, blockId: string): void;

    /**
     * Spawns a new entity in the world that is controlled by a script object.
     * @param entityType The namespaced ID of the entity (e.g., 'minecraft:zombie').
     * @param jsInstance The script object with an `onTick` method to control the entity.
     */
    spawnScriptedEntity(entityType: string, x: number, y: number, z: number, jsInstance: any): any;
}

/**
 * API for creating and managing dynamic lights in the world.
 */
interface LightingAPI {
    /**
     * Creates a new point light (emits light in all directions).
     * @param lightId A unique numeric ID for this light.
     */
    createPointLight(lightId: number, position: Vector3, color: Vector3, radius: number, brightness: number): void;

    /**
     * Creates a new area light (a directional spotlight).
     * @param lightId A unique numeric ID for this light.
     */
    createAreaLight(lightId: number, position: Vector3, direction: Vector3, color: Vector3, width: number, height: number, brightness: number): void;

    /**
     * Updates the properties of an existing light.
     * @param lightId The ID of the light to update.
     * @param properties An object containing the properties to change (e.g., { x: 10, y: 20, z: 30 }).
     */
    updateLight(lightId: number, properties: { [key: string]: any }): void;

    /**
     * Removes a light from the world.
     * @param lightId The ID of the light to remove.
     */
    removeLight(lightId: number): void;
}

/**
 * API for running tasks on a separate thread to avoid lagging the server.
 */
interface AsyncManager {
    /**
     * Submits a task to be executed on a worker thread.
     * @param task A function containing the long-running computation.
     * @returns A Promise that resolves with the return value of the task.
     */
    submit(task: () => any): Promise<any>;

    /**
     * Schedules a task to be run on the main server thread on the next tick.
     * Useful for applying results from an async task (e.g., setting blocks).
     * @param task The function to execute on the main thread.
     */
    runOnServerThread(task: () => void): void;
}


// --- Entity & Event Interfaces ---

/**
 * Represents a player in the game.
 */
interface Player {
    getName(): string;
    getUuid(): string;
    sendMessage(message: string): void;
    kick(reason: string): void;
    isOnline(): boolean;
    getClient(): PlayerClient;
    getPosition(): Vector3;
    getDirection(): Vector3;
    getCameraDirection(): Vector3;
    teleport(x: number, y: number, z: number): void;
    getShared(): SharedValueApiProxy;
}

/**
 * Represents the client-side of a player connection, for sending client-only events.
 */
interface PlayerClient {
    /**
     * Sends a custom event to this specific player's client script.
     * @param eventName The name of the event to trigger on the client.
     * @param data The data payload for the event.
     */
    send(eventName: string, data: any): void;
}

/**
 * Represents a player chat event.
 */
interface ChatEvent {
    getPlayer(): Player;
    getMessage(): string;
    cancel(): void;
    isCancelled(): boolean;
}


// --- Shared Value Synchronization ---

/**
 * Entry point to the Shared Values API for a specific player.
 */
interface SharedValueApiProxy {
    /**
     * Gets or creates a named data store for this player.
     * @param storeName A unique name for the store (e.g., 'inventory', 'playerStats').
     * @returns The SharedStore proxy.
     */
    getStore(storeName: string): SharedStoreProxy;
}

/**
 * A key-value data store that can be synchronized between the server and a client.
 */
interface SharedStoreProxy {
    /**
     * Sets a value in the store. This will be synchronized to the client.
     * @param key The key for the data.
     * @param value The value to store. Must be serializable (numbers, strings, booleans, nested objects/arrays).
     * @param syncMode 'batched' (default) groups changes, 'immediate' sends instantly.
     * @param permission 'hybrid' (default) allows client to request changes, 'server-only' is read-only for client.
     */
    set(key: string, value: any, syncMode?: 'batched' | 'immediate', permission?: 'hybrid' | 'server-only' | 'client-readonly'): void;

    /**
     * Gets a value from the store.
     * @param key The key of the data to retrieve.
     */
    get(key: string): any;

    /**
     * Checks if a key exists in the store.
     */
    has(key: string): boolean;

    /**
     * Removes a key-value pair from the store.
     */
    remove(key: string): void;

    /**
     * Registers a callback that fires when any value in this store changes.
     * @param event Must be 'change'.
     * @param callback Function to execute.
     */
    on(event: 'change', callback: (key: string, newValue: any, oldValue: any) => void): void;

    /**
     * Registers a callback that fires only when a specific key's value changes.
     * @param key The specific key to watch.
     * @param callback Function to execute.
     */
    onChange(key: string, callback: (newValue: any, oldValue: any) => void): void;
}

export {}