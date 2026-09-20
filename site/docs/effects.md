# Particles, beams, trails, highlights and decals

Effects are instances that draw something the world does not have geometry for: sparks, flame, a
laser, a glow around a crate, a picture on a wall. Put one in the tree and every client draws it. Take
it out and it is gone. They draw in play and in the editor viewport. In the Explorer,
**Insert > Effects** lists all twelve.

| Class | Goes inside | What it draws |
|---|---|---|
| `ParticleEmitter` | a part, a model or an attachment | particles you describe |
| `Fire` | a part or an attachment | flames |
| `Smoke` | a part or an attachment | rising puffs |
| `Sparkles` | a part or an attachment | glints falling out every way |
| `Beam` | anywhere | a ribbon between two attachments |
| `Trail` | anywhere | a ribbon left behind two moving attachments |
| `Highlight` | anything, or set `adornee` | a fill and outline over parts, mesh parts, models and characters |
| `Decal` | a part | an image on one face |
| `Texture` | a part | an image tiled across one face |
| `SelectionBox` | anything, or set `adornee` | a box around it, always on top |
| `SelectionSphere` | anything, or set `adornee` | a sphere around it, always on top |
| `Explosion` | the world | a blast that pushes parts and kills bodies |

The **Goes inside** column is what you parent the effect to, and it decides where the effect draws.
An emitter inside a part sends particles out of that part, and a decal inside a part puts an image on
that part's face.

Sizes and distances are metres. Colours are 0 to 1. `transparency` is 0 solid and 1 gone.

> [!NOTE]
> Unlike the rest of Moud, `spreadAngle` and particle rotations are **degrees**. A `spreadAngle` of
> 25 is 25 degrees, and `rotSpeedMin = 90` is a quarter turn a second.

## Images

`texture` on a particle emitter, beam, trail, decal or texture takes:

| Value | Image |
|---|---|
| `"res://effects/spark.png"` | an image in the place |
| `"flame"`, `"glint"`, `"big_smoke_0"` | a Minecraft particle, from `textures/particle/` |
| `"minecraft:block/stone"` | any other game texture, by its path under `textures/` |
| `canvas.uri` | an image a script drew or loaded, see [Images](images.md) |
| `""` | a soft round dot on emitters, a plain colour on beams and trails |

Game textures keep their pixels sharp. Images in the place are smoothed. An image that cannot be
found draws the dot, and is looked for again a second later, so a file you add while the place is
running appears without a reload.

## Light

Particles, beams and trails share three numbers that decide how bright they are drawn:

| Property | |
|---|---|
| `brightness` | multiplies the colour; above 1 glows |
| `lightEmission` | 0 blends normally, 1 adds its light to what is behind it, like fire or a laser |
| `lightInfluence` | 0 is always fully bright, 1 darkens in shadow and at night like a block |

Raise `brightness` and `lightEmission` for anything that is meant to be a source of light itself, such
as sparks or a laser. Raise `lightInfluence` for anything that is meant to sit in the world, such as
dust or falling leaves, so it goes dark at night with everything around it.

None of them light up the world. Add a [light](rendering.md#lights) next to the effect for that.

## A campfire

Most effects are one `add` call with a table of properties. A campfire is four of them in the same
part:

```lua
local pit = world:add("Part", {
    name = "campfire",
    size = vec3(1, 0.2, 1),
    cframe = cframe(0, 64.1, 0),
    color = color(0.25, 0.18, 0.12),
})

pit:add("Fire", { size = 2, heat = 12 })
pit:add("Smoke", { size = 1.5, opacity = 0.25, riseVelocity = 2, color = color(0.35, 0.35, 0.35) })
pit:add("PointLight", { color = color(1, 0.6, 0.3), brightness = 2, range = 10 })

pit:add("ParticleEmitter", {
    name = "embers",
    texture = "spark_4",
    rate = 6,
    lifetimeMin = 0.8, lifetimeMax = 1.6,
    speedMin = 2, speedMax = 4,
    spreadAngle = 25,
    acceleration = vec3(0, -3, 0),
    drag = 1,
    sizeStart = 0.15, sizeEnd = 0,
    colorStart = color(1, 0.8, 0.3), colorEnd = color(1, 0.3, 0.1),
    brightness = 2,
    lightEmission = 1,
})
```

- The `Fire` and the `Smoke` draw the flames and the puffs. Neither one lights anything, which is why
  the `PointLight` is there as well.
- The emitter sends six embers a second, each living between 0.8 and 1.6 seconds and leaving at
  between 2 and 4 metres a second. `spreadAngle = 25` lets each one stray up to 25 degrees from
  straight up, so they rise in a loose cone.
- `acceleration` pulls them back down at three metres a second squared, and `drag` slows them as they
  go, which is what makes an ember arc over and fade rather than fly off.
- `sizeStart` and `sizeEnd` shrink each ember to nothing, while `colorStart` and `colorEnd` cool it
  from yellow to red over the same life.

## Particle emitters

A `ParticleEmitter` sends particles out of what it is inside. From a part they start inside its box.
From an attachment or a model they start at its point. Inside anything else an emitter sends nothing.

| Property | Default | |
|---|---|---|
| `enabled` | true | off stops the steady flow; particles already out finish, and `emit` still works |
| `texture` | `""` | see [Images](#images) |
| `rate` | 20 | particles a second |
| `lifetimeMin`, `lifetimeMax` | 5, 10 | seconds; each particle picks between the two |
| `speedMin`, `speedMax` | 5, 5 | metres a second as it leaves |
| `spreadAngle` | 0 | degrees a particle may stray from `emissionDirection`, the same in every direction; 180 sends them every way |
| `emissionDirection` | `"top"` | which face they leave from, in the frame of what the emitter is inside |
| `shapeStyle` | `"volume"` | `volume` starts them anywhere in the part, `surface` only on its faces |
| `acceleration` | 0, 0, 0 | a pull in world space, metres a second squared; gravity is `vec3(0, -9.8, 0)` |
| `drag` | 0 | a particle loses half its speed every `1 / drag` seconds |
| `rotationMin`, `rotationMax` | 0, 0 | starting angle, degrees |
| `rotSpeedMin`, `rotSpeedMax` | 0, 0 | spin, degrees a second |
| `sizeStart`, `sizeEnd` | 1, 1 | width in metres when born and when it dies |
| `transparencyStart`, `transparencyEnd` | 0, 0 | |
| `colorStart`, `colorEnd` | white, white | |
| `brightness`, `lightEmission`, `lightInfluence` | 1, 0, 0 | see [Light](#light) |
| `orientation` | `"facingCamera"` | see below |
| `lockedToPart` | false | on, particles move with the part after they leave; off, they stay in the world |
| `timeScale` | 1 | 1 is normal speed, 0 freezes them |

Every curve is a start and an end value, with no points in between. The value blends from one to the
other over the particle's life, so a particle with `sizeStart = 1` and `sizeEnd = 0` is half a metre
wide when it is halfway through.

Because `emissionDirection` is read in the frame of whatever the emitter is inside, turning that part
turns the spray. An emitter in a part you tip on its side sprays sideways.

`orientation` decides which way each particle faces the camera:

| `orientation` | Each particle |
|---|---|
| `facingCamera` | turns fully to the camera |
| `facingCameraWorldUp` | turns to the camera but stays upright |
| `velocityParallel` | stands along where it is going, turned towards the camera |
| `velocityPerpendicular` | faces where it is going |

`velocityParallel` is what makes a spark look like a streak, because the image is stretched along the
direction the particle is travelling.

One emitter holds at most 8192 particles at once. `rate` times the longest lifetime tells you how many
an emitter keeps alive, so at `rate = 20` and `lifetimeMax = 10` an emitter carries 200.

<!-- demo:effects-emitter -->

## Bursts

`emitter:emit(count)` sends out `count` particles at once, 16 when you leave it out, up to 10000.
Set `rate = 0` for an emitter that only bursts.

```lua
-- server: a crate that bursts when clicked
local crate = world:find("Crate") :: Part
local burst = crate:add("ParticleEmitter", {
    rate = 0,
    speedMin = 3, speedMax = 7,
    spreadAngle = 180,
    lifetimeMin = 0.4, lifetimeMax = 0.9,
    acceleration = vec3(0, -9.8, 0),
    sizeStart = 0.2, sizeEnd = 0,
    colorStart = color(1, 0.9, 0.4),
    lightEmission = 1,
})
local detector = crate:add("ClickDetector", {})

detector.mouseClick:connect(function(player)
    burst:emit(40)
end)
```

- `rate = 0` means the emitter never sends anything by itself. It sits in the crate doing nothing
  until a click calls `emit`.
- `spreadAngle = 180` sends the 40 particles in every direction rather than out of one face.
- `acceleration = vec3(0, -9.8, 0)` pulls them down at the usual gravity, so the burst arcs and falls.
- Every particle is gone within 0.9 seconds, which is `lifetimeMax`.

How a burst reaches the other players:

- On the server, `emit` adds to `emitted`. That count replicates, and every client emits the
  difference. A client that reaches the emitter late (joining, or being sent a branch it did not
  have) starts from the count it arrives with, so it never replays bursts from before it was
  there.
- On a client, `emit` happens only on that client.

> [!IMPORTANT]
> Leave `emitted` alone; it only exists to carry bursts across. Writing it yourself makes clients
> emit the difference between the old value and the new one.

## Fire, smoke and sparkles

`Fire`, `Smoke` and `Sparkles` are ready-made emitters with fewer knobs. Reach for one when you want
the usual look and do not want to set fifteen properties.

| Class | Properties |
|---|---|
| `Fire` | `enabled`, `size` (0.2 to 30), `heat` (0 to 25, how fast the flames rise), `color`, `secondaryColor` (what the flames fade to), `timeScale` |
| `Smoke` | `enabled`, `size` (0.1 to 100), `opacity`, `riseVelocity` (-25 to 25, negative sinks), `color`, `timeScale` |
| `Sparkles` | `enabled`, `sparkleColor`, `timeScale` |

Turning one off stops new flames, puffs or glints. What is already out finishes, so a fire you switch
off burns down over the next moment rather than vanishing.

## A laser

A `Beam` draws a ribbon from `attachment0` to `attachment1`. Any part or attachment works as either end.

```lua
local turret = world:find("Turret") :: Part
local target = world:find("Target") :: Part

turret:add("Beam", {
    attachment0 = turret:add("Attachment", { cframe = cframe(0, 0, -0.5) }),
    attachment1 = target:add("Attachment", {}),
    texture = "res://effects/laser.png",
    textureMode = "wrap",
    textureLength = 0.5,
    textureSpeed = 4,
    colorStart = color(1, 0.1, 0.1), colorEnd = color(1, 0.4, 0.2),
    transparencyStart = 0, transparencyEnd = 0,
    width0 = 0.15, width1 = 0.15,
    faceCamera = true,
    brightness = 2,
    lightEmission = 1,
})
```

- The two attachments are the ends. One sits half a metre out of the front of the turret and the
  other in the middle of the target, and the beam follows them: move either part and the beam moves
  with it on the next frame.
- `textureMode = "wrap"` with `textureLength = 0.5` repeats the image every half metre, so a long beam
  and a short one have the same sized markings.
- `textureSpeed = 4` scrolls the image four lengths a second from the turret towards the target, which
  is what makes the laser look like it is flowing.
- `faceCamera = true` turns the ribbon's flat side towards each player, so the beam never goes
  edge-on and disappears.
- `brightness = 2` with `lightEmission = 1` adds the beam's colour to whatever is behind it, which is
  what gives it the glow.

| Property | Default | |
|---|---|---|
| `enabled` | true | |
| `attachment0`, `attachment1` | | where it starts and ends; with either missing, nothing draws |
| `texture` | `""` | see [Images](#images) |
| `textureMode` | `"stretch"` | `stretch` fits the image to the whole beam and scrolls it; `wrap` repeats it every `textureLength` metres and scrolls it; `static` repeats it the same way and holds it still from `attachment0` |
| `textureLength` | 1 | `stretch`: how many times the image repeats; otherwise metres per repeat |
| `textureSpeed` | 1 | image lengths a second the image scrolls, from `attachment0` towards `attachment1`; `static` ignores it |
| `colorStart`, `colorEnd` | white, white | at `attachment0` and at `attachment1` |
| `transparencyStart`, `transparencyEnd` | 0.5, 0.5 | |
| `width0`, `width1` | 1, 1 | metres at each end |
| `curveSize0`, `curveSize1` | 0, 0 | how far the beam bows out of each attachment, along its X axis |
| `segments` | 10 | straight pieces the curve is drawn with, 1 to 1000 |
| `faceCamera` | false | on, it turns its flat side to the camera; off, it lies flat facing the attachments' up axis |
| `brightness`, `lightEmission`, `lightInfluence` | 1, 0, 0 | see [Light](#light) |

With both curve sizes 0 the beam is straight. Otherwise it is a curve that leaves `attachment0` along
its X axis and arrives at `attachment1` along its X axis, so turning the attachments shapes it. Raise
`segments` when a tight curve looks like a run of straight lines.

<!-- demo:effects-beam -->

## A sword trail

A `Trail` follows two attachments and leaves a ribbon across the gap between them. You put both
attachments on the same moving part, one at each edge of the shape you want the ribbon to be as wide
as.

```lua
local blade = world:find("Sword") :: Part
local trail = blade:add("Trail", {
    attachment0 = blade:add("Attachment", { cframe = cframe(0, -1, 0) }),
    attachment1 = blade:add("Attachment", { cframe = cframe(0, 1, 0) }),
    enabled = false,
    lifetime = 0.25,
    colorStart = color(1, 1, 1), colorEnd = color(0.6, 0.8, 1),
    transparencyStart = 0.2, transparencyEnd = 1,
    widthScaleEnd = 0.3,
    lightEmission = 1,
})

local function swing()
    trail:clear()
    trail.enabled = true
    -- turn the blade here
    task.delay(0.3, function() trail.enabled = false end)
end
```

- The attachments are two metres apart along the blade, so the ribbon is as wide as the blade is long.
- `enabled = false` at creation means the trail lays nothing until a swing starts. Without it the
  sword would smear a ribbon behind the player as they walk.
- `trail:clear()` before each swing wipes whatever is left of the last one, so the first swing and the
  tenth look the same.
- `lifetime = 0.25` means each piece of the ribbon fades out a quarter of a second after it is laid,
  and `widthScaleEnd = 0.3` narrows the ribbon to a third of its width as it goes.

| Property | Default | |
|---|---|---|
| `enabled` | true | off lays no new points; what is left fades away |
| `attachment0`, `attachment1` | | the two edges; with either missing, the trail is wiped |
| `texture`, `textureLength` | `""`, 1 | as on a beam |
| `textureMode` | `"stretch"` | `stretch` fits the image to the whole trail; `wrap` repeats it by distance and keeps it still against the attachments; `static` repeats it by distance and leaves each repeat where it was laid in the world |
| `lifetime` | 2 | seconds a piece lasts, up to 20 |
| `minLength` | 0.1 | how far the attachments must move before a new point is laid |
| `maxLength` | 0 | the longest it gets, in metres; 0 is no limit |
| `colorStart`, `colorEnd` | white, white | where it is new and where it is about to vanish |
| `transparencyStart`, `transparencyEnd` | 0, 1 | |
| `widthScaleStart`, `widthScaleEnd` | 1, 1 | scales the gap between the attachments along its life |
| `faceCamera` | false | on, it turns its flat side to the camera |
| `brightness`, `lightEmission`, `lightInfluence` | 1, 0, 0 | see [Light](#light) |

`trail:clear()` wipes what is behind it. Like `emit`, on the server it adds to `clears` and every
client wipes; on a client it wipes only there. A client that reaches the trail late starts from the
count it arrives with, so an old wipe never happens twice.

## Highlighting what the pointer is on

A `Highlight` lays a colour over parts and draws an outline around them. It covers `adornee`, or its
parent when `adornee` is empty, and every part inside it, so one highlight covers a whole model or
character.

```lua
-- client: glow the crate the pointer is on
for _, crate in world:byTag("crate") do
    local detector = crate:find("ClickDetector") :: ClickDetector
    local glow = crate:add("Highlight", {
        enabled = false,
        fillColor = color(1, 0.9, 0.3), fillTransparency = 0.8,
        outlineColor = color(1, 0.9, 0.3),
        depthMode = "occluded",
    })
    detector.mouseHoverEnter:connect(function() glow.enabled = true end)
    detector.mouseHoverLeave:connect(function() glow.enabled = false end)
end
```

- One highlight is made per crate and left switched off, and the hover events only flip `enabled`.
  Creating the highlight once is cheaper than adding and destroying one on every hover.
- `fillTransparency = 0.8` lets most of the crate's own colour through, so the glow reads as a tint
  rather than as paint.
- `depthMode = "occluded"` hides the parts of the glow that are behind something else, so a crate
  round a corner does not shine through the wall.

The highlight is made on the client, so only that player sees it. The hover events are in
[Click detectors](zones-and-triggers.md#click-detectors).

| Property | Default | |
|---|---|---|
| `enabled` | true | |
| `adornee` | empty | what it covers; empty uses its parent |
| `fillColor`, `fillTransparency` | red, 0.5 | the colour laid over it; 1 leaves the inside as it is |
| `outlineColor`, `outlineTransparency` | white, 0 | the line around it; 1 hides the line |
| `depthMode` | `"alwaysOnTop"` | `alwaysOnTop` shows it through walls, `occluded` hides what is behind something |

Invisible parts and parts with `transparency = 1` are left out.

> [!NOTE]
> There is no limit on how many highlights a scene has, but each one is its own pass over the screen.
> Switch them off with `enabled` rather than keeping hundreds on at once.

## A sign and a tiled floor

A `Decal` stretches an image over one face of its parent part. A `Texture` is a decal that tiles.

```lua
local board = world:add("Part", { name = "sign", size = vec3(2, 1, 0.1), cframe = cframe(3, 65, 0) })
board:add("Decal", { texture = "res://decals/shop.png", face = "front" })

local floor = world:add("Part", { name = "floor", size = vec3(12, 0.2, 12), cframe = cframe(0, 63.9, 0) })
floor:add("Texture", {
    texture = "minecraft:block/oak_planks",
    face = "top",
    studsPerTileU = 1, studsPerTileV = 1,
})
```

The decal fills the sign's whole front face however big the part is. The texture on the floor repeats
the plank image once every metre, so the twelve metre floor is covered by 144 copies of it, and
widening the floor adds more copies instead of stretching the ones it has.

| Property | Default | |
|---|---|---|
| `texture` | `""` | see [Images](#images); empty draws nothing |
| `face` | `"front"` | `front`, `back`, `left`, `right`, `top` or `bottom` |
| `color` | white | tints the image |
| `transparency` | 0 | |
| `zIndex` | 1 | higher draws over lower on the same face |

`Texture` adds:

| Property | Default | |
|---|---|---|
| `studsPerTileU`, `studsPerTileV` | 1, 1 | metres one copy of the image covers across and down the face |
| `offsetStudsU`, `offsetStudsV` | 0, 0 | slides the image across and down, in metres |

A decal takes the light of its part and shades like a block face. It only draws inside a part.

## Selection boxes and spheres

A `SelectionBox` draws the edges of a box around what it is on, and a `SelectionSphere` draws a
sphere. Both always draw on top of everything and ignore light, so they read as markers over the world
rather than as objects in it.

```lua
local box = crate:add("SelectionBox", { color = color(0.2, 0.6, 1), lineThickness = 0.04 })
local ring = world:add("SelectionSphere", { adornee = crate, surfaceTransparency = 0.8 })
```

The box is inside the crate, so it goes around the crate. The sphere is in the world and reaches the
crate through `adornee`, which is how you mark something you cannot or do not want to add children to.

| Property | Default | |
|---|---|---|
| `visible` | true | |
| `adornee` | empty | what it goes around; empty uses its parent |
| `color`, `transparency` | blue, 0 | the edges, or the rings of a sphere |
| `surfaceColor`, `surfaceTransparency` | blue, 1 | the faces; 1 leaves them empty |
| `lineThickness` | 0.05 | `SelectionBox` only, metres |

Around a model the box wraps every part inside, turned with the model. The sphere passes through the
corners of that box, so it is wider than the model rather than tight to it.

## Explosions

An `Explosion` goes off. Adding one to the world from a server `Script` sets a blast at `position`,
and the explosion takes itself out of the tree afterwards, so you never destroy one yourself. Nothing
goes off while you edit a scene.

```lua
-- server: a grenade that goes off three seconds after it is thrown
local function grenade(from, velocity)
    local bomb = world:add("Part", {
        name = "grenade", shape = "ball", size = vec3(0.4, 0.4, 0.4),
        cframe = cframe(from), anchored = false, color = color(0.2, 0.25, 0.2),
    })
    bomb:applyImpulse(velocity * bomb:getMass())

    task.delay(3, function()
        if not bomb.parent then return end
        local blast = world:add("Explosion", {
            position = bomb.worldCframe.position,
            blastRadius = 8,
            blastPressure = 400000,
            destroyJointRadiusPercent = 0.4,
            explosionType = "noCraters",
        })
        blast.hit:connect(function(part, distance)
            print("caught", part.name, "at", distance)
        end)
        bomb:destroy()
    end)
end
```

- The bomb is an unanchored ball, so it flies and rolls. Multiplying `velocity` by its mass gives it
  the speed you asked for whatever its density is. See [Physics](physics.md#pushing-a-part).
- `if not bomb.parent then return end` covers the case where something destroyed the grenade during
  those three seconds. Without it the next line reads a position off an instance that is gone.
- `position` is taken from `bomb.worldCframe.position` at the moment it goes off, which is wherever
  the grenade rolled to, not where it was thrown from.
- `destroyJointRadiusPercent = 0.4` kills anything with a humanoid inside 3.2 metres, which is 40 per
  cent of the eight metre radius. Things further out are thrown but survive.
- `explosionType = "noCraters"` leaves the blocks alone, so the blast does not chew a hole in your
  map.

| Property | Default | |
|---|---|---|
| `position` | 0, 0, 0 | where it goes off, in world coordinates |
| `blastRadius` | 4 | how far it reaches, in metres; nothing outside is touched |
| `blastPressure` | 500000 | how hard it throws things |
| `destroyJointRadiusPercent` | 1 | the share of the radius that kills |
| `explosionType` | `"craters"` | `craters` breaks the blocks it reaches, `noCraters` leaves them alone |
| `visible` | true | players see the blast and hear it |
| `hit` | | fires with the part it caught and how far that part was |

It goes off on the next tick, not while your script is still running, so connecting `hit` right after
you add the explosion still catches everything.

What the blast does when it goes off:

- Every part within the radius fires `hit`, and the distance is measured from the part's nearest face
  rather than its middle. Anchored parts and parts that do not collide fire it too; they just are not
  pushed.
- Loose parts and player bodies are thrown straight away from the centre. The push fades to nothing
  at the edge: at 500000 a part right on the centre leaves at 20 metres a second, and one halfway out
  at 10.
- Anything with a humanoid closer in than `destroyJointRadiusPercent` of the radius dies. A
  [`ForceField`](movement.md#force-fields) inside the body still saves it.
- A body is measured from its own position, where it stands, and not from its nearest point.
- `craters` breaks blocks within the radius, never further than 24 metres out however big the radius
  is. It skips blast-resistant blocks like obsidian and bedrock, and nothing drops.
- `hit` does not fire for the limbs of a character, and the blast does not damage the game's own mobs.

<!-- demo:effects-explosion -->

## Server and client

Where you create an effect decides who sees it, and where you put it decides whether it draws at all:

- Effects the server adds show on every client. Effects a client adds show only there.
- Particles and trails move on each client, so two players do not see the same particles in the
  same places.
- A property you write shows on the next frame. Colour, size, transparency and acceleration also
  change particles already out; lifetime, speed and direction only reach new ones.
- Nothing inside a `ViewportFrame` draws.
- Nothing kept out of the world draws: an emitter, beam or trail inside `ServerStorage`,
  `ReplicatedStorage`, a starter folder or a backpack sends nothing, and drops the particles it
  already had when it is moved in. Cloning it out into the world starts it clean. See
  [Containers](containers.md).
