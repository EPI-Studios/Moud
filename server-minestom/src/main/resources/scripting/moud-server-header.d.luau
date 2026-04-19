--!strict
-- moud.d.luau — Luau declarations for the Moud scripting API.
-- The node type declarations at the bottom are auto-generated from the server's
-- NodeTypeRegistry. The rest is the stable, hand-curated API surface.

-- ============================================================================
-- SHARED TYPES
-- ============================================================================

export type Vector3 = { x: number, y: number, z: number }
export type Vector2 = { x: number, y: number }
export type Color   = { r: number, g: number, b: number, a: number }
export type Quaternion = { x: number, y: number, z: number, w: number }

export type InstanceEntry = {
    position: Vector3?,
    rotation: Quaternion?,
    scale:    Vector3?,
    color:    Color?,
}

declare class InstanceData
    function set(self: InstanceData, index: number, entry: InstanceEntry): ()
end

--- Enum-like table of known node types.
export type NodeType = string

export type Shape = "box" | "sphere" | "capsule"

-- ============================================================================
-- INPUT API
-- ============================================================================

declare class ScriptInputApi
    function isActionPressed(self: ScriptInputApi, action: string): boolean
    function isActionJustPressed(self: ScriptInputApi, action: string): boolean
    function isActionJustReleased(self: ScriptInputApi, action: string): boolean
    function getActionStrength(self: ScriptInputApi, action: string): number
    function getAxis(self: ScriptInputApi, negative: string, positive: string): number
    function getYaw(self: ScriptInputApi): number
    function getPitch(self: ScriptInputApi): number
    function getCursorX(self: ScriptInputApi): number
    function getCursorY(self: ScriptInputApi): number
    function getCursorPosition(self: ScriptInputApi): Vector2
    function getVector(self: ScriptInputApi, negX: string, posX: string, negY: string, posY: string): Vector2
end

-- ============================================================================
-- CONNECT / SIGNAL OPTIONS
-- ============================================================================

export type SignalConnectOpts = {
    signal:  string,
    target:  Node,
    handler: string,
}

export type TweenOpts = {
    property: string,
    to:       number,
    duration: number,
    onComplete: (() -> ())?,
}

export type CollisionEvent = { other: Node, contact: Vector3 }

export type CameraFollowOpts = {
    offset: Vector3?,
    pitch:  number?,
    roll:   number?,
}

export type CameraScriptableOpts = {
    position: Vector3?,
    yaw:      number?,
    pitch:    number?,
    roll:     number?,
}

-- ============================================================================
-- BASE NODE
-- ============================================================================

declare class Node
    name: string
    type: string

    -- Tree
    function find(self: Node, path: string): Node
    function getChildren(self: Node): {Node}
    function exists(self: Node): boolean
    function rename(self: Node, name: string): ()
    function reparent(self: Node, newParent: Node): ()
    function free(self: Node): ()
    function flush(self: Node): ()
    function createChild(self: Node, name: string, type: string): Node

    -- Signals
    function connect(self: Node, opts: SignalConnectOpts): ()
    function disconnect(self: Node, opts: SignalConnectOpts): ()
    function emit(self: Node, signal: string, a1: any?, a2: any?, a3: any?): ()

    -- Tweens
    function tween(self: Node, opts: TweenOpts): ()

    -- Generic property access
    function getProperty(self: Node, key: string): any
    function setProperty(self: Node, key: string, value: any): ()
    function removeProperty(self: Node, key: string): ()

    -- Physics
    function getBodyVelocity(self: Node): Vector3
    function applyForce(self: Node, force: Vector3): ()
    function applyImpulse(self: Node, force: Vector3): ()
    function setLinearVelocity(self: Node, velocity: Vector3): ()
    function getCollisionEvents(self: Node): {CollisionEvent}

    -- Camera (valid on Camera3D, ignored elsewhere)
    function follow(self: Node, opts: CameraFollowOpts): ()
    function scriptable(self: Node, opts: CameraScriptableOpts): ()
    function scene(self: Node): ()
    function reset(self: Node): ()

    -- Input
    function getInput(self: Node): ScriptInputApi?

    -- Rendering
    function setUniform(self: Node, name: string, ...: number): ()
    function setInstances(self: Node, data: InstanceData): ()
end

-- ============================================================================
-- PLAYER ATTACHMENT / CHARACTER BODY AUGMENTATIONS
-- (These fields extend the generated node types at runtime.)
-- ============================================================================

export type PlayerAttachmentApi = {
    --- Display name of the player this node represents.
    playerName: string,
    --- Teleports the player to the given anchor node every server tick.
    setAnchor: (self: any, anchorNode: Node) -> (),
    --- Clears any active anchor.
    clearAnchor: (self: any) -> (),
    --- Sets the player's velocity, overriding any current velocity.
    setVelocity: (self: any, vx: number, vy: number, vz: number) -> (),
    --- Adds to the player's current velocity (impulse).
    addVelocity: (self: any, vx: number, vy: number, vz: number) -> (),
    --- Returns the player's current velocity in blocks per second.
    getVelocity: (self: any) -> Vector3,
}

-- ============================================================================
-- MATH MODULE  (accessed via `moud.math`)
-- ============================================================================

declare class Vec3
    x: number
    y: number
    z: number
    function add(self: Vec3, v: Vector3): Vec3
    function sub(self: Vec3, v: Vector3): Vec3
    function scale(self: Vec3, s: number): Vec3
    function length(self: Vec3): number
    function normalize(self: Vec3): Vec3
end

export type MathModule = {
    Vec3:     (x: number?, y: number?, z: number?) -> Vec3,
    add:      (a: Vec3, b: Vec3) -> Vec3,
    sub:      (a: Vec3, b: Vec3) -> Vec3,
    dot:      (a: Vec3, b: Vec3) -> number,
    distance: (a: Vec3, b: Vec3) -> number,
    forward:  (yawDeg: number) -> Vec3,
    up:       () -> Vec3,
    right:    () -> Vec3,
    down:     () -> Vec3,

    lerp:  (a: number, b: number, t: number) -> number,
    clamp: (v: number, min: number, max: number) -> number,
    randf: (min: number, max: number) -> number,
    randi: (min: number, max: number) -> number,
}

-- ============================================================================
-- TIMERS MODULE
-- ============================================================================

export type TimersModule = {
    after: (seconds: number, callback: () -> ()) -> (() -> ()),
}

-- ============================================================================
-- PHYSICS MODULE
-- ============================================================================

export type RaycastHit = {
    point:    Vector3,
    normal:   Vector3,
    distance: number,
    bodyId:   Node,
}

export type PhysicsModule = {
    raycast: (origin: Vector3, direction: Vector3, maxDistance: number) -> RaycastHit?,
    overlapSphere: (center: Vector3, radius: number) -> {Node},
}

-- ============================================================================
-- PLAYERS MODULE
-- ============================================================================

declare class PlayerInfo
    uuid: string
    name: string
    x: number
    y: number
    z: number
    yaw: number
    function state(self: PlayerInfo, key: string): string
end

export type TeleportOpts = {
    uuid:     string,
    position: Vector3,
    yaw:      number?,
    pitch:    number?,
}

export type PlayersModule = {
    getPlayers: () -> {PlayerInfo},
    teleportPlayer: (opts: TeleportOpts) -> (),
}

-- ============================================================================
-- SCENE MODULE
-- ============================================================================

export type SceneModule = {
    loadScene: (sceneId: string) -> (),
    instantiate: (scenePath: string, parent: Node) -> Node,
    getRoot: () -> Node,
    findNodesByType: (type: string) -> {Node},
}

-- ============================================================================
-- INPUT ENUMS
-- ============================================================================

export type InputAction = "jump" | "sprint"
    | "move_left" | "move_right"
    | "move_forward" | "move_back"

-- ============================================================================
-- CURSOR MODULE
-- ============================================================================

export type CursorModule = {
    setCursorMode: (enabled: boolean) -> (),
    isCursorModeEnabled: () -> boolean,
    setOsCursorVisible: (visible: boolean) -> (),
    isOsCursorVisible: () -> boolean,
    getCursorPosition: () -> Vector2,
}

-- ============================================================================
-- DECORATORS / LIFECYCLE MARKERS
-- ============================================================================

declare function property(target: any, key: string): ()
declare function signal(name: string): any
declare function emits(): any
declare function process(): any
declare function ready(): any
declare function enterTree(): any
declare function exitTree(): any
declare function physicsProcess(): any
declare function input(): any
