# Instances

An instance is one object in the tree that the engine draws, collides and replicates for you. Parts,
characters, lights, gui widgets, sounds and scripts are all instances, and everything a place makes
hangs off `game.world`. The class you give an instance, such as `Part` or `PointLight`, decides which
properties, methods and signals it has. Everything else on this page works the same on all of them.

## Making things

You create an instance with `add`. It takes the class name and a table of properties, parents the new
instance to whatever you called it on, and returns it:

```lua
local part = world:add("Part", {
    name = "plate",
    size = vec3(6, 1, 6),
    cframe = cframe(0, 66, 12),
    color = color(0.85, 0.5, 0.2),
    anchored = true,
})
```

The properties in that table are the same properties you could assign one at a time afterwards, so
the part above is the part you would get by creating an empty one and filling it in. Leaving a
property out gives it the class default.

For a field of things, `addAll` makes them in one call instead of one call each:

```lua
local rows = {}
for n = 1, 200 do
    rows[n] = { name = "tile" .. n, size = vec3(2, 0.06, 2), cframe = cframe(n * 2, 64.5, 0) }
end
world:addAll("Part", rows)
```

Each entry in the list is a property table, exactly what you would hand to `add`. Every instance in
one `addAll` is the same class.

## Finding things

A child is reachable by name as a field, which is the short way to walk down a branch you know
exists:

```lua
local torch = player.character.rightArm.torch
```

Use `find` when you want to ask whether a child is there at all. It answers `nil` instead of raising
an error:

```lua
local maybe = world:find("statue")   -- nil rather than an error
```

`children()` returns a list of the direct children, `isA` asks whether an instance is of a class, and
`destroy` removes an instance along with everything under it:

```lua
for _, thing in ipairs(world:children()) do
    if thing:isA("Character") then thing.display = "hitbox" end
end
```

## The hierarchy carries everything

A child is positioned relative to its parent. Move the parent and the whole subtree comes along, and
nothing under it is told where the parent is a second time:

```lua
local deck = world:add("Part", { name = "deck", size = vec3(7, 0.5, 7), cframe = cframe(0, 65, 12) })
local post = deck:add("Part", { name = "post", cframe = cframe(2, 1.5, 2) })
post:add("Part", { name = "lantern", cframe = cframe(0, 1.4, 0) })

game.stepped:connect(function(dt)
    deck.cframe = cframe(0, 65, 12) * cframe.angles(0, dt, 0)   -- all three turn
end)
```

- `post` is created on `deck`, and `lantern` on `post`, so the three form one branch.
- Writing `deck.cframe` each tick turns the deck. The post and the lantern keep their own frames,
  which are stated against their parent, so they turn with it.

<!-- demo:instances-hierarchy -->

`parent` is assignable, and writing it is how you move an instance somewhere else in the tree. To
remove an instance you call `destroy`, so there is one way to take something out rather than two that
mean different things.

## Frames

Every `Spatial` carries a frame. There are four ways to read and write it, and they are views on one
thing:

| Field | What it holds |
|---|---|
| `cframe` | the whole local frame, position and rotation |
| `position` | just the position, relative to the parent |
| `rotation` | just the rotation, turning without moving |
| `worldCframe` | read only: the frame composed through every parent |
| `pivot` | the point the frame turns about, from the middle outward |

You set `pivot` so that a door turns at its hinge instead of at its middle. Without it, a rig needs an
empty frame at every joint just to carry that offset.

<!-- demo:instances-pivot -->

## Signals

The tree tells you what happened to it, so you connect a function instead of checking every tick:

```lua
part.changed:connect(function(property)
    if property == "color" then end
end)

folder.childAdded:connect(function(child) end)
part.destroying:connect(function(thing) end)
```

`changed` fires with the **name** of the property that changed, which lets one handler watch a whole
instance. When you care about a single property, `getPropertyChangedSignal` gives you a signal for
that one, and it fires with nothing:

```lua
part:getPropertyChangedSignal("size"):connect(function()
    print("now", part.size)
end)
```

Asking for a name the class does not have is an error, so a typo shows up the moment the line runs
rather than as a handler that never fires.

Five names that are not properties work as well:

| Name | Fires when |
|---|---|
| `name` | it is renamed |
| `parent` | it moves to another parent |
| `position` | where it stands in the world changes |
| `rotation` | how it is turned in the world changes |
| `worldCframe` | either of those changes |

The last three are the world values, so they fire when an ancestor moves too. A part welded inside a
model that drives away fires `position` without anyone writing to the part:

```lua
door:getPropertyChangedSignal("worldCframe"):connect(function()
    print("the door is now at", door.worldCframe.position)
end)
```

> [!NOTE]
> Those five only exist on something with a place in the world. Asking a `Folder` for `position` is
> an error.

<!-- demo:instances-signals -->

## Attributes

An attribute is a named value you put on any instance, without a class declaring it. Use one to mark
things for your own scripts: a door that is locked, a crate worth 20 coins, a team colour.

```lua
-- server
local door = world:add("Part", { name = "door", size = vec3(1, 3, 2), anchored = true })
door:setAttribute("locked", true)
local prompt = door:add("ProximityPrompt", { actionText = "Lock or unlock", keys = "e" })

door:getAttributeChangedSignal("locked"):connect(function()
    local locked = door:getAttribute("locked")
    door.color = if locked then color(0.8, 0.2, 0.2) else color(0.3, 0.8, 0.3)
end)

prompt.triggered:connect(function(body)
    door:setAttribute("locked", not door:getAttribute("locked"))
end)
```

- The prompt handler only flips the attribute. It does not touch the colour.
- The colour is written by the handler on `getAttributeChangedSignal("locked")`, so the door turns
  red or green whatever set the attribute, including a script somewhere else.

<!-- demo:instances-attributes -->

| Call | What it does |
|---|---|
| `setAttribute(name, value)` | sets one; `nil` removes it |
| `getAttribute(name)` | its value, or `nil` |
| `getAttributes()` | every attribute, as a table keyed by name |
| `getAttributeChangedSignal(name)` | fires with nothing when that one changes |
| `attributeChanged` | fires with the name of any attribute that changes |

Setting the value it already holds fires nothing, so writing the same value every tick costs you no
handlers.

- A value is `nil`, a boolean, a number, text, a `Vector3`, a `Color`, a `CFrame` or a `UDim2`.
  Anything else, like a table or an instance, is an error.
- A name uses letters, digits and `_`, and is at most 100 characters.
- Attributes replicate, so a client reads the server's and hears it change. A vector, colour, frame
  or `UDim2` crosses at float precision.
- They are saved in scene files, and `clone` copies them.

> [!IMPORTANT]
> A client may only set attributes on an instance it made or one it owns, and the change stays on
> that client. Nothing a client writes reaches the server or any other player.

In the editor, the Properties panel has an **Attributes** section: name one, pick its kind, edit its
value in place, and right-click or press **x** to remove it. The section below it, **Values**, lists
the value children read by `instance:values()`.

## Raycasts

A raycast fires a line from a point along a direction and tells you the first part it meets:

```lua
local part, at, distance = world:raycast(from, direction, 50)
if part then
    print(part.name .. " at " .. distance)
end
```

You get three answers rather than one table: the part that was hit, the point where the line met it,
and how far along the line that was. When you only want to know which part, take the first and drop
the rest. The range defaults to 100 when you leave it out.

A raycast asks about the tree, so it answers about the tree. It tests every part under the instance
you called it on, turned boxes included. Whether a body can walk into that part is a separate
question with a separate answer, and it is not the one asked here: a character's limbs do not
collide with anything, and a ray still hits them. That is what lets you ask which limb was shot:

```lua
-- which limb was shot
local limb = character:raycast(eye, look, 30)
```

## Where a thing is

| Field | Means |
|---|---|
| `position`, `rotation` | **where it actually is, in the world** |
| `cframe` | the frame *stated against its parent*: the field itself |
| `worldCframe` | the whole composed frame, pivot included |

`position` is a world question and is answered in the world. That matters
because the engine reparents things you did not ask it to: a body standing on something that moves is
hung off it, so a place reading `position` would otherwise start getting deck-relative numbers without
ever mentioning a parent.

Writing `position` puts the instance there in the world too, converted through whatever it hangs off.
Writing `rotation` turns it and leaves it where it is, which is why an arm keeps its shoulder.
