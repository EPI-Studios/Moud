# Physics

A part with `anchored = false` is a real body. While the place plays, it falls, lands, tumbles over
edges, bounces and knocks into other parts. Its `cframe` is written by the simulation and replicates
like any other property, so every player watches the part land in the same spot. The server
simulates it, or the client of a player standing next to it; see
[Who simulates a part](#who-simulates-a-part).

Nothing moves while you edit a scene. Parts start falling when the place plays.

You make a falling part by setting `anchored = false` when you create it, or by writing the property
later:

```lua
local crate = world:add("Part", {
    name = "crate",
    size = vec3(1, 1, 1),
    cframe = cframe(0, 70, 0) * cframe.angles(0.4, 0, 0.3),
    color = color(0.6, 0.4, 0.2),
    anchored = false,
})
```

Four rules decide which parts the simulation touches and how you may move them:

- A part that does not collide has no body. It stays where it is, even unanchored.
- Each unanchored part is its own body. An unanchored part inside another part does not ride along
  with it; join them with a [weld](#welds).
- Writing `cframe` or `position` on an unanchored part teleports it.
- Parts inside a viewport frame are never simulated.

## Shapes

`shape` says what a part is. It starts a `"block"`, which is the box everything used to be. You
change it when a part has to roll, slope or round off, and leaving it alone gives you the box that
`size` describes.

| `shape` | |
|---|---|
| `"block"` | the whole box, as `size` gives it |
| `"ball"` | a sphere as wide as the part's smallest side |
| `"cylinder"` | lies along the part's X axis, as round as the smaller of Y and Z |
| `"wedge"` | tall at the back, sloping down to the bottom front edge |
| `"cornerWedge"` | peaks at the top front right corner and falls away from it |

```lua
local ramp = world:add("Part", {
    name = "ramp",
    shape = "wedge",
    size = vec3(4, 2, 6),
    cframe = cframe(0, 65, -8),
    anchored = true,
})
```

A wedge's tall face is its back, `+Z`, and the slope runs down to the front, `-Z`, so a body walks up
it from in front. Turn the part to aim the ramp. A corner wedge is the same slope cut in two, with
its high corner at `+X, +Y, -Z`.

A ball and a cylinder are drawn as wide as the sides they are round across, and the rest of `size` is
left empty: a ball in a part sized `vec3(4, 1, 1)` is a one metre ball with three metres of nothing
around it.

<!-- demo:physics-shapes -->

> [!TIP]
> Give round parts even sizes. A ball in a part sized `vec3(1, 1, 1)` fills the part, so the box you
> drag in the editor is the ball you get in the game.

```lua
-- a ball that rolls down the ramp
local ball = world:add("Part", {
    shape = "ball",
    size = vec3(1, 1, 1),
    cframe = cframe(0, 70, -10),
    anchored = false,
    elasticity = 0.4,
    friction = 0.8,
})
```

The shape is the real thing for rays, overlaps, nearest points, touches and collision, and for
drawing in the world, in a viewport, under a highlight and in the editor's selection. A ray meets a
ball where the ball is round and passes through the corners it does not fill, and a box beside the
empty half of a wedge does not touch it.

Where it is not exact:

| | |
|---|---|
| a falling ball | a real sphere to the simulation, so it rolls |
| a falling cylinder or wedge | a hull through its points, and a cylinder has 16 sides |
| an anchored shaped part | triangles, 8 sides and 4 rings of them, and they carry no collision group |
| standing on a shaped part while it moves | a stack of 8 boxes under your feet |
| `blockcast` | the box around the shape |
| a ball or a cylinder drawn | flat faces, so the shading is faceted |

In the Explorer, **Insert > Shapes** adds a Ball, Cylinder, Wedge or CornerWedge. The collision view
(**F7**) draws the shape a part really collides as, see [Debugging](debug.md#seeing-collisions).

## Gravity

`game.gravity` is how hard everything falls, in metres per second squared. It starts at 32, which is
Minecraft's own, and is set on the server.

```lua
-- server
game.gravity = 32 / 6      -- the moon
```

What writing it does:

- It pulls straight down, and a change takes effect at once: on parts already falling, and on bodies
  already in the air.
- It also scales how heavy every body feels, on top of each humanoid's own `gravityScale`. A
  humanoid at `gravityScale = 2` in a place at `game.gravity = 16` falls at 32 again. See
  [Movement](movement.md#humanoid).
- Constraints, springs and welds all feel it, so a rope bridge sags less on the moon.
- `0` leaves unanchored parts hanging where they are, and a negative number pushes them up. It goes
  from -500 to 500; anything else is an error that says so.
- It survives a hot reload. Set it from `server/main.luau` if a place wants it back at 32 every run.

## Mass and surfaces

Four properties give a part its weight and decide how it behaves against the surfaces it meets. Two
more are written back by the simulation for you to read.

| Property | Default | |
|---|---|---|
| `density` | 1 | mass is `density` times the part's volume |
| `friction` | 0.5 | how much it grips what it slides on; 0 is ice |
| `elasticity` | 0 | how much it bounces, from 0 (lands dead) to 1 (bounces back as high) |
| `massless` | false | weighs nothing, so welding it to something does not make that heavier |
| `velocity` | | metres per second, written by the simulation |
| `angularVelocity` | | radians per second about each axis, written by the simulation |

```lua
local ball = world:add("Part", { size = vec3(0.5, 0.5, 0.5), cframe = cframe(0, 75, 0),
                                 anchored = false, elasticity = 0.8, density = 0.3 })
print(ball:getMass())      -- 0.0375, which is 0.3 * 0.5 * 0.5 * 0.5
```

`getMass` multiplies `density` by the volume `size` gives, which is why a part twice as wide in every
direction weighs eight times as much at the same density.

Changing these while the place plays takes effect at once. There are no named physical materials
yet; set the numbers.

Writing `velocity` or `angularVelocity` sets how fast the part moves, and is the same as calling `setVelocity` or `setAngularVelocity`. On a
part a player simulates, the player's next report writes over it.

## Pushing a part

You move an unanchored part by giving it an impulse or by replacing its velocity outright. Both are
methods on the part.

> [!IMPORTANT]
> These only work from a server `Script`. A `LocalScript` calling them gets an error, and so does
> calling them on a part that is anchored, does not collide, or was handed to a player by a script.

| | |
|---|---|
| `part:applyImpulse(impulse)` | pushes at the centre |
| `part:applyImpulseAtPosition(impulse, position)` | pushes at a world point, so it also spins |
| `part:applyAngularImpulse(impulse)` | spins it about each axis |
| `part:setVelocity(v)` | replaces how fast it moves |
| `part:setAngularVelocity(v)` | replaces how fast it spins |
| `part:getVelocityAtPosition(position)` | how fast that world point of the part moves |
| `part:getMass()` | its mass; works on the client too |

An impulse is mass times the change in velocity. The same push moves a
light part further than a heavy one. To change a part's speed by a fixed amount whatever it weighs,
multiply by its mass:

```lua
-- server: kick the crate away from whoever touches it
crate.touched:connect(function(other)
    local away = (crate.position - other.position).unit
    crate:applyImpulse((away * 6 + vec3(0, 4, 0)) * crate:getMass())
end)
```

- `(crate.position - other.position).unit` points from whatever touched the crate towards the crate
  and is one metre long, so the crate always leaves in the direction it was hit from.
- `away * 6 + vec3(0, 4, 0)` asks for six metres a second outwards and four upwards, which lifts the
  crate off the floor instead of scraping it along.
- Multiplying by `crate:getMass()` makes a dense crate and a light one leave at the same speed.

<!-- demo:physics-impulse -->

```lua
crate:applyImpulseAtPosition(vec3(0, 0, -5), crate.position + vec3(0.5, 0.5, 0))   -- push and flip
crate:setVelocity(vec3(0, 0, 0))                                                  -- stop dead
crate:setAngularVelocity(vec3(0, 0, 0))
```

Pushing at a point half a metre off the centre is what makes the crate flip: the same impulse through
the centre would slide it without turning it.

### Walking into a part

A body walking into a loose part shoves it. How hard depends on how fast the body walks into it, and
a part heavier than 20 is pushed as if it weighed 20, so a player can still move a heavy crate, only
slowly.

Walking into the side of a part shoves it flat along the ground, at the height of its middle. It
slides and can turn about its upright axis, but it does not tip over or lift. A side is any face
within about 45 degrees of upright. A flatter face, such as the top of a part or a gentle slope, is
pushed straight away from the face at the point the body touches it.

You can push a part in the same tick you add it. Reading `velocity` right after a push still gives
the old value until the next tick.

A part a player's client is simulating is handled differently when the server pushes it. See
[Who simulates a part](#who-simulates-a-part).

## Who simulates a part

Each loose part is simulated in one place: the server, or one player's client. That place is the
part's network owner, and `part.networkOwner` holds it: the owning player's id, or `""` for the
server. It replicates, so server and client scripts can both read it and listen for it changing:

```lua
local cart = world:find("cart") :: Part
cart:getPropertyChangedSignal("networkOwner"):connect(function()
    print(if cart.networkOwner == "" then "the server simulates the cart" else "a player simulates the cart")
end)
```

Choose an owner with the methods under [Choosing the owner from a script](#choosing-the-owner-from-a-script),
not by writing `networkOwner`: it is read-only for scripts, and the engine works the owner out every tick.

This exists so that pushing things feels right. When the server simulates a crate and you walk into
it, your client sends your movement up, the server moves the crate, and the new position comes back.
You feel that round trip as a crate that is soft and late. When your own client simulates the crate,
it moves the moment you touch it and it stops you like a solid object.

The owning client simulates the part and sends its position, rotation and speed to the server about
twenty times a second. The server copies that onto the part, and it replicates to everyone else from
there. The owner is not sent its own part's `cframe`, `velocity` and `angularVelocity` back. While
the reports keep coming, the server's copy of the body no longer falls on its own: it follows the
reported position so other server bodies still bump into it.

Parts joined by welds or constraints form an **assembly**, and an assembly has one owner. A car's
chassis, its welded seat and its wheels on hinges are all simulated in the same place, so the joints
between them run in one simulation. See [Assemblies and vehicles](#assemblies-and-vehicles).

`setOwner` is something else. It lets a client script write an instance's properties on its own
copy, and it does not change who simulates anything. See [Who may write what](movement.md#who-may-write-what).

### The automatic rule

By default the engine picks the owner of each assembly every tick, in this order:

1. A player whose body sits in a `Seat` or `VehicleSeat` of the assembly takes it.
2. An owner who is still connected but has no living body, such as a player waiting to respawn,
   keeps it.
3. The owner keeps it while their body is within 14 metres of the centre of a part of the
   assembly.
4. Otherwise the nearest player whose body is alive and within 10 metres of the centre of a part of
   the assembly takes it. The gap between 10 and 14 stops an assembly from flicking between two players standing
   near each other.
5. With no player close enough, the server has it.

The 10 and 14 metre distances are fixed. There is no setting for them.

A part joined to nothing is an assembly of one, so all of this works the same for a single crate.

<!-- demo:physics-ownership -->

A new owner has 100 ticks (five seconds) to send its first report. Once it has reported, 40 quiet
ticks (two seconds) is enough. Either way, a silent owner loses the assembly, and the server keeps it
for 100 ticks before the automatic rule may hand it out again. One silent part is enough to take back
the whole assembly.

The server does not take a report on trust. It drops a report for a part that player does not own,
one with a number that is not finite or a rotation that is not close to a unit quaternion, one that
puts the part more than 64 metres from the owner's body, and one faster than 400 metres a second.
Anything inside those limits is accepted, so an owner can move its parts wherever it likes within
them. Do not give a player a part whose position decides who wins.

Ownership only happens while the place plays. Opening the editor gives every part back to the server
and forgets every owner a script chose.

### What stays with the server

These parts are never owned by a player, automatically or by a script:

- an anchored part,
- a part that does not collide (but see below for one inside an assembly),
- a part in `ServerStorage`, `ReplicatedStorage`, a `Backpack` or the `StarterPack`,
- a part whose parent is a body (`Character`).

An assembly with one of these in it stays with the server as a whole. Joining a loose part to an
anchored one keeps the whole assembly on the server, and so does welding a part kept in storage or
inside a body to it.

A loose part with `collides` off is the exception: it is left out when the engine decides, so
welding one to a car does not keep the car on the server. It has no body, though, so it does not
ride along with the car either.

If a part becomes one of these while a player owns it, the server takes the whole assembly back.

### Choosing the owner from a script

| | |
|---|---|
| `part:setNetworkOwner(player)` | hands the part's assembly to a player, and stops the automatic rule for it |
| `part:setNetworkOwner(nil)` | keeps the part's assembly on the server, and stops the automatic rule for it |
| `part:setNetworkOwnershipAuto()` | puts the part's assembly back on the automatic rule |
| `part:isNetworkOwnershipAuto()` | whether the automatic rule is choosing |
| `part:canSetNetworkOwnership()` | `true`, or `false` and the reason |
| `part:getNetworkOwner()` | the owning `Player`, or `nil` for the server |

`setNetworkOwner` and `setNetworkOwnershipAuto` work only from a server `Script`. `setNetworkOwner`
takes a `Player`, a body that has a player, or `nil`. It is an error on a part that can not be owned;
`canSetNetworkOwnership` tells you first and says why:

```lua
local ok, why = wall:canSetNetworkOwnership()
-- false, "an anchored part is always the server's"

local ok, why = wheel:canSetNetworkOwnership()
-- false, "it is joined to an anchored part, which keeps the whole assembly on the server"
```

Call these on any part of an assembly. The choice applies to every part of it, and a part joined to
the assembly later takes the same owner.

A player chosen by a script keeps the assembly however far away they walk, and while their body is
dead. If they leave the game, it goes back to the automatic rule. If they stop reporting, they keep
ownership and the server simulates it in the meantime.

A thrown ball is the usual case. The server throws it, then hands it to the thrower so it flies
without delay on their screen, and takes it back once it lands:

```lua
-- server
local throw = world:add("Remote", { name = "throw", accepts = "vec3" })

throw.onServer:connect(function(body, aim)
    if not body then return end
    local ball = world:add("Part", {
        shape = "ball",
        size = vec3(0.5, 0.5, 0.5),
        position = body.position + vec3(0, 1.5, 0) + aim.unit,
        anchored = false,
    })
    ball:applyImpulse(aim.unit * 25 * ball:getMass())
    ball:setNetworkOwner(body)

    task.delay(3, function()
        ball:setNetworkOwner(nil)
    end)
end)
```

- The push comes before `setNetworkOwner`. Once a script has handed a part to a player, pushing it
  from the server is an error.
- `setNetworkOwner(nil)` keeps the ball on the server for good. Call `setNetworkOwnershipAuto()`
  instead to let whoever walks up to it take it again.

`getNetworkOwner` also works on a client, but a client only knows about itself. It answers with the
local player when you own the part and `nil` for anyone else, including another player.
`networkOwner` has the owner's id on every client.

### Pushing a part a player owns

A server push is `applyImpulse`, `applyImpulseAtPosition`, `applyAngularImpulse`, `setVelocity` or
`setAngularVelocity`.

- On a part the automatic rule gave to a player, a push takes its whole assembly back to the server
  at once and keeps it there for 40 ticks (two seconds), so the push is not undone by the player's
  next report. Then the automatic rule picks again. This holds for a driver too: a pushed car is the
  server's for two seconds, then goes back to whoever sits in it.
- Any push on an automatic part holds its assembly on the server for those 40 ticks, owned or not.
- On a part whose assembly a script gave to a player, a push is an error. Give it back first with
  `setNetworkOwner(nil)` or `setNetworkOwnershipAuto()`.

Writing `cframe` or `position` on the server does not take a part back. The owner is not sent the
new position and its next report puts the part back where the owner has it. `velocity` and
`angularVelocity` go the same way. To teleport an owned part, give it to the server first.

Anchoring a part, turning its `collides` off or moving it into storage takes its whole assembly back
at once, because such a part can not be owned.

### Assemblies and vehicles

An assembly is every part linked to another by an enabled `WeldConstraint`, or by an enabled
constraint whose two attachments are inside parts. Hinges, sliders, ball sockets, ropes, springs and
rods all count. Parts in the same `Model` that are not joined are separate assemblies. The server
works the assemblies out again every tick, so welding or unwelding a part moves it in or out at once.

The owner's client simulates the whole assembly, joints included. It builds each weld and constraint
between two parts it simulates and runs it in its own physics. The server stops running those joints
while the client has them, and reports them `active`.

- A motor on a hinge or slider still follows the properties the server writes, such as
  `angularVelocity`. They reach the owner by replication, so a driver's key press goes to the server
  and back before the wheels answer it.
- While a client simulates a joint, the numbers it reads out are not written: `currentAngle`,
  `currentPosition`, `currentDistance` and `currentLength` keep the last value the server wrote.
  Read them while the server has the assembly.
- A winch keeps winding while a client has the rope: the server still moves `length` towards
  `winchTarget` and the owner follows it. It does not check `winchForce` then, so it reels in
  whatever hangs on it.
- A bouncy rope bounces in the owner's simulation, the same way it does on the server.
- A client whose window draws few frames or none, because it is hidden or in the background, still
  steps its physics every game tick, so what it simulates does not freeze for everyone else.
- When a client starts simulating a part, the part starts where the server had it, so it does not
  jump.
- Whichever side takes an assembly over builds its joints again from where the parts are at that
  moment. A weld keeps the offset the parts have then. Hinges, sliders and ball sockets are built
  from their attachments, so their angles and positions don't change on a handover.

A car is an assembly with a seat in it. The player in the seat owns it however far the other players
are, unless a script chose the owner or a server push is holding it, so the car answers their
steering on their own screen, and everyone else sees it through their reports. While the driver's
client simulates the seat, the server no longer holds the driver onto the seat; the driver's client
does, and moves the player with the seat every tick. See [Driving](movement.md#driving) for a whole
car.

## Pivots

Every `Spatial` has a pivot: the frame it moves about. You read it with `getPivot` and move the thing
by it with `pivotTo`.

```lua
local frame = thing:getPivot()
thing:pivotTo(cframe(0, 80, 0) * cframe.angles(0, math.rad(90), 0))
```

`pivotTo` moves the thing so its pivot lands exactly on the frame you give, and everything inside it
comes along. For a part the pivot is its own world frame.

## Models

A `Model` is a spatial group of parts that moves, measures and scales as one. You put parts in one
when you want to place, turn or resize them together.

| | |
|---|---|
| `primaryPart` | the part the model pivots about |
| `scale` | the model's current scale; change it with `scaleTo` |
| `model:getPivot()` / `model:pivotTo(frame)` | read and move the pivot |
| `model:getBoundingBox()` | a box around every part inside |
| `model:scaleTo(n)` / `model:getScale()` | grow or shrink the whole model |

With a `primaryPart` the pivot is that part's world frame. Without one it is the model's own frame,
which is wherever the model was made, not the middle of its parts.

> [!TIP]
> Set `primaryPart` whenever you plan to move a model with `pivotTo`. A model built at the origin
> still pivots about the origin however far away its parts have since been placed, which is what
> makes a `pivotTo` fling everything off somewhere unexpected.

While the place plays, moving a model carries the unanchored parts inside it too. This goes for any
`Spatial`: writing its `cframe`, `position` or `pivot`, calling `pivotTo`, or moving it to a new parent
teleports every part inside it along with it.

```lua
-- server: put the ship on the pad, facing along the pad
local ship = world:find("ship") :: Model
ship.primaryPart = ship.hull
ship:pivotTo(pad.worldCframe * cframe(0, 3, 0))
```

Setting `primaryPart` to the hull makes the hull's frame the pivot, so `pivotTo` lands the hull on the
frame you pass and brings the rest of the ship with it. `pad.worldCframe * cframe(0, 3, 0)` is three
metres above the pad, turned the way the pad is turned, so the ship faces along the pad.

`getBoundingBox` returns a frame and a size. The box is turned like the pivot, so a model turned 45
degrees gets a tight box, not one grown to fit the world axes. A model with no parts gives its pivot
and a size of zero.

```lua
local frame, size = ship:getBoundingBox()
local top = frame.position + frame.upVector * (size.y / 2)
```

`frame.upVector` is the box's own up rather than the world's, so `top` stays on the middle of the
model's top face even when the model is tilted.

`scaleTo` sets an absolute scale, not a multiplier: `scaleTo(2)` twice leaves the model at 2. It
resizes every part inside and moves every offset in it, attachments included, then keeps the pivot
where it was. With a `primaryPart` that part stays put and the rest grows or shrinks around it.
Assigning `scale` directly only changes the number; use `scaleTo`.

```lua
ship:scaleTo(0.5)
print(ship:getScale())    -- 0.5
```

A rope's `length` or a spring's `freeLength` does not scale with the model, so a shrunk model hangs
on the rope it always had. Set those lengths yourself after scaling.

In the editor, select parts and press **Ctrl+G** to group them into a Model. A selection with guis
or scripts in it still goes into a Folder.

## Welds

A `WeldConstraint` holds two parts together so they move as one body. Use it when a handle, a lid or
a plate has to travel with the part it sits on.

| | |
|---|---|
| `part0`, `part1` | the two parts |
| `enabled` | on by default |
| `active` | read only: on while the weld really holds |

```lua
local handle = world:add("Part", { size = vec3(0.2, 0.6, 0.2), cframe = cframe(0, 70.8, 0), anchored = false })
world:add("WeldConstraint", { part0 = crate, part1 = handle })
```

The parts keep the offset they had when the weld starts. To weld
at a different offset, move the part first, then enable the weld. Welding an unanchored part to an
anchored one pins it in place.

## Constraints

The other joints are attachment based. Put an `Attachment` inside each of the two parts and point the
constraint at both. The constraint itself can live anywhere in the tree; what matters is which two
attachments it holds.

Every constraint has:

| | |
|---|---|
| `attachment0`, `attachment1` | an `Attachment` inside each part |
| `enabled` | on by default |
| `collideConnected` | lets the two parts still collide with each other; off by default |
| `visible` | draws the constraint in the world; off by default |
| `color` | the colour it is drawn in, a `Color`; grey by default |
| `active` | read only: on while the constraint is really holding its parts |

These rules apply to every constraint on this page:

- At least one of the two parts must be unanchored. Joining to an anchored part pins that end to the
  world.
- `active` is false when an attachment or its part is missing, a part does not collide, or both
  parts are anchored.
- A hinge or ball socket pulls attachment1 onto attachment0. A slider pulls attachment1 onto the
  line through attachment0 along its axis. Put the two attachments where the parts meet: if the parts
  have drifted apart by the time the joint starts, it drags them back together.
- A hinge's angle and a ball socket's twist count from the attachments: 0 when attachment1 is turned
  the same way as attachment0. A slider's position is how far attachment1 is from
  attachment0 along the axis. `currentAngle`, `currentPosition`, `currentDistance`, `currentLength`
  and `active` are read-only for scripts.
- Constraints only run while the place plays.

> [!IMPORTANT]
> Where the attachments are is read when the constraint starts. Moving an attachment afterwards does
> nothing until you turn `enabled` off and on again.

The editor draws joints in pink over the viewport: each constraint as a line between its two ends
with a circle at each end, hinges and sliders with their axis through attachment0, and every
attachment inside a part as its X axis with a shorter, fainter line for its Y axis. They show with
**Helpers** on, with the **Constraints** toggle beside it on, and always while the constraint tool
is on. See [Joining parts](editor.md#joining-parts) for the tool that makes them by clicking two parts.

A constraint with `visible` on is drawn for every player, in the editor and while the place plays,
in its `color` and lit by the light around it:

- A rope is a round tube `thickness` wide. Taut, it runs straight; slack, it hangs down in a curve so
  that its drawn length is about `length`.
- A spring is a coil of wire, `coils` turns of `radius` round a line between its ends.
- A rod is a straight round tube `thickness` wide.
- A hinge or slider draws its axis through attachment0, 1.2 metres long, and a thin line between its
  two ends. So does a ball socket, without the axis.

A disabled constraint is not drawn.

### Hinges

A `HingeConstraint` turns about attachment0's X axis, its `rightVector`. In the editor that is the
longer of the two pink lines drawn out of the attachment.

`actuatorType` decides what turns it. At `"none"` the hinge swings under gravity and whatever pushes
it. At `"motor"` it spins at `angularVelocity` for as long as it is enabled. At `"servo"` it turns to
`targetAngle` and holds there.

| Property | Default | |
|---|---|---|
| `actuatorType` | `"none"` | `"none"` swings freely, `"motor"` spins at a speed, `"servo"` turns to an angle |
| `angularVelocity` | 0 | motor speed, in radians per second |
| `motorMaxTorque` | 10000 | how hard the motor may push |
| `targetAngle` | 0 | servo target, in degrees |
| `angularSpeed` | 3 | the fastest the servo turns, in radians per second |
| `servoMaxTorque` | 10000 | how hard the servo may push |
| `limitsEnabled` | false | stops it between `lowerAngle` and `upperAngle` |
| `lowerAngle`, `upperAngle` | -45, 45 | degrees |
| `currentAngle` | | read only: the angle now, in degrees |

A door on a servo, opened and closed by a prompt:

```lua
-- server
local frame = world:add("Part", { name = "doorFrame", size = vec3(0.2, 2.2, 0.2),
                                  cframe = cframe(10, 65.1, 0), anchored = true })
local door = world:add("Part", { name = "door", size = vec3(1, 2, 0.1),
                                 cframe = cframe(10.6, 65.1, 0), anchored = false })

local up = cframe.angles(0, 0, math.pi / 2)            -- turns the X axis to point up
local a0 = frame:add("Attachment", { cframe = cframe(0.1, 0, 0) * up })
local a1 = door:add("Attachment", { cframe = cframe(-0.5, 0, 0) * up })

local hinge = world:add("HingeConstraint", {
    attachment0 = a0, attachment1 = a1,
    actuatorType = "servo", angularSpeed = 2,
    limitsEnabled = true, lowerAngle = 0, upperAngle = 100,
})

local prompt = door:add("ProximityPrompt", { actionText = "Open", keys = "e" })
prompt.triggered:connect(function(body)
    local open = hinge.targetAngle == 0
    hinge.targetAngle = open and 90 or 0
    prompt.actionText = open and "Close" or "Open"
end)
```

- The frame is anchored and the door is not, so the hinge pins one end to the world and leaves the
  door free to swing.
- A hinge turns about attachment0's X axis, which points sideways. `up` turns both attachments a
  quarter turn about Z so that X points up and the door swings the way a door does.
- The two attachment offsets put the hinge on the inner face of the frame and on the edge of the
  door, which is where the two meet.
- The limits stop the swing between 0 and 100 degrees, so the door cannot carry on round through the
  wall.
- Writing `targetAngle` is all it takes to move the door. The servo turns it there at `angularSpeed`
  and holds it, and every player sees the same swing because the server does the simulating.

If the door swings the wrong way, use -90 and swap the limits to -100 and 0.

A wheel on a motor. The chassis and the wheels are all unanchored, so the whole thing drives off:

```lua
-- server
local chassis = world:add("Part", { name = "cart", size = vec3(2, 0.4, 3),
                                    cframe = cframe(0, 66, 20), anchored = false })

local wheels = {}
local function wheel(x, z)
    local w = world:add("Part", { shape = "cylinder", size = vec3(0.3, 0.9, 0.9),
                                  cframe = chassis.worldCframe * cframe(x, -0.2, z), anchored = false })
    local side = x > 0 and 1 or -1
    local a0 = chassis:add("Attachment", { cframe = cframe(x - 0.15 * side, -0.2, z) })
    local a1 = w:add("Attachment", { cframe = cframe(-0.15 * side, 0, 0) })
    local hinge = world:add("HingeConstraint", {
        attachment0 = a0, attachment1 = a1,
        actuatorType = "motor", motorMaxTorque = 50,
    })
    table.insert(wheels, hinge)
end

wheel(1.15, 1.1)   wheel(-1.15, 1.1)
wheel(1.15, -1.1)  wheel(-1.15, -1.1)

local function drive(speed)
    for _, hinge in wheels do hinge.angularVelocity = speed end
end
drive(4)
```

The attachments keep the chassis' rotation, so their X axis runs along the axle, which is also the
axis a cylinder lies along. `side` flips the offset for the wheels on the left, which puts each
attachment on the chassis' own side face rather than in its middle. `drive` writes `angularVelocity`
on all four hinges at once, and `drive(0)` stops the cart.

A cylinder rolls on 16 flat sides, so the cart hums a little on a smooth floor. For a cart a player
drives, sit them in a [VehicleSeat](movement.md#seats).

### Sliders

A `PrismaticConstraint` slides along attachment0's X axis and cannot turn. Its three actuator types
match the hinge's: free, a motor that slides at `velocity`, and a servo that moves to
`targetPosition`.

| Property | Default | |
|---|---|---|
| `actuatorType` | `"none"` | `"none"`, `"motor"` or `"servo"` |
| `velocity` | 0 | motor speed along the axis, in metres per second |
| `motorMaxForce` | 10000 | how hard the motor may push |
| `targetPosition` | 0 | servo target along the axis, in metres |
| `speed` | 2 | the fastest the servo moves, in metres per second |
| `servoMaxForce` | 10000 | how hard the servo may push |
| `limitsEnabled` | false | stops it between `lowerLimit` and `upperLimit` |
| `lowerLimit`, `upperLimit` | -5, 5 | metres |
| `currentPosition` | | read only: how far along the axis it is now |

```lua
-- a lift platform: X axis turned to point up, servo between floors
local lift = world:add("PrismaticConstraint", {
    attachment0 = shaft:add("Attachment", { cframe = cframe.angles(0, 0, math.pi / 2) }),
    attachment1 = platform:add("Attachment", { cframe = cframe.angles(0, 0, math.pi / 2) }),
    actuatorType = "servo", speed = 1.5,
    limitsEnabled = true, lowerLimit = 0, upperLimit = 8,
})
lift.targetPosition = 8
```

Both attachments are turned a quarter turn about Z, which points the slide axis up. The position is
the height of the platform's middle above the shaft's middle, so the limits keep the platform between
level with the shaft and eight metres above it, and writing `targetPosition` sends it to a floor at
one and a half metres a second. Set `targetPosition` back to 0 to bring the lift down. The slider
also pulls the platform's middle onto the line straight up from the shaft's middle, so build the
platform there.

### Ball sockets

A `BallSocketConstraint` lets the parts turn any way about attachment0, like a shoulder. Turn the
limits on when you want the swing kept inside a cone instead.

| Property | Default | |
|---|---|---|
| `limitsEnabled` | false | keeps the swing inside a cone |
| `upperAngle` | 45 | the cone's half angle, in degrees |
| `twistLimitsEnabled` | false | limits how far the part may twist |
| `twistLowerAngle`, `twistUpperAngle` | -45, 45 | degrees |

### Ropes

A `RopeConstraint` keeps its two attachments at most `length` apart. Below that the rope is slack and
the parts move freely; at it, the rope is taut and pulls.

| Property | Default | |
|---|---|---|
| `length` | 5 | metres |
| `restitution` | 0 | how fast the load bounces back when the rope snaps taut, from 0 (stops dead) to 1 |
| `thickness` | 0.1 | how thick it is drawn, in metres |
| `mesh` | | a model drawn repeated along the rope instead of the tube |
| `meshLength` | 0 | how far apart the models are, in metres; 0 uses the model's own length |
| `meshTwist` | 0 | degrees each model turns about the rope from the one before |
| `winchEnabled` | false | reels `length` towards `winchTarget` |
| `winchTarget` | 5 | the length the winch reels to, in metres |
| `winchSpeed` | 2 | the fastest the winch changes `length`, in metres per second |
| `winchForce` | 10000 | the heaviest pull the winch still reels in against |
| `winchResponsiveness` | 45 | how sharply the winch slows as it nears the target, 0 to 200 |
| `currentDistance` | | read only: how far apart the attachments are now |

A lamp swinging from a beam:

```lua
-- server
local beam = world:add("Part", { size = vec3(4, 0.3, 0.3), cframe = cframe(0, 72, -10), anchored = true })
local lamp = world:add("Part", { size = vec3(0.4, 0.5, 0.4), cframe = cframe(0, 69, -10),
                                 anchored = false, color = color(1, 0.9, 0.6) })
lamp:add("PointLight", { color = color(1, 0.8, 0.5), brightness = 2, range = 10 })

world:add("RopeConstraint", {
    attachment0 = beam:add("Attachment", { cframe = cframe(0, -0.15, 0) }),
    attachment1 = lamp:add("Attachment", { cframe = cframe(0, 0.25, 0) }),
    length = 3,
})

task.wait(1)
lamp:applyImpulse(vec3(2, 0, 0) * lamp:getMass())    -- set it swinging
```

- The beam is anchored and the lamp is not, so the top of the rope is fixed and the lamp hangs from
  it.
- The attachments sit on the underside of the beam and the top of the lamp, so the rope runs between
  the two faces instead of between the middles of the parts.
- `length = 3` is the furthest the lamp may get from the beam. It falls until the rope is taut and
  then swings under it.
- The impulse is multiplied by the lamp's mass, so the lamp leaves at two metres a second sideways
  whatever its density is.

Turn `visible` on to see the rope. It hangs in a curve while the lamp swings inwards and runs
straight while the rope is taut.

#### Bouncing

At `restitution` 0 a rope that snaps taut stops its load dead. Above 0 it throws the load back:
the ends leave each other at `restitution` times the speed they were coming apart at. A bungee cord
is a rope at about 0.5.

```lua
-- server: a weight dropped on a bungee cord
local beam = world:add("Part", { size = vec3(2, 0.3, 0.3), cframe = cframe(6, 80, -10), anchored = true })
local weight = world:add("Part", { size = vec3(0.6, 0.6, 0.6), cframe = cframe(6, 79.5, -10), anchored = false })

world:add("RopeConstraint", {
    attachment0 = beam:add("Attachment", { cframe = cframe(0, -0.15, 0) }),
    attachment1 = weight:add("Attachment", { cframe = cframe(0, 0.3, 0) }),
    length = 6, restitution = 0.5, visible = true,
})
```

The weight starts right under the beam with six metres of slack, falls until the rope snaps taut,
flies back up at half the speed it fell at, and bounces a little lower each time.

<!-- demo:physics-bungee -->

- The bounce is worked out on every physics substep: the engine notes how fast the ends are coming
  apart just before a slack rope reaches `length`, and sets them moving together at `restitution`
  times that speed once the step is done.
- Ends coming apart slower than half a metre a second do not bounce, so a load resting on a taut
  rope stays still instead of jittering.
- The push is shared out by mass. An anchored end does not move, so the loose end takes all of it.
- It works the same when a player's client simulates the rope. See
  [Assemblies and vehicles](#assemblies-and-vehicles).

#### Winches

With `winchEnabled` on, the engine moves `length` towards `winchTarget` every tick. Writing `length`
yourself only moves the starting point: the winch carries on from there towards `winchTarget`. The rope pays out or reels in at up to `winchSpeed`
metres a second, and slows down as it gets close: the speed is the distance still to go times
`winchResponsiveness` divided by 10, capped at `winchSpeed`. At the defaults it keeps full speed
until it is about 45 centimetres from the target. At 0 it does not slow down and runs at
`winchSpeed` all the way.

Reeling in stops while the rope pulls on its load harder than `winchForce`, and starts again when the
load drops back under it. Paying out always carries on. `length` replicates, so every player sees
the rope change.

A crane hook, lowered and raised by a prompt:

```lua
-- server
local jib = world:add("Part", { name = "jib", size = vec3(6, 0.4, 0.4),
                                cframe = cframe(0, 80, 30), anchored = true })
local hook = world:add("Part", { name = "hook", size = vec3(0.5, 0.6, 0.5),
                                 cframe = cframe(2.5, 78, 30), anchored = false })

local cable = world:add("RopeConstraint", {
    attachment0 = jib:add("Attachment", { cframe = cframe(2.5, -0.2, 0) }),
    attachment1 = hook:add("Attachment", { cframe = cframe(0, 0.3, 0) }),
    length = 2, visible = true, thickness = 0.06, color = color(0.2, 0.2, 0.22),
    winchEnabled = true, winchTarget = 2, winchSpeed = 1.5,
})

local prompt = jib:add("ProximityPrompt", { actionText = "Lower", keys = "e" })
prompt.triggered:connect(function(body)
    local down = cable.winchTarget < 8
    cable.winchTarget = down and 12 or 2
    prompt.actionText = down and "Raise" or "Lower"
end)
```

- The jib is anchored and the hook is not, so the hook hangs from the end of the jib.
- `winchTarget` starts equal to `length`, so the winch holds still until the prompt changes it.
- Setting `winchTarget` to 12 pays the cable out at a metre and a half a second and the hook follows
  it down; setting it back to 2 reels it in again.
- Weld a load to the hook and the winch lifts it too, unless the load pulls harder than `winchForce`.

#### Drawing a rope as a model

Set `mesh` and the rope is drawn as a model repeated along it instead of as a plain tube, which is
how you make a chain. `mesh` takes any id a `MeshPart` takes: a `res://` path to a `.gltf`, `.glb`,
`.ammesh` or `.bbmodel` file, or a resource pack id. `.obj` files are not supported.

- The model is laid along its own Z axis and centred on its bounds, so build one link pointing down
  Z.
- `meshLength` is the distance from one link to the next. At 0 it is the model's length along Z.
  The model is scaled evenly to fit that spacing, so a shorter `meshLength` makes smaller links, not
  overlapping ones.
- `meshTwist` turns each link that many degrees about the rope from the one before. 90 makes a
  chain, with every other link on its side.
- The links follow the same sag as the tube, so a slack chain hangs in a curve.
- The plain tube is drawn until the model has loaded, then the links replace it.
- Links are fitted to the rope: their spacing is stretched a little so the chain reaches both ends. A rope draws at most 512 links; past that, fewer, longer links cover it.
- `thickness` and `color` are for the tube only. The links keep the model's own size and colours.

A chain between two posts:

```lua
-- server
local left = world:add("Part", { size = vec3(0.4, 3, 0.4), cframe = cframe(-3, 66.5, 0), anchored = true })
local right = world:add("Part", { size = vec3(0.4, 3, 0.4), cframe = cframe(3, 66.5, 0), anchored = true })

world:add("RopeConstraint", {
    attachment0 = left:add("Attachment", { cframe = cframe(0.2, 1.3, 0) }),
    attachment1 = right:add("Attachment", { cframe = cframe(-0.2, 1.3, 0) }),
    length = 6.5, visible = true,
    mesh = "res://models/chain_link.gltf", meshLength = 0.6, meshTwist = 90,
})
```

- The posts are 5.6 metres apart at the attachments and the rope is 6.5 long, so the chain sags.
- Each link is scaled so the next one starts 0.6 metres along, and every link turns a quarter turn
  from the last.

### Springs

A `SpringConstraint` pulls its attachments towards `freeLength` apart and lets them bounce about it.

| Property | Default | |
|---|---|---|
| `freeLength` | 2 | the length it rests at, in metres |
| `stiffness` | 50 | how hard it pulls back, in newtons per metre |
| `damping` | 2 | how quickly it stops bouncing |
| `limitsEnabled` | false | stops it between `minLength` and `maxLength` |
| `minLength`, `maxLength` | 0, 5 | metres |
| `coils` | 5 | how many turns it is drawn with, 0 to 50 |
| `radius` | 0.4 | how wide the drawn coil is, in metres |
| `thickness` | 0.1 | how thick the drawn wire is, in metres |
| `currentLength` | | read only: its length now |

```lua
world:add("SpringConstraint", {
    attachment0 = ground:add("Attachment", { cframe = cframe(0, 0.5, 0) }),
    attachment1 = pad:add("Attachment"),
    freeLength = 1, stiffness = 400, damping = 10,
})
```

That spring rests with the pad one metre from the ground attachment. The `stiffness` of 400 is eight
times the default, which holds a heavy pad up instead of letting it squash flat, and the `damping` of
10 takes the wobble out of it after something lands.

A heavier part sags further on the same spring; raise `stiffness` with its mass.

`coils`, `radius` and `thickness` only change how the spring is drawn when `visible` is on. A spring
with `coils` or `radius` at 0 is drawn as a straight wire.

### Rods

A `RodConstraint` holds its two attachments exactly `length` apart, like a stiff bar with a ball
joint at each end. It neither stretches nor goes slack, and the parts turn freely about each end.

| Property | Default | |
|---|---|---|
| `length` | 5 | the distance it holds the ends at, in metres |
| `thickness` | 0.1 | how thick it is drawn, in metres |
| `currentDistance` | | read only: how far apart the attachments are now |

```lua
-- server: a pendulum that swings on a rigid arm
local pivot = world:add("Part", { size = vec3(0.3, 0.3, 0.3), cframe = cframe(0, 75, 40), anchored = true })
local bob = world:add("Part", { shape = "ball", size = vec3(0.6, 0.6, 0.6),
                                cframe = cframe(3, 75, 40), anchored = false })

world:add("RodConstraint", {
    attachment0 = pivot:add("Attachment"),
    attachment1 = bob:add("Attachment"),
    length = 3, visible = true,
})
```

The bob starts level with the pivot and three metres from it. A rope would let it fall straight down
until it went taut; the rod keeps it three metres away the whole time, so it swings down in an arc
and back up the other side.

Writing `length` while the place plays moves the ends to the new distance.

## Not in yet

- Movers such as `AlignPosition` or `VectorForce`. Push with the methods above each step instead.
- Physical materials by name. Set `density`, `friction` and `elasticity`.
