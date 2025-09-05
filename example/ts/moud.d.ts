declare global {
    const api: MoudAPI;
    const console: Console;
}

interface MoudAPI {
    on(eventName: 'player.join', callback: (player: Player) => void): void;
    on(eventName: 'player.chat', callback: (event: ChatEvent) => void): void;
    on(eventName: string, callback: (...args: any[]) => void): void;

    getServer(): Server;
    createWorld(): World;
}

interface Player {
    getName(): string;
    getUuid(): string;
    sendMessage(message: string): void;
    kick(reason: string): void;
    isOnline(): boolean;
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
    createInstance(): World;
    setFlatGenerator(): World;
    setVoidGenerator(): World;
    setSpawn(x: number, y: number, z: number): World;
}

interface Console {
    log(...args: any[]): void;
    warn(...args: any[]): void;
    error(...args: any[]): void;
}

export {};