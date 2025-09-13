interface Vector3 {
    readonly x: number;
    readonly y: number;
    readonly z: number;
}

interface Entity {
    getId(): number;
    getPosition(): Vector3;
    getDirection(): Vector3;
    teleport(x: number, y: number, z: number): void;
    lookAt(x: number, y: number, z: number): void;
    destroy(): void;
}

interface Player extends Entity {
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
    getWorld(): World;
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
    getBlock(x: number, y: number, z: number): string;
    setBlock(x: number, y: number, z: number, blockId: string): void;
    spawnScriptedEntity(entityType: string, x: number, y: number, z: number, jsInstance: any): Entity;
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

declare const Moud: ClientAPI;

interface ClientAPI {
    readonly network: NetworkService;
    readonly rendering: RenderingService;
    readonly ui: UIService;
    readonly console: MoudConsole;
    readonly camera: CameraService;
        readonly cursor: CursorService;
}

interface NetworkService {
    sendToServer(eventName: string, data: any): void;
    on(eventName: string, callback: (data: any) => void): void;
}

interface RenderingService {
    applyPostEffect(effectId: string): void;
    removePostEffect(effectId: string): void;
    on(eventName: 'beforeWorldRender', callback: (deltaTime: number) => void): void;
}

interface UIService {
    createElement(type: string): UIElement;
    createContainer(): UIContainer;
    createText(content: string): UIText;
    createButton(text: string): UIButton;
    createInput(placeholder: string): UIInput;
    createScreen(title: string): UIScreen;
    showScreen(screen: UIScreen): void;
}

interface UIElement {
    setId(id: string): void;
    getId(): string;
    setProperty(key: string, value: any): UIElement;
    getProperty(key: string): any;
    setText(text: string): UIElement;
    getText(): string;
    setPosition(x: number, y: number): UIElement;
    setPositionMode(mode: string): UIElement;
    setSize(width: number, height: number): UIElement;
    setBackgroundColor(color: string): UIElement;
    setTextColor(color: string): UIElement;
    setBorder(width: number, color: string): UIElement;
    setBorderRadius(radius: string): UIElement;
    setBoxShadow(shadow: string): UIElement;
    setFont(family: string, size: number, weight: string): UIElement;
    setTextAlign(align: string): UIElement;
    setPadding(top: number, right: number, bottom: number, left: number): UIElement;
    setMargin(top: number, right: number, bottom: number, left: number): UIElement;
    setOpacity(opacity: number): UIElement;
    onClick(callback: (...args: any[]) => void): UIElement;
    onHover(callback: (...args: any[]) => void): UIElement;
    onFocus(callback: (...args: any[]) => void): UIElement;
    onBlur(callback: (...args: any[]) => void): UIElement;
    appendChild(child: UIElement): UIElement;
    removeChild(child: UIElement): UIElement;
    show(): UIElement;
    hide(): UIElement;
    showAsOverlay(): UIElement;
    hideOverlay(): UIElement;
    isVisible(): boolean;
    animate(property: string, from: any, to: any, duration: number): UIElement;
    getX(): number;
    getY(): number;
    getWidth(): number;
    getHeight(): number;
}

interface CursorService {
    show(): void;
    hide(): void;
    toggle(): void;
    isVisible(): boolean;
}

interface UIContainer extends UIElement {
    setFlexDirection(direction: string): UIContainer;
    setJustifyContent(justify: string): UIContainer;
    setAlignItems(align: string): UIContainer;
    setFlexWrap(wrap: string): UIContainer;
    setGap(gap: number): UIContainer;
}

interface UIText extends UIElement {
}

interface UIButton extends UIElement {
}

interface UIInput extends UIElement {
    getValue(): string;
    setValue(value: string): UIInput;
    setFocused(focused: boolean): UIInput;
}

interface UIScreen {
    addElement(element: UIElement): UIScreen;
    removeElement(element: UIElement): UIScreen;
}

interface MoudConsole {
    log(...args: any[]): void;
    warn(...args: any[]): void;
    error(...args: any[]): void;
}

interface RenderTypeOptions {
    shader: string;
    textures?: string[];
    transparency?: "opaque" | "translucent" | "additive";
    cull?: boolean;
    lightmap?: boolean;
    depthTest?: boolean;
}

interface RenderingService {
    applyPostEffect(effectId: string): void;
    removePostEffect(effectId: string): void;
    createRenderType(options: RenderTypeOptions): string;
    setShaderUniform(shaderId: string, uniformName: string, value: number | boolean): void;
    on(eventName: 'beforeWorldRender', callback: (deltaTime: number) => void): void;
}

interface CameraService {
    enableCustomCamera(): void;
    disableCustomCamera(): void;
    isCustomCameraActive(): boolean;
    setRenderYawOverride(yaw: number): void;
    setRenderPitchOverride(pitch: number): void;
    clearRenderOverrides(): void;
    getRenderYawOverride(): number | null;
    getRenderPitchOverride(): number | null;
    getPitch(): number;
    setPitch(pitch: number): void;
    getYaw(): number;
    setYaw(yaw: number): void;
    getX(): number;
    getY(): number;
    getZ(): number;
    setPosition(x: number, y: number, z: number): void;
    addRotation(pitchDelta: number, yawDelta: number): void;
    getFov(): number;
    setFov(fov: number): void;
    isThirdPerson(): boolean;
    setThirdPerson(thirdPerson: boolean): void;
    lookAt(targetX: number, targetY: number, targetZ: number): void;
}

export {}