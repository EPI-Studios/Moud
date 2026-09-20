# Queries

A query is a question you ask the engine about space: what a ray hits, which parts sit inside a box,
how far the ground is under a point. You ask it of an instance, and it only looks at that instance
and everything under it. Asked of `game.world` it looks at the whole place, and rays also stop at the
level's solid blocks.

Queries are how a gun decides what it shot, how a door knows somebody is standing in front of it, and
how a script places a crate on the ground instead of inside it.

## How fast they are

Every part in a tree is kept in a spatial grid. A query only tests the parts near what it asks about,
never every part in the place. The grid refreshes lazily: a write marks the branch that moved, and
the next query updates just that branch. You do not maintain any of this, and you do not have to keep
your own list of nearby parts.

A query asked again with the same arguments while nothing in the tree has changed returns the answer
it got the first time. A place asking the same question from several scripts in one frame pays for it
once, so you can call `world:partsInRadius` from three different systems without three searches.

`game.debug:queryStats()` tells you how many queries ran and how many parts they tested since you
last asked. See [Debugging](debug.md).

## Casts

A cast sends a shape along a direction and hands back the first thing it meets. `raycast` sends a
line, and the other three send a volume, which is what you want when the thing you are testing is not
a point:

```lua
local part, at, distance, normal, block = world:raycast(from, direction, 100, options)
local part, at, distance, normal = world:spherecast(from, 0.5, direction, 40, options)
local part, at, distance, normal = world:blockcast(cframe(from), vec3(1, 2, 1), direction, 40, options)
local part, at, distance, normal = world:capsulecast(from, to, 0.4, options)
```

Each one returns the part it hit, the point it hit at, how far along that was, and the surface normal
there. When nothing was hit, the part is `nil`.

- `direction` does not need to be unit length; `range` defaults to 100.
- A ray starting inside a part passes out of it, so a cast from inside a player's own body does not
  immediately hit that body.
- A cast meets a [shaped part](physics.md#shapes) where its shape really is. `blockcast` is the one
  exception: it tests the box around the shape.
- Asked of the world, a ray that hits a block returns `nil` for the part and the block's id, like
  `minecraft:stone`, as the fifth value, so a ray that comes back with no part still tells you what
  it stopped against.

<!-- demo:raycast -->

**Every hit** along a ray, nearest first. Use this when a shot goes through glass, or when you want
each part in a line rather than only the closest:

```lua
for _, hit in world:raycastAll(from, direction, 50) do
    print(hit.part.name, hit.position, hit.distance, hit.normal)
end
-- partsAlongRay is the same thing
```

Each entry is a table with `part`, `position`, `distance` and `normal`, the same four values a single
cast returns.

**Many rays** in one call. Each entry is `{ from, direction, range? }`; a miss is `false`:

```lua
local hits = world:raycastMany({ { eye, dir1, 30 }, { eye, dir2, 30 } }, { exclude = { body } })
```

The answers come back in the order you asked them, so `hits[1]` belongs to the first ray. A shotgun
that fires eight pellets is one call instead of eight.

**A part's own box** swept along a direction, never hitting itself:

```lua
local hit, at, distance = world:sweep(crate, vec3(1, 0, 0), 5)
```

That answers "how far can this crate slide before it runs into something", which is the question a
pushing or sliding script actually has.

## Overlaps

An overlap asks which parts are inside a region right now, without moving anything along a direction:

```lua
world:partsInBox(cframe(0, 65, 0), vec3(4, 4, 4), options)
world:partsInRadius(position, 6, options)
world:partsInPart(zonePart, options)
world:partsAtPoint(position, options)       -- the parts a point is inside
```

- `partsInBox` takes a frame and a size, so the box can be turned.
- `partsInRadius` takes a centre and a distance in metres.
- `partsInPart` uses an existing part as the region, which is the usual way to write a trigger volume
  you can see and move in the editor.
- `partsAtPoint` answers whether a single position is inside anything.

## Options

Every cast and overlap takes the same table as its last argument. Leave it out and the query tests
everything under the instance you asked:

| Option | Meaning |
|---|---|
| `exclude = { ... }` | skip these instances and everything under them |
| `include = { ... }` | only these instances and everything under them |
| `respectCollides = true` | skip parts with `collides = false` |
| `collisionGroup = "players"` | skip parts that group passes through |
| `tag = "enemy"` | only parts with this tag |
| `className = "MeshPart"` | only parts of this class or a subclass |
| `limit = 5` | at most this many results (overlaps, `raycastAll`) |
| `sorted = true` | nearest first (overlaps) |
| `ignoreBlocks = true` | a world ray ignores blocks |

`exclude = { body }` is the one you reach for most: it keeps a player's own shot from hitting the
player who fired it.

<!-- demo:queries-overlap -->

A part with `visible = false` or `canQuery = false` is never found. A zone that should be found but
not seen uses `transparency = 1` instead.

> [!NOTE]
> A part kept in `ServerStorage`, `ServerScriptService`, `ReplicatedStorage`, `StarterPack`,
> `StarterCharacterScripts` or a backpack is not in the world, so no query asked of `game.world`
> finds it. Asking the container itself, or something inside it, still finds what is in there. The
> same goes for a part inside a viewport frame. See [Containers](containers.md).

## Helpers

These are the questions places ask often enough that the engine answers them directly, instead of
making you cast and sort the results yourself:

```lua
local part, distance = world:nearestPart(position, 20, options)
local lava, distance = world:nearestTagged(position, "lava", 50)

local height, normal = world:groundAt(x, z)       -- the top of whatever is under a column, parts or blocks
local normal = world:surfaceNormal(position)
local rows = world:heightmap(vec3(-10, 0, -10), vec3(10, 0, 10), 1)   -- rows[z][x], nil where empty

local spot = world:findFreeSpot(vec3(0, 65, 0), vec3(1, 2, 1), 16)
-- somewhere a 1x2x1 box fits: not inside a part or solid block, standing on something, nearest first

local frame, size = world:boundsOf({ car, trailer })   -- the box around several instances
```

- `nearestPart` and `nearestTagged` take a search distance and hand back the instance and how far
  away it was, so a monster can find the closest player without you sorting a list.
- `groundAt` answers with the height and the surface normal of the top of the column at x and z. It
  counts both parts and blocks, which is what you want before you place something on the terrain.
- `heightmap` runs `groundAt` over a rectangle at a spacing you choose and hands back rows indexed
  `rows[z][x]`, with `nil` where the column was empty.
- `findFreeSpot` is the spawn-point question: give it a starting position, the size of the body or
  crate, and how far to look, and it returns somewhere that box fits standing on something.

On a part:

```lua
part:overlapping(options)
part:closestPoint(position)
part:contains(position)
local frame, size = part:bounds()        -- its own turned box
local low, high = part:worldBounds()     -- the box around it lined up with the world
```

`bounds` keeps the part's own rotation, and `worldBounds` gives you the upright box that encloses it,
which is the one to compare against another upright box.

## Rewinding for lag compensation

A player shoots at what they see, and what they see is already a fraction of a second old by the time
their message reaches the server. Rewinding lets the server test the shot against the world as that
player saw it.

The server keeps one second of where every **moving** part was. Parts that never move cost nothing.
Run queries inside `game.history:rewind` to test against what a player saw when they acted:

```lua
shoot.onServer:connect(function(body, from, direction)
    local seen = game.history:viewTime(body)       -- now, minus their round trip and draw delay
    local hit = game.history:rewind(seen, function()
        return game.world:raycast(from, direction, 100, { exclude = { body } })
    end)
end)
```

- `game.history:viewTime(body)` works out which moment that player was looking at, from their round
  trip and how far ahead the client draws.
- `game.history:rewind(time, fn)` moves the recorded parts back to where they were at that time, runs
  your function, and puts everything back. Any query you run inside it sees the old positions.
- `game.history:now()` is the server's time on the same clock, for when you want to rewind by a fixed
  amount instead.

<!-- demo:queries-rewind -->

> [!IMPORTANT]
> The client's own claim about when it acted is never trusted. `viewTime` is worked out on the
> server, so a client that lies about its clock cannot widen its own window.
