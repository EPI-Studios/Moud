declare class Blocks
    function get(self, at: Vector3): string
    function set(self, at: Vector3, block: string): ()
    function fill(self, from: Vector3, to: Vector3, block: string): number
    function raycast(self, from: Vector3, direction: Vector3, range: number?, options: { fluids: boolean? }?): (string?, Vector3?, number?, Vector3?)
    function isSolid(self, at: Vector3): boolean
    function isAir(self, at: Vector3): boolean
    function isFluid(self, at: Vector3): boolean
    function lightAt(self, at: Vector3): number
    function topAt(self, x: number, z: number): number
    function find(self, block: string, centre: Vector3, radius: number, limit: number?): { Vector3 }
    function count(self, block: string, from: Vector3, to: Vector3): number
    function replace(self, from: string, to: string, a: Vector3, b: Vector3): number
    function sphere(self, centre: Vector3, radius: number, block: string, hollow: boolean?): number
    function cylinder(self, base: Vector3, radius: number, height: number, block: string, hollow: boolean?): number
    function line(self, from: Vector3, to: Vector3, block: string): number
    function hollowBox(self, from: Vector3, to: Vector3, block: string): number
    function copy(self, from: Vector3, to: Vector3): BlockCopy
    function paste(self, copy: BlockCopy, at: Vector3, quarterTurns: number?, skipAir: boolean?): number
    changed: BlockChangedSignal
end

type BlockCopy = { size: Vector3, palette: { string }, blocks: { number } }

declare class BlockChangedSignal
    function connect(self, handler: (at: Vector3, block: string) -> ()): Connection
    function once(self, handler: (...any) -> ()): Connection
    function wait(self, timeout: number?): ...any
    function every(self, n: number, handler: (...any) -> ()): Connection
end

declare class Tags
    function tagged(self, tag: string): { Instance }
    function added(self, tag: string): InstanceSignal
    function removed(self, tag: string): InstanceSignal
end

declare class ChatCommandSignal
    function connect(self, handler: (body: Instance?, text: string, args: { string }) -> ()): Connection
    function once(self, handler: (...any) -> ()): Connection
    function wait(self, timeout: number?): ...any
    function every(self, n: number, handler: (...any) -> ()): Connection
end

type ChatMessage = { id: number, text: string, prefix: string, metadata: string, channel: Instance?, source: Instance?, body: Instance?, position: Vector3?, timestamp: number, status: string, [string]: any }
type ChatCheck = (message: ChatMessage, source: Instance) -> boolean

declare class ChatMessageSignal
    function connect(self, handler: (message: ChatMessage) -> ()): Connection
    function once(self, handler: (...any) -> ()): Connection
    function wait(self, timeout: number?): ...any
    function every(self, n: number, handler: (...any) -> ()): Connection
end

declare class ChatAnySignal
    function connect(self, handler: (...any) -> ()): Connection
    function once(self, handler: (...any) -> ()): Connection
    function wait(self, timeout: number?): ...any
    function every(self, n: number, handler: (...any) -> ()): Connection
end

declare class Chat
    function send(self, channelOrText: Instance | string, textOrOptions: (string | { [string]: any })?, options: { [string]: any }?): number
    function system(self, text: string, options: { [string]: any }?): number
    function edit(self, id: number, changes: string | { [string]: any }): ()
    function delete(self, id: number): ()
    function addPlayer(self, channel: Instance, body: Instance): ()
    function removePlayer(self, channel: Instance, body: Instance): ()
    function open(self, prefill: string?): ()
    function close(self): ()
    function isOpen(self): boolean
    function clear(self): ()
    function setTarget(self, channel: Instance?): ()
    function getTarget(self): Instance?
    function messages(self): { ChatMessage }
    function bubble(self, target: Instance, text: string, look: { [string]: any }?): ()
    onBubble: ((message: ChatMessage) -> (boolean | { [string]: any })?)?
    function escape(text: string): string
    function plain(text: string): string
    function bodyLink(body: Instance, label: string?): string
    function itemLink(id: string, count: number?): string
    function within(range: number): ChatCheck
    function all(...: ChatCheck): ChatCheck
    function any(...: ChatCheck): ChatCheck
    function distance(message: ChatMessage, source: Instance): number?
    function audible(range: number): ChatCheck
    function sameTag(tag: string): ChatCheck
    onIncoming: ((message: ChatMessage) -> { [string]: any }?)?
    shouldSend: ((message: ChatMessage) -> boolean?)?
    messageReceived: ChatMessageSignal
    sending: ChatMessageSignal
    edited: ChatMessageSignal
    deleted: ChatAnySignal
    linkClicked: ChatAnySignal
    bodyClicked: ChatAnySignal
    messageClicked: ChatMessageSignal
    opened: ChatAnySignal
    closed: ChatAnySignal
    typing: ChatAnySignal
end

declare class History
    function now(self): number
    function viewTime(self, body: Instance): number
    function rewind<T...>(self, time: number, query: () -> T...): T...
end

declare class ProximityWatcher
    entered: ChatAnySignal
    left: ChatAnySignal
    near: boolean
    function stop(self): ()
end

declare class Proximity
    function closestInteractable(self, body: Instance): (Instance?, number?)
    function watch(self, a: Instance, b: Instance, range: number): ProximityWatcher
    function falloff(distance: number, min: number, max: number, rolloff: number?): number
end

type PathOptions = { partial: boolean? }

declare class Path
    function find(self, from: Vector3, to: Vector3, options: PathOptions?): { Vector3 }?
    function isReachable(self, from: Vector3, to: Vector3, options: PathOptions?): boolean
    function randomPointNear(self, point: Vector3, radius: number, options: PathOptions?): Vector3?
end

declare class Debug
    function drawLine(self, from: Vector3, to: Vector3, color: Color?, seconds: number?): ()
    function drawRay(self, from: Vector3, direction: Vector3, color: Color?, seconds: number?): ()
    function drawBox(self, frame: CFrame, size: Vector3, color: Color?, seconds: number?): ()
    function drawSphere(self, centre: Vector3, radius: number, color: Color?, seconds: number?): ()
    function drawPoint(self, at: Vector3, color: Color?, seconds: number?): ()
    function label(self, at: Vector3, text: string, color: Color?, seconds: number?): ()
    function watch(self, name: string, value: any): ()
    function clear(self): ()
    function queryStats(self): { queries: number, partsTested: number }
    function profile(self): { [string]: { milliseconds: number, calls: number, worst: number } }
end

declare class Zones
    function at(self, position: Vector3): { Instance }
end

declare class Game
    world: Instance
    zones: Zones
    proximity: Proximity
    path: Path
    debug: Debug
    history: History
    players: Players
    tags: Tags
    chat: Chat
    blocks: Blocks
    stepped: StepSignal
    renderStepped: StepSignal
    reloaded: ReloadedSignal
    persist: { [string]: any }
end

declare class Input
    mouseX: number
    mouseY: number
    mouseDeltaX: number
    mouseDeltaY: number
    screenWidth: number
    screenHeight: number
    mouseLocked: boolean
    sensitivity: number
    function down(self, action: string): boolean
    function lockMouse(self): ()
    function releaseMouse(self): ()
end

declare game: Game

declare camera: Camera
declare input: Input

declare class Voice
    function stop(self): ()
    function setVolume(self, volume: number): ()
    function setPitch(self, pitch: number): ()
    function fade(self, volume: number, seconds: number): ()
    function fadeOut(self, seconds: number): ()
    function setPosition(self, at: Vector3): ()
    function isPlaying(self): boolean
end

declare class Music
    function setState(self, name: string, seconds: number?): ()
    function transitionTo(self, name: string, seconds: number?, quantize: string?): ()
    function setLayer(self, layer: number, volume: number): ()
    function blend(self, intensity: number): ()
    function stop(self, seconds: number?): ()
end

type PlayOptions = { volume: number?, pitch: number?, looped: boolean?, bus: string?,
    priority: number?, at: Vector3?, minDistance: number?, maxDistance: number?,
    rollOff: number?, fadeIn: number?, stream: boolean? }

declare audio: {
    play: (soundId: string, options: PlayOptions?) -> Voice?,
    playEvent: (name: string, at: Vector3?) -> Voice?,
    stinger: (name: string, quantize: string?) -> Voice?,
    defineEvent: (name: string, event: { sounds: { string }, bus: string?, volume: any?,
        pitch: any?, looped: boolean? }) -> (),
    tempo: (bpm: number, beatsPerBar: number?) -> (),
    stopTempo: () -> (),
    beats: () -> number,
    beat: StepSignal,
    bar: StepSignal,
    setParameter: (name: string, value: number) -> (),
    getParameter: (name: string) -> number,
    bindBusVolume: (parameter: string, bus: string, curve: { { number } }) -> (),
    setSwitch: (group: string, value: string) -> (),
    getSwitch: (group: string) -> string?,
    lfo: (parameter: string, shape: string, hertz: number, min: number, max: number) -> (),
    snapshot: (volumes: { [string]: number }, seconds: number?) -> (),
    clearSnapshot: (seconds: number?) -> (),
    sidechain: (sourceBus: string, targetBus: string, amount: number) -> (),
    reverb: (decaySeconds: number, wet: number) -> (),
    hrtf: (enabled: boolean) -> (),
    occlusion: (enabled: boolean) -> (),
    music: (music: { layers: { string }, states: { [string]: { number } }?, bus: string? }) -> Music,
    voiceChat: (settings: { enabled: boolean?, strength: number?, filters: { any }? }) -> (),
    voiceCount: () -> number,
}

declare function require(path: string): any
declare script: Instance

declare class StoreSession
    data: { [string]: any }
    function save(self): ()
    function release(self): ()
end

declare class Store
    function get(self, key: any): any
    function set(self, key: any, value: any): ()
    function remove(self, key: any): ()
    function update(self, key: any, change: (old: any) -> any): any
    function keys(self, prefix: string?, limit: number?): { string }
    function session(self, key: any): StoreSession
end

declare function store(name: string): Store
declare scene: {
    load: (path: string, parent: Instance?) -> { Instance },
    save: (instances: any, path: string) -> (),
    encode: (instances: any) -> string,
    decode: (text: string, parent: Instance?) -> { Instance },
}
declare function clock(): number

declare cooldown: {
    ready: (self: any, key: any, name: string, seconds: number) -> boolean,
    remaining: (self: any, key: any, name: string) -> number,
    reset: (self: any, key: any, name: string) -> (),
}

declare quat: {
    identity: Quat,
    axisAngle: (axis: Vector3, radians: number) -> Quat,
    euler: (x: number, y: number, z: number) -> Quat,
    lookAt: (forward: Vector3, up: Vector3?) -> Quat,
    fromTo: (from: Vector3, to: Vector3) -> Quat,
}

declare angle: {
    wrap: (a: number) -> number,
    delta: (a: number, b: number) -> number,
    lerp: (a: number, b: number, t: number) -> number,
}

declare random: {
    range: (min: number, max: number) -> number,
    int: (min: number, max: number) -> number,
    chance: (probability: number) -> boolean,
    pick: <T>(list: { T }) -> T?,
    shuffle: <T>(list: { T }) -> { T },
    unit: () -> Vector3,
    onSphere: (radius: number) -> Vector3,
    inSphere: (radius: number) -> Vector3,
    inCircle: (radius: number) -> Vector3,
    inBox: (size: Vector3) -> Vector3,
}

declare function vec3(x: number, y: number, z: number): Vector3
declare function color(r: number, g: number, b: number, a: number?): Color

declare udim2: {
    fromScale: (x: number, y: number) -> UDim2,
    fromOffset: (x: number, y: number) -> UDim2,
} & ((xScale: number, xOffset: number, yScale: number, yOffset: number) -> UDim2)

declare cframe: {
    identity: CFrame,
    angles: (pitch: number, yaw: number, roll: number) -> CFrame,
    lookAt: (from: Vector3, to: Vector3) -> CFrame,
} & ((x: number, y: number, z: number) -> CFrame)
  & ((position: Vector3) -> CFrame)
  & ((from: Vector3, to: Vector3) -> CFrame)

declare class MixinCall
    returning: boolean
    cancelled: boolean
    function cancel(self, result: any?): ()
    function setReturn(self, value: any): ()
    function getReturn(self): any
    function getArg(self, index: number): any
    function setArg(self, index: number, value: any): ()
end

declare class Mixin
    methods: number
    function remove(self): ()
end

declare mixin: {
    inject: (className: string, method: string, at: "head" | "return",
        handler: (self: any, info: MixinCall, ...any) -> (), options: { descriptor: string?, params: number? }?) -> Mixin,
}

declare java: {
    class: (name: string) -> any,
    typeof: (value: any) -> string?,
    instanceOf: (value: any, className: string) -> boolean,
}

declare task: {
    wait: (seconds: number?) -> number,
    spawn: (handler: () -> ()) -> number,
    delay: (seconds: number, handler: () -> ()) -> number,
    cancel: (handle: number) -> (),
    every: (seconds: number, handler: () -> ()) -> { cancel: (self: any) -> () },
    after: (seconds: number, handler: () -> ()) -> { cancel: (self: any) -> () },
    debounce: <A...>(handler: (A...) -> (), seconds: number) -> (A...) -> (),
    throttle: <A..., R...>(handler: (A...) -> R..., seconds: number) -> (A...) -> R...,
}
