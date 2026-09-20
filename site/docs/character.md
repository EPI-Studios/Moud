# Characters

A `Character` is a body in the world: a rig of boxes held together by joints, with health, a pose, a
skin and somewhere to keep its tools. Every player gets one when they spawn, and a place creates as
many of its own as it likes. A body a player drives and a body a place made are the same class and
obey the same rules.

```lua
local statue = world:add("Character", { cframe = cframe(6, 64.5, 0), scale = 2 })
```

`cframe` puts the body where you want it, and `scale` builds the rig at twice its usual size. From
that line on the body stands there with nobody driving it, and every property below works on it.

## What a body is made of

A character builds its own rig the moment it exists, so you never add the head, the joints or the
backpack yourself:

```
character
  head  torso  leftArm  rightArm  leftLeg  rightLeg    each with an `overlay` child
  root                                                  the frame the whole body hangs from
  hitbox                                                the volume it really collides with
  humanoid                                              health, state, how it moves
  animator                                              the tracks it is playing
  backpack                                              the tools it carries
  appearance                                            the sheet, and what you see of your own
  armour   wings                                        what it is wearing
  joints
    root  head  torso  leftArm  rightArm  leftLeg  rightLeg
```

Four of those are separate classes on purpose. The `Character` is the shape, the `Humanoid` decides
how it moves, the `Appearance` decides what it is drawn in, and the `Animator` decides what it is
posed as. One class holding all of it was on its way to existing: `Character` reached sixty-five
properties and the engine refused to build it.

The geometry is the game's own, converted once: the pivots, the boxes hung off them, and how far
each shell stands proud of the box under it. An arm turns two pixels below its own top, the legs
pivot at ±1.9, and a box sits off-centre from its pivot. Those numbers are exact, down to the pixel.

## Posing: joints, not limbs

A limb's `cframe` is engine output. The engine composes it every tick from where the rig says the
joint stands and the turn your script wrote at that joint. Writing the limb writes the output, and
the next tick overwrites it.

```lua
-- wrong: writing the output
character.rightArm.cframe = cframe.angles(math.pi, 0, 0)

-- right: the turn at the joint
character.joints.rightArm.transform = cframe.angles(math.pi, 0, 0)
```

To pose a body, write `transform` on the joint that holds the limb you want to move. A `Joint` holds
one thing onto another:

| Property | What it is |
|---|---|
| `part0` | what it hangs off |
| `part1` | what hangs off it |
| `c0` | where the joint sits on `part0`: the engine's, and it moves when the body is rescaled |
| `c1` | where it sits on `part1`, usually nothing |
| `transform` | the turn at the joint. **this is the one a place writes** |
| `scale` | how big the limb it holds is drawn, over the body's own scale |

`joints.root` is the body's own joint. Its `transform` tilts the whole body, which is how a body
lies down without any limb knowing it did.

`scale` on a joint grows the limb **away from its joint** rather than about the middle of its box,
so a head at `vec3(2, 2, 2)` is still joined at the neck:

```lua
character.joints.head.scale = vec3(2.2, 2.2, 2.2)
```

> [!IMPORTANT]
> The engine never writes a property you can write, and you never write one it composes. If a pose
> you set disappears on the next tick, you wrote a limb where you meant to write its joint.

<!-- demo:character-joints -->

## Adding a limb

A body is six boxes because the model is six boxes. The engine holds as many as you hang on it. Put
a seventh on a joint of your own and it is composed like the rest. It is not animated, because there
is no walk cycle for a tail.

```lua
local tail = character:add("Part", { name = "tail", size = vec3(0.2, 0.2, 0.8), collides = false })
character.joints:add("Joint", { name = "tail", part0 = character, part1 = tail,
                                c0 = cframe(0, 0.8, 0.2) })
```

- `collides = false` keeps the tail out of the physics, so it does not push the body around or catch
  on walls while it swings.
- `c0` is where the joint sits on `part0`, so this one hangs the tail off the body 0.8 m up and
  0.2 m forward.
- The tail's `cframe` is now engine output like every other limb's. Turn it by writing `transform`
  on `character.joints.tail`.

A `Part` hung on a joint is drawn flat in its own colour. To have the new box cut out of a skin
sheet instead, add a `Limb`. See [Limbs, and adding one](#limbs-and-adding-one) below.

## Who owns the limbs

```lua
character.animate = false
```

With `animate` on, the engine poses the body every tick from the animation state below. With it off
the engine stops writing the joints entirely and your place has the rig to itself.

You do not have to choose all or nothing. The state below is a set of plain properties, so you can
set where a body looks or whether it crouches without taking a single limb away from the engine.

Animation tracks are laid over either one. With `animate` off they are the only thing moving the
joints. See [Animations](animation.md).

## The animation state

Everything the body is doing, all writable:

| Property | What it is |
|---|---|
| `lookPitch` `lookYaw` | where the head looks, relative to the body |
| `moveDistance` `moveSpeed` | how far it has walked and how fast: **distance, not time** |
| `speedValue` | what the limb swing is divided by: a longer stride swings *less* |
| `crouching` | |
| `attackTime` `attackLeft` | the swing, zero to one, and which arm it belongs to |
| `rightArmPose` `leftArmPose` | `empty` `item` `block` `bow` `trident` `crossbowCharge` `crossbowHold` `spyglass` `horn` `brush` |
| `usingItem` `useLeftHand` `mainLeft` | which arm is posed first, and whether the other gets its own pose |
| `chargeProgress` | how far through winding a crossbow |
| `swimAmount` `inWater` `crawling` | |
| `riding` | folds the legs; a player's body sets it while riding or while its humanoid sits |
| `flying` `flyingTime` `flyingYaw` | under an elytra: the tilt and the bank |
| `sleeping` `bedYaw` | lies the body along the bed |
| `deathTime` | twenty ticks from upright to flat |
| `spinning` `frozen` `upsideDown` | |
| `hurt` `whiteFlash` | washes the body red, or white |

The walk runs on **distance walked**, not on time, so a body animates the same however long the
frame took. To walk a body the engine does not drive, feed it ground:

```lua
game.stepped:connect(function(dt)
    clock += dt
    mannequin.moveDistance = clock * 8
    mannequin.moveSpeed = 1
end)
```

- `moveDistance` is the total distance the body has covered. Add to it every tick and the legs swing.
  Stop adding to it and the legs stop where they are.
- `moveSpeed` tells the walk how fast that ground is going by, which is what decides how far each
  swing goes.

<!-- demo:character-walk -->

## What a body is drawn in

A body is **never** drawn as flat boxes. The engine takes the first sheet it finds, in this order:
what the place asked for, then the skin of the player who drives the body, then the game's own.

```lua
statue.appearance.skin = "minecraft:textures/entity/player/wide/alex.png"
statue.appearance.slim = true
```

The skin lives on the `appearance`, not on the body, because the sheet is a thing a client can see
and the server cannot. `slim` is the sheet's business too: the arms are drawn a texel narrower and
nothing about what the body collides with changes.

The unwrap is the game's own, read out of how it builds a cube's six quads rather than guessed at.
Each face gets the rect the skin format puts it in, the right way round.

Per-limb tint **multiplies** the texture rather than replacing it, so a grey statue is a grey skin
rather than a grey box:

```lua
for _, limb in ipairs({ "head", "torso", "leftArm", "rightArm", "leftLeg", "rightLeg" }) do
    statue[limb].color = color(0.55, 0.55, 0.62)
end
```

Transparency is per limb too, and the second layer over each limb is yours to switch off:

```lua
character.head.overlay.visible = false
character.leftArm.transparency = 0.5
```

## Shape and display

```lua
character.scale = 2                     -- rebuilds the rig rather than stretching what is drawn
character.radius = 0.4                  -- the capsule it collides with, which scale does not follow
character.height = 2.4
character.appearance.display = "hitbox" -- "model", "hitbox" or "hidden"
```

- **`scale`** rebuilds the rig at a new size, so the joints move with it and the body keeps its
  proportions. Leave it alone and the body is the size the game's model is.
- **`radius`** and **`height`** are the capsule the body collides with. `scale` does not follow them,
  which is deliberate: a place that wants a bigger character says so on both, on purpose.
- **`display`** is a render policy. It is read where the body is drawn rather than written into the
  limbs, so it does not forget which limbs you hid, and it says nothing about what a ray can reach.
  A body drawn as its collision volume is still a body standing there.

The `hitbox` child is engine output, like every limb is. The engine writes it from `radius` and
`height`, and writing it back does nothing. It is also a box where the collision is a capsule, so in
`hitbox` mode you are looking at the capsule's bounds rather than the capsule.

## What you see of your own body

The game has one answer to what a player sees of themselves, and it is a hand. The body picks what
its player sees, so a place with a person and a spider in it does not have to pick one answer for
both.

```lua
body.appearance.firstPerson = "arm"   -- the game's own arm, in the player's skin (the default)
body.appearance.firstPerson = "hand"  -- the same as "arm"
body.appearance.firstPerson = "body"  -- all of it, from inside its own head
body.appearance.firstPerson = "none"  -- nothing
```

- `"arm"` and `"hand"` both draw the game's own first person arm, with its animations and what the
  player really holds, in the player's own skin.
- `"none"` hides the arm, unless the place turns the `hand` switch on in `[features]`. With that
  switch on the arm always shows, for `"none"` too. See [the place file](place.md).
- `"body"` hides the arm even with the switch on, because the body is drawn instead.
- A player with no body sees no arm, unless the `hand` switch is on.

<!-- demo:character-firstperson -->

`"body"` is the one the game has never had: look down and you are standing there. It needs nothing
special of the renderer. The camera sits in the head, and a box seen from the inside shows nothing
because its faces point away.

## Limbs, and adding one

Every box a body is drawn from is a `Limb`: the six, the second layer over each, the cape, the
wings, each plate of armour, a worn head, the ears. A `Limb` carries its own cut out of a sheet:

| Property | What it is |
|---|---|
| `u`, `v` | where on the sheet the box is cut from, in texels |
| `texels` | how big the box is *on the sheet*, not how big it is drawn |
| `sheetWidth`, `sheetHeight` | how big that sheet is (a skin is 64×64, an elytra 64×32) |
| `sheet` | which sheet, or empty for the body's own |
| `mirrored` | the same rect read the other way round in x |
| `cutout` | a blank texel is nothing rather than black |

`texels` and `size` are deliberately separate. The game grows a wing by a texel on every side and
leaves its rect sized for the box before it grew, so a renderer that derives its rect from the drawn
size is wrong on every grown box there is.

A seventh limb is a limb:

```lua
local tail = body.torso:add("Limb", {
    name = "tail",
    size = vec3(0.125, 0.5, 0.125),
    texels = vec3(2, 8, 2),
    u = 0, v = 56,
    collides = false,
})

body.joints:add("Motor", {
    name = "tail", part0 = body.torso, part1 = tail, c0 = cframe(0, -0.35, 0.15),
})
```

- `size` is how big the tail is in the world, in metres. `texels` is how big its rect is on the
  sheet. The two describe different things and you set both.
- `u = 0, v = 56` reads that rect out of the bottom-left of a 64×64 skin.
- The `Motor` hangs the tail off the torso, so the tail follows the torso's pose.

It is drawn through the sheet like the rest, and the engine leaves what it did not build alone,
including the joint holding it.

A `Part` that is *not* a limb (a sword in a hand, a torch on a grip) is drawn flat in its own
colour. That is where the line falls. It used to be a table of six names, which is why everything
the table did not name was drawn twice.

## Armour

Four slots, ten boxes. Each plate is the limb it covers again, a little bigger, sharing its middle,
so it swings and crouches with the limb without being posed.

```lua
body.armour.head = "minecraft:textures/entity/equipment/humanoid/diamond.png"
body.armour.chest = "minecraft:textures/entity/equipment/humanoid/iron.png"
body.armour.legs = "minecraft:textures/entity/equipment/humanoid_leggings/iron.png"
body.armour.feet = "minecraft:textures/entity/equipment/humanoid/gold.png"
body.armour.hat = "minecraft:textures/entity/skeleton/skeleton.png"  -- takes the helmet's place
```

Four different sheets on one body is normal, and each lands in its own batch. The growth per piece
is the game's: a whole texel on head, chest and boots, half on leggings, and a tenth taken back off
anything covering a leg. That is exactly what stops each piece coming through the one under it.

## Dressing a body in one go

`applyDescription` writes a whole look, and what walks around in it, from one table. It runs in a
server script.

```lua
-- server
body.humanoid:applyDescription({
    skin = "res://skins/guard.png",
    slim = false,
    hat = "minecraft:textures/entity/skeleton/skeleton.png",
    chest = "minecraft:textures/entity/equipment/humanoid/iron.png",
    scale = 1.2,
    walkSpeed = 8,
    jumpPower = 9,
    maxHealth = 40,
    health = 40,
})
```

Each key lands on the instance that owns it:

| Key | Where it lands |
|---|---|
| `skin`, `slim`, `ears` | the body's `appearance` |
| `hat`, `head`, `chest`, `legs`, `feet`, `hatLayered` | the body's `armour` |
| `walkSpeed`, `jumpPower`, `health`, `maxHealth` | the `humanoid` |
| `scale`, `height`, `radius` | the body itself |

- A key that is not one of these is an error that names it, so a typo is caught rather than ignored.
- It is the same as writing those properties one at a time. Nothing is remembered as a description:
  changing `body.appearance.skin` afterwards is not undone by anything.
- `getAppliedDescription()` reads a table back: `skin`, `slim`, `ears`, `scale`, `height`, `radius`,
  `walkSpeed`, `jumpPower` and `maxHealth`. The armour and the current `health` are not in it.
- `health` and `maxHealth` in the same call are both in place before the next tick, so raising both
  at once works whichever way round the table is read.

<!-- demo:character-description -->

A description is a plain table applied once, not an instance you keep and apply again.

## Riding something that moves

A body standing on a part that moves is **hung off that part**. Its `parent` becomes the part, and
its `cframe` becomes a frame stated against the part. Step off and it goes back under the world.

That reparenting is the whole of being carried. What gets interpolated between ticks is a **local**
frame, so a body hanging off a turning deck has a frame that is not moving at all, and the arc comes
out of the deck's own rotation being interpolated as a rotation. A body left at the top of the tree
is interpolated in a straight line through the world, which for anything carried round is the chord
of an arc: right at both ends of a tick, wrong in the middle, wrong again twenty times a second.

The engine only does this for a body a player is bound to, because that is the only one it is told
about. A character a place made and wants carried is parented by the place:

```lua
local deck = world:add("Part", { name = "deck", ... })
local rider = deck:add("Character", { cframe = cframe(-2, 0.25, -2) })
```

Because `rider` is a child of `deck`, its `cframe` is stated against the deck, and it rides along
when the deck moves or turns.

> [!NOTE]
> Positions crossing the API are **world** positions either way (`player:spawn`, `humanoid.walkTo`,
> `worldCframe`) and are converted through whatever the body hangs off. Only `cframe` is local,
> which is true of every instance.

## Who writes what

Every property has exactly **one** writer. Where the engine has to compute something a place also
wants hold of, they are two properties rather than one.

| | You write | The engine writes |
|---|---|---|
| A joint | `transform`, the turn at it | `c0`, where it stands, which moves when the body grows |
| A limb's size | `scale` on its joint | the box, derived from both scales |
| A limb's look | `visible`, `color`, `transparency` | nothing |
| A limb's frame | nothing | `cframe`, composed from the joint |
| The volume | `radius`, `height` | the `hitbox` part |

The engine never writes a property you can write. That is what lets you take a limb away, recolour
one, or hold a pose of your own without the next tick putting it all back:

```lua
body.leftArm:destroy()               -- and it stays gone
body.head.visible = false            -- and it stays hidden
body.joints.head.scale = vec3(2,2,2) -- a big head, still joined at the neck
```

## Movement

The movement profile is in metres and seconds, and the engine converts to whatever the tick rate is.

```lua
character.walkSpeed = 6
character.jumpPower = 9        -- the speed you leave the ground at, not a height
character.gravityScale = 1
character.groundAcceleration = 36
character.airDrag = 0.667      -- the fraction of speed a second of air leaves you with
character.stepHeight = 0.6
character.slopeLimit = 45
character.coyoteTime = 0.15
```

- **`walkSpeed`** is metres a second. At 6 the body crosses six metres in a second whatever the tick
  rate is.
- **`jumpPower`** is the speed the body leaves the ground at. Raise it and the body goes higher,
  because how high it gets falls out of that speed and the gravity pulling it back.
- **`gravityScale`** is this body's own share of the pull. Leave it at 1 and the body falls the way
  the place falls.
- **`stepHeight`** is how tall a ledge the body walks up without jumping, and **`slopeLimit`** is how
  steep a slope it walks up at all, in degrees.
- **`coyoteTime`** is how long after walking off an edge a jump still counts.

The full profile, the states a body can be in and the events it fires are in
[Movement, collision and input](movement.md).

## Finding your own

On the client only:

```lua
local body = game.players:me()   -- nil until one arrives
```

It is a call rather than a field because a respawn and a reload both hand out a new body, and a
reference held across either points at something destroyed. Call it again where you need it.
