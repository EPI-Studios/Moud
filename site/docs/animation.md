# Animations

An animation is a set of poses for a rig's joints over time. You load it into an `Animator`, which
gives you an `AnimationTrack`, and you play the track.

```lua
-- server
local wave = world:add("Animation", { name = "wave", animationId = "res://animations/wave.anim" })
local track = body.animator:loadAnimation(wave)
track:play()
```

- The `Animation` instance is the animation itself, and `animationId` says which file it reads.
- `animator:loadAnimation(wave)` binds that animation to that rig and returns an `AnimationTrack`.
  One animation can be loaded onto as many rigs as you like; each gets its own track with its own
  time, speed and weight.
- `track:play()` starts it. Nothing moves until you call that.

Make an animation in the [animation editor](#the-animation-editor), write it as a `.anim` file, build
it out of instances in a script, or import it from Blockbench with
[`scene.importBbmodel`](#importing-a-blockbench-model).

## Rigs: bodies and models

An `Animator` poses the joints of the rig it sits in. There are two kinds of rig:

- A **body** (`Character`) has its animator at `body.animator`, and its joints are the `Motor`s
  under `body.joints`: `root`, `head`, `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, `cape`,
  `rightWing`, `leftWing`, `spinInner` and `spinOuter`.
- Any other **`Model`** is animated through an `AnimationController`. Put an `AnimationController`
  in the model and an `Animator` in the controller. Its joints are every `Bone` and every `Motor`
  anywhere inside the model, found by name.

```
Model "dragon"
  AnimationController "animationController"
    Animator "animator"
  MeshPart "mesh"          meshId = "res://models/dragon.bbmodel"
    Bone "body"
      Bone "neck"
        Bone "head"
      Bone "tail"
```

The engine does not make the `Animator` for you; add it, or let the importer do it.

### Bone

A `Bone` is a joint in a model's rig. It is an `Attachment`, so it has a `cframe` and sits inside its
parent, which is the `MeshPart` it moves or another bone.

| Property | |
|---|---|
| `cframe` | where the bone's pivot rests, relative to its parent bone or part |
| `transform` | the animated offset on top of the rest, in the bone's own space. Animators, IK and springs write it |
| `scale` | scales the bone's geometry and everything under it, about its pivot |

A bone's world frame is its parent's world frame, times `cframe`, times `transform`. Anything you
put inside a bone, an attachment or another bone, follows the animated bone, and `worldCframe`
includes the animation. `scale` is drawn but does not move the children's `worldCframe`.

A `MeshPart` that holds bones draws each bone's piece of the model where the bone is posed. Its
`animation` property is ignored while it has bones, and its geometry is drawn at the size it was
modelled, not stretched to `size`. Which piece belongs to which bone is decided by name: a
`.bbmodel` group called `head` is drawn by the bone called `head`. A piece whose bone is missing
follows its parent's bone.

A controller starts each joint it animates from its rest on every tick, then lays the tracks on top.
A joint no track moves keeps whatever `transform` you gave it.

## Where an animation comes from

An `Animation` points at a `.anim` file with `animationId`, a `res://` path inside the place. If the
`Animation` holds a `KeyframeSequence`, the sequence is used and the file is ignored.

`loadAnimation` also takes a `KeyframeSequence` directly, so an animation you build in a script does
not need an `Animation` wrapped around it.

> [!NOTE]
> A file is read once and kept. When a `.anim` in the server's place changes, whether you saved it in
> the animation editor or edited it on disk, the server drops its copy and sends the new file to every
> player, who drop theirs too. Tracks that are playing it carry on from the same time with the new
> curves; they do not restart. A file that cannot be read shows an error in the output, and the track
> plays nothing, which is what you are looking at when a track runs its full length and the body never
> moves.

### Clips and models on a server

Players read a place's files from their own copy of the place. For `.anim` and `.bbmodel` files the
server keeps them up to date while they are connected:

- The server watches its place for `.anim` and `.bbmodel` files that change or are removed, and sends
  each one to every player. A player keeps what the server sent in memory, in place of their own file,
  until they leave. Nothing is written to the player's disk.
- A player who joins later is sent every such file that changed since the server started.
- Saving a clip in the animation editor while connected to a server also sends it to the server,
  which writes it into its place and passes it on. So do clips written by importing a `.bbmodel`, and
  `.anim` or `.bbmodel` files imported into or duplicated in the assets panel. Undoing an edit that
  wrote clips removes them from the server too, unless the server's copy was changed since. Only
  operators may do this, only for `.anim` and `.bbmodel` files, and only inside the place; anyone
  else is told no.
- A `.bbmodel` drawn by a `MeshPart` is redrawn within a second, and a view model rebuilds its rig.

Limits: a file must be under 8 MB. A player whose own copy of the place is out of date and whose files
did not change during this server's run still reads their own copy. Renaming, moving or deleting a
file in the editor does not reach the server. Scripts, scenes and other assets are not sent.

## A sequence in the tree

A `KeyframeSequence` is an animation made of instances. It holds `Keyframe` children, each at a time
in seconds, and each keyframe holds a `Pose` for every joint it moves:

```
KeyframeSequence      looped, priority, mask, blend, space
  Keyframe            time
    Pose              name is the joint; cframe, easing, direction, weight
    KeyframeMarker    name, value
```

| Class | Property | |
|---|---|---|
| `KeyframeSequence` | `looped` | start over at the end |
| | `priority` | which sequence wins a joint when several move it, see [priority and weight](#priority-and-weight) |
| | `mask` | joint names split by commas or spaces; only those joints are moved. Empty moves every joint |
| | `blend` | `"normal"`, or `"additive"` to add on top of the other tracks |
| | `space` | `"body"`, or `"view"` for a clip that plays on the [view model](#first-person) |
| `Keyframe` | `time` | seconds from the start |
| `Pose` | `name` | the joint it moves, like `rightArm` |
| | `cframe` | the joint's `transform` at this keyframe |
| | `easing` | how the joint moves from this keyframe to the next: `linear`, `sine`, `quad`, `cubic`, `quart`, `quint`, `expo`, `circ`, `back`, `elastic`, `bounce` |
| | `direction` | `in`, `out` or `inOut`; `inOut` by default |
| | `weight` | 0 to 1, how much of the pose is blended in |
| `KeyframeMarker` | `name` | what `keyframeReached` and `getMarkerReachedSignal` listen for |
| | `value` | a string handed to `getMarkerReachedSignal` |

- The sequence is as long as its last keyframe.
- A joint does not need a pose in every keyframe. It moves between the keyframes that have one, holds
  its first pose before the first and its last pose after the last.
- A pose can hold more poses. Each is found by its own name, so nesting only groups them.
- A joint's `weight` is read from its first pose. The weights of later poses are ignored for now.
- A marker fires at the time of the keyframe it is in.

A wave, built in a server script:

```lua
-- server
local wave = world:add("KeyframeSequence", { name = "wave", looped = true, priority = 1 })

local function key(time, roll)
    local frame = wave:add("Keyframe", { time = time })
    frame:add("Pose", {
        name = "rightArm",
        cframe = cframe.angles(math.pi, 0, roll),
        easing = "sine",
    })
end

key(0, -0.3)
key(0.4, 0.3)
key(0.8, -0.3)

local track = body.animator:loadAnimation(wave)
track:play()
```

The three keyframes put the arm up and rock it from one roll to the other and back. The last pose
matches the first, so `looped = true` runs it again with no jump.

> [!IMPORTANT]
> Keep the sequence somewhere every client has it, like under `world`. Clients do the posing, so a
> client that cannot find the sequence poses nothing, and that body stands still for that player
> while it animates for everyone else.

## The .anim file

A `.anim` file is JSON. There are two layouts. The first, from before, lists whole poses at
keyframes. The second, version 2, gives each joint its own curves for rotation, position and scale,
which is what the Blockbench importer and the animation editor write.

### Version 2: curves per joint

```json
{
  "version": 2,
  "length": 1,
  "loop": "loop",
  "priority": "movement",
  "euler": "yxz",
  "joints": {
    "rightLeg": {
      "rotation": [
        [0,   [30, 0, 0], "catmullrom"],
        [0.5, [-30, 0, 0], "catmullrom"],
        [1,   [30, 0, 0]]
      ]
    },
    "torso": {
      "position": [
        [0,    [0, 0, 0], "bezier", { "out": [0.1, [0, 0.05, 0]] }],
        [0.25, [0, 0.06, 0], "bezier", { "in": [-0.1, 0], "out": [0.1, 0] }],
        [0.5,  [0, 0, 0], "step"]
      ],
      "weight": 0.8
    }
  },
  "mask": ["rightLeg", "leftLeg", "torso"],
  "blend": "normal",
  "markers": [{ "time": 0.25, "name": "step", "value": "right" }],
  "events": [{ "time": 0.75, "name": "sound", "payload": { "effect": "step", "volume": 0.6 } }]
}
```

| Field | | |
|---|---|---|
| `version` | optional | `2`. A file with `joints` is read as version 2 either way |
| `length` | optional | seconds; the last key or marker when left out |
| `loop` | optional | `loop` starts over, `once` stops at the end, `hold` stays on the last frame until stopped. `once` when left out |
| `priority` | optional | `core`, `idle`, `movement`, `action`, `action2`, `action3`, `action4`, or a number. `core` when left out |
| `euler` | optional | the order the three rotation angles are put together in, like `yxz` (the default, the same as `cframe.angles`) or `zyx` (Blockbench's) |
| `joints` | | joint name to its channels |
| `mask` | optional | a list of joint names, or one string of names split by commas or spaces. Only those joints are moved |
| `blend` | optional | `normal`, or `additive` |
| `space` | optional | `body`, or `view` for a clip that plays on the [view model](#first-person). A body's animator refuses a view clip |
| `rig` | optional | the rig the clip was made for: `player`, `view`, the `res://` path of the `.bbmodel` a model was made from, or a model's name. Only the editor reads it |
| `markers` | optional | each with a `time`, a `name` and an optional string `value` |
| `events` | optional | each with a `time`, a `name`, a `payload`, which is any JSON, and an `on`: `server`, `client` or `both`, where it fires. `both` when left out |
| `skeleton` | optional | the hierarchy the clip was made on, see [retargeting](#playing-an-imported-clip-on-a-body) |
| `retarget` | optional | joint name in the clip to the body joint it plays on, overriding the guess from its name. An empty name keeps that joint off a body |
| `fps`, `viewModel` | optional | written by the editor for itself, as is an event's `preview`. The engine reads past them |

A joint has up to three channels and a weight:

| Channel | Values |
|---|---|
| `rotation` | `[x, y, z]` angles in degrees, put together in the file's `euler` order, or `[x, y, z, w]`, a quaternion |
| `position` | `[x, y, z]`, the offset from the joint's rest, in blocks |
| `scale` | `[x, y, z]`, 1 is the joint's own size. On a body joint it is the joint's `scale` |
| `weight` | 0 to 1, how much of the joint the clip takes; 1 when left out |

A channel is a list of keys, and a key is `[time, value, interpolation, handles]`. The last two are
optional. The interpolation says how the value travels from this key **to the next one**:

| Interpolation | |
|---|---|
| `linear` | a straight line. The default |
| `catmullrom` | a smooth curve through every key, bent by the keys on either side |
| `bezier` | a curve shaped by this key's `out` handle and the next key's `in` handle |
| `step` | holds this key's value, then jumps to the next key when its time comes |

Angles are interpolated one axis at a time, like Blockbench does, so a key can spin a joint more than
half a turn. Quaternion keys slerp, and treat `catmullrom` and `bezier` as `linear`.

**Bezier handles** are `{ "in": [dt, dv], "out": [dt, dv] }`. `dt` is the handle's time from its key
in seconds, negative for `in`. `dv` is how far the handle is from the key's value, either one number
for all three axes or `[dx, dy, dz]`. The two handles of a segment share one time line, so the three
axes ease in step, and a handle only moves its own axis's value. A missing handle is flat and a
third of the segment long, which gives an ease in and out.

**Two keys at the same time** make a jump: the value arrives at the first and leaves from the second.

Channels a joint does not have are left alone. A clip with only rotations turns the joints and does
not pull their positions back to the rest.

<!-- demo:animation-curves -->

### Version 1: poses at keyframes

```json
{
  "length": 1,
  "looped": true,
  "priority": 1,
  "keyframes": [
    { "time": 0,   "poses": { "rightLeg": { "angles": [30, 0, 0], "easing": "sine" },
                              "leftLeg":  { "angles": [-30, 0, 0], "easing": "sine" } } },
    { "time": 0.5, "poses": { "rightLeg": { "angles": [-30, 0, 0], "easing": "sine" },
                              "leftLeg":  { "angles": [30, 0, 0], "easing": "sine" } } },
    { "time": 1,   "poses": { "rightLeg": { "angles": [30, 0, 0] },
                              "leftLeg":  { "angles": [-30, 0, 0] } } }
  ],
  "markers": [
    { "time": 0.25, "name": "step", "value": "right" },
    { "time": 0.75, "name": "step", "value": "left" }
  ]
}
```

| Field | | |
|---|---|---|
| `length` | optional | seconds; the last keyframe's time when left out |
| `looped` | optional | false when left out |
| `priority` | optional | a number or a level name; 0 when left out |
| `keyframes` | | each with a `time` and `poses`, a table of joint name to pose |
| `markers` | optional | each with a `time`, a `name` and an optional `value` |
| `mask`, `blend`, `space` | optional | as in version 2 |

A pose in the file:

| Field | |
|---|---|
| `position` | `[x, y, z]`, the offset from the joint's rest |
| `rotation` | `[x, y, z, w]`, a quaternion |
| `angles` | `[x, y, z]` in degrees, used when there is no `rotation` |
| `easing` | same names as tweens; `linear` when left out |
| `direction` | `in`, `out` or `inOut`; `inOut` when left out |
| `weight` | 0 to 1; 1 when left out |

Everything a pose leaves out is the rest: no offset and no turn. Markers in a file have their own
times and do not belong to a keyframe, so you can put a marker between two keyframes.

## Markers and events

A marker is a name at a time, with a string `value`. An event is the same with a `payload` that can
be any JSON: a string, a number, a list or an object. Both fire through the same signals:

- `track:getMarkerReachedSignal(name)` fires with the marker's `value` or the event's `payload`, each
  time the track passes one with that name. An object payload arrives as a table.
- `track.keyframeReached` fires with the name of every marker and event.

Footsteps from a file, on a body the place owns:

```lua
-- server
local walk = body.animator:loadAnimation(world:add("Animation", {
    name = "walk",
    animationId = "res://animations/walk.anim",
}))
local step = body:add("Sound", { soundId = "res://sounds/step.ogg", volume = 0.6 })

walk:getMarkerReachedSignal("step"):connect(function(foot)
    step.pitch = if foot == "left" then 0.95 else 1.05
    step:play()
end)
walk:play()
```

- `getMarkerReachedSignal("step")` returns a signal for markers named `step` alone. The two markers
  in the file share that name, so the handler runs twice a loop.
- The value handed to the handler is the marker's `value`, which is `"right"` or `"left"` here, so
  one handler gives each foot its own pitch.

An event carrying a table:

```lua
--!strict
local sounds = track:getMarkerReachedSignal("sound")
sounds:connect(function(payload: any)
    print(payload.effect, payload.volume)
end)
```

Markers and events fire wherever a track runs: on the server, and on every client that has the
track. An event's `on` narrows that to `server` or `client`; a marker fires on both. They only fire
while a track plays forwards.

## Tracks

An `AnimationTrack` is one playing copy of an animation on one rig. It holds where the animation has
got to, how fast it runs and how strongly it poses the joints.

`animator:loadAnimation(animation)` makes an `AnimationTrack` inside the animator and returns it. It
takes an `Animation` or a `KeyframeSequence`. `animator:getPlayingAnimationTracks()` lists the tracks
that are playing, and `animator.animationPlayed` fires with a track each time one starts.

| Property | |
|---|---|
| `animation` | the `Animation` or `KeyframeSequence` it plays |
| `playing` | |
| `weight` | 0 to 1 |
| `speed` | 1 is normal, 2 twice as fast, negative plays backwards |
| `looped` | starts over at the end; a looped animation loops either way |
| `priority` | 0 uses the animation's own priority |
| `mask` | joint names split by commas or spaces. Empty uses the animation's own mask |
| `blend` | `"additive"` makes the track add to the others; an additive animation adds either way |
| `fadeTime` | seconds it takes to blend in when played and out when stopped |
| `timePosition` | where it is, in seconds |
| `length` | how long the animation is, in seconds |

Leave `priority` at 0 and the track uses the priority written in the animation. Set it to something
else and the track uses that instead, so one file can win a joint on one body and lose it on another.
Setting `looped` on the track makes an animation loop that does not say so itself; an animation that
is already looped loops either way, and clearing the track's `looped` does not stop it.

> [!NOTE]
> `timePosition` is not replicated. The server and each client keep their own, so writing it only
> seeks on the side that wrote it.

| Method | |
|---|---|
| `play(fadeTime?, weight?, speed?)` | starts from the beginning, or from the end when `speed` is negative, fading in over `fadeTime` (0.1 when left out). A track that is already playing keeps its place |
| `stop(fadeTime?)` | fades out over `fadeTime` (0.1 when left out) |
| `adjustSpeed(speed)` | sets `speed` |
| `adjustWeight(weight, fadeTime?)` | sets `weight`, and moves to it over `fadeTime` when given |
| `getMarkerReachedSignal(name)` | a signal that fires with the value or payload each time the track passes a marker or event with that name |

| Event | Fires with | |
|---|---|---|
| `stopped` | the track | when `playing` turns false |
| `ended` | the track | when a track that does not loop reaches the end |
| `didLoop` | the track | each time it starts over |
| `keyframeReached` | the name | each time it passes a marker or an event |

How a track ends depends on the animation's `loop`:

- `loop`, or `looped` on the track: it starts over and fires `didLoop`.
- `once`: it fires `ended` and stops. When the server plays it, the server sets `playing` to false
  and every client follows; it fades out from its last frame over `fadeTime`. A track a
  `LocalScript` loads is only seen by that client, and it holds its last frame after `ended` until
  you stop it.
- `hold`: it fires `ended` once and stays playing on its last frame until you call `stop`.

Played backwards, a track that does not loop stays at the start, still playing.

<!-- demo:animation-ends -->

## Priority and weight

Several tracks can move the same joint, and priority and weight decide the pose it ends up with.
Priorities are numbers, and each level has a name:

| Level | Number |
|---|---|
| `core` | 0 |
| `idle` | 1 |
| `movement` | 2 |
| `action` | 3 |
| `action2` | 4 |
| `action3` | 5 |
| `action4` | 6 |

A file takes either the name or the number; a track or a `KeyframeSequence` takes the number. Any
other number works too and sits between the levels.

For each joint, the tracks that move it are grouped by priority and laid on top of each other from
the lowest group to the highest:

- Inside one priority, the tracks share the joint by weight. Two tracks at 1 and 1 land halfway
  between their poses.
- A group's weights add up, to at most 1, and the group covers what is under it by that much. A
  higher priority at full weight hides everything below it; at 0.5 it leaves half of the joint to
  the priorities below.
- What is under the lowest group is what the joint held before: the engine's walk on a body with
  `animate` on, the rest on a model's controller.

A track's weight is its `weight` times how far it has faded in, times the joint's weight in the
animation. Fading a high priority in over 0.3 seconds hands the joint over smoothly.

Idle and attack, faded into each other:

```lua
-- server
local idle = body.animator:loadAnimation(world:find("idle"))
local attack = body.animator:loadAnimation(world:find("attack"))
idle.looped = true
attack.priority = 3

idle:play(0.3)

task.every(4, function()
    attack:play(0.2)
end)
```

The attack is at `action`, above the idle. While it fades in, the idle shows through less and less,
and when it ends it fades out and the idle comes back, with no need to stop and restart the idle.

### Masks

A mask keeps a track to some joints. It is a list of joint names in the file, or a string of names
split by commas or spaces on a track or a sequence:

```lua
local wave = body.animator:loadAnimation(world:find("wave"))
wave.mask = "rightArm"
wave.priority = 3
wave:play()
```

The wave now only moves the right arm, so the legs keep walking whatever the file says about them.
A track's own `mask` replaces the animation's mask; leave it empty to use the animation's. A mask
name can be the rig's joint name or the name in the file, so a retargeted clip can be masked either
way.

### Additive tracks

An additive track does not replace the pose, it adds its own on top. It is applied after every
normal track, whatever its priority, turning each joint by the track's rotation and moving it by its
position, scaled by the track's weight. Author an additive clip as the change you want: a nod is a
head turned down a little from zero.

```lua
local nod = body.animator:loadAnimation(world:find("nod"))
nod.blend = "additive"
nod:play()
```

Played over a walk, the head nods while it walks. Additive clips go well with masks.

<!-- demo:animation-blend -->

## Tracks and the engine's walk

Posing happens on clients. The server keeps time for its tracks and fires their events, but it never
moves a joint for them.

On each client, a body with `animate` on is posed by the engine first, from its walk and the rest of
its [animation state](character.md#the-animation-state). The tracks are laid over that pose. A joint
no track moves keeps the walk cycle, so a wave on `rightArm` still walks with the legs. A track at
full weight on the legs replaces the walk.

With `animate` off, only tracks move the joints. A joint starts from where it was left, which is the
last pose a track gave it or a `transform` you wrote, and stays there when the tracks stop.

A model's controller starts every joint a track moves from its rest on each tick, so a joint goes
back to rest as its last track fades out.

Animations do not play while you edit a scene. Play the place to see one run.

## Inverse kinematics: IKControl

An `IKControl` bends joints so that one of them reaches, points at or looks at a target. It is
solved on each client every tick, after the tracks and the
engine's walk, so it bends the animated pose rather than replacing it. The server can create one,
and it replicates like any instance; put it anywhere, usually inside the rig.

| Property | |
|---|---|
| `type` | `"aim"`, `"lookAt"`, `"position"` or `"transform"`; `"position"` by default |
| `chainRoot` | the first joint of the chain, like an upper arm. Empty uses `endEffector` alone |
| `endEffector` | the joint that reaches, a `Bone` or a `Motor` |
| `target` | an instance to reach for: a part, an attachment, a bone or a joint |
| `targetCframe` | the world frame to reach for when `target` is empty |
| `offset` | moves the goal in the target's own space |
| `pole` | an instance the middle joint of a two-bone chain bends toward, like the way an elbow points |
| `axis` | for `aim`, the joint's own axis that points at the target. `vec3(0, -1, 0)` by default, which is down a Minecraft limb |
| `weight` | 0 leaves the pose alone, 1 solves all the way |
| `rootWeight` | for `lookAt`, how much of the turn the chain root takes. 0.3 by default |
| `smoothTime` | seconds the goal takes to catch up with a moving target. 0.05 by default, 0 snaps |
| `priority` | lower priorities are solved first |
| `enabled` | off leaves the joints to the tracks |

What each type does:

- **`aim`** turns `endEffector` alone so that its `axis` points at the target. A body's arm is one
  joint, so aiming is how a body points.
- **`lookAt`** turns `endEffector` so that its front, `vec3(0, 0, -1)`, faces the target. With a
  `chainRoot`, like the torso, the root turns by `rootWeight` of the way first and the end effector
  takes the rest.
- **`position`** moves the end of the chain onto the target. A chain of two bones, like an upper
  arm and a forearm ending at a hand bone, is solved exactly, with `pole` choosing which way the
  elbow bends. One bone points at the target. Longer chains turn each joint in turn until the end
  gets there. The end effector's own turn is left alone.
- **`transform`** does what `position` does and also turns the end effector to the target's
  rotation.

The chain is the joints from `chainRoot` down to `endEffector`: parent bones for bones, and for
motors each motor whose `part0` is the part the next one holds. A body's motors all hang off its
root, so a body's chain is one joint long.

A body pointing its right arm at a part:

```lua
--!strict
local body = world:find("npc") :: Character
local joints = body:find("joints") :: Folder
local point = body:add("IKControl", {
    type = "aim",
    endEffector = joints:find("rightArm"),
    target = world:find("lamp"),
}) :: IKControl
point.weight = 1
```

A two-bone arm on a model reaching for a handle, bending outwards:

```lua
--!strict
local mesh = model:find("mesh") :: MeshPart
local shoulder = mesh:find("RightArm") :: Bone
local hand = shoulder:findFirstDescendant("RightHand") :: Bone
model:add("IKControl", {
    type = "position",
    chainRoot = shoulder,
    endEffector = hand,
    target = world:find("handle"),
    pole = world:find("elbowHint"),
})
```

Turning `enabled` off, or bringing `weight` to 0, gives the joints back to the tracks on the next
tick. A weight between leaves the joint that far between its animated pose and the solved one, and
stays there tick after tick.

<!-- demo:animation-ik -->

> [!NOTE]
> IK and springs run once per tick, like the rest of posing, and are drawn smoothly between ticks.
> A `transform` a `LocalScript` writes on a joint that nothing animates is where they start from.

## Secondary motion: JointSpring

A `JointSpring` makes a joint lag behind and swing, like a cape, a tail or an ear. It follows a tip
point a little way out along the joint, simulated in the world, pulled back toward where the
animation puts it and pulled down by gravity. The joint is then turned to point at the tip.

| Property | |
|---|---|
| `joint` | the `Bone` or `Motor` that swings. Empty uses the spring's parent |
| `stiffness` | how hard the tip is pulled back to the animated pose. 60 by default |
| `damping` | how quickly the swinging dies down. 8 by default |
| `gravity` | the pull on the tip, in blocks per second squared. `vec3(0, -9.8, 0)` by default |
| `axis` | the joint's own direction from its pivot to the tip. Down by default |
| `length` | how far the tip is from the pivot, in blocks |
| `maxAngle` | the furthest the joint swings from its animated pose, in degrees |
| `weight` | how much of the swing is used, 0 to 1 |
| `enabled` | |

```lua
--!strict
local tail = mesh:findFirstDescendant("tail") :: Bone
tail:add("JointSpring", {
    axis = vec3(0, 0, 1),
    length = 0.5,
    stiffness = 40,
    damping = 6,
})
```

The tail here sticks out backwards, so its `axis` is `+z`. When the model runs forward the tail trails
behind, and when it stops the tail swings back and settles. Put a spring on each bone of a chain and
the chain swings as a whole; parents are solved before their children. Springs run on each client
after IK, and a rig that jumps more than a few blocks in one tick starts its springs over instead of
flinging them.

<!-- demo:animation-spring -->

## First person

What you see of yourself in first person is the body's **view model**, `body.viewModel`: a pair of
arms and whatever they hold, drawn in front of your camera. It is a model kept at the camera and
posed every frame, built in. It has its own `Animator`, so first-person clips play
on it and not on the body, and other players never see it: they see the body and the body's tracks.

```
Character
  ViewModel "viewModel"      enabled, model, fieldOfView
    Animator "animator"
    Bone "camera"            made on your client
      Bone "rightArm"
        Bone "rightItem"
      Bone "leftArm"
        Bone "leftItem"
```

Every body is made with a `ViewModel` and its `Animator`. The bones are only made on the client that
wears the body, the first time a script asks for them or the first frame it draws, and they never
replicate. A server script sees `viewModel.joints` as an empty table.

| Property | |
|---|---|
| `enabled` | draw the view model even when no clip plays |
| `model` | a `res://` `.bbmodel` to use instead of your skin's arms, see [a Blockbench view model](#a-blockbench-view-model) |
| `fieldOfView` | the view model's own field of view in degrees. 0, the default, uses the one Minecraft draws its hand with |
| `visible` | off never draws it, whatever else says so |
| `cframe` | where your eye was this frame, written by your client. The bones' `worldCframe` follows it |

| Member | |
|---|---|
| `animator` | the view model's `Animator` |
| `joints` | its bones by name, a table like `{ camera = Bone, rightArm = Bone, ... }` |
| `isActive()` | whether it is drawn this frame |

### When it draws

The view model is **active** when it is `visible` and it is `enabled`, has a `model`, or one of its
tracks is playing or still fading out. While it is active and you are in first person, it takes the
place of Minecraft's hand, whatever `appearance.firstPerson` says. When it is not active nothing
changes: the hand, `firstPerson = "arm"`, `"hand"`, `"body"` and `"none"` behave as they always did.
In third person the view model is not drawn, and a body drawn in first person with
`firstPerson = "body"` is still drawn under it.

It is drawn the way Minecraft draws your hand: after the world, on top of it, so it never pokes into
a wall, and with the field of view Minecraft gives its hand rather than the world's, so your field
of view setting does not stretch it. `fieldOfView` gives it a field of view of its own. The arms
wear your skin, slim or wide, sleeves and all. Your held item, `body.rightItem` and `body.leftItem` or their overrides, is drawn at
`rightItem` and `leftItem` the way it is held in third person.

### The rig

The bones live in the camera's space: the `camera` bone is the eye, `-z` is where you look, `+x` is
right and `+y` is up. At rest the arms sit exactly where Minecraft holds its empty hand, so turning
`enabled` on with no clip looks like the hand you know, with both arms out. `rightItem` and
`leftItem` are the grips, where an item sits in the fist.

Every frame, on your own client, the view model is posed in this order:

1. every bone goes back to its rest, `transform` the identity and `scale` one;
2. its tracks move on by the frame's time and pose the bones, with the same priorities, weights,
   masks and blending as [any other track](#priority-and-weight);
3. render step functions run, so a script can add to what the clips did;
4. `IKControl`s whose end effector is in the view model are solved;
5. the camera is placed and the view model is drawn.

Because every bone starts from rest each frame, a script adds to the clips by multiplying:
`bone.transform = bone.transform * offset`. A transform written once outside a render step is gone
the next frame. `JointSpring`s do not run on the view model.

### The camera bone

The `camera` bone's `transform` is added to your camera after the camera has been placed: its
position is along the camera's own axes, then it turns the view. A
clip that kicks `camera` up kicks your view up. The arms hang under the `camera` bone, so they stay
where they are on the screen while the world behind them moves.

It only changes what you see. Where you aim, what you click and where your body looks are the
player's own look, as before, and other players see nothing of it. It applies in first person only.

### Playing a clip on it

A clip with `"space": "view"` is made for the view model, and you load it with the view model's
animator:

```lua
local reload = body.viewModel.animator:loadAnimation(game.world:find("reload") :: Animation)
reload:play(0.1)
```

- A view clip loaded on a body's animator is refused with an error: it names `camera`, `rightArm`
  and the rest in the camera's space, which would twist a body out of shape.
- A body clip loaded on the view model works, but only the joints the two share move, so a body clip
  that moves legs, a torso or a head prints a warning once. Mark a clip made for the view model with
  `"space": "view"`.
- The server or a client can load and play a view clip. The track replicates like any other, the
  wearer's client plays it once a frame, and other clients keep it but never draw it. Markers and
  events fire on the server for tracks the server plays, and on the wearer's client.

A reload, `animations/reload.anim`:

```json
{
  "version": 2,
  "length": 1.2,
  "loop": "once",
  "space": "view",
  "priority": "action",
  "joints": {
    "leftArm": {
      "rotation": [[0, [0, 0, 0], "catmullrom"], [0.35, [-40, 25, 10], "catmullrom"],
                   [0.8, [-40, 25, 10], "catmullrom"], [1.2, [0, 0, 0]]],
      "position": [[0, [0, 0, 0]], [0.35, [0.05, -0.1, 0.05]], [0.8, [0.05, -0.1, 0.05]], [1.2, [0, 0, 0]]]
    },
    "rightArm": {
      "rotation": [[0, [0, 0, 0], "catmullrom"], [0.35, [15, 0, -35], "catmullrom"],
                   [0.8, [15, 0, -35], "catmullrom"], [1.2, [0, 0, 0]]]
    },
    "camera": { "rotation": [[0, [0, 0, 0]], [0.35, [-2, 0, 3]], [1.2, [0, 0, 0]]] }
  },
  "events": [
    { "time": 0.3, "name": "magOut", "payload": { "volume": 0.8 } },
    { "time": 0.85, "name": "magIn", "payload": { "volume": 1 } }
  ]
}
```

### Sway, bob and recoil

Sway, bob and recoil are yours to write, in a render step, on top of whatever the clips do. This
`LocalScript` is copied into every body through [`StarterCharacterScripts`](containers.md#startercharacterscripts):
the arms lag behind the mouse, the camera bobs while you walk, a click kicks the camera up and `R`
plays the reload above, with a sound on its event.

```lua
--!strict
local me = script.parent :: Character
if me ~= game.players:me() then return end
local view = me.viewModel
view.enabled = true

local joints = view.joints
local reload = view.animator:loadAnimation(game.world:find("reload") :: Animation)
local magOut = me:add("Sound", { soundId = "res://sounds/mag_out.ogg" }) :: Sound

reload:getMarkerReachedSignal("magOut"):connect(function(payload: any)
    magOut.volume = payload.volume
    magOut:play()
end)
reload.ended:connect(function()
    print("reloaded")
end)

local kick = 0
local sway = vec3(0, 0, 0)
local walked = 0

input.inputBegan:connect(function(object: InputObject, typing: boolean)
    if typing then return end
    if object.inputType == "mouseButton1" then
        kick += math.rad(4)
    elseif object.keyCode == "r" then
        reload:play(0.1)
    end
end)

game:bindToRenderStep("viewModel", 200, function(dt: number)
    local turned = vec3(-input.mouseDeltaX, input.mouseDeltaY, 0) * 0.0015
    sway = sway:lerp(turned, math.min(1, dt * 10))

    local speed = vec3(me.velocity.x, 0, me.velocity.z).magnitude
    walked += dt * speed * 1.6
    local stride = math.min(1, speed / 5)
    local bob = vec3(math.sin(walked) * 0.02, -math.abs(math.cos(walked)) * 0.03, 0) * stride

    kick *= math.exp(-dt * 14)

    local eye = joints.camera
    eye.transform = eye.transform * cframe.new(bob) * cframe.angles(kick, 0, 0)
    for _, name in { "rightArm", "leftArm" } do
        local arm = joints[name]
        arm.transform = cframe.new(sway) * arm.transform
    end
end)
```

- The render step runs after the clips each frame, so the bob and the kick are added to the reload's
  own camera move, and the sway is laid over the arms wherever the reload put them.
- `kick` dies away on its own, so each click adds to it and the view settles back.
- The sway comes before the arm's own transform, so it slides the whole arm along the camera's axes.

### Reaching with IK

An `IKControl` works on the view model's bones like on any other, solved each frame after the render
steps. The left hand holding a foregrip on whatever the right hand carries:

```lua
--!strict
local me = script.parent :: Character
if me ~= game.players:me() then return end
local view = me.viewModel
local joints = view.joints

local grip = joints.rightItem:add("Attachment", {
    name = "foregrip",
    cframe = cframe.new(vec3(0, 0.1, -0.35)),
}) :: Attachment

view:add("IKControl", {
    name = "support",
    type = "position",
    chainRoot = joints.leftArm,
    endEffector = joints.leftItem,
    target = grip,
    smoothTime = 0,
})
```

The foregrip hangs off `rightItem`, so it follows every clip and sway that moves the right arm, and
the left arm turns to put its hand on it. A skin arm is one bone, so it points its hand at the grip;
an arm of two bones in a Blockbench view model bends at the elbow to reach it, with a `pole` to pick
the way.

### A Blockbench view model

Set `model` to a `.bbmodel` in the place and it replaces your skin's arms:

```lua
view.model = "res://models/rifle.bbmodel"
```

- Each Blockbench group becomes a `Bone` with the group's name, nested like the groups, and its
  cubes are drawn with it. A group named `rightArm` is the bone a view clip's `rightArm` keys move,
  so name the groups after the joints your clips use.
- The model's origin is the eye, looking north, `-z`. A top-level group named `camera` is the eye
  instead, and every other top-level group hangs under it. Without one, a `camera` bone is made at
  the origin.
- Groups named `rightItem` and `leftItem` get your held items. Without them nothing is held.
- Textures must be saved inside the `.bbmodel`. Only cubes are drawn; mesh elements are skipped.
- Changing `model` builds the bones again, so anything you put under the old bones goes with them.
  A model that cannot be read falls back to the skin arms and says why in the output.
- A clip imported with [`scene.importBbmodel`](#importing-a-blockbench-model) moves these bones by
  the same names. Give it `"space": "view"` so it is not taken for a body clip.

## Importing a Blockbench model

`scene.importBbmodel(path, parent?)` turns a `.bbmodel` in the place into a model you can animate,
and writes one `.anim` file per Blockbench animation. It returns the model and a list of notes about
anything it could not carry over.

```lua
--!strict
local model, notes = scene.importBbmodel("res://models/fox.bbmodel")
for _, note in notes do
    print(note)
end

local controller = model:firstChildOfClass("AnimationController") :: AnimationController
local animator = controller:firstChildOfClass("Animator") :: Animator
local animations = model:find("animations") :: Folder
local run = animator:loadAnimation(animations:find("run") :: Animation)
run:play()
```

What it builds:

```
Model "fox"                        under parent, or world when left out
  MeshPart "mesh"                  meshId = the .bbmodel, sized to the model, anchored
    Bone ...                       one per Blockbench group, nested like the groups
  AnimationController "animationController"
    Animator "animator"
  Folder "animations"
    Animation "run"                animationId = res://models/fox/run.anim
```

- **Each group becomes a `Bone`** at the group's pivot, turned by the group's rotation, and the
  cubes in the group are drawn rigidly with it. Blockbench's 16 units are one block. The model's
  origin sits at the `Model`'s position, so an entity model stands on it; the `MeshPart` sits at the
  middle of the cubes.
- Two groups with the same name are told apart by a number, `tail` and `tail2`.
- Cubes outside any group are drawn with the `MeshPart` and never move.
- Textures must be saved inside the `.bbmodel`. One that only points at a file on disk is drawn blank.
- **Each animation becomes a `.anim` file** next to the model, in a folder with the model's name.
  Keyframes become rotation, position and scale keys with their interpolation: `linear`,
  `catmullrom`, `bezier` with its handles, and `step`. A keyframe with separate before and after
  values becomes two keys at the same time. The animation's `loop` mode comes across as it is.
- **Sound, particle and timeline keyframes become events.** A sound fires `sound` with
  `{ effect, file }`, a particle fires `particle` with `{ effect, locator, file, script }`, and a
  timeline keyframe fires `timeline` with its script as a string. Connect with
  `track:getMarkerReachedSignal("sound")`.
- **Molang is not run.** A keyframe value that is a plain number, even written as text, comes
  across; any other expression becomes 0 (1 for scale) and is listed in the notes. `anim_time_update`,
  `start_delay`, `loop_delay` and `blend_weight` are listed when set and otherwise ignored.

The importer writes what Blockbench shows, so the clip looks the same in Moud. The angles are written
in Blockbench's `zyx` order and positions are in blocks. A file saved by Blockbench 5 or later
(`format_version` 5 and up) keeps its keyframes in the axes Blockbench shows, and they come across as
they are. Older files saved animated rotations with x and y turned the other way and positions with
x turned, and the importer turns those back. Each clip also carries the model's `skeleton`, which
lets a bone's keys be added to its group's own rotation the way Blockbench adds them.

The Java entry point, for tools such as the editor, is `BbmodelImport.build(text, res, parent)`,
which returns the model, the `.anim` files to write and the notes. `BbmodelImport.read(text, name)`
does the conversion without making instances.

### Playing an imported clip on a body

A clip made on the player's shape plays straight on a body: `body.animator:loadAnimation(clip)`. Group
names are matched to body joints without caring about case, spaces, `_` or `-`, and a leading
`biped`:

| Group names | Body joint |
|---|---|
| `head` | `head` |
| `body`, `torso`, `chest`, `upperBody` | `torso` |
| `rightArm`, `armRight`, `rArm` | `rightArm` |
| `leftArm`, `armLeft`, `lArm` | `leftArm` |
| `rightLeg`, `legRight`, `rLeg` | `rightLeg` |
| `leftLeg`, `legLeft`, `lLeg` | `leftLeg` |
| `cape` | `cape` |
| `rightWing`, `wingRight`, `leftWing`, `wingLeft` | `rightWing`, `leftWing` |

So Blockbench's own names, `Head`, `Body`, `RightArm`, Bedrock's `rightArm` and Java's `right_arm`
all land on the right joint. A model with at least three of these is marked `"rig": "player"`.

Blockbench players are usually nested: arms and head under the body, the body under a waist. A body's
joints are not, they all hang off its root. When a clip with a `skeleton` plays on a body, each joint
gets the whole movement its group made in the model, parents included, so turning `Body` in the
clip turns the arms and head with it and moves their pivots. A body's limbs pivot where the player
model's do, at the shoulders, neck and hips, which is where Blockbench's player templates put them.

```lua
--!strict
local model, _ = scene.importBbmodel("res://models/emotes.bbmodel")
local animations = model:find("animations") :: Folder
local body = world:find("npc") :: Character
local dance = body.animator:loadAnimation(animations:find("dance") :: Animation)
dance:play(0.2)
```

The model's own bones are not needed for this; the clip only reads its `.anim` file. Keep the
`animations` folder where every client can see it, or make your own `Animation` pointing at the same
file.

## The animation editor

The editor has an animation workspace for making and changing `.anim` clips on a rig in the scene.
What it shows is what plays: the preview samples the clip with the same code a track uses, turns
the joints the way the runtime turns them, and runs the rig's IK and springs after the clip.

### Opening it

Open it from the editor with any of:

- the **Animation** button in the toolbar, next to play
- **Window → Animation Editor**
- **Ctrl+Shift+A**

The same button or shortcut takes you back to the scene. The workspace opens the last clip you had,
or the first clip in `animations/`. Clips live in the place's `animations` folder, and a new clip is
saved as `animations/<name>.anim`.

### The workspace

- **Top bar.** Play and pause, the previous and next key, loop playback, and the time and frame
  under the playhead. On the right, **Body** or **View** sets the clip's `space`, the rig picker
  chooses what the clip is shown on, and **Save** writes the clip.
- **Rig.** The joints of the rig as a tree, with the number of keys each has; filter them by name.
  **Controls** lists the rig's `IKControl`s and `JointSpring`s. **Clips** lists the clips for the
  rig: every clip in the place for the player, or the model's own folder for a model; the plus makes
  a new one there.
- **Viewport.** The rig, posed at the playhead. Click a limb or a bone to select its joint, then turn
  it with the rotate gizmo or move it with the move gizmo. Changing a joint keys it at the playhead.
  Onion skin draws the poses at the keys before and after the playhead.
- **Inspector.** What is selected: a key's value and interpolation with a small curve, a joint's
  rotation, position and scale at the playhead, a marker, an event, or a control's properties. Under
  it, the clip itself: length, frame rate, playback (`loop`, `once`, `hold`), priority, blend and
  mask.
- **Timeline.** A lane for markers, one for script events, and one per joint. Click the arrow by a
  joint's name, or double-click the name, to open its rotation, position and scale lanes, and
  double-click inside a lane to add a key there. The curve view draws each channel's x, y and z over
  time, with the bezier handles of the selected keys.

A clip is saved in the runtime's format, version 2, with a few fields the runtime reads past: `fps`,
each event's `preview`, and `viewModel`. A clip written in the first format, or in the editor's early
format with `channels`, opens as it is and is written in version 2 when you save it; the top bar says
so while it is open.

Saving a clip drops the copy the engine keeps, so the next track that plays it reads the new file.
Play the place after saving and the scripts that play the clip play the change.

### Keys and shortcuts

| Key | |
|---|---|
| Space | play and pause |
| Home, End | the start or the end of the clip |
| Left, Right | one frame back or on |
| R, G | the rotate or the move gizmo |
| X | the gizmo turns with the joint, or stays on the world's axes |
| B | show or hide a model's bones and the handles of every control; off, only the selected joint and control are drawn |
| M | mirror the selected joint's pose to the other side |
| Ctrl while dragging the gizmo | snap to 15 degrees, or to 1/16 of a block |
| Alt and drag an arm | aim a body's arm at the pointer |
| V | in a view clip, look through the camera bone |
| Numpad 5 | orthographic view |
| Ctrl+C, Ctrl+V, Ctrl+D | copy, paste and duplicate keys; paste lands at the playhead |
| Delete | delete the selected keys, event or marker |
| Ctrl+A | select every key |
| Ctrl+S | save the clip |
| Ctrl+Z, Ctrl+Y | undo and redo, for keys, events and control handles alike |

In the timeline, drag a key to move it in time, Shift or Ctrl click to add to the selection, and drag
across empty lanes to select a box of keys. Ctrl and the wheel zoom the time, Shift and the wheel
scroll it. Right-click a lane to add a key, a marker or an event, or to change the interpolation of
the selected keys. In the curve view, drag a key up and down to change its value, and hold Alt to
keep its time.

Keys land on frames while **Snap to frames** is on. The frame rate is only the editor's grid; the
runtime plays the curves at any time.

### Script events

An event is a name at a time with a payload, which the inspector edits as a table of fields, or as
a single value for an event that carries a string or a number. **Fires on** is the event's `on`:
`server`, `client` or `both`. A server event fires only on the server, a client event only on the
clients, which is where sounds and particles belong.

Scripts hear an event with `getMarkerReachedSignal`, and the payload arrives as a table:

```lua
--!strict
local body = world:find("npc") :: Character
local swing = world:add("Animation", {
    name = "swing",
    animationId = "res://animations/swing.anim",
}) :: Animation
local track = body.animator:loadAnimation(swing)

track:getMarkerReachedSignal("hit"):connect(function(payload: any)
    print(payload.damage, payload.side)
end)
track:play()
```

The inspector writes this handler for the selected event, with its clip and fields filled in, and
**Copy snippet** puts it on the clipboard. **Listeners in this place** lists the scripts that
already connect to that name; click one to open it at the line.

**Preview in editor** plays a sound and shows particles in the viewport when the playhead passes the
event, so its timing can be judged without playing the place. The sound and particle are only for
the editor; they are saved in the event's `preview` and the runtime ignores them.

### Importing a Blockbench model

Right-click a `.bbmodel` in the Assets panel and choose **Import animations...**. The dialog shows
the model on the left and three tabs:

- **Bones** lists every Blockbench group, its pivot and its number of cubes, and the body joint it
  plays on. The guess comes from the group's name, as in the
  [table above](#playing-an-imported-clip-on-a-body); pick another joint, or **new bone** to keep a
  group off the body. A choice that differs from the guess is written into each clip's `retarget`.
- **Animations** has a box per Blockbench animation. Only the ticked ones are written. Molang keys
  are flagged, since they come across as numbers.
- **Options** sets the folder the clips go in, `animations/<model>/` by default, and whether the
  model itself is added to the scene. Turn that off to write only the clips for a model you already
  have.

**Import** does what [`scene.importBbmodel`](#importing-a-blockbench-model) does: it writes a `.anim`
per ticked animation, adds the model with a `Bone` per group, an `AnimationController` and an
`Animator` in front of the camera as one undoable step, and lists the importer's notes. The first
clip then opens on the new model.

### Animating a Blockbench mesh

A `.bbmodel` dragged into the scene arrives as a model you can animate: a `Model` with a `Bone` per
Blockbench group, an `AnimationController` and an `Animator`, and an `Animation` per Blockbench
animation. A `.bbmodel` with no groups stays a plain `MeshPart`.

A `.bbmodel` that was placed before as a plain `MeshPart` shows under **Blockbench meshes** in the rig
picker. Picking it asks first, then turns it into such a model in the same place, with its name,
size, look and parent, as one undoable step.

Either way, each animation is written as a `.anim` in `animations/<file>/`, named after the
`.bbmodel` file and not the instance: `res://models/knight.bbmodel` writes to `animations/knight/`,
whatever the model is called. Every model made from the same file shares that folder, and a clip that
is already there is kept, never overwritten. Each clip's `rig` is the `.bbmodel`'s path.

The step owns the clips it wrote. Undo removes them, redo writes them again with the same content.
A clip that was there before is never removed, and one you changed after it was written is kept on
undo, with a line in Output saying so. Connected to a server, the clips are written and removed on
the server too.

### Rigs

The rig picker in the top bar chooses what the clip is shown on:

- **Preview character**, a body with your skin or one of the default skins. It stands in front of
  the camera and is not part of the scene.
- **Characters in the scene**, which lends the editor a body the place already has, with its own
  skin and controls. It is handed back as it was when you pick something else or leave.
- **Models in the scene**, every `Model` with an `AnimationController` and at least one `Bone` or
  `Motor`. The Rig panel shows its bones as a tree, the viewport draws them as a skeleton you can
  click while **Bones** is on, and the gizmo turns them.

A clip remembers its rig in `rig`: `player`, `view`, or, for a model made from a `.bbmodel`, the
`res://` path of that file. Opening a clip shows it on a model in the scene made from that file, so a
clip opens on any copy of the model. Older clips that name a model by its name still find it.

Undo keeps the clip you have open. When undo takes away the model you are animating, the clip stays
open on the preview character; redo brings the model back and shows the clip on it again. Undoing a
key change in a clip that is not open changes that clip without opening it, and the status bar says
which one.

**Controls** lists the `IKControl`s and `JointSpring`s that move the rig's joints. Selecting one
shows its properties in the inspector. In the viewport, an `IKControl`'s target is a diamond and its
pole a circle, each joined to the chain by a dashed line; drag them to move `targetCframe`, or the
part or attachment the control points at. A `JointSpring` is drawn as its axis, out to `length`.
Moving a handle is an undoable scene edit. The preview solves the controls after the clip on every
frame, so the pose in the viewport is the one a client draws.
