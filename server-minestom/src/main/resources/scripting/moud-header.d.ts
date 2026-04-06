// moud.d.ts — TypeScript declarations for the Moud scripting API.
// The node type declarations at the bottom are auto-generated from the server's
// NodeTypeRegistry. The rest is the stable, hand-curated API surface.

// =============================================================================
// CORE MODULE  import { Node3D, process, property } from "moud"
// =============================================================================
declare module "moud" {

  // --- Shared types ---
  export interface Vector3 { x: number; y: number; z: number; }
  export interface Vector2 { x: number; y: number; }
  export interface Color   { r: number; g: number; b: number; a: number; }
  export interface Quaternion { x: number; y: number; z: number; w: number; }

  export interface InstanceEntry {
    position?: Vector3;
    rotation?: Quaternion;
    scale?:    Vector3;
    color?:    Color;
  }

  export class InstanceData {
    constructor(count: number);
    set(index: number, entry: InstanceEntry): void;
  }

  // --- Enums ---
  export enum NodeType {
    Node = "Node", Node3D = "Node3D", Node2D = "Node2D",
    RigidBody3D = "RigidBody3D", StaticBody3D = "StaticBody3D",
    CharacterBody3D = "CharacterBody3D", Area3D = "Area3D",
    Camera3D = "Camera3D", MeshInstance3D = "MeshInstance3D",
    MultiMeshInstance3D = "MultiMeshInstance3D", Sprite3D = "Sprite3D",
    Decal = "Decal", AudioPlayer3D = "AudioPlayer3D",
    AudioPlayer2D = "AudioPlayer2D", OmniLight3D = "OmniLight3D",
    DirectionalLight3D = "DirectionalLight3D", SpotLight3D = "SpotLight3D",
    Marker3D = "Marker3D", Raycast3D = "Raycast3D", PlayerStart = "PlayerStart",
    CSGBlock = "CSGBlock", CSGBox = "CSGBox",
    SceneInstance3D = "SceneInstance3D", WorldEnvironment = "WorldEnvironment",
    CanvasLayer = "CanvasLayer", CanvasItem = "CanvasItem", Control = "Control",
    Label = "Label", RichTextLabel = "RichTextLabel", Button = "Button",
    TextureButton = "TextureButton", CheckBox = "CheckBox",
    HSlider = "HSlider", VSlider = "VSlider", LineEdit = "LineEdit",
    TextureRect = "TextureRect", ColorRect = "ColorRect",
    ProgressBar = "ProgressBar", HBoxContainer = "HBoxContainer",
    VBoxContainer = "VBoxContainer", GridContainer = "GridContainer",
    MarginContainer = "MarginContainer", ScrollContainer = "ScrollContainer",
    PanelContainer = "PanelContainer"
  }

  export enum Shape { Box = "box", Sphere = "sphere", Capsule = "capsule" }

  // --- Decorators ---
  export function property(target: object, key: string): void;
  export function signal(name: string): MethodDecorator;
  export function emits<T extends Record<string, any[]>>(): ClassDecorator;
  export function process():       MethodDecorator;
  export function ready():         MethodDecorator;
  export function enterTree():     MethodDecorator;
  export function exitTree():      MethodDecorator;
  export function physicsProcess():MethodDecorator;
  export function input():         MethodDecorator;

  // --- Base Node class ---
  export class Node {
    readonly name: string;
    readonly type: string;

    // Tree
    find<T extends Node = Node>(path: string): T;
    getChildren<T extends Node = Node>(): T[];
    exists(): boolean;
    rename(name: string): void;
    reparent(newParent: Node): void;
    free(): void;
    flush(): void;
    createChild<T extends Node = Node>(name: string, type: string): T;

    // Signals
    connect(opts: { signal: string; target: Node; handler: string }): void;
    disconnect(opts: { signal: string; target: Node; handler: string }): void;
    emit(signal: string, arg1?: unknown, arg2?: unknown, arg3?: unknown): void;

    // Tweens
    tween(opts: { property: string; to: number; duration: number; onComplete?: () => void }): void;

    // Generic property access (use when you have an untyped ref)
    getProperty<T = string>(key: string): T;
    setProperty(key: string, value: unknown): void;
    removeProperty(key: string): void;

    // Physics
    getBodyVelocity(): Vector3;
    applyForce(force: Vector3): void;
    applyImpulse(force: Vector3): void;
    setLinearVelocity(velocity: Vector3): void;
    getCollisionEvents(): Array<{ other: Node; contact: Vector3 }>;

    // Camera (valid on Camera3D, ignored otherwise)
    follow(opts: { offset?: Vector3; pitch?: number; roll?: number }): void;
    scriptable(opts: { position?: Vector3; yaw?: number; pitch?: number; roll?: number }): void;
    scene(): void;
    reset(): void;

    // Input
    getInput(): ScriptInputApi | null;

    // Rendering
    setUniform(name: string, ...values: number[]): void;
    setInstances(data: InstanceData): void;
  }

  // --- Input API ---
  export interface ScriptInputApi {
    isActionPressed(action: string): boolean;
    isActionJustPressed(action: string): boolean;
    isActionJustReleased(action: string): boolean;
    getActionStrength(action: string): number;
    getAxis(negative: string, positive: string): number;
    getYaw(): number;
    getPitch(): number;
    getCursorX(): number;
    getCursorY(): number;
    getCursorPosition(): Vector2;
    getVector(axes: { negX: string; posX: string; negY: string; posY: string }): Vector2;
  }
}

// =============================================================================
// MATH  import { Vec3, lerp, clamp, randf, randi } from "moud/math"
// =============================================================================
declare module "moud/math" {
  export class Vec3 {
    x: number; y: number; z: number;
    constructor(x?: number, y?: number, z?: number);
    add(v: { x: number; y: number; z: number }): Vec3;
    sub(v: { x: number; y: number; z: number }): Vec3;
    scale(s: number): Vec3;
    length(): number;
    normalize(): Vec3;
    static add(a: Vec3, b: Vec3): Vec3;
    static sub(a: Vec3, b: Vec3): Vec3;
    static dot(a: Vec3, b: Vec3): number;
    static distance(a: Vec3, b: Vec3): number;
    static forward(yawDeg: number): Vec3;
    static up(): Vec3;
    static right(): Vec3;
    static down(): Vec3;
  }
  export function lerp(a: number, b: number, t: number): number;
  export function clamp(v: number, min: number, max: number): number;
  export function randf(min: number, max: number): number;
  export function randi(min: number, max: number): number;
}

// =============================================================================
// TIMERS  import { after } from "moud/timers"
// =============================================================================
declare module "moud/timers" {
  export function after(seconds: number, callback: () => void): () => void;
}

// =============================================================================
// PHYSICS  import { raycast, overlapSphere } from "moud/physics"
// =============================================================================
declare module "moud/physics" {
  import { Node } from "moud";
  export interface RaycastHit {
    point:    { x: number; y: number; z: number };
    normal:   { x: number; y: number; z: number };
    distance: number;
    bodyId:   Node;
  }
  export function raycast(
    origin:    { x: number; y: number; z: number },
    direction: { x: number; y: number; z: number },
    maxDistance: number
  ): RaycastHit | null;
  export function overlapSphere(
    center: { x: number; y: number; z: number },
    radius: number
  ): Node[];
}

// =============================================================================
// PLAYERS  import { getPlayers, teleportPlayer } from "moud/players"
// =============================================================================
declare module "moud/players" {
  export interface PlayerInfo {
    uuid: string;
    name: string;
    x: number; y: number; z: number;
    yaw: number;
  }
  export function getPlayers(): PlayerInfo[];
  export function teleportPlayer(opts: {
    uuid:      string;
    position:  { x: number; y: number; z: number };
    yaw?:      number;
    pitch?:    number;
  }): void;
}

// =============================================================================
// SCENE  import { loadScene, instantiate, getRoot, findNodesByType } from "moud/scene"
// =============================================================================
declare module "moud/scene" {
  import { Node, NodeType } from "moud";
  export function loadScene(sceneId: string): void;
  export function instantiate<T extends Node = Node>(
    scenePath: string,
    parent:    Node
  ): T;
  export function getRoot(): Node;
  export function findNodesByType<T extends Node = Node>(type: NodeType | string): T[];
}

// =============================================================================
// PLAYER ATTACHMENT AUGMENTATION — runtime helpers for player-bound nodes
// =============================================================================
declare module "moud" {
  export interface PlayerAttachment {
    /** Display name of the player this node represents (runtime-created nodes). */
    readonly playerName: string;
    /**
     * Teleports the player to the given anchor node every server tick,
     * effectively mounting the player to that position.
     */
    setAnchor(anchorNode: Node): void;
    /** Clears any active anchor, letting the player move freely. */
    clearAnchor(): void;
    /**
     * Sets the player's velocity, overriding any current velocity.
     * Units are blocks per second - positive Y is up.
     */
    setVelocity(vx: number, vy: number, vz: number): void;
    /**
     * Adds to the player's current velocity (impulse).
     * Units are blocks per second - positive Y is up.
     */
    addVelocity(vx: number, vy: number, vz: number): void;
    /** Returns the player's current velocity in blocks per second. */
    getVelocity(): Vector3;
  }
}

// =============================================================================
// INPUT ENUMS  import { InputAction } from "moud/input"
// =============================================================================
declare module "moud/input" {
  export enum InputAction {
    Jump         = "jump",
    Sprint       = "sprint",
    MoveLeft     = "move_left",
    MoveRight    = "move_right",
    MoveForward  = "move_forward",
    MoveBack     = "move_back"
  }
}

// =============================================================================
// CURSOR  import { setCursorMode, isCursorModeEnabled, setOsCursorVisible, isOsCursorVisible, getCursorPosition } from "moud/cursor"
// =============================================================================
declare module "moud/cursor" {
  import { Vector2 } from "moud";
  export function setCursorMode(enabled: boolean): void;
  export function isCursorModeEnabled(): boolean;
  export function setOsCursorVisible(visible: boolean): void;
  export function isOsCursorVisible(): boolean;
  export function getCursorPosition(): Vector2;
}
