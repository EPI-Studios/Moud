// --- SHARED TYPES ---

interface Player {
    getName(): string;
    getUuid(): string;
    sendMessage(message: string): void;
    kick(reason: string): void;
    isOnline(): boolean;
    getClient(): PlayerClient;
}

interface PlayerClient {
    send(eventName: string, data: any): void;
}

// --- SERVER-SIDE API ---

declare global {
    const api: MoudAPI;
    const assets: ServerAssets;
    const console: MoudConsole;
}

interface MoudAPI {
    on(eventName: 'player.join', callback: (player: Player) => void): void;
    on(eventName: 'player.chat', callback: (event: ChatEvent) => void): void;
    on(eventName: string, callback: (data: any, player: Player) => void): void;
    getServer(): Server;
    createWorld(): World;
}

interface ChatEvent {
    getPlayer(): Player;
    getMessage(): string;
    cancel(): void;
    isCancelled(): boolean;
}

interface Server {
    broadcast(message: string): void;
    getPlayerCount(): number;
    getPlayers(): Player[];
}

interface World {
    setFlatGenerator(): World;
    setVoidGenerator(): World;
    setSpawn(x: number, y: number, z: number): World;
}

interface ServerAssets {
    loadShader(path: string): ShaderAsset;
    loadTexture(path: string): TextureAsset;
    loadData(path: string): DataAsset;
}

interface ShaderAsset {
    getId(): string;
    getCode(): string;
}

interface TextureAsset {
    getId(): string;
    getData(): Uint8Array;
}

interface DataAsset {
    getId(): string;
    getContent(): string;
}

// --- CLIENT-SIDE API ---

declare const Moud: ClientAPI;

interface ClientAPI {
    readonly network: NetworkService;
    readonly rendering: RenderingService;
    readonly ui: UIService;
    readonly console: MoudConsole;
}

interface NetworkService {
    sendToServer(eventName: string, data: any): void;
    on(eventName: string, callback: (data: any) => void): void;
}

interface RenderingService {
    applyPostEffect(effectId: string): void;
    removePostEffect(effectId: string): void;
    on(eventName: string, callback: (deltaTime: number) => void): void;
}

interface UIService {
}

interface MoudConsole {
    log(...args: any[]): void;
    warn(...args: any[]): void;
    error(...args: any[]): void;
}

export {};