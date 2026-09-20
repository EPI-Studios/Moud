# Movement, collision and input

This page covers how a body walks, what it collides with, and how you read the keys a player is
pressing. Movement lives on the `Humanoid` inside every character. Collision is decided by collision
groups you create and name. Input is read through `InputAction` instances on the client.

## Humanoid

Every character has a `humanoid`, and it holds the numbers that decide how that body moves, all in
metres and seconds. A player's body moves with these numbers under the player's own control; a body
a place owns is walked by the engine with the same numbers.

```lua
local h = body.humanoid
h.walkSpeed = 6           h.sprintMultiplier = 1.3     h.sneakMultiplier = 0.3
h.jumpPower = 8.4         h.gravityScale = 1
h.groundAcceleration = 22.7   h.groundDeceleration = 22.7
h.airSpeed = 4.444        h.airAcceleration = 3.64
h.airDrag = 0.1516        h.fallDrag = 0.6676
h.stepHeight = 0.6        h.slopeLimit = 45           h.followSlopes = true
h.coyoteTime = 0.15       h.jumpBuffer = 0.15
h.health = 20             h.maxHealth = 20             h.healthRegen = 0
```

The defaults are Minecraft's own movement converted to metres and seconds, so a place that changes
nothing feels like the game. Change one of them and the body moves differently from the next tick
on, for every player who can see it.

- **`walkSpeed`** is how many metres the body covers in a second on the ground. `sprintMultiplier`
  and `sneakMultiplier` scale it while the player sprints or sneaks.
- **`jumpPower`** is the speed the body leaves the ground at, not a height. How high it gets falls
  out of that speed and the gravity pulling it back down.
- **`gravityScale`** is this body's own share of the pull, on top of the place's. The two multiply:
  at `game.gravity = 16` a humanoid at `gravityScale = 1` falls half as hard as it does by default,
  and one at `gravityScale = 2` falls as hard as ever. Change one body with `gravityScale`, and
  everything at once with [`game.gravity`](physics.md#gravity).
- **`stepHeight`** is how tall a ledge the body walks up without jumping, and **`slopeLimit`** is how
  steep a slope it walks up at all, in degrees.
- **`health`** and **`maxHealth`** are the body's life. Health reaching 0 puts the body in the `dead`
  state and fires `died`.

<!-- demo:movement-jump -->

### State

`state` says what the body is doing right now. It is one of `standing`, `running`, `jumping`,
`falling`, `swimming`, `climbing`, `flying`, `seated`, `platformStanding`, `dead`.
`humanoid:getState()` reads the same thing.

A player's body gets its state from the player every tick on the server: on a ladder or vine is
`climbing`, in water off the ground is `swimming`, creative flight is `flying`, in the air is
`jumping` on the way up and `falling` on the way down, and on the ground is `running` or `standing`.
A body without a player is `running` while it walks, `jumping` and `falling` through a hop, and
`standing` otherwise.

> [!IMPORTANT]
> Writing `state` yourself lasts until the next tick, when the engine works it out again and
> overwrites you. To put a body in a state and have it stay there, use `sit`, `platformStand` or
> `changeState` below.

| Property | |
|---|---|
| `moveDirection` | read only: the flat unit direction the body moves in, zero when still |
| `floorMaterial` | read only: the block under the feet, like `"minecraft:grass_block"`, or `"air"` off the ground |
| `sit` | sits the body down |
| `platformStand` | the body stands limp and cannot walk or jump |
| `autoRotate` | turns a body without a player to face where it walks; on by default |
| `healthRegen` | health given back each second while alive and below `maxHealth`; 0 by default |

A player's `moveDirection` comes from how their body really moves. A body without a player keeps the
direction `move` gave it, and while it walks to a point with `walkTo` or `moveTo` it is the way it
walks.

`floorMaterial` only sees blocks. Standing on a part reads the block under the part, or `"air"`.

With `autoRotate` off, a body without a player keeps facing where it was while it walks. It does
nothing for a player's body yet.

A player that sits loses the move control but can still jump, and jumping stands them up. Their
body shows the sitting pose. A body without a player stands up and hops when `jump` is set, but its
legs do not fold on their own; set `body.riding` for that. `platformStand` takes the move and jump
controls from a player, and stops a body without a player walking and jumping, until you set it back
to false.

```lua
body.humanoid.sit = true
body.humanoid.seated:connect(function(active)
    print(active and "sat down" or "stood up")
end)
```

### Events

Every one of these is a signal you connect a function to, and it runs until you disconnect it or the
body is destroyed:

| Event | Fires with | |
|---|---|---|
| `stateChanged` | the humanoid | whenever `state` changes |
| `died` | the humanoid | when the state becomes `dead` |
| `healthChanged` | the humanoid | whenever `health` changes |
| `arrived` | the humanoid | when a body reaches a point it was sent to; never while `move` steers it |
| `damaged` | the amount | after `takeDamage` took health away |
| `running` | the speed | the body starts running or changes speed by half a metre a second; 0 when it stops |
| `jumping` | true or false | entering and leaving `jumping` |
| `freeFalling` | true or false | entering and leaving `falling` |
| `swimming` | a speed | entering `swimming` with `walkSpeed`, leaving with 0 |
| `climbing` | a speed | entering `climbing` with `walkSpeed`, leaving with 0 |
| `seated` | true or false | entering and leaving `seated` |
| `platformStanding` | true or false | entering and leaving `platformStanding` |

A body without a player runs at its `walkSpeed`. It only swims or climbs when `changeState` puts it
there.

```lua
local h = body.humanoid
h.freeFalling:connect(function(active)
    if not active then print("landed on", h.floorMaterial) end
end)
h.damaged:connect(function(amount)
    print(body.name, "took", amount)
end)
```

`freeFalling` fires with true when the body enters `falling` and with false when it leaves, so the
false is the landing. Reading `floorMaterial` at that moment tells you what it landed on, which is
enough to pick a footstep sound or hurt a body that hit stone.

### Methods

Every method except `getState` and `getStateEnabled` runs in a server `Script`. A `LocalScript`
calling one gets an error.

```lua
local h = body.humanoid
h:move(vec3(1, 0, 0))                  -- keep walking along +X
h:move(vec3(0, 0, -1), true)           -- keep walking forward, the way the player faces
h:move(vec3(0, 0, 0))                  -- stop
h:moveTo(vec3(20, 65, 4))              -- straight at the point
h:takeDamage(5)
h:changeState("seated")
h:setStateEnabled("jumping", false)
print(h:getState(), h:getStateEnabled("jumping"))
```

- `move(direction, relativeToCamera?)` walks the body in a direction until you call it again with a
  zero vector. Only the flat part of the direction counts. With `relativeToCamera` the direction is
  turned by where the player faces: -Z is forward, +X is right. A player is steered by their own
  client, and pressing a movement key takes over while it is held. A body without a player walks
  that way smoothly on its own, and `arrived` does not fire while it does.
- `moveTo(point)` walks in a straight line to the point and fires `arrived`. It does not
  go around anything; `body:walkTo(point)` finds a path.
- `takeDamage(amount)` takes health away and fires `damaged`. It does nothing while the body has a
  `ForceField` inside it. Writing `health` yourself ignores the force field.
- `changeState(state)` does what the state means: `dead` sets health to 0, `jumping` jumps, `seated`
  sets `sit`, `platformStanding` sets `platformStand`. Any other state only applies to a body
  without a player, and only until its next tick. A state that is turned off is ignored.
- `setStateEnabled(state, enabled)` turns a state off or back on. `getStateEnabled(state)` reads it.

Turning a state off takes the thing it stands for away from the body:

| State | |
|---|---|
| `jumping` | the body cannot jump; a player loses the jump control |
| `running` | the body cannot walk or move; a player loses the move control |
| `seated` | the body cannot sit, and a sitting body stands up |
| `dead` | health stays above 0, so the body cannot die |

Turning off any other state is kept but changes nothing about how the body moves yet. The one thing
it does is on a player's body: where the player would be in a state that is off, other than
`running` or `jumping`, `state` reads `standing` instead. The switches live on the server, so
`getStateEnabled` on a client always says true.

```lua
-- server: a lobby where nobody can die or jump
game.players.spawned:connect(function(player)
    local h = (player.character :: Character).humanoid
    h:setStateEnabled("dead", false)
    h:setStateEnabled("jumping", false)
end)
```

`game.players.spawned` fires each time a player gets a body, so the two switches are set again after
every respawn. `(player.character :: Character)` tells the type checker what the body is, which is
what lets the editor autocomplete `humanoid`.

### Force fields

A `ForceField` is an instance you put inside a body, and while it is there `takeDamage` does
nothing to that body.

```lua
-- server: three seconds of spawn protection
game.players.spawned:connect(function(player)
    local shield = (player.character :: Character):add("ForceField")
    task.delay(3, function() shield:destroy() end)
end)
```

Destroying the force field ends the protection, so `task.delay` above hands the body back to damage
three seconds after it spawned. `visible` is kept on the class, but nothing draws a force field yet.

> [!NOTE]
> A force field only stops `takeDamage`. A script that writes `health` directly still kills the body
> it is protecting.

<!-- demo:movement-states -->

### Walking

For bodies a place owns: `walkTo` and `walking` walk in a straight line; `jump = true` hops once.
For walking around obstacles use `body:walkTo(point)` and `body:follow(target)`, see
[Players and bodies](players.md).

## Pushing a body

`body:applyImpulse(change)` adds to a body's velocity, in metres per second. `body:setVelocity(velocity)`
replaces it. Bodies have no mass, so the same push moves every body the same way.

```lua
body:applyImpulse(vec3(0, 30, 0))                          -- launch up about 9 m
body:applyImpulse(body.worldCframe.lookVector * 20)        -- dash forward
body:setVelocity(vec3(0, 0, 0))                            -- stop dead
```

- `body.worldCframe.lookVector` is the direction the body faces in the world, so multiplying it by a
  speed pushes the body the way it is looking.
- A server `Script` can push any player's body. A `LocalScript` can only push its own, which feels
  instant because nothing waits for the network.
- Only player bodies can be pushed for now; a body without a player raises an error.
- On the ground, walking friction eats a sideways push within a few ticks. Push up a little as well
  to carry it through the air.

## Seats

A `Seat` is a part a body sits on. Walk a player's body into a free one and it sits down: the body is
held on the seat's top face, turned the way the seat faces, and it keeps its place while the seat
moves. Jumping stands it up, and so does `humanoid.sit = false`. A body that just stood up waits one
second before it can sit again, so it does not drop straight back into the seat it left.

```lua
-- server
local bench = world:add("Seat", {
    name = "bench",
    size = vec3(2, 0.4, 1),
    cframe = cframe(4, 65.2, 0),
    anchored = true,
})

bench.changed:connect(function(property)
    if property ~= "occupant" then return end
    print(bench.occupant and "someone sat down" or "the bench is free")
end)
```

`changed` fires with the name of whatever property changed, so the guard clause at the top is what
makes this a seat handler rather than a handler for every property the bench has.

| | |
|---|---|
| `disabled` | nobody sits down on it, and whoever is on it stands up |
| `occupant` | read only: the `Humanoid` sitting on it, or nothing |
| `seat:sit(humanoid)` | sits that body on it and says whether it took |

A new `Seat` is two metres square, 0.4 high and blue, so one you drop in the editor already looks
like something to sit on.

Only a player's body sits down by walking in, and only a player's body is held on the seat.
`seat:sit(humanoid)` takes any body: it fills `occupant` and sets `sit`, but a body without a player
stays where it is, so put it on the seat yourself.

`sit` runs in a server `Script`. It refuses a disabled seat, a seat someone else is on, a dead body,
and a body with the `seated` state turned off. Sitting a body that is already on another seat takes
it off that one first.

`occupant` is filled in by the engine and stays on the server, so a `LocalScript` always reads
nothing.

### Driving

A `VehicleSeat` is a seat that reads the keys of whoever sits on it.

| Property | Default | |
|---|---|---|
| `maxSpeed` | 25 | how fast the thing it drives should go |
| `torque` | 10 | how hard its motors should push |
| `turnSpeed` | 1 | how fast it should turn |
| `throttle` | | read only: -1 back, 0 still, 1 forward |
| `steer` | | read only: -1 left, 0 straight, 1 right |

The engine drives nothing with `maxSpeed`, `torque` and `turnSpeed`. They are numbers kept with the
seat so the thing you build around it can read them. `throttle` and `steer` come off the sitting
player's own movement keys, and like `occupant` they are only on the server.

A seat welded or jointed to other parts is part of their assembly, and the player sitting in it owns
the whole assembly. Their client simulates the car, wheels and hinges included, so it answers them
at once on their own screen, and everyone else sees it move through their reports. The driver's
client also holds the driver on the seat; the server stops doing it while that client simulates the
seat. When they stand up, the automatic rule picks the owner again. See
[Assemblies and vehicles](physics.md#assemblies-and-vehicles).

#### A car

A chassis, a seat and four wheels on hinge motors, all in one server script:

```lua
-- server/main.luau
local world = game.world

world:add("SpawnLocation", { name = "Start", cframe = cframe(0, 64, 10) })
world:add("Part", { name = "Floor", size = vec3(120, 1, 120), cframe = cframe(0, 63.5, 0) })

local car = world:add("Model", { name = "Car" }) :: Model
local chassis = car:add("Part", {
    name = "Chassis", anchored = false, size = vec3(3.6, 0.6, 5.6), color = color(0.85, 0.2, 0.2),
    cframe = cframe(0, 65.3, 0),
}) :: Part
car.primaryPart = chassis

local seat = car:add("VehicleSeat", {
    name = "Seat", anchored = false, size = vec3(1.4, 0.4, 1.4),
    cframe = cframe(0, 65.8, 0.8),
}) :: VehicleSeat
car:add("WeldConstraint", { part0 = chassis, part1 = seat })

type Wheel = { hinge: HingeConstraint, left: boolean }
local wheels: { Wheel } = {}
for _, spot in { vec3(-2.2, 0, -1.9), vec3(2.2, 0, -1.9), vec3(-2.2, 0, 1.9), vec3(2.2, 0, 1.9) } do
    local wheel = car:add("Part", {
        name = "Wheel", anchored = false, shape = "cylinder", size = vec3(0.6, 1.6, 1.6),
        color = color(0.1, 0.1, 0.1), cframe = cframe(spot.x, 64.8, spot.z), friction = 1.2,
    }) :: Part
    local hinge = car:add("HingeConstraint", {
        attachment0 = chassis:add("Attachment", { cframe = cframe(spot.x, -0.5, spot.z) }),
        attachment1 = wheel:add("Attachment", {}),
        actuatorType = "motor", motorMaxTorque = 400,
    }) :: HingeConstraint
    table.insert(wheels, { hinge = hinge, left = spot.x < 0 })
end

local SPEED = 14
local TURN = 7
game.stepped:connect(function()
    local forward = seat.throttle * SPEED
    local turn = seat.steer * TURN
    for _, wheel in wheels do
        local spin = if wheel.left then forward + turn else forward - turn
        wheel.hinge.angularVelocity = -spin
    end
end)
```

- The floor is anchored and everything in the car is loose. Nothing in the car is anchored, or the
  whole car would stay with the server.
- The weld joins the seat to the chassis, and the hinges join the wheels. That makes the chassis, the
  seat and the four wheels one assembly, so the player who sits down owns all six parts.
- A cylinder lies along its X axis, and a hinge turns about attachment0's X axis. The wheels are
  0.6 wide along X and 1.6 round, so they roll the way the car points.
- The chassis's attachment for each wheel sits at the wheel's centre, 0.5 below the chassis's
  centre. The wheel's own attachment is at its centre, so the wheel turns about its axle.
- The front of a part is -Z. A wheel turning a positive angle about +X rolls towards +Z, so the
  speed is negated to drive forwards.
- `game.stepped` runs every tick on the server. It reads `throttle` and `steer` and writes each
  hinge's `angularVelocity`. The left wheels turn faster when you steer right, and slower when you
  steer left, so the car turns without steering wheels.
- `angularVelocity` is written on the server and reaches the driver's client by replication, which
  runs the motors. The car answers the keys one round trip after they are pressed.
- `friction = 1.2` on the wheels gives them more grip than the default of 0.5.

<!-- demo:movement-drive -->

Walk into the seat and drive with the movement keys. Jump to get out.

The hinges are in [Physics](physics.md#hinges).

## Collision groups

A `CollisionGroup` is a named set of things that pass through each other. You create the group, then
put parts and bodies in it by name:

```lua
world:add("CollisionGroup", { name = "players", ignores = "players" })
world:add("CollisionGroup", { name = "ghosts", ignores = "walls, players" })

wall.collisionGroup = "walls"
body.collisionGroup = "ghosts"
```

- Every part and body starts in `default`. An unknown name counts as `default`.
- `ignores` works both ways: if either group ignores the other, they pass through each other.
- A limb collides as the body it is on unless it names a group of its own.
- The level's blocks are solid to every group.
- Queries can ask as a group: `{ collisionGroup = "ghosts" }`.

<!-- demo:collision -->

The first line above is the usual one: a group that ignores itself, so players walk through each
other instead of shoving each other off a ledge.

## Input actions

An `InputAction` is a named action bound to one or more keys. You create it on the client, connect
to `began` and `ended`, and ask `input:down` whether it is held right now:

```lua
-- client
local interact = world:add("InputAction", { name = "interact", keys = "e, mousebutton2" })
interact.began:connect(function() end)
interact.ended:connect(function() end)

if input:down("interact") then end
```

- `keys` is a list of key names separated by commas. Any one of them fires the action.
- `began` runs on the frame the key goes down, `ended` on the frame it comes back up. Use those for
  things that happen once, such as opening a door.
- `input:down(name)` is true for as long as the key is held. Use that inside `renderStepped` for
  things that happen continuously, such as leaning a camera.

A letter, digit or symbol names what the key **types on the player's own keyboard**, whatever its
layout: `"m"` is the key marked M on a qwerty, azerty, qwertz or dvorak board alike, and `"é"` or `"<"`
work where the layout has them. Switching layouts while playing is picked up within a couple of seconds.

Key names: `a`–`z`, `0`–`9`, `f1`–`f12`, `keypad0`–`keypad9`, `keypadenter`, `space`, `enter`,
`tab`, `escape`, `backspace`, `delete`, `insert`, `home`, `end`, `pageup`, `pagedown`, `capslock`,
`leftshift`, `rightshift`, `leftcontrol`, `rightcontrol`, `leftalt`, `rightalt`, `leftsuper`,
`rightsuper`, `up`, `down`, `left`, `right`, `mousebutton1`, `mousebutton2`, `mousebutton3`, and the
gamepad names in [Camera and input](camera-and-input.md#gamepads).

The built-in actions follow the player's own key bindings: `forward`, `back`, `left`, `right`,
`jump`, `sneak`, `sprint`, `attack`, `use`, `pointer`. A screen that is open (like the chat)
owns the keyboard, so actions do not fire while typing.

## Who may write what

The server writes the tree. A client writing a replicated property is a Luau error, except:

- instances the client made itself,
- properties a class keeps to each client (like how a body is drawn),
- instances its player owns.

The server hands ownership of an instance to a player with `setOwner`. It takes a body or `nil`:

```lua
-- server: let that player's LocalScripts write the sign's properties
sign:setOwner(body)
sign:setOwner(nil)      -- back to the server
```

What the owner's client writes stays on its own copy. Nothing goes up to the server or out to the
other players, and the next change the server sends for that property overwrites it. Use a
[remote](talking.md) to tell the server about a change.

`setOwner` does not change who simulates a part, so it does not make pushing a crate feel instant.
That is network ownership: a loose part near a player is already simulated by that player's client,
and `part:setNetworkOwner(player)` hands one over on purpose. See
[Who simulates a part](physics.md#who-simulates-a-part).

Ownership carries down a branch: owning a cart means owning what stands on it.
