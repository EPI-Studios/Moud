# Blocks

The level is the Minecraft world your place runs inside: the terrain, the buildings, everything made
of blocks rather than of instances. `game.blocks` is how you read it and change it. Positions are
`vec3` values floored to the block they are inside, so `vec3(0.7, 64.2, -3.1)` and `vec3(0, 64, -3)`
name the same block.

The server writes the level. A client reads its own copy of it, so a `LocalScript` can ask what a
block is but cannot change one.

## Reading

```lua
local b = game.blocks
b:get(vec3(0, 64, 0))           -- "minecraft:oak_stairs[facing=north,half=bottom]"
b:isSolid(at)  b:isAir(at)  b:isFluid(at)
b:lightAt(at)                   -- 0 to 15
b:topAt(x, z)                   -- y of the highest block something stands on
b:count("minecraft:stone", from, to)
local ores = b:find("minecraft:diamond_ore", centre, 20, 10)   -- block centres, nearest first, at most 10
```

- `get(at)` returns the block's name along with its state, in the game's own text form.
- `isSolid`, `isAir` and `isFluid` answer the three questions you ask most often without your having
  to read the name apart.
- `lightAt(at)` is the light level at that block, from 0 to 15.
- `topAt(x, z)` gives you the y of the highest block something stands on at that column, which is
  what you want before dropping a part or a body onto the terrain.
- `count(name, from, to)` counts the matching blocks in the box between the two corners.
- `find(name, centre, radius, limit)` returns the centre of each matching block within `radius` of
  `centre`, nearest first, and stops at `limit` of them.

> [!TIP]
> A block name matches with or without its state. `minecraft:oak_stairs` matches every stair,
> whichever way it faces, and `minecraft:oak_stairs[facing=north,half=bottom]` matches only the ones
> facing north on the bottom half.

<!-- demo:blocks-match -->

## Raycasts

A block raycast shoots a line through the level and tells you the first block it hits:

```lua
local block, at, distance, normal = b:raycast(from, direction, 50, { fluids = true })
```

- `from` is where the line starts and `direction` is which way it goes. The `50` is how far it looks
  before giving up.
- `block` is the name of what it hit, `at` is where it hit, `distance` is how far along the line that
  was, and `normal` is the face it came in through.
- `{ fluids = true }` counts water and lava as something to hit. Leave it out and the ray goes
  through them.

This ray only sees blocks. To hit parts and bodies as well, use the casts in [Queries](queries.md).

## Writing

These all run on the server, and every player sees the change:

```lua
b:set(at, "minecraft:stone")
b:fill(from, to, "minecraft:glass")                    -- returns how many
b:replace("minecraft:dirt", "minecraft:grass_block", from, to)
b:sphere(centre, 5, "minecraft:stone", hollow)
b:cylinder(base, 3, 10, "minecraft:quartz_block", hollow)
b:line(from, to, "minecraft:gold_block")
b:hollowBox(from, to, "minecraft:oak_planks")
```

- `set(at, name)` changes one block.
- `fill(from, to, name)` fills the box between two corners and returns how many blocks it changed,
  which is how you tell a fill that did nothing from one that did.
- `replace(from_name, to_name, from, to)` only touches the blocks in that box that already match the
  first name, so you can turn every dirt block in a region to grass and leave the stone alone.
- `sphere`, `cylinder`, `line` and `hollowBox` are the shapes you would otherwise write three nested
  loops for. `hollow` on the sphere and the cylinder leaves the inside empty.

<!-- demo:blocks-shapes -->

> [!WARNING]
> One call changes at most 4,000,000 blocks. A bigger call is almost always two corners typed wrong.

## Copy and paste

`copy` reads a region of the level out into a table, and `paste` puts one back:

```lua
local house = b:copy(vec3(0, 64, 0), vec3(8, 70, 8))   -- { size, palette, blocks }
b:paste(house, vec3(20, 64, 0), 1, true)                -- quarter turns clockwise, skip air
```

- `copy(from, to)` takes the two corners of the region and hands you a table of `size`, `palette` and
  `blocks`.
- `paste(data, at, rotation, skipAir)` puts it back down at `at`. `rotation` is a number of quarter
  turns clockwise, so `1` is a quarter turn and `2` is a half turn. With `skipAir` true the
  air in the copy leaves whatever is already there alone, which is what you want when you paste a
  building onto ground you want to keep.

<!-- demo:blocks-paste -->

A copy is plain data, so it can be saved with `store` (see [Saving](saving.md)) or sent through a
remote (see [Talking across the boundary](talking.md)). Pasting turned rotates block states too, so
stairs still face the right way.

## Hearing changes

`changed` fires for every block that changes, whoever changed it:

```lua
b.changed:connect(function(at, block)
    print("now", block, "at", at)
end)
```

- `at` is the block's position and `block` is what it is now.
- It fires once a step for every block that changed, from anything: a place, a player or the game
  itself. A player mining a block, a `set` call in your own script and a fire spreading all arrive
  the same way.

> [!NOTE]
> A `fill` over a large region fires `changed` once for every block it touched. If you are counting
> or logging in the handler, a single call can hand you thousands of blocks in one step.
