# Reference

Written out of the class registry rather than by hand, so it cannot describe a property
that does not exist. Regenerate it by running `DumpTypes` in `script` and converting the
file it writes.

A plain public field on a class is a replicated property; a public final signal is an
event; a public final callback is a function a place assigns. Nothing is declared twice,
which is why this page, the editor's type file, the replication codec and the debug overlay
all say the same thing.

<!-- demo:reference-classes -->

## Vector3

| Property | Type |
|---|---|
| `x` | `number` |
| `y` | `number` |
| `z` | `number` |
| `magnitude` | `number` |
| `unit` | `Vector3` |

Methods:

- `__add(other: Vector3): Vector3`
- `__sub(other: Vector3): Vector3`
- `__mul(scale: number): Vector3`
- `__unm(): Vector3`
- `distance(other: Vector3): number`
- `distanceSq(other: Vector3): number`
- `dot(other: Vector3): number`
- `cross(other: Vector3): Vector3`
- `lerp(other: Vector3, t: number): Vector3`
- `angleTo(other: Vector3): number`
- `flat(): Vector3`
- `clampMagnitude(max: number): Vector3`
- `abs(): Vector3`
- `floor(): Vector3`

## UDim2

| Property | Type |
|---|---|
| `xScale` | `number` |
| `xOffset` | `number` |
| `yScale` | `number` |
| `yOffset` | `number` |

Methods:

- `__add(other: UDim2): UDim2`
- `__sub(other: UDim2): UDim2`

## Color

| Property | Type |
|---|---|
| `r` | `number` |
| `g` | `number` |
| `b` | `number` |
| `a` | `number` |

## Quat

| Property | Type |
|---|---|
| `x` | `number` |
| `y` | `number` |
| `z` | `number` |
| `w` | `number` |

Methods:

- `slerp(other: Quat, t: number): Quat`
- `inverse(): Quat`
- `rotate(v: Vector3): Vector3`
- `mul(other: Quat): Quat`

## CFrame

| Property | Type |
|---|---|
| `position` | `Vector3` |
| `rotation` | `Quat` |
| `lookVector` | `Vector3` |
| `rightVector` | `Vector3` |
| `upVector` | `Vector3` |

Methods:

- `__mul(other: CFrame): CFrame`
- `inverse(): CFrame`
- `lerp(other: CFrame, t: number): CFrame`
- `toObjectSpace(other: CFrame): CFrame`
- `toWorldSpace(other: CFrame): CFrame`
- `pointToObjectSpace(point: Vector3): Vector3`
- `pointToWorldSpace(point: Vector3): Vector3`
- `vectorToObjectSpace(v: Vector3): Vector3`
- `vectorToWorldSpace(v: Vector3): Vector3`
- `lookAt(target: Vector3): CFrame`

## Connection

Methods:

- `disconnect(): ()`

## ChangedSignal

Methods:

- `connect(handler: (property: string) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## InstanceSignal

Methods:

- `connect(handler: (instance: Instance) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## AttributeSignal

Methods:

- `connect(handler: (name: string) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## AncestrySignal

Methods:

- `connect(handler: (child: Instance, parent: Instance?) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## AnySignal

Methods:

- `connect(handler: (...any) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## NumberSignal

Methods:

- `connect(handler: (value: number) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## BoolSignal

Methods:

- `connect(handler: (active: boolean) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## StringSignal

Methods:

- `connect(handler: (value: string) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## StepSignal

Methods:

- `connect(handler: (delta: number) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## ReloadedSignal

Methods:

- `connect(handler: () -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## PlayerSignal

Methods:

- `connect(handler: (player: Player) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## ExplosionSignal

Methods:

- `connect(handler: (part: Instance, distance: number) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## PointerSignal

Methods:

- `connect(handler: (x: number, y: number) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## FocusLostSignal

Methods:

- `connect(handler: (enterPressed: boolean) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## Player

| Property | Type |
|---|---|
| `name` | `string` |
| `id` | `string` |
| `character` | `Instance?` |
| `controls` | `PlayerControls` |
| `team` | `Team?` (set on the server) |
| `leaderstats` | `Leaderstats?` |

Methods:

- `spawn(position: Vector3?): ()`
- `kick(message: string?): ()`
- `ban(reason: string?, seconds: number?): ()`
- `ping(): number`
- `viewTime(): number`

## Players

| Property | Type |
|---|---|
| `joined` | `PlayerSignal` |
| `leaving` | `PlayerSignal` |
| `spawned` | `PlayerSignal` |
| `autoSpawn` | `boolean` (server) |
| `respawnTime` | `number` (server) |
| `controls` | `PlayerControls` (client) |

Methods:

- `me(): Instance?`
- `list(): { Player }` (server)
- `byId(id: string): Player?` (server)
- `playerOf(body: Instance): Player?` (server)
- `all(): { Instance }`
- `near(position: Vector3, radius: number, except: Instance?): { Instance }`
- `nearest(position: Vector3, radius: number?, except: Instance?): (Instance?, number?)`
- `bodyOf(player: string): Instance?`
- `count(): number`
- `inBox(frame: CFrame, size: Vector3, except: Instance?): { Instance }`
- `inPart(part: Instance, except: Instance?): { Instance }`
- `inCone(position: Vector3, direction: Vector3, angle: number, range: number, except: Instance?): { Instance }`
- `visibleFrom(position: Vector3, range: number, except: Instance?): { Instance }`
- `withTag(tag: string): { Instance }`
- `random(except: Instance?): Instance?`
- `sortedByDistance(position: Vector3): { Instance }`
- `inRange(a: Instance, b: Instance, range: number): boolean`
- `fromName(name: string): Instance?`

## PlayerControls

| Property | Type |
|---|---|
| `move` | `boolean` |
| `jump` | `boolean` |
| `look` | `boolean` |

Methods:

- `enable(): ()`
- `disable(): ()`

## Tween

| Property | Type |
|---|---|
| `completed` | `StepSignal` |

Methods:

- `pause(): ()`
- `resume(): ()`
- `cancel(): ()`
- `state(): string`

## Instance

| Property | Type |
|---|---|
| `name` | `string` |
| `className` | `string` |
| `parent` | `Instance?` |
| `changed` | `ChangedSignal` |
| `childAdded` | `InstanceSignal` |
| `destroying` | `InstanceSignal` |
| `attributeChanged` | `AttributeSignal` |
| `ancestryChanged` | `AncestrySignal` |
| `descendantAdded` | `InstanceSignal` |
| `descendantRemoving` | `InstanceSignal` |

Methods:

- `add(className: string, properties: { [string]: any }?): Instance`
- `addAll(className: string, properties: { { [string]: any } }): number`
- `children(): { Instance }`
- `find(name: string): Instance?`
- `isA(className: string): boolean`
- `raycast(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?, string?)`
- `spherecast(from: Vector3, radius: number, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)`
- `blockcast(frame: CFrame, size: Vector3, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)`
- `partsInBox(frame: CFrame, size: Vector3, options: QueryOptions?): { Instance }`
- `partsInRadius(position: Vector3, radius: number, options: QueryOptions?): { Instance }`
- `partsInPart(part: Instance, options: QueryOptions?): { Instance }`
- `raycastAll(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): { RayHit }`
- `raycastMany(rays: { { any } }, options: QueryOptions?): { RayHit | false }`
- `nearestPart(position: Vector3, radius: number, options: QueryOptions?): (Instance?, number?)`
- `nearestTagged(position: Vector3, tag: string, radius: number?): (Instance?, number?)`
- `partsAlongRay(from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): { RayHit }`
- `capsulecast(from: Vector3, to: Vector3, radius: number, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)`
- `sweep(part: Instance, direction: Vector3, distance: number, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)`
- `partsAtPoint(position: Vector3, options: QueryOptions?): { Instance }`
- `boundsOf(instances: { Instance }): (CFrame?, Vector3?)`
- `groundAt(x: number, z: number, from: number?, options: QueryOptions?): (number?, Vector3?)`
- `surfaceNormal(position: Vector3, options: QueryOptions?): Vector3?`
- `heightmap(from: Vector3, to: Vector3, step: number?, options: QueryOptions?): { { number } }`
- `findFreeSpot(near: Vector3, size: Vector3, radius: number?): Vector3?`
- `findFirstDescendant(name: string): Instance?`
- `descendants(className: string?): { Instance }`
- `childrenOfClass(className: string): { Instance }`
- `firstAncestorOfClass(className: string): Instance?`
- `firstChildOfClass(className: string, recursive: boolean?): Instance?`
- `firstAncestor(name: string): Instance?`
- `isDescendantOf(other: Instance): boolean`
- `isAncestorOf(other: Instance): boolean`
- `getFullName(): string`
- `clearAllChildren(): ()`
- `byTag(tag: string): { Instance }`
- `values(): { [string]: any }`
- `clone(parent: Instance?): Instance?`
- `query(selector: string): { Instance }`
- `queryFirst(selector: string): Instance?`
- `waitForChild(name: string, timeout: number?): Instance?`
- `onChild(name: string, fn: (child: Instance) -> ()): Connection`
- `destroy(): ()`
- `setOwner(to: Instance?): ()`
- `addTag(tag: string): ()`
- `removeTag(tag: string): ()`
- `hasTag(tag: string): boolean`
- `getTags(): { string }`
- `setAttribute(name: string, value: any): ()`
- `getAttribute(name: string): any`
- `getAttributes(): { [string]: any }`
- `getAttributeChangedSignal(name: string): AnySignal`
- `getPropertyChangedSignal(property: string): AnySignal`
- `tween(goals: { [string]: any }, info: TweenInfo?): Tween`
- `fireServer(...: any): ()`
- `fireClient(to: Instance, ...: any): ()`
- `fireAllClients(...: any): ()`

<a id="folder"></a>

## Folder

Extends [Instance](#instance).

<a id="spatial"></a>

## Spatial

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `position` | `Vector3` |
| `rotation` | `Quat` |
| `worldCframe` | `CFrame` |
| `alwaysRelevant` | `boolean` |
| `cframe` | `CFrame` |
| `owner` | `string` |
| `pivot` | `Vector3` |
| `visible` | `boolean` |

Methods:

- `getPivot(): CFrame`
- `pivotTo(target: CFrame): ()`

<a id="part"></a>

## Part

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `touched` | `InstanceSignal` |
| `touchEnded` | `InstanceSignal` |
| `anchored` | `boolean` |
| `angularVelocity` | `Vector3` |
| `canQuery` | `boolean` |
| `canTouch` | `boolean` |
| `collides` | `boolean` |
| `collisionGroup` | `string` |
| `color` | `Color` |
| `density` | `number` |
| `elasticity` | `number` |
| `friction` | `number` |
| `locked` | `boolean` |
| `massless` | `boolean` |
| `networkOwner` | `string` |
| `shape` | `"block" \| "ball" \| "cylinder" \| "wedge" \| "cornerWedge"` |
| `size` | `Vector3` |
| `transparency` | `number` |
| `velocity` | `Vector3` |

Methods:

- `overlapping(options: QueryOptions?): { Instance }`
- `closestPoint(position: Vector3): Vector3`
- `contains(position: Vector3): boolean`
- `bounds(): (CFrame, Vector3)`
- `worldBounds(): (Vector3, Vector3)`
- `applyImpulse(impulse: Vector3): ()` (server)
- `applyImpulseAtPosition(impulse: Vector3, position: Vector3): ()` (server)
- `applyAngularImpulse(impulse: Vector3): ()` (server)
- `setVelocity(velocity: Vector3): ()` (server)
- `setAngularVelocity(velocity: Vector3): ()` (server)
- `getVelocityAtPosition(position: Vector3): Vector3` (server)
- `getMass(): number`
- `setNetworkOwner(player: Player?): ()` (server)
- `setNetworkOwnershipAuto(): ()` (server)
- `isNetworkOwnershipAuto(): boolean`
- `canSetNetworkOwnership(): (boolean, string?)`
- `getNetworkOwner(): Player?`

<a id="character"></a>

## Character

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `animate` | `boolean` |
| `attackLeft` | `boolean` |
| `attackTime` | `number` |
| `bedYaw` | `number` |
| `chargeProgress` | `number` |
| `collisionGroup` | `string` |
| `crawling` | `boolean` |
| `crouching` | `boolean` |
| `deathTime` | `number` |
| `flying` | `boolean` |
| `flyingTime` | `number` |
| `flyingYaw` | `number` |
| `frozen` | `boolean` |
| `height` | `number` |
| `hurt` | `boolean` |
| `inWater` | `boolean` |
| `leftArmPose` | `"empty" \| "item" \| "block" \| "bow" \| "trident" \| "crossbowCharge" \| "crossbowHold" \| "spyglass" \| "horn" \| "brush" \| "spear"` |
| `leftItem` | `string` |
| `lookPitch` | `number` |
| `lookYaw` | `number` |
| `mainLeft` | `boolean` |
| `moveDistance` | `number` |
| `moveSpeed` | `number` |
| `radius` | `number` |
| `riding` | `boolean` |
| `rightArmPose` | `"empty" \| "item" \| "block" \| "bow" \| "trident" \| "crossbowCharge" \| "crossbowHold" \| "spyglass" \| "horn" \| "brush" \| "spear"` |
| `rightItem` | `string` |
| `scale` | `number` |
| `sleeping` | `boolean` |
| `speedValue` | `number` |
| `spinning` | `boolean` |
| `swimAmount` | `number` |
| `team` | `Instance?` |
| `upsideDown` | `boolean` |
| `useLeftHand` | `boolean` |
| `usingItem` | `boolean` |
| `velocity` | `Vector3` |
| `whiteFlash` | `number` |

Methods:

- `distanceTo(other: Instance): number`
- `distanceSqTo(other: Instance): number`
- `canSee(other: Instance, range: number?): boolean`
- `isGrounded(): boolean`
- `isInWater(): boolean`
- `isMoving(): boolean`
- `applyImpulse(change: Vector3): ()`
- `setVelocity(velocity: Vector3): ()`
- `lookDirection(): Vector3`
- `facing(other: Instance, maxAngle: number?): boolean`
- `walkTo(point: Vector3, options: PathOptions?): boolean`
- `follow(target: Instance, distance: number?): ()`
- `stopWalking(): ()`
- `isWalking(): boolean`
- `jump(): ()`
- `lookAt(point: Vector3): ()`
- `face(direction: Vector3): ()`

<a id="camera"></a>

## Camera

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `distance` | `number` |
| `fov` | `number` |
| `mode` | `"firstPerson" \| "thirdPerson" \| "scriptable"` |
| `offset` | `Vector3` |
| `subject` | `Instance?` |

Methods:

- `shake(trauma: number): ()`
- `kick(pitch: number, yaw: number, roll: number, seconds: number): ()`
- `fovPunch(degrees: number, seconds: number): ()`
- `clearEffects(): ()`
- `worldToScreen(world: Vector3): Vector3?`
- `screenToRay(x: number, y: number): (Vector3, Vector3)`
- `play(path: CameraPath, info: CameraPlayInfo?): CameraPlayback`

`CameraPlayInfo` is `{ duration: number?, easing: string?, direction: string?, restore: boolean? }`.

## CameraPlayback

| Property | Type |
|---|---|
| `finished` | `AnySignal` |
| `progress` | `number` |
| `playing` | `boolean` |

Methods:

- `stop(): ()`

<a id="camerapath"></a>

## CameraPath

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `closed` | `boolean` |
| `direction` | `"in" \| "out" \| "inOut"` |
| `duration` | `number` |
| `easing` | `"linear" \| "sine" \| "quad" \| "cubic" \| "quart" \| "quint" \| "expo" \| "circ" \| "back" \| "elastic" \| "bounce"` |
| `faceAlong` | `boolean` |
| `lookAt` | `Instance?` |
| `looped` | `boolean` |

<a id="spawnlocation"></a>

## SpawnLocation

Extends [Part](#part).

| Property | Type |
|---|---|
| `allowTeamChangeOnTouch` | `boolean` |
| `enabled` | `boolean` |
| `neutral` | `boolean` |
| `teamColor` | `Color` |

<a id="seat"></a>

## Seat

Extends [Part](#part).

| Property | Type |
|---|---|
| `disabled` | `boolean` |
| `occupant` | `Instance?` (read-only) |

Methods:

- `sit(humanoid: Humanoid): boolean` (server)

<a id="vehicleseat"></a>

## VehicleSeat

Extends [Seat](#seat).

| Property | Type |
|---|---|
| `maxSpeed` | `number` |
| `steer` | `number` (read-only) |
| `throttle` | `number` (read-only) |
| `torque` | `number` |
| `turnSpeed` | `number` |

<a id="window"></a>

## Window

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `alwaysOnTop` | `boolean` |
| `camera` | `Instance?` |
| `clickThrough` | `boolean` |
| `clickable` | `Instance?` |
| `decorated` | `boolean` |
| `focused` | `boolean` |
| `gui` | `Instance?` |
| `height` | `number` |
| `mirror` | `boolean` |
| `opacity` | `number` |
| `resizable` | `boolean` |
| `title` | `string` |
| `transparent` | `boolean` |
| `visible` | `boolean` |
| `width` | `number` |
| `x` | `number` |
| `y` | `number` |
| `closing` | `InstanceSignal` |
| `moved` | `InstanceSignal` |
| `resized` | `InstanceSignal` |

Methods:

- `close(): ()`
- `preventClose(): ()`
- `focus(): ()`
- `moveTo(x: number, y: number): ()`
- `resize(width: number, height: number): ()`
- `center(): ()`

<a id="animator"></a>

## Animator

Extends [Instance](#instance).

Methods:

- `loadAnimation(animation: Instance): AnimationTrack`
- `getPlayingAnimationTracks(): { AnimationTrack }`

<a id="animationtrack"></a>

## AnimationTrack

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `stopped` | `InstanceSignal` |
| `ended` | `InstanceSignal` |
| `didLoop` | `InstanceSignal` |
| `keyframeReached` | `StringSignal` |
| `animation` | `Instance?` |
| `fadeTime` | `number` |
| `length` | `number` |
| `looped` | `boolean` |
| `playing` | `boolean` |
| `priority` | `number` |
| `speed` | `number` |
| `timePosition` | `number` |
| `weight` | `number` |

Methods:

- `play(fadeTime: number?, weight: number?, speed: number?): ()`
- `stop(fadeTime: number?): ()`
- `adjustSpeed(speed: number): ()`
- `adjustWeight(weight: number, fadeTime: number?): ()`
- `getMarkerReachedSignal(name: string): StringSignal`

`timePosition` and `length` are not replicated.

<a id="animation"></a>

## Animation

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `animationId` | `string` |

<a id="keyframesequence"></a>

## KeyframeSequence

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `looped` | `boolean` |
| `priority` | `number` |

<a id="keyframe"></a>

## Keyframe

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `time` | `number` |

<a id="pose"></a>

## Pose

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `cframe` | `CFrame` |
| `direction` | `"in" \| "out" \| "inOut"` |
| `easing` | `"linear" \| "sine" \| "quad" \| "cubic" \| "quart" \| "quint" \| "expo" \| "circ" \| "back" \| "elastic" \| "bounce"` |
| `weight` | `number` |

<a id="keyframemarker"></a>

## KeyframeMarker

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `value` | `string` |

<a id="appearance"></a>

## Appearance

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `display` | `"hitbox" \| "model" \| "hidden"` |
| `ears` | `boolean` |
| `firstPerson` | `"arm" \| "hand" \| "body" \| "none"` |
| `skin` | `string` |
| `slim` | `boolean` |

<a id="armour"></a>

## Armour

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `chest` | `string` |
| `feet` | `string` |
| `hat` | `string` |
| `hatLayered` | `boolean` |
| `head` | `string` |
| `legs` | `string` |

<a id="attachment"></a>

## Attachment

Extends [Spatial](#spatial).

<a id="limb"></a>

## Limb

Extends [Part](#part).

| Property | Type |
|---|---|
| `touched` | `InstanceSignal` |
| `touchEnded` | `InstanceSignal` |
| `cutout` | `boolean` |
| `mirrored` | `boolean` |
| `sheet` | `string` |
| `sheetHeight` | `number` |
| `sheetWidth` | `number` |
| `swing` | `number` |
| `swingPhase` | `number` |
| `swingSideways` | `boolean` |
| `texels` | `Vector3` |
| `u` | `number` |
| `v` | `number` |

<a id="meshpart"></a>

## MeshPart

Extends [Part](#part).

| Property | Type |
|---|---|
| `touched` | `InstanceSignal` |
| `touchEnded` | `InstanceSignal` |
| `animation` | `string` |
| `animationLooped` | `boolean` |
| `animationSpeed` | `number` |
| `meshId` | `string` |

<a id="posteffect"></a>

## PostEffect

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `enabled` | `boolean` |

<a id="screeneffect"></a>

## ScreenEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `intensity` | `number` |
| `order` | `number` |

<a id="bloomeffect"></a>

## BloomEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `intensity` | `number` |
| `knee` | `number` |
| `occlude` | `boolean` |
| `resolution` | `number` |
| `size` | `number` |
| `threshold` | `number` |

<a id="colorgradeeffect"></a>

## ColorGradeEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `brightness` | `number` |
| `contrast` | `number` |
| `exposure` | `number` |
| `gamma` | `number` |
| `lut` | `string` |
| `lutIntensity` | `number` |
| `lutSize` | `number` |
| `saturation` | `number` |
| `temperature` | `number` |
| `tint` | `number` |

<a id="ambientocclusioneffect"></a>

## AmbientOcclusionEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `bias` | `number` |
| `intensity` | `number` |
| `power` | `number` |
| `radius` | `number` |
| `resolution` | `number` |
| `temporal` | `boolean` |

<a id="globalilluminationeffect"></a>

## GlobalIlluminationEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `history` | `number` |
| `intensity` | `number` |
| `radius` | `number` |
| `resolution` | `number` |

<a id="reflectioneffect"></a>

## ReflectionEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `edgeFade` | `number` |
| `intensity` | `number` |
| `maxDistance` | `number` |
| `reflectivity` | `number` |
| `resolution` | `number` |
| `steps` | `number` |
| `temporal` | `boolean` |
| `thickness` | `number` |

<a id="antialiasingeffect"></a>

## AntiAliasingEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `clip` | `number` |
| `history` | `number` |
| `sharpness` | `number` |

<a id="volumetriceffect"></a>

## VolumetricEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `anisotropy` | `number` |
| `density` | `number` |
| `resolution` | `number` |
| `shadows` | `boolean` |
| `steps` | `number` |
| `strength` | `number` |

<a id="contactshadoweffect"></a>

## ContactShadowEffect

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `distance` | `number` |
| `steps` | `number` |
| `thickness` | `number` |

<a id="shadowquality"></a>

## ShadowQuality

Extends [PostEffect](#posteffect).

| Property | Type |
|---|---|
| `contactHardening` | `boolean` |
| `entities` | `boolean` |
| `lightSize` | `number` |
| `maxDistance` | `number` |
| `resolution` | `number` |
| `softness` | `number` |
| `sunCascades` | `number` |
| `sunDistance` | `number` |
| `sunResolution` | `number` |

<a id="vignetteeffect"></a>

## VignetteEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `color` | `Color` |
| `radius` | `number` |
| `softness` | `number` |

<a id="chromaticaberrationeffect"></a>

## ChromaticAberrationEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `amount` | `number` |

<a id="filmgraineffect"></a>

## FilmGrainEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `amount` | `number` |
| `size` | `number` |

<a id="blureffect"></a>

## BlurEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `size` | `number` |

<a id="depthoffieldeffect"></a>

## DepthOfFieldEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `falloff` | `number` |
| `focusDistance` | `number` |
| `focusRange` | `number` |
| `size` | `number` |

<a id="motionblureffect"></a>

## MotionBlurEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `samples` | `number` |
| `strength` | `number` |

<a id="pixelateeffect"></a>

## PixelateEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `pixelSize` | `number` |

<a id="posterizeeffect"></a>

## PosterizeEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `levels` | `number` |

<a id="sharpeneffect"></a>

## SharpenEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `amount` | `number` |

<a id="fogeffect"></a>

## FogEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `baseHeight` | `number` |
| `color` | `Color` |
| `density` | `number` |
| `heightFalloff` | `number` |
| `sky` | `boolean` |
| `start` | `number` |

<a id="outlineeffect"></a>

## OutlineEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `color` | `Color` |
| `thickness` | `number` |
| `threshold` | `number` |

<a id="tonemapeffect"></a>

## TonemapEffect

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `exposure` | `number` |
| `tonemapper` | `"aces" \| "agx" \| "reinhard" \| "uncharted" \| "none"` |

<a id="postshader"></a>

## PostShader

Extends [ScreenEffect](#screeneffect).

| Property | Type |
|---|---|
| `shader` | `string` |

<a id="zone"></a>

## Zone

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `entered` | `InstanceSignal` |
| `left` | `InstanceSignal` |
| `cooldown` | `number` |
| `enabled` | `boolean` |
| `priority` | `number` |
| `shape` | `"box" \| "sphere" \| "cylinder" \| "polygon"` |
| `size` | `Vector3` |
| `soundId` | `string` |
| `textChannel` | `Instance?` |
| `trackClass` | `string` |
| `trackPlayers` | `boolean` |
| `trackTag` | `string` |
| `volume` | `number` |

Methods:

- `players(): { Instance }`
- `occupants(): { Instance }`
- `contains(position: Vector3): boolean`

<a id="proximityprompt"></a>

## ProximityPrompt

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `shown` | `InstanceSignal` |
| `holdBegan` | `InstanceSignal` |
| `hidden` | `InstanceSignal` |
| `holdEnded` | `InstanceSignal` |
| `triggered` | `InstanceSignal` |
| `actionText` | `string` |
| `backgroundColor` | `Color` |
| `backgroundTransparency` | `number` |
| `enabled` | `boolean` |
| `holdDuration` | `number` |
| `keyColor` | `Color` |
| `keys` | `string` |
| `maxActivationDistance` | `number` |
| `objectText` | `string` |
| `offset` | `Vector3` |
| `requiresLineOfSight` | `boolean` |
| `textColor` | `Color` |

<a id="light"></a>

## Light

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `brightness` | `number` |
| `color` | `Color` |
| `enabled` | `boolean` |
| `falloff` | `"smooth" \| "linear" \| "inverseSquare" \| "exponent"` |
| `falloffExponent` | `number` |
| `godrays` | `number` |
| `range` | `number` |
| `shader` | `string` |
| `shadowStrength` | `number` |
| `shadows` | `boolean` |
| `temperature` | `number` |

<a id="pointlight"></a>

## PointLight

Extends [Light](#light).

<a id="spotlight"></a>

## SpotLight

Extends [Light](#light).

| Property | Type |
|---|---|
| `innerAngle` | `number` |
| `outerAngle` | `number` |

<a id="arealight"></a>

## AreaLight

Extends [Light](#light).

| Property | Type |
|---|---|
| `height` | `number` |
| `shape` | `"rectangle" \| "disc"` |
| `width` | `number` |

<a id="tubelight"></a>

## TubeLight

Extends [Light](#light).

| Property | Type |
|---|---|
| `length` | `number` |

<a id="lighting"></a>

## Lighting

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `ambient` | `Color` |
| `brightness` | `number` |
| `clockTime` | `number` |
| `environmentDiffuseScale` | `number` |
| `environmentSpecularScale` | `number` |
| `exposureCompensation` | `number` |
| `geographicLatitude` | `number` |
| `globalShadows` | `boolean` |
| `outdoorAmbient` | `Color` |
| `rain` | `number` |
| `shadowSoftness` | `number` |
| `thunder` | `number` |
| `timeScale` | `number` |

Methods:

- `getMinutesAfterMidnight(): number`
- `setMinutesAfterMidnight(minutes: number): ()`
- `getSunDirection(): Vector3`
- `getMoonDirection(): Vector3`

<a id="sky"></a>

## Sky

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `celestialBodiesShown` | `boolean` |
| `moonAngularSize` | `number` |
| `moonTextureId` | `string` |
| `skyboxBk` | `string` |
| `skyboxDn` | `string` |
| `skyboxFt` | `string` |
| `skyboxLf` | `string` |
| `skyboxOrientation` | `Vector3` |
| `skyboxRt` | `string` |
| `skyboxUp` | `string` |
| `starCount` | `number` |
| `sunAngularSize` | `number` |
| `sunTextureId` | `string` |

<a id="atmosphere"></a>

## Atmosphere

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `color` | `Color` |
| `decay` | `Color` |
| `density` | `number` |
| `glare` | `number` |
| `haze` | `number` |
| `offset` | `number` |

<a id="clouds"></a>

## Clouds

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `color` | `Color` |
| `cover` | `number` |
| `density` | `number` |
| `enabled` | `boolean` |

<a id="cape"></a>

## Cape

Extends [Limb](#limb).

| Property | Type |
|---|---|
| `touched` | `InstanceSignal` |
| `touchEnded` | `InstanceSignal` |
| `flap` | `number` |
| `lean` | `number` |
| `sway` | `number` |

<a id="humanoid"></a>

## Humanoid

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `died` | `InstanceSignal` |
| `healthChanged` | `InstanceSignal` |
| `stateChanged` | `InstanceSignal` |
| `arrived` | `InstanceSignal` |
| `running` | `NumberSignal` |
| `jumping` | `BoolSignal` |
| `freeFalling` | `BoolSignal` |
| `swimming` | `NumberSignal` |
| `climbing` | `NumberSignal` |
| `seated` | `BoolSignal` |
| `platformStanding` | `BoolSignal` |
| `damaged` | `NumberSignal` |
| `airAcceleration` | `number` |
| `airDrag` | `number` |
| `airSpeed` | `number` |
| `autoRotate` | `boolean` |
| `coyoteTime` | `number` |
| `fallDrag` | `number` |
| `floorMaterial` | `string` |
| `followSlopes` | `boolean` |
| `gravityScale` | `number` |
| `groundAcceleration` | `number` |
| `groundDeceleration` | `number` |
| `health` | `number` |
| `healthRegen` | `number` |
| `jump` | `boolean` |
| `jumpBuffer` | `number` |
| `jumpPower` | `number` |
| `maxHealth` | `number` |
| `moveDirection` | `Vector3` |
| `platformStand` | `boolean` |
| `sit` | `boolean` |
| `slideAcceleration` | `number` |
| `slopeLimit` | `number` |
| `sneakMultiplier` | `number` |
| `sprintMultiplier` | `number` |
| `state` | `"standing" \| "running" \| "jumping" \| "falling" \| "swimming" \| "flying" \| "seated" \| "climbing" \| "platformStanding" \| "dead"` |
| `stepHeight` | `number` |
| `walkRadius` | `number` |
| `walkSpeed` | `number` |
| `walkTo` | `Vector3` |
| `walking` | `boolean` |

Methods:

- `move(direction: Vector3, relativeToCamera: boolean?): ()` (server)
- `moveTo(point: Vector3): ()` (server)
- `takeDamage(amount: number): ()` (server)
- `changeState(state: "standing" | "running" | "jumping" | "falling" | "swimming" | "flying" | "seated" | "climbing" | "platformStanding" | "dead"): ()` (server)
- `getState(): "standing" | "running" | "jumping" | "falling" | "swimming" | "flying" | "seated" | "climbing" | "platformStanding" | "dead"`
- `setStateEnabled(state: "standing" | "running" | "jumping" | "falling" | "swimming" | "flying" | "seated" | "climbing" | "platformStanding" | "dead", enabled: boolean): ()` (server)
- `getStateEnabled(state: "standing" | "running" | "jumping" | "falling" | "swimming" | "flying" | "seated" | "climbing" | "platformStanding" | "dead"): boolean`
- `equipTool(tool: Tool): ()` (server)
- `unequipTools(): ()` (server)
- `applyDescription(description: { [string]: any }): ()` (server)
- `getAppliedDescription(): { [string]: any }`

<a id="wings"></a>

## Wings

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `skin` | `string` |
| `worn` | `boolean` |
| `x` | `number` |
| `y` | `number` |
| `z` | `number` |

<a id="value"></a>

## Value

Extends [Instance](#instance).

<a id="numbervalue"></a>

## NumberValue

Extends [Value](#value).

| Property | Type |
|---|---|
| `value` | `number` |

<a id="stringvalue"></a>

## StringValue

Extends [Value](#value).

| Property | Type |
|---|---|
| `value` | `string` |

<a id="boolvalue"></a>

## BoolValue

Extends [Value](#value).

| Property | Type |
|---|---|
| `value` | `boolean` |

<a id="vector3value"></a>

## Vector3Value

Extends [Value](#value).

| Property | Type |
|---|---|
| `value` | `Vector3` |

<a id="objectvalue"></a>

## ObjectValue

Extends [Value](#value).

| Property | Type |
|---|---|
| `value` | `Instance?` |

<a id="remote"></a>

## Remote

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `onServer` | `RemoteServerSignal` |
| `onServerPlayer` | `RemotePlayerSignal` |
| `onClient` | `InstanceSignal` |
| `accepts` | `string` |

<a id="unreliableremote"></a>

## UnreliableRemote

Extends [Remote](#remote).

| Property | Type |
|---|---|
| `onServer` | `InstanceSignal` |
| `onClient` | `InstanceSignal` |

<a id="remotefunction"></a>

## RemoteFunction

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `accepts` | `string` |
| `timeout` | `number` |
| `onServerInvoke` | `((player: Player, ...any) -> ...any)?` |
| `onClientInvoke` | `((...any) -> ...any)?` |

Methods:

- `invokeServer(...any): ...any` (client)
- `invokeClient(player: Player, ...any): ...any` (server)

<a id="screengui"></a>

## ScreenGui

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `displayOrder` | `number` |
| `enabled` | `boolean` |
| `font` | `string` |

<a id="billboardgui"></a>

## BillboardGui

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `adornee` | `Instance?` |
| `alwaysOnTop` | `boolean` |
| `enabled` | `boolean` |
| `font` | `string` |
| `maxDistance` | `number` |
| `offset` | `Vector3` |
| `pixelsPerMetre` | `number` |
| `size` | `UDim2` |

<a id="surfacegui"></a>

## SurfaceGui

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `adornee` | `Instance?` |
| `alwaysOnTop` | `boolean` |
| `enabled` | `boolean` |
| `face` | `"front" \| "back" \| "left" \| "right" \| "top" \| "bottom"` |
| `font` | `string` |
| `maxDistance` | `number` |
| `pixelsPerMetre` | `number` |

<a id="guiobject"></a>

## GuiObject

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `absolutePosition` | `Vector3` (read-only) |
| `absoluteSize` | `Vector3` (read-only) |
| `anchorX` | `number` |
| `anchorY` | `number` |
| `automaticSize` | `"none" \| "x" \| "y" \| "xy"` |
| `backgroundColor` | `Color` |
| `backgroundTransparency` | `number` |
| `borderColor` | `Color` |
| `borderSize` | `number` |
| `clipsDescendants` | `boolean` |
| `cornerRadius` | `number` |
| `layoutOrder` | `number` |
| `mouseButton1Down` | `PointerSignal` |
| `mouseButton1Up` | `PointerSignal` |
| `mouseButton2Click` | `PointerSignal` |
| `mouseEnter` | `InstanceSignal` |
| `mouseLeave` | `InstanceSignal` |
| `mouseMoved` | `PointerSignal` |
| `mouseWheelBackward` | `PointerSignal` |
| `mouseWheelForward` | `PointerSignal` |
| `position` | `UDim2` |
| `size` | `UDim2` |
| `visible` | `boolean` |
| `zIndex` | `number` |

<a id="frame"></a>

## Frame

Extends [GuiObject](#guiobject).

<a id="textlabel"></a>

## TextLabel

Extends [GuiObject](#guiobject).

| Property | Type |
|---|---|
| `font` | `string` |
| `richText` | `boolean` |
| `text` | `string` |
| `textBounds` | `Vector3` (read-only) |
| `textColor` | `Color` |
| `textScaled` | `boolean` |
| `textShadow` | `boolean` |
| `textSize` | `number` |
| `textTransparency` | `number` |
| `textWrapped` | `boolean` |
| `textXAlignment` | `"left" \| "center" \| "right"` |
| `textYAlignment` | `"top" \| "center" \| "bottom"` |

<a id="textbutton"></a>

## TextButton

Extends [TextLabel](#textlabel).

| Property | Type |
|---|---|
| `activated` | `InstanceSignal` |
| `autoButtonColor` | `boolean` |
| `hovered` | `boolean` |
| `pressed` | `boolean` |

<a id="textbox"></a>

## TextBox

Extends [TextLabel](#textlabel).

| Property | Type |
|---|---|
| `clearTextOnFocus` | `boolean` |
| `cursorPosition` | `number` |
| `focused` | `AnySignal` |
| `focusLost` | `FocusLostSignal` |
| `multiLine` | `boolean` |
| `placeholderColor` | `Color` |
| `placeholderText` | `string` |
| `selectionStart` | `number` |
| `textEditable` | `boolean` |

Methods:

- `captureFocus(): ()`
- `releaseFocus(): ()`
- `isFocused(): boolean`

<a id="imagelabel"></a>

## ImageLabel

Extends [GuiObject](#guiobject).

| Property | Type |
|---|---|
| `image` | `string` |
| `imageColor` | `Color` |
| `imageRectOffset` | `Vector3` |
| `imageRectSize` | `Vector3` |
| `imageTransparency` | `number` |
| `resampleMode` | `"default" \| "pixelated"` |
| `scaleType` | `"stretch" \| "fit" \| "crop" \| "tile" \| "slice"` |
| `sliceMax` | `Vector3` |
| `sliceMin` | `Vector3` |
| `sliceScale` | `number` |
| `tileSize` | `UDim2` |

<a id="imagebutton"></a>

## ImageButton

Extends [ImageLabel](#imagelabel).

| Property | Type |
|---|---|
| `activated` | `InstanceSignal` |
| `autoButtonColor` | `boolean` |
| `hovered` | `boolean` |
| `hoverImage` | `string` |
| `pressed` | `boolean` |
| `pressedImage` | `string` |

<a id="scrollingframe"></a>

## ScrollingFrame

Extends [GuiObject](#guiobject).

| Property | Type |
|---|---|
| `absoluteCanvasSize` | `Vector3` (read-only) |
| `absoluteWindowSize` | `Vector3` (read-only) |
| `automaticCanvasSize` | `"none" \| "x" \| "y" \| "xy"` |
| `canvasPosition` | `Vector3` |
| `canvasSize` | `UDim2` |
| `scrollBarImageColor` | `Color` |
| `scrollBarImageTransparency` | `number` |
| `scrollBarThickness` | `number` |
| `scrollingDirection` | `"x" \| "y" \| "xy"` |
| `scrollingEnabled` | `boolean` |

<a id="canvasgroup"></a>

## CanvasGroup

Extends [GuiObject](#guiobject).

| Property | Type |
|---|---|
| `groupColor` | `Color` |
| `groupTransparency` | `number` |

<a id="uicomponent"></a>

## UIComponent

Extends [Instance](#instance).

<a id="uilayout"></a>

## UILayout

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `fillDirection` | `"vertical" \| "horizontal"` |
| `horizontalAlignment` | `"left" \| "center" \| "right"` |
| `sortOrder` | `"layoutOrder" \| "name"` |
| `verticalAlignment` | `"top" \| "center" \| "bottom"` |

<a id="uilistlayout"></a>

## UIListLayout

Extends [UILayout](#uilayout).

| Property | Type |
|---|---|
| `padding` | `UDim2` |
| `wraps` | `boolean` |

<a id="uigridlayout"></a>

## UIGridLayout

Extends [UILayout](#uilayout).

| Property | Type |
|---|---|
| `cellPadding` | `UDim2` |
| `cellSize` | `UDim2` |
| `fillDirectionMaxCells` | `number` |
| `startCorner` | `"topLeft" \| "topRight" \| "bottomLeft" \| "bottomRight"` |

<a id="uipadding"></a>

## UIPadding

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `paddingBottom` | `UDim2` |
| `paddingLeft` | `UDim2` |
| `paddingRight` | `UDim2` |
| `paddingTop` | `UDim2` |

<a id="uiaspectratioconstraint"></a>

## UIAspectRatioConstraint

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `aspectRatio` | `number` |
| `aspectType` | `"fitWithinMaxSize" \| "scaleWithParentSize"` |
| `dominantAxis` | `"width" \| "height"` |

<a id="uisizeconstraint"></a>

## UISizeConstraint

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `maxSize` | `Vector3` |
| `minSize` | `Vector3` |

<a id="uiscale"></a>

## UIScale

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `scale` | `number` |

<a id="uistroke"></a>

## UIStroke

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `applyStrokeMode` | `"contextual" \| "border"` |
| `color` | `Color` |
| `enabled` | `boolean` |
| `thickness` | `number` |
| `transparency` | `number` |

<a id="uigradient"></a>

## UIGradient

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `enabled` | `boolean` |
| `endColor` | `Color` |
| `endTransparency` | `number` |
| `keypoints` | `string` |
| `offset` | `Vector3` |
| `rotation` | `number` |
| `startColor` | `Color` |
| `startTransparency` | `number` |

Methods:

- `setKeypoints(keypoints: { GradientKeypoint }): ()`
- `getKeypoints(): { GradientKeypoint }`

<a id="uicorner"></a>

## UICorner

Extends [UIComponent](#uicomponent).

| Property | Type |
|---|---|
| `cornerRadius` | `UDim2` |

<a id="sound"></a>

## Sound

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `ended` | `InstanceSignal` |
| `loaded` | `InstanceSignal` |
| `played` | `InstanceSignal` |
| `bus` | `string` |
| `event` | `string` |
| `fadeIn` | `number` |
| `isLoaded` | `boolean` (read-only) |
| `looped` | `boolean` |
| `maxDistance` | `number` |
| `minDistance` | `number` |
| `paused` | `boolean` |
| `pitch` | `number` |
| `playbackLoudness` | `number` (read-only) |
| `playing` | `boolean` |
| `plays` | `number` |
| `priority` | `number` |
| `rollOff` | `number` |
| `soundId` | `string` |
| `stream` | `boolean` |
| `timeLength` | `number` (read-only) |
| `timePosition` | `number` |
| `volume` | `number` |

Methods:

- `play(): ()`
- `stop(): ()`
- `pause(): ()`
- `resume(): ()`

<a id="soundbus"></a>

## SoundBus

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `bus` | `string` |
| `lowPass` | `number` |
| `muted` | `boolean` |
| `volume` | `number` |

<a id="soundeffect"></a>

## SoundEffect

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `enabled` | `boolean` |
| `priority` | `number` |

<a id="reverbsoundeffect"></a>

## ReverbSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `decayTime` | `number` |
| `density` | `number` |
| `diffusion` | `number` |
| `dryLevel` | `number` |
| `wetLevel` | `number` |

<a id="equalizersoundeffect"></a>

## EqualizerSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `highGain` | `number` |
| `lowGain` | `number` |
| `midGain` | `number` |
| `midHigh` | `number` |
| `midLow` | `number` |

<a id="distortionsoundeffect"></a>

## DistortionSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `level` | `number` |

<a id="echosoundeffect"></a>

## EchoSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `delay` | `number` |
| `dryLevel` | `number` |
| `feedback` | `number` |
| `wetLevel` | `number` |

<a id="pitchshiftsoundeffect"></a>

## PitchShiftSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `octave` | `number` |

<a id="compressorsoundeffect"></a>

## CompressorSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `attack` | `number` |
| `makeupGain` | `number` |
| `ratio` | `number` |
| `release` | `number` |
| `threshold` | `number` |

<a id="chorussoundeffect"></a>

## ChorusSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `depth` | `number` |
| `mix` | `number` |
| `rate` | `number` |

<a id="flangesoundeffect"></a>

## FlangeSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `depth` | `number` |
| `mix` | `number` |
| `rate` | `number` |

<a id="tremolosoundeffect"></a>

## TremoloSoundEffect

Extends [SoundEffect](#soundeffect).

| Property | Type |
|---|---|
| `depth` | `number` |
| `duty` | `number` |
| `frequency` | `number` |

<a id="script"></a>

## Script

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `code` | `string` |
| `enabled` | `boolean` |
| `source` | `string` |

<a id="localscript"></a>

## LocalScript

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `code` | `string` |
| `enabled` | `boolean` |
| `source` | `string` |

<a id="modulescript"></a>

## ModuleScript

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `code` | `string` |
| `source` | `string` |

<a id="inputaction"></a>

## InputAction

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `began` | `InstanceSignal` |
| `ended` | `InstanceSignal` |
| `enabled` | `boolean` |
| `keys` | `string` |

<a id="clickdetector"></a>

## ClickDetector

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `mouseClick` | `PlayerSignal` |
| `rightMouseClick` | `PlayerSignal` |
| `mouseHoverEnter` | `PlayerSignal` |
| `mouseHoverLeave` | `PlayerSignal` |
| `cursorIcon` | `string` |
| `maxActivationDistance` | `number` |

<a id="collisiongroup"></a>

## CollisionGroup

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `ignores` | `string` |

<a id="chatwindow"></a>

## ChatWindow

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `anchorX` | `number` |
| `anchorY` | `number` |
| `animationTime` | `number` |
| `backdrop` | `"always" \| "focused" \| "never"` |
| `backgroundColor` | `Color` |
| `backgroundTransparency` | `number` |
| `cornerRadius` | `number` |
| `easing` | `"linear" \| "sine" \| "quad" \| "cubic" \| "quart" \| "quint" \| "expo" \| "circ" \| "back" \| "elastic" \| "bounce"` |
| `enabled` | `boolean` |
| `enterAnimation` | `"none" \| "fade" \| "slideLeft" \| "slideRight" \| "slideUp" \| "slideDown" \| "pop" \| "typewriter"` |
| `exitAnimation` | `"none" \| "fade" \| "slideLeft" \| "slideRight" \| "slideUp" \| "slideDown" \| "pop" \| "typewriter"` |
| `fadeTime` | `number` |
| `font` | `string` |
| `hideAnimation` | `"none" \| "fade" \| "slideLeft" \| "slideRight" \| "slideUp" \| "slideDown" \| "pop" \| "typewriter"` |
| `horizontalAlignment` | `"left" \| "center" \| "right"` |
| `lineBackgroundColor` | `Color` |
| `lineBackgroundTransparency` | `number` |
| `lineCornerRadius` | `number` |
| `lineFitsText` | `boolean` |
| `lineGap` | `number` |
| `linePaddingX` | `number` |
| `linePaddingY` | `number` |
| `maxMessages` | `number` |
| `padding` | `number` |
| `position` | `UDim2` |
| `prefixColor` | `Color` |
| `shader` | `string` |
| `size` | `UDim2` |
| `textColor` | `Color` |
| `textShadow` | `boolean` |
| `textSize` | `number` |
| `textStrokeColor` | `Color` |
| `textStrokeTransparency` | `number` |
| `timestampColor` | `Color` |
| `timestampFormat` | `string` |
| `timestamps` | `boolean` |
| `verticalAlignment` | `"top" \| "center" \| "bottom"` |
| `visible` | `boolean` |
| `visibleTime` | `number` |

<a id="chatcommand"></a>

## ChatCommand

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `invoked` | `ChatCommandSignal` |
| `triggers` | `string` |

<a id="bubblechat"></a>

## BubbleChat

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `alwaysOnTop` | `boolean` |
| `animation` | `"none" \| "fade" \| "slideLeft" \| "slideRight" \| "slideUp" \| "slideDown" \| "pop" \| "typewriter"` |
| `animationTime` | `number` |
| `backgroundColor` | `Color` |
| `backgroundTransparency` | `number` |
| `cornerRadius` | `number` |
| `enabled` | `boolean` |
| `font` | `string` |
| `maxBubbles` | `number` |
| `maxDistance` | `number` |
| `maxWidth` | `number` |
| `offset` | `Vector3` |
| `padding` | `number` |
| `pixelsPerMetre` | `number` |
| `tail` | `boolean` |
| `textColor` | `Color` |
| `textSize` | `number` |
| `visibleTime` | `number` |

<a id="textchannel"></a>

## TextChannel

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `shouldDeliver` | `((...any) -> ...any)?` |
| `shouldSend` | `((...any) -> ...any)?` |
| `onIncoming` | `((...any) -> ...any)?` |
| `messageReceived` | `InstanceSignal` |
| `autoJoin` | `boolean` |
| `color` | `Color` |
| `displayName` | `string` |
| `maxLength` | `number` |
| `slowMode` | `number` |

<a id="textsource"></a>

## TextSource

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `body` | `Instance?` |
| `canSend` | `boolean` |
| `player` | `string` |

<a id="chatinputbar"></a>

## ChatInputBar

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `autocomplete` | `boolean` |
| `backgroundColor` | `Color` |
| `backgroundTransparency` | `number` |
| `enabled` | `boolean` |
| `maxLength` | `number` |
| `placeholder` | `string` |
| `placeholderColor` | `Color` |
| `targetChannel` | `Instance?` |
| `textColor` | `Color` |

<a id="chattabs"></a>

## ChatTabs

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `backgroundColor` | `Color` |
| `enabled` | `boolean` |
| `selectedColor` | `Color` |
| `textColor` | `Color` |
| `unreadColor` | `Color` |

<a id="chattextshader"></a>

## ChatTextShader

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `shader` | `string` |

<a id="joint"></a>

## Joint

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `c0` | `CFrame` |
| `c1` | `CFrame` |
| `part0` | `Instance?` |
| `part1` | `Instance?` |
| `scale` | `Vector3` |
| `transform` | `CFrame` |

<a id="motor"></a>

## Motor

Extends [Joint](#joint).

<a id="model"></a>

## Model

Extends [Spatial](#spatial).

| Property | Type |
|---|---|
| `primaryPart` | `Instance?` |
| `scale` | `number` |

Methods:

- `getBoundingBox(): (CFrame, Vector3)`
- `getScale(): number`
- `scaleTo(scale: number): ()`

<a id="forcefield"></a>

## ForceField

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `visible` | `boolean` |

<a id="tool"></a>

## Tool

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `activated` | `InstanceSignal` |
| `deactivated` | `InstanceSignal` |
| `equipped` | `InstanceSignal` |
| `unequipped` | `InstanceSignal` |
| `canBeDropped` | `boolean` |
| `enabled` | `boolean` |
| `grip` | `CFrame` |
| `item` | `string` |
| `manualActivationOnly` | `boolean` |
| `requiresHandle` | `boolean` |
| `toolTip` | `string` |

Methods:

- `activate(): ()`
- `deactivate(): ()`

A part named `Handle` inside the tool is held in the right hand. See [Tools](tools.md).

<a id="team"></a>

## Team

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `playerAdded` | `PlayerSignal` (server) |
| `playerRemoved` | `PlayerSignal` (server) |
| `autoAssignable` | `boolean` |
| `teamColor` | `Color` |

Methods:

- `getPlayers(): { Player }`

On a client `getPlayers` answers with the local player alone, and only while they are on the team.
See [Teams](players.md#teams).

<a id="leaderboard"></a>

## Leaderboard

Extends [Instance](#instance).

Made by the engine at the root of the world, and holds one `Leaderstats` per player. Sent to every
player whatever the streaming radius, and out of the world: not drawn, not found by queries, not
saved into a scene. See [Leaderstats](players.md#leaderstats).

<a id="leaderstats"></a>

## Leaderstats

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `owner` | `string` |

Named after the player, and reached as `player.leaderstats`. The `Value` children are what the Tab
list shows.

<a id="replicatedstorage"></a>

## ReplicatedStorage

Extends [Instance](#instance).

Sent to players. Scripts inside do not run and parts inside are not in the world.
See [Containers](containers.md).

<a id="serverstorage"></a>

## ServerStorage

Extends [Instance](#instance).

Never sent to players. Scripts inside do not run and parts inside are not in the world.

<a id="serverscriptservice"></a>

## ServerScriptService

Extends [Instance](#instance).

Never sent to players. `Script`s inside run; parts inside are not in the world.

<a id="startergui"></a>

## StarterGui

Extends [Instance](#instance).

A container for guis. Everything inside it draws and runs as it would anywhere else.

<a id="starterplayerscripts"></a>

## StarterPlayerScripts

Extends [Instance](#instance).

A container for `LocalScript`s. Everything inside it runs as it would anywhere else.

<a id="startercharacterscripts"></a>

## StarterCharacterScripts

Extends [Instance](#instance).

Its children are copied into each new body a player gets. The originals do not run.

<a id="backpack"></a>

## Backpack

Extends [Instance](#instance).

Every body has one, named `backpack`. The tools inside it are carried, and out of the world.

<a id="starterpack"></a>

## StarterPack

Extends [Instance](#instance).

The tools inside every `StarterPack` are copied into each new body a player gets. They are out of
the world themselves, and their scripts do not run.

<a id="weldconstraint"></a>

## WeldConstraint

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `active` | `boolean` |
| `enabled` | `boolean` |
| `part0` | `Instance?` |
| `part1` | `Instance?` |

<a id="constraint"></a>

## Constraint

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `active` | `boolean` |
| `attachment0` | `Instance?` |
| `attachment1` | `Instance?` |
| `collideConnected` | `boolean` |
| `color` | `Color` |
| `enabled` | `boolean` |
| `visible` | `boolean` |

<a id="hingeconstraint"></a>

## HingeConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `actuatorType` | `"none" \| "motor" \| "servo"` |
| `angularSpeed` | `number` |
| `angularVelocity` | `number` |
| `currentAngle` | `number` |
| `limitsEnabled` | `boolean` |
| `lowerAngle` | `number` |
| `motorMaxTorque` | `number` |
| `servoMaxTorque` | `number` |
| `targetAngle` | `number` |
| `upperAngle` | `number` |

<a id="prismaticconstraint"></a>

## PrismaticConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `actuatorType` | `"none" \| "motor" \| "servo"` |
| `currentPosition` | `number` |
| `limitsEnabled` | `boolean` |
| `lowerLimit` | `number` |
| `motorMaxForce` | `number` |
| `servoMaxForce` | `number` |
| `speed` | `number` |
| `targetPosition` | `number` |
| `upperLimit` | `number` |
| `velocity` | `number` |

<a id="ballsocketconstraint"></a>

## BallSocketConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `limitsEnabled` | `boolean` |
| `twistLimitsEnabled` | `boolean` |
| `twistLowerAngle` | `number` |
| `twistUpperAngle` | `number` |
| `upperAngle` | `number` |

<a id="ropeconstraint"></a>

## RopeConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `currentDistance` | `number` |
| `length` | `number` |
| `mesh` | `string` |
| `meshLength` | `number` |
| `meshTwist` | `number` |
| `thickness` | `number` |
| `winchEnabled` | `boolean` |
| `winchForce` | `number` |
| `winchResponsiveness` | `number` |
| `winchSpeed` | `number` |
| `winchTarget` | `number` |

<a id="springconstraint"></a>

## SpringConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `coils` | `number` |
| `currentLength` | `number` |
| `damping` | `number` |
| `freeLength` | `number` |
| `limitsEnabled` | `boolean` |
| `maxLength` | `number` |
| `minLength` | `number` |
| `radius` | `number` |
| `stiffness` | `number` |
| `thickness` | `number` |

<a id="rodconstraint"></a>

## RodConstraint

Extends [Constraint](#constraint).

| Property | Type |
|---|---|
| `currentDistance` | `number` |
| `length` | `number` |
| `thickness` | `number` |

<a id="particleemitter"></a>

## ParticleEmitter

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `acceleration` | `Vector3` |
| `brightness` | `number` |
| `colorEnd` | `Color` |
| `colorStart` | `Color` |
| `drag` | `number` |
| `emissionDirection` | `"front" \| "back" \| "left" \| "right" \| "top" \| "bottom"` |
| `emitted` | `number` |
| `enabled` | `boolean` |
| `lifetimeMax` | `number` |
| `lifetimeMin` | `number` |
| `lightEmission` | `number` |
| `lightInfluence` | `number` |
| `lockedToPart` | `boolean` |
| `orientation` | `"facingCamera" \| "facingCameraWorldUp" \| "velocityParallel" \| "velocityPerpendicular"` |
| `rate` | `number` |
| `rotSpeedMax` | `number` |
| `rotSpeedMin` | `number` |
| `rotationMax` | `number` |
| `rotationMin` | `number` |
| `shapeStyle` | `"volume" \| "surface"` |
| `sizeEnd` | `number` |
| `sizeStart` | `number` |
| `speedMax` | `number` |
| `speedMin` | `number` |
| `spreadAngle` | `number` |
| `texture` | `string` |
| `timeScale` | `number` |
| `transparencyEnd` | `number` |
| `transparencyStart` | `number` |

Methods:

- `emit(count: number?): ()`

<a id="beam"></a>

## Beam

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `attachment0` | `Instance?` |
| `attachment1` | `Instance?` |
| `brightness` | `number` |
| `colorEnd` | `Color` |
| `colorStart` | `Color` |
| `curveSize0` | `number` |
| `curveSize1` | `number` |
| `enabled` | `boolean` |
| `faceCamera` | `boolean` |
| `lightEmission` | `number` |
| `lightInfluence` | `number` |
| `segments` | `number` |
| `texture` | `string` |
| `textureLength` | `number` |
| `textureMode` | `"stretch" \| "wrap" \| "static"` |
| `textureSpeed` | `number` |
| `transparencyEnd` | `number` |
| `transparencyStart` | `number` |
| `width0` | `number` |
| `width1` | `number` |

<a id="trail"></a>

## Trail

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `attachment0` | `Instance?` |
| `attachment1` | `Instance?` |
| `brightness` | `number` |
| `clears` | `number` |
| `colorEnd` | `Color` |
| `colorStart` | `Color` |
| `enabled` | `boolean` |
| `faceCamera` | `boolean` |
| `lifetime` | `number` |
| `lightEmission` | `number` |
| `lightInfluence` | `number` |
| `maxLength` | `number` |
| `minLength` | `number` |
| `texture` | `string` |
| `textureLength` | `number` |
| `textureMode` | `"stretch" \| "wrap" \| "static"` |
| `transparencyEnd` | `number` |
| `transparencyStart` | `number` |
| `widthScaleEnd` | `number` |
| `widthScaleStart` | `number` |

Methods:

- `clear(): ()`

<a id="highlight"></a>

## Highlight

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `adornee` | `Instance?` |
| `depthMode` | `"alwaysOnTop" \| "occluded"` |
| `enabled` | `boolean` |
| `fillColor` | `Color` |
| `fillTransparency` | `number` |
| `outlineColor` | `Color` |
| `outlineTransparency` | `number` |

<a id="decal"></a>

## Decal

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `color` | `Color` |
| `face` | `"front" \| "back" \| "left" \| "right" \| "top" \| "bottom"` |
| `texture` | `string` |
| `transparency` | `number` |
| `zIndex` | `number` |

<a id="explosion"></a>

## Explosion

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `hit` | `ExplosionSignal` |
| `blastPressure` | `number` |
| `blastRadius` | `number` |
| `destroyJointRadiusPercent` | `number` |
| `explosionType` | `"noCraters" \| "craters"` |
| `position` | `Vector3` |
| `visible` | `boolean` |

<a id="texture"></a>

## Texture

Extends [Decal](#decal).

| Property | Type |
|---|---|
| `offsetStudsU` | `number` |
| `offsetStudsV` | `number` |
| `studsPerTileU` | `number` |
| `studsPerTileV` | `number` |

<a id="fire"></a>

## Fire

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `color` | `Color` |
| `enabled` | `boolean` |
| `heat` | `number` |
| `secondaryColor` | `Color` |
| `size` | `number` |
| `timeScale` | `number` |

<a id="smoke"></a>

## Smoke

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `color` | `Color` |
| `enabled` | `boolean` |
| `opacity` | `number` |
| `riseVelocity` | `number` |
| `size` | `number` |
| `timeScale` | `number` |

<a id="sparkles"></a>

## Sparkles

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `enabled` | `boolean` |
| `sparkleColor` | `Color` |
| `timeScale` | `number` |

<a id="selectionbox"></a>

## SelectionBox

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `adornee` | `Instance?` |
| `color` | `Color` |
| `lineThickness` | `number` |
| `surfaceColor` | `Color` |
| `surfaceTransparency` | `number` |
| `transparency` | `number` |
| `visible` | `boolean` |

<a id="selectionsphere"></a>

## SelectionSphere

Extends [Instance](#instance).

| Property | Type |
|---|---|
| `adornee` | `Instance?` |
| `color` | `Color` |
| `surfaceColor` | `Color` |
| `surfaceTransparency` | `number` |
| `transparency` | `number` |
| `visible` | `boolean` |

## Blocks

| Property | Type |
|---|---|
| `changed` | `BlockChangedSignal` |

Methods:

- `get(at: Vector3): string`
- `set(at: Vector3, block: string): ()`
- `fill(from: Vector3, to: Vector3, block: string): number`
- `raycast(from: Vector3, direction: Vector3, range: number?, options: { fluids: boolean? }?): (string?, Vector3?, number?, Vector3?)`
- `isSolid(at: Vector3): boolean`
- `isAir(at: Vector3): boolean`
- `isFluid(at: Vector3): boolean`
- `lightAt(at: Vector3): number`
- `topAt(x: number, z: number): number`
- `find(block: string, centre: Vector3, radius: number, limit: number?): { Vector3 }`
- `count(block: string, from: Vector3, to: Vector3): number`
- `replace(from: string, to: string, a: Vector3, b: Vector3): number`
- `sphere(centre: Vector3, radius: number, block: string, hollow: boolean?): number`
- `cylinder(base: Vector3, radius: number, height: number, block: string, hollow: boolean?): number`
- `line(from: Vector3, to: Vector3, block: string): number`
- `hollowBox(from: Vector3, to: Vector3, block: string): number`
- `copy(from: Vector3, to: Vector3): BlockCopy`
- `paste(copy: BlockCopy, at: Vector3, quarterTurns: number?, skipAir: boolean?): number`

## BlockChangedSignal

Methods:

- `connect(handler: (at: Vector3, block: string) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## Tags

Methods:

- `tagged(tag: string): { Instance }`
- `added(tag: string): InstanceSignal`
- `removed(tag: string): InstanceSignal`

## ChatCommandSignal

Methods:

- `connect(handler: (body: Instance?, text: string, args: { string }) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## ChatMessageSignal

Methods:

- `connect(handler: (message: ChatMessage) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## ChatAnySignal

Methods:

- `connect(handler: (...any) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## Chat

| Property | Type |
|---|---|
| `onBubble` | `((message: ChatMessage) -> (boolean \| { [string]: any })?)?` |
| `onIncoming` | `((message: ChatMessage) -> { [string]: any }?)?` |
| `shouldSend` | `((message: ChatMessage) -> boolean?)?` |
| `messageReceived` | `ChatMessageSignal` |
| `sending` | `ChatMessageSignal` |
| `edited` | `ChatMessageSignal` |
| `deleted` | `ChatAnySignal` |
| `linkClicked` | `ChatAnySignal` |
| `bodyClicked` | `ChatAnySignal` |
| `messageClicked` | `ChatMessageSignal` |
| `opened` | `ChatAnySignal` |
| `closed` | `ChatAnySignal` |
| `typing` | `ChatAnySignal` |

Methods:

- `send(channelOrText: Instance | string, textOrOptions: (string | { [string]: any })?, options: { [string]: any }?): number`
- `system(text: string, options: { [string]: any }?): number`
- `edit(id: number, changes: string | { [string]: any }): ()`
- `delete(id: number): ()`
- `addPlayer(channel: Instance, body: Instance): ()`
- `removePlayer(channel: Instance, body: Instance): ()`
- `open(prefill: string?): ()`
- `close(): ()`
- `isOpen(): boolean`
- `clear(): ()`
- `setTarget(channel: Instance?): ()`
- `getTarget(): Instance?`
- `messages(): { ChatMessage }`
- `bubble(target: Instance, text: string, look: { [string]: any }?): ()`
- `function escape(text: string): string`
- `function plain(text: string): string`
- `function bodyLink(body: Instance, label: string?): string`
- `function itemLink(id: string, count: number?): string`
- `function within(range: number): ChatCheck`
- `function all(...: ChatCheck): ChatCheck`
- `function any(...: ChatCheck): ChatCheck`
- `function distance(message: ChatMessage, source: Instance): number?`
- `function audible(range: number): ChatCheck`
- `function sameTag(tag: string): ChatCheck`

## History

Methods:

- `now(): number`
- `viewTime(body: Instance): number`
- `function rewind<T...>(self, time: number, query: () -> T...): T...`

## ProximityWatcher

| Property | Type |
|---|---|
| `entered` | `ChatAnySignal` |
| `left` | `ChatAnySignal` |
| `near` | `boolean` |

Methods:

- `stop(): ()`

## Proximity

Methods:

- `closestInteractable(body: Instance): (Instance?, number?)`
- `watch(a: Instance, b: Instance, range: number): ProximityWatcher`
- `function falloff(distance: number, min: number, max: number, rolloff: number?): number`

## Path

Methods:

- `find(from: Vector3, to: Vector3, options: PathOptions?): { Vector3 }?`
- `isReachable(from: Vector3, to: Vector3, options: PathOptions?): boolean`
- `randomPointNear(point: Vector3, radius: number, options: PathOptions?): Vector3?`

## Debug

Methods:

- `drawLine(from: Vector3, to: Vector3, color: Color?, seconds: number?): ()`
- `drawRay(from: Vector3, direction: Vector3, color: Color?, seconds: number?): ()`
- `drawBox(frame: CFrame, size: Vector3, color: Color?, seconds: number?): ()`
- `drawSphere(centre: Vector3, radius: number, color: Color?, seconds: number?): ()`
- `drawPoint(at: Vector3, color: Color?, seconds: number?): ()`
- `label(at: Vector3, text: string, color: Color?, seconds: number?): ()`
- `watch(name: string, value: any): ()`
- `clear(): ()`
- `queryStats(): { queries: number, partsTested: number }`
- `profile(): { [string]: { milliseconds: number, calls: number, worst: number } }`

## Zones

Methods:

- `at(position: Vector3): { Instance }`

## Debris

Methods:

- `addItem(instance: Instance, seconds: number?): ()`

## Game

| Property | Type |
|---|---|
| `world` | `Instance` |
| `zones` | `Zones` |
| `proximity` | `Proximity` |
| `path` | `Path` |
| `debug` | `Debug` |
| `history` | `History` |
| `players` | `Players` |
| `tags` | `Tags` |
| `chat` | `Chat` |
| `blocks` | `Blocks` |
| `debris` | `Debris` |
| `isServer` | `boolean` |
| `isClient` | `boolean` |
| `isStudio` | `boolean` |
| `renderPriority` | `{ first: number, input: number, camera: number, character: number, last: number }` |
| `stepped` | `StepSignal` |
| `renderStepped` | `StepSignal` |
| `reloaded` | `ReloadedSignal` |
| `persist` | `{ [string]: any }` |
| `gravity` | `number` (set on the server) |
| `pauseRequested` | `PauseSignal` (client) |
| `paused` | `boolean` (client) |
| `exported` | `boolean` (client) |

Methods:

- `bindToClose(handler: () -> ()): ()`

Methods (client):

- `quit(): ()`
- `openSettings(): ()`
- `bindToRenderStep(name: string, priority: number, handler: (delta: number) -> ()): ()`
- `unbindFromRenderStep(name: string): ()`

## Settings

| Property | Type |
|---|---|
| `fov` | `number` |
| `fullscreen` | `boolean` |
| `vsync` | `boolean` |
| `maxFps` | `number` |
| `guiScale` | `number` |
| `renderDistance` | `number` |
| `sensitivity` | `number` |

Methods:

- `volume(category: string): number`
- `setVolume(category: string, value: number): ()`
- `volumes(): { string }`
- `actions(): { string }`
- `keyOf(action: string): string`
- `bind(action: string, key: string): ()`
- `save(): ()`

## MainWindow

| Property | Type |
|---|---|
| `title` | `string` |
| `icon` | `string` |
| `fullscreen` | `boolean` |
| `width` | `number` |
| `height` | `number` |
| `x` | `number` |
| `y` | `number` |
| `canMove` | `boolean` |
| `minWidth` | `number` |
| `minHeight` | `number` |
| `resizable` | `boolean` |
| `focused` | `boolean` |
| `minimized` | `boolean` |
| `visible` | `boolean` |
| `opacity` | `number` |
| `cursor` | `string` |
| `cursorVisible` | `boolean` |
| `fps` | `number` |
| `displayWidth` | `number` |
| `displayHeight` | `number` |
| `resized` | `WindowSizeSignal` |
| `moved` | `WindowSizeSignal` |
| `focusChanged` | `WindowFocusSignal` |
| `closing` | `AnySignal` |

Methods:

- `open(properties: { [string]: any }?): Window` (called with a dot)
- `overlay(properties: { [string]: any }?): Window` (called with a dot)
- `resize(width: number, height: number): ()`
- `moveTo(x: number, y: number): ()`
- `center(): ()`
- `monitors(): { { [string]: any } }`
- `flash(): ()`
- `setClipboard(text: string): ()`
- `preventClose(): ()`

## Input

| Property | Type |
|---|---|
| `mouseX` | `number` |
| `mouseY` | `number` |
| `mouseDeltaX` | `number` |
| `mouseDeltaY` | `number` |
| `screenWidth` | `number` |
| `screenHeight` | `number` |
| `mouseLocked` | `boolean` |
| `sensitivity` | `number` |
| `inputBegan` | `InputSignal` |
| `inputChanged` | `InputSignal` |
| `inputEnded` | `InputSignal` |
| `gamepadConnected` | `GamepadSignal` |
| `gamepadDisconnected` | `GamepadSignal` |
| `mouseBehavior` | `"default" \| "lockCenter" \| "lockCurrentPosition"` |
| `mouseIconEnabled` | `boolean` |

Methods:

- `down(action: string): boolean`
- `lockMouse(): ()`
- `releaseMouse(): ()`
- `isKeyDown(key: string): boolean`
- `isMouseButtonPressed(button: string | number): boolean`
- `getKeysPressed(): { InputObject }`
- `keyName(key: string): string`
- `getConnectedGamepads(): { number }`
- `getGamepadState(gamepad: number?): GamepadState?`
- `bindAction(name: string, handler: ActionHandler, ...string): ()`
- `bindActionAtPriority(name: string, handler: ActionHandler, priority: number, ...string): ()`
- `unbindAction(name: string): ()`
- `getBoundActions(): { string }`

## InputObject

| Property | Type |
|---|---|
| `inputType` | `string` |
| `keyCode` | `string` |
| `state` | `"begin" \| "change" \| "end" \| "cancel"` |
| `position` | `Vector3` |
| `delta` | `Vector3` |

## GamepadState

| Property | Type |
|---|---|
| `gamepad` | `number` |
| `leftStick` | `Vector3` |
| `rightStick` | `Vector3` |
| `leftTrigger` | `number` |
| `rightTrigger` | `number` |
| `buttons` | `{ [string]: boolean }` |

## InputSignal

Methods:

- `connect(handler: (input: InputObject, gameProcessed: boolean) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## GamepadSignal

Methods:

- `connect(handler: (gamepad: number) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `every(n: number, handler: (...any) -> ()): Connection`

## Voice

Methods:

- `stop(): ()`
- `setVolume(volume: number): ()`
- `setPitch(pitch: number): ()`
- `fade(volume: number, seconds: number): ()`
- `fadeOut(seconds: number): ()`
- `setPosition(at: Vector3): ()`
- `isPlaying(): boolean`

## Music

Methods:

- `setState(name: string, seconds: number?): ()`
- `transitionTo(name: string, seconds: number?, quantize: string?): ()`
- `setLayer(layer: number, volume: number): ()`
- `blend(intensity: number): ()`
- `stop(seconds: number?): ()`

## StoreSession

| Property | Type |
|---|---|
| `data` | `{ [string]: any }` |

Methods:

- `save(): ()`
- `release(): ()`

## Store

Methods:

- `get(key: any): any`
- `set(key: any, value: any): ()`
- `remove(key: any): ()`
- `update(key: any, change: (old: any) -> any): any`
- `keys(prefix: string?, limit: number?): { string }`
- `session(key: any): StoreSession`

## OrderedStore

Methods:

- `get(key: any): number?`
- `set(key: any, value: number): ()`
- `increment(key: any, delta: number?): number`
- `remove(key: any): ()`
- `getSorted(ascending: boolean, limit: number?, min: number?, max: number?): { { key: string, value: number } }`

## MemoryStore

The `memory` global. Server only. See [Memory stores](memory.md).

Methods:

- `hashMap(name: string): MemoryHashMap`
- `sortedMap(name: string): MemorySortedMap`
- `queue(name: string, invisibilityTimeout: number?): MemoryQueue`

## MemoryHashMap

Methods:

- `get(key: string): any?`
- `set(key: string, value: any, expiration: number): boolean`
- `update(key: string, transform: (old: any?) -> any?, expiration: number): any?`
- `remove(key: string): ()`
- `list(pageSize: number?): { string }`

## MemorySortedMap

Methods:

- `get(key: string): (any?, (number | string)?)`
- `set(key: string, value: any, expiration: number, sortKey: (number | string)?): boolean`
- `update(key: string, transform: (value: any?, sortKey: (number | string)?) -> (any?, (number | string)?), expiration: number): (any?, (number | string)?)`
- `remove(key: string): ()`
- `getRange(direction: "ascending" | "descending", count: number, exclusiveLowerBound: { key: string?, sortKey: (number | string)? }?, exclusiveUpperBound: { key: string?, sortKey: (number | string)? }?): { { key: string, value: any, sortKey: (number | string)? } }`
- `getSize(): number`

## MemoryQueue

Methods:

- `addAsync(value: any, expiration: number, priority: number?): ()`
- `readAsync(count: number, allOrNothing: boolean?, waitTimeout: number?): ({ any }, string?)` (yields)
- `removeAsync(id: string): ()`
- `getSize(excludeInvisible: boolean?): number`

## Http

Functions:

- `get(url: string, headers: { [string]: string }?): HttpResponse` (server)
- `post(url: string, body: string, contentType: string?, headers: { [string]: string }?): HttpResponse` (server)
- `request(options: { url: string, method: string?, headers: { [string]: string }?, body: string? }): HttpResponse` (server)
- `jsonEncode(value: any): string`
- `jsonDecode(text: string): any`
- `generateGuid(wrapInBraces: boolean?): string`
- `urlEncode(text: string): string`

## HttpResponse

| Property | Type |
|---|---|
| `status` | `number` |
| `ok` | `boolean` |
| `body` | `string` |
| `headers` | `{ [string]: string }` |

## Messaging

Methods:

- `publish(topic: string, message: any): ()`
- `subscribe(topic: string, handler: (message: any) -> ()): Subscription`

One board per side. See [Talking across the boundary](talking.md#messages-between-scripts-on-one-side).

## Subscription

Methods:

- `unsubscribe(): ()`

## CoreGui

Functions:

- `setCoreGuiEnabled(name: string, on: boolean): ()` (client)
- `getCoreGuiEnabled(name: string): boolean` (client)
- `sendNotification(notification: Notification): ()` (client)

`name` is `health`, `hunger`, `hotbar`, `chat`, `playerList`, `crosshair`, `experience` or `all`.
See [The core interface](game-and-screens.md#the-core-interface).

## Os

Functions:

- `time(): number`
- `clock(): number`
- `date(format: string?, when: number?): string`
- `difftime(later: number, earlier: number): number`

## Signal

| Property | Type |
|---|---|
| `count` | `number` |

Methods:

- `connect(handler: (...any) -> ()): Connection`
- `once(handler: (...any) -> ()): Connection`
- `every(n: number, handler: (...any) -> ()): Connection`
- `wait(timeout: number?): ...any`
- `fire(...any): ()`
- `disconnectAll(): ()`

## Java

Methods:

- `function use(name: string): any`
- `function class(name: string): any`
- `function typeof(value: any): string?`
- `function instanceOf(value: any, className: string): boolean`

## JavaMethod

| Property | Type |
|---|---|
| `name` | `string` |

Methods:

- `before(handler: (self: any, ...any) -> any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `after(handler: (self: any, result: any, ...any) -> any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `replace(handler: (original: (...any) -> any, self: any, ...any) -> any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `args(handler: (...any) -> ...any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `redirect(call: JavaMethod, handler: (original: (...any) -> any, target: any, ...any) -> any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `constant(value: number | string, replacement: any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `variable(local: string | number, replacement: any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean?, ordinal: number? }?): Mixin`
- `overload(...string | number): JavaMethod`
- called: `Class.method(self, ...)` or `Class:staticMethod(...)`

## JavaField

| Property | Type |
|---|---|
| `name` | `string` |
| `type` | `string` |

Methods:

- `changed(handler: (self: any, old: any, new: any) -> any, options: { when: ((...any) -> boolean)?, once: boolean?, limit: number?, priority: number?, raw: boolean? }?): Mixin`

## MixinService

Methods:

- `function state(name: string | { [string]: any }, defaults: { [string]: any }?): MixinState`
- `function hooks(): { Mixin }`
- called: `mixin "name" { ... }` groups hooks and returns a `MixinGroup`

## Mixin

| Property | Type |
|---|---|
| `label` | `string` |
| `methods` | `number` |
| `calls` | `number` |
| `errors` | `number` |
| `enabled` | `boolean` |

Methods:

- `remove(): ()`

## MixinGroup

| Property | Type |
|---|---|
| `name` | `string` |
| `enabled` | `boolean` |
| `hooks` | `{ Mixin }` |

Methods:

- `remove(): ()`

## MixinState

Methods:

- `ref(key: string): MixinStateRef`

## MixinStateRef

| Property | Type |
|---|---|
| `key` | `string` |
| `value` | `any` |

## Shaders

Methods:

- `function patch(targets: string | { string }, spec: { uniforms: { [string]: string }?, vertex: { [string]: any }?, fragment: { [string]: any }? }): ShaderPatch & { [string]: any }`

## ShaderPatch

Methods:

- `remove(): ()`

<a id="editableimage"></a>

## EditableImage

An image a script draws on. See [Images](images.md#drawing).

| Property | Type |
|---|---|
| `width` | `number` |
| `height` | `number` |
| `uri` | `string` |

Methods:

- `setPixel(x: number, y: number, color: Color, transparency: number?): ()`
- `getPixel(x: number, y: number): (Color, number)`
- `fill(color: Color, transparency: number?): ()`
- `clear(): ()`
- `drawRectangle(x: number, y: number, width: number, height: number, color: Color, options: DrawOptions?): ()`
- `drawCircle(x: number, y: number, radius: number, color: Color, options: DrawOptions?): ()`
- `drawEllipse(x: number, y: number, radiusX: number, radiusY: number, color: Color, options: DrawOptions?): ()`
- `drawLine(x1: number, y1: number, x2: number, y2: number, color: Color, options: DrawOptions?): ()`
- `drawPolygon(points: { Vector3 } | { number }, color: Color, options: DrawOptions?): ()`
- `drawGradient(x: number, y: number, width: number, height: number, from: Color, to: Color, options: GradientOptions?): ()`
- `drawImage(image: EditableImage, x: number, y: number, options: ImageDrawOptions?): ()`
- `floodFill(x: number, y: number, color: Color, options: FillOptions?): number`
- `drawText(text: string, x: number, y: number, color: Color, options: TextOptions?): (number, number)` (Minecraft's font, see [Text](images.md#text))
- `measureText(text: string, options: TextOptions?): (number, number)`
- `readPixels(x: number?, y: number?, width: number?, height: number?): { number }`
- `writePixels(x: number, y: number, width: number, height: number, pixels: { number }): ()`
- `resize(width: number, height: number, smooth: boolean?): ()`
- `crop(x: number, y: number, width: number, height: number): EditableImage`
- `copy(): EditableImage`
- `flip(direction: "horizontal" | "vertical"): ()`
- `rotate(quarterTurns: number): ()`
- `destroy(): ()`

<a id="images"></a>

## Images

The `images` global. See [Images](images.md).

Functions:

- `create(width: number, height: number, color: Color?, transparency: number?): EditableImage`
- `fromPixels(width: number, height: number, pixels: { number }): EditableImage`
- `load(source: string): EditableImage` (yields; Minecraft textures on the client only)
- `skin(player: any, part: ("head" | "full")?): EditableImage` (client, yields)
- `measureText(text: string, options: TextOptions?): (number, number)` (client, or a singleplayer server)

## Types

```luau
type TweenInfo = { time: number?, easing: string?, direction: string?, repeats: number?,
    reverses: boolean?, delay: number? }
type RayHit = { part: Instance, position: Vector3, distance: number, normal: Vector3 }
type QueryOptions = { exclude: { Instance }?, include: { Instance }?, respectCollides: boolean?, collisionGroup: string?, tag: string?, className: string?, limit: number?, sorted: boolean?,
    ignoreBlocks: boolean? }
type BlockCopy = { size: Vector3, palette: { string }, blocks: { number } }
type ChatMessage = { id: number, text: string, prefix: string, metadata: string, channel: Instance?, source: Instance?, body: Instance?, position: Vector3?, timestamp: number, status: string, [string]: any }
type ChatCheck = (message: ChatMessage, source: Instance) -> boolean
type PathOptions = { partial: boolean? }
type GradientKeypoint = { number | Color }
type ActionHandler = (name: string, state: "begin" | "change" | "end" | "cancel", input: InputObject) -> ("sink" | "pass")?
type PlayOptions = { volume: number?, pitch: number?, looped: boolean?, bus: string?,
    priority: number?, at: Vector3?, minDistance: number?, maxDistance: number?,
    rollOff: number?, fadeIn: number?, stream: boolean? }
type Notification = { title: string, text: string?, icon: string?, duration: number?,
    button1: string?, button2: string?, callback: ((button: string) -> ())? }
type ImageBlend = "over" | "replace" | "add" | "multiply" | "erase"
type DrawOptions = { transparency: number?, blend: ImageBlend?, smooth: boolean?, filled: boolean?,
    thickness: number?, cornerRadius: number? }
type GradientOptions = { rotation: number?, fromTransparency: number?, toTransparency: number?,
    blend: ImageBlend? }
type ImageDrawOptions = { width: number?, height: number?, sourceX: number?, sourceY: number?,
    sourceWidth: number?, sourceHeight: number?, transparency: number?, blend: ImageBlend?,
    smooth: boolean? }
type FillOptions = { tolerance: number?, transparency: number?, blend: ImageBlend? }
type TextAlign = "left" | "center" | "right"
type TextOptions = { size: number?, shadow: boolean?, align: TextAlign?, wrap: number?,
    lineHeight: number?, transparency: number?, blend: ImageBlend?, bold: boolean?,
    italic: boolean?, smooth: boolean? }
```

## Globals

```luau
declare game: Game

declare camera: Camera

declare window: MainWindow

declare settings: Settings

declare input: Input

declare ui: CoreGui           -- client only

declare messaging: Messaging

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

declare function require(module: string | Instance): any

declare script: Instance

declare function store(name: string): Store

declare function orderedStore(name: string): OrderedStore

declare http: Http

declare memory: MemoryStore   -- server only

declare os: Os

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

declare function udim(scale: number, offset: number): UDim2

declare cframe: {
    identity: CFrame,
    angles: (pitch: number, yaw: number, roll: number) -> CFrame,
    lookAt: (from: Vector3, to: Vector3) -> CFrame,
} & ((x: number, y: number, z: number) -> CFrame)
  & ((position: Vector3) -> CFrame)
  & ((from: Vector3, to: Vector3) -> CFrame)

declare function signal(name: string?): Signal

declare java: Java

declare mixin: MixinService

declare shaders: Shaders     -- client only

declare images: Images

declare task: {
    wait: (seconds: number?) -> number,
    spawn: (handler: () -> ()) -> number,
    delay: (seconds: number, handler: () -> ()) -> number,
    defer: <A...>(handler: (A...) -> (), A...) -> number,
    cancel: (handle: number) -> (),
    every: (seconds: number, handler: () -> ()) -> { cancel: (self: any) -> () },
    after: (seconds: number, handler: () -> ()) -> { cancel: (self: any) -> () },
    debounce: <A...>(handler: (A...) -> (), seconds: number) -> (A...) -> (),
    throttle: <A..., R...>(handler: (A...) -> R..., seconds: number) -> (A...) -> R...,
}
```
