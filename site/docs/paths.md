# Paths

A path is the way a body can walk from one point to another around what is in the way. `walkTo` and
`follow` find one and walk it for you. `game.path` finds one and hands it to you, either as a list of
corners or as a `Path` you compute, read and watch yourself.

## Walking a body somewhere

```lua
-- server
local ok = npc:walkTo(vec3(20, 65, 4))    -- false when there is no way there
npc:follow(targetBody, 3)                 -- keeps 3 m away and re-plans as the target moves
npc:stopWalking()
npc:isWalking()
```

`walkTo` answers false straight away when there is no path, so check what it returns before you wait
on `humanoid.arrived`. A body without a player is walked by the engine; a body a player wears is
walked by their client, see [Players and bodies](players.md#moving-bodies).

- Four times a second a walking body checks the rest of its path. When a block or a part now stands
  in the way it finds a new path from where it is, and when there is none it stops. `arrived` does not
  fire then.
- A follower plans again every half second while the target moves, stops at its distance, sets off
  again once the target is a step and a half further, and walks as close as it can get when the target
  stands somewhere it cannot reach.
- Both take the options below as their last argument: `npc:walkTo(point, { radius = 1 })`,
  `npc:follow(target, 3, { canClimb = false })`.

## Asking for a path

```lua
local corners = game.path:find(from, to)                    -- nil when there is no way
local closest = game.path:find(from, to, { partial = true }) -- as close as it gets instead of nil
game.path:isReachable(from, to)
local wander = game.path:randomPointNear(npc.position, 12)
npc:walkTo(point, { partial = true })
```

`find` gives the corners a body turns at, without the start. `partial = true` is what you want for a
wandering npc, which should set off in roughly the right direction rather than stand still because the
target is unreachable.

## Path objects

`game.path:create(options)` makes a `Path`. `compute` finds the way and keeps it, `waypoints` reads it,
and the path tells you when something blocks it later.

```lua
-- server
local path = game.path:create({ radius = 0.6, costs = { water = 4 } })
local status = path:compute(npc.position, vec3(40, 65, 12))
if status == "success" then
    npc:walkAlong(path)
end
```

`compute` returns the status and also leaves it in `path.status`:

| Status | |
|---|---|
| `success` | the path reaches the target |
| `closest` | the target cannot be reached; the path ends as close to it as a body can get |
| `noPath` | the start is not somewhere a body can stand, or nothing can be reached at all |
| `tooFar` | the search would build more than 400 tiles, see [How it is built](#how-it-is-built) |
| `notComputed` | `compute` has not run yet |

A `closest` path still has waypoints, so a body can walk it. `walkTo` and `find` treat it as no path
unless you pass `partial = true`.

### Waypoints

`path:waypoints()` gives a list of tables. The first is the start. A waypoint's `action` and `label`
describe the stretch from the waypoint before it:

```lua
for i, waypoint in path:waypoints() do
    print(i, waypoint.position, waypoint.action, waypoint.label)
end
```

| Action | The body gets to this waypoint by |
|---|---|
| `walk` | walking, stepping up or dropping down |
| `jump` | jumping from the waypoint before, across a one block gap |
| `climb` | climbing a ladder, vine or scaffolding, up or down |
| `custom` | crossing a [`PathfindingLink`](#pathfindinglink); `label` is the link's |

`label` is nil on plain ground. Otherwise it is one of the labels under
[Costs and labels](#costs-and-labels).

`npc:walkAlong(path)` walks a body along a computed path, jumping and climbing where the waypoints say.
It answers false when the path found no way at all. It does not plan again on its own: compute the same
path again and every body walking it switches to the new waypoints.

A loop of your own does the same with `moveTo`, and gets to decide what a `custom` waypoint means:

```lua
-- server
for i, waypoint in path:waypoints() do
    if i == 1 then continue end
    if waypoint.label == "teleporter" then
        npc.position = waypoint.position
        continue
    end
    if waypoint.action == "jump" then npc:jump() end
    npc.humanoid:moveTo(waypoint.position)
    npc.humanoid.arrived:wait()
end
```

`moveTo` walks in a straight line, so this loop does not climb. Leave ladders out with
`canClimb = false`, or use `walkAlong`.

### Blocked paths

```lua
path.blocked:connect(function(index)
    path:compute(npc.position, goal)
end)
path.unblocked:connect(function(index) end)
local first = path:checkOcclusion(1)      -- -1 when the whole path is clear
```

- `blocked` fires with the index of the first waypoint that can no longer be reached from the one
  before it. `unblocked` fires with that index once it can be reached again, or once another
  waypoint is the first blocked one.
- A path is checked four times a second, and only while something is connected to one of the two.
  `compute` starts it over.
- `checkOcclusion(start)` checks now, from waypoint `start` on, and gives the first blocked index or
  -1.
- A path is checked from its first waypoint, not from where a body walking it has got to, so a block
  dropped behind the body still fires `blocked`.

## Options

`create`, `find`, `isReachable`, `randomPointNear`, `walkTo` and `follow` take the same table:

| Option | Default | |
|---|---|---|
| `radius` | 0.3 | how far the path keeps from walls, from 0.1 to 4 |
| `height` | 1.8 | how much room the body needs above the floor, from 0.5 to 16 |
| `canJump` | true | step up a whole block and jump one block gaps. Off: steps of 0.6, a slab or a stair |
| `canClimb` | true | use ladders, vines and scaffolding |
| `spacing` | 0 | 0 gives only the corners; a number adds waypoints so none are further apart |
| `costs` | | see [Costs and labels](#costs-and-labels) |
| `partial` | false | `find` and `walkTo` only: keep a `closest` path instead of giving up |

- `radius` is rounded down to a quarter metre. At 0.3 a path keeps 0.25 m from a wall, which is what
  lets a body the size of a player through a one block doorway.
- Every different `radius`, `height` and `canJump` builds a navmesh of its own, so a place with bodies
  of three sizes builds three. `costs`, `canClimb` and `spacing` cost nothing extra.

## Costs and labels

`costs` makes some ground dearer to cross. The cost multiplies the distance walked there, so
`water = 4` makes a metre of water count as four. The default is 1, and `math.huge` means never.

```lua
local path = game.path:create({
    costs = { water = 4, soul_sand = 3, danger = math.huge, climb = 2 },
})
```

| Label | Where |
|---|---|
| a block's name | the floor is that block: `soul_sand`, `grass_block`, or the full id outside `minecraft:` |
| `water` | the body stands in water |
| `lava` | the body stands in lava. Costs `math.huge` unless you give it one |
| `jump` | a jump across a gap |
| `climb` | a ladder, vine or scaffolding |
| a modifier's label | inside a part with a [`PathfindingModifier`](#pathfindingmodifier) |
| a link's label | across a [`PathfindingLink`](#pathfindinglink) |

- A block's name labels a path only after some path's `costs` has named it. Until then that floor is
  plain ground, with no label.
- There are 58 labels to go round, counting block names, modifier labels and link labels together.
  Past that a new one is plain ground.
- Costs below 0.01 count as 0.01.

## PathfindingModifier

A `PathfindingModifier` inside a part changes what paths make of that part. Inside a `Model` it
changes every part in the model.

| Property | Default | |
|---|---|---|
| `label` | `""` | paths through the part's volume carry this label and pay its cost |
| `passThrough` | false | paths go through the part as if it did not collide |

```lua
-- server: a zone paths keep out of
local zone = world:add("Part", {
    size = vec3(8, 2, 8), cframe = cframe(10, 65, 0), collides = false, transparency = 1,
})
zone:add("PathfindingModifier", { label = "danger" })
local careful = game.path:create({ costs = { danger = math.huge } })

-- a door paths treat as open; open it yourself when a body gets there
door:add("PathfindingModifier", { passThrough = true })
```

- The volume reaches a quarter metre past the part's top and bottom, so a label part lying on the
  floor labels the floor under it.
- A part that does not collide is never in the way. Without a label, a modifier on it does nothing.
- A part that collides and has a label is still in the way, and its top carries the label.

## PathfindingLink

A `PathfindingLink` joins two attachments, and paths may cross between them however far apart they
are: a boat, a zip line, a teleporter.

| Property | Default | |
|---|---|---|
| `attachment0` | | where the link starts |
| `attachment1` | | where it ends |
| `label` | `""` | the waypoint across the link carries it, and it can be given a cost |
| `bidirectional` | true | off: only from `attachment0` to `attachment1` |

```lua
-- server
world:add("PathfindingLink", {
    attachment0 = pier:add("Attachment", { cframe = cframe(0, 0.5, 3) }),
    attachment1 = island:add("Attachment", { cframe = cframe(0, 0.5, -3) }),
    label = "teleporter",
})
```

- Each end has to sit within `radius` plus 0.3 of floor a body can stand on, or the link joins
  nothing.
- The waypoint at the far end has the action `custom` and the link's label.
- `walkTo` and `walkAlong` walk straight across a link as if it were floor. To do something else
  there, walk the waypoints yourself as in [Waypoints](#waypoints).

## What a path can cross

- Blocks by their collision shape: a slab is half a block, a fence or a wall a block and a half. An
  open door leaves its doorway open and a closed one shuts it.
- A step up of one block, or 0.6 with `canJump` off.
- A drop of up to three blocks, one way.
- A gap one block wide, to a landing up to a block higher or lower.
- A ladder, from its foot to any floor beside it that it reaches.
- Slopes up to 50 degrees on rotated parts.
- Water, along the bottom.
- Every part that collides, except the parts of a body.

Lava is never crossed unless `costs` gives `lava` a number.

A body without a player jumps at a `jump` waypoint and climbs a ladder straight up or down at about
half its `walkSpeed`. A player walked by their client presses jump at a gap, and at a ladder walks
into it until the game climbs it.

## How it is built

Paths run on a navmesh built with Recast and walked with Detour. It is made from the level's blocks and
the parts that collide, in 32 by 32 block tiles. A tile is built the first time a path crosses it,
and built again when a block in it changes, or a part, modifier or link in it moves or changes.

- A search first builds the tiles along the straight line between its ends. When the target is not
  reached it widens that band and tries again, up to 400 tiles, about 640 blocks across.
- A tile covers 16 blocks above and below the highest and lowest end of the path that built it.
- The first path over new ground builds its tiles in the call, so a long one can take a second.
  Later paths through the same tiles are quick.
