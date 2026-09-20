# Working with the tree

Everything a place creates lives in one tree of instances, and every instance has a name, a parent
and any number of children. This page covers the calls that read that tree: finding an instance
again, hearing when one arrives or leaves, waiting for one that has not been made yet, and copying
one. See [Instances](instances.md) for what an instance is and how you create one.

## Finding

You reach an instance by asking one above it. Each of these starts from the instance you call it on
and searches somewhere below or above it.

```lua
world:find("statue")                        -- a direct child, or nil
world:findFirstDescendant("statue")         -- anywhere below
world:descendants()  world:descendants("Part")
folder:childrenOfClass("Light")
limb:firstAncestorOfClass("Character")
limb:firstAncestor("enemies")
part:isDescendantOf(world)
world:byTag("lava")                         -- tagged instances under this one
folder:firstChildOfClass("Part")            -- the first child that is a part, or nil
folder:firstChildOfClass("Part", true)      -- the same, searching every level below
world:isAncestorOf(part)                    -- the other way round from isDescendantOf
chair:getFullName()                         -- "World.map.room.chair"
```

- **`find`** looks at the direct children only and gives you `nil` when there is no child by that
  name. It is the one you reach for most, and it is what you call each time you need the instance
  rather than keeping it in a variable across a reload.
- **`findFirstDescendant`** keeps going down through every level, so it finds a part buried inside a
  model without you naming each folder on the way.
- **`descendants`** hands back everything below, and with a class name it hands back only instances of
  that class. **`childrenOfClass`** does the same one level down.
- **`firstAncestor`** and **`firstAncestorOfClass`** search upwards. From a limb,
  `firstAncestorOfClass("Character")` gets you the body it belongs to, which is how a touch handler
  works out who stepped on something.
- **`isDescendantOf`** and **`isAncestorOf`** answer the same question from the two ends. Use them to
  check where something ended up, such as whether a coin is now inside a backpack.
- **`byTag`** gathers every tagged instance below this one. Tags are set in the editor or from a
  script, and they are covered in
  [Zones, touches, tags, prompts and clicks](zones-and-triggers.md).
- **`firstChildOfClass`** gives the first child of a class, or `nil`. Pass `true` as the second
  argument and it searches every level below instead of only the children.

`getFullName` joins every name from the root down with `.`. Use it in a log line or an error message.
Two siblings can share a name, so the string it hands you is not enough to find the instance again.

<!-- demo:tree-finding -->

## Clearing

```lua
folder:clearAllChildren()                   -- destroys every child, keeps the folder
```

The folder itself stays where it is, so anything holding onto it keeps working. Every child is
destroyed, along with everything inside those children.

## Hierarchy signals

Instead of checking the tree every tick, connect to the signal that fires when it changes.

```lua
map.descendantAdded:connect(function(instance) end)      -- anything arrives anywhere below
map.descendantRemoving:connect(function(instance) end)   -- before it leaves, still in place
coin.ancestryChanged:connect(function(child, parent) end)
```

- `descendantAdded` fires for something made below and for something moved in from elsewhere. A
  folder moved in fires it for the folder and then for each thing inside it.
- `descendantRemoving` fires for something destroyed or moved out, while it is still there to read.
  That is the moment to read its name or its position, because a tick later there is nothing to read.
- `ancestryChanged` fires on the instance that moved and on everything under it. `child` is the one
  that moved and `parent` is where it went.
- Destroying fires `ancestryChanged` on each destroyed instance with itself and `nil`, children
  first.

Because a destroy arrives as `ancestryChanged` with a `nil` parent, a handler that only cares about
moves checks for that first:

```lua
coin.ancestryChanged:connect(function(child, parent)
    if parent == nil then return end                      -- destroyed
    if backpack:isAncestorOf(coin) then print("picked up") end
end)
```

<!-- demo:tree-signals -->

## Queries

`query` finds instances with a short selector, the way CSS finds elements. It searches everything
below the instance you call it on and hands back a list; `queryFirst` stops at the first match.

```lua
world:query("Part")                              -- every part (and subclasses)
world:query("Part.lava")                         -- parts tagged lava
world:query("#door")                             -- anything named door
world:query("Folder#enemies > Character")        -- characters directly inside the enemies folder
world:query("Model Part[anchored=false]")        -- unanchored parts anywhere under a model
world:query("Light[brightness>=2]")
world:queryFirst("SpotLight.stage")
```

The parts of a selector:

- A class name on its own matches instances of that class. `*` is any class, and a class matches its
  subclasses, so `Part` also picks up the classes that extend it.
- `.name` after a class matches a tag, and `#name` matches the instance's name.
- A space means anywhere below, and `>` means directly below. `Folder#enemies > Character` reads as
  "a Character whose parent is the folder named enemies".
- `[property=value]` compares numbers, booleans, text, enum names and references (by name). `!=`,
  `<`, `>`, `<=`, `>=` work on numbers.

<!-- demo:selector -->

## Waiting and watching

A script that runs before an instance exists cannot find it. These two calls let you say what to do
when it turns up.

```lua
task.spawn(function()
    local door = world:waitForChild("door", 5)   -- nil after 5 seconds; waits, so inside task.spawn
end)
world:onChild("door", function(door) end)        -- every door, the one there now and later ones
```

- `waitForChild` yields until the child appears and hands it back. The second argument is how many
  seconds to wait before giving up and returning `nil`. Because it yields, it goes inside a
  `task.spawn` rather than in the body of a script. See [Tweens and timing](tweens-and-timing.md).
- `onChild` runs your function for the child that is already there and again for every later one, so
  you write the handling once and it covers both.

<!-- demo:tree-wait -->

## Values

A value instance holds one number or one piece of text in the tree, where both sides can see it and
scripts can watch it change.

```lua
stats:add("NumberValue", { name = "coins", value = 10 })
stats:add("StringValue", { name = "rank", value = "gold" })
print(stats:values().coins)                      -- { coins = 10, rank = "gold" }
```

`values()` reads the value instances inside and hands back a plain Luau table keyed by their names,
so one call gets you the lot instead of a `find` each.

## Copies

```lua
local second = car:clone()                       -- beside the original
local third = car:clone(garage)                  -- under another parent
```

`clone` copies the instance and everything inside it. With no argument the copy lands beside the
original, under the same parent; pass a parent and it lands there instead.

The copy is announced once, finished, where it lands: `childAdded` on the new parent fires with the
copy, and `descendantAdded` fires for it and everything inside it. Nothing fires while the copy is
being built, so a handler never sees a half-made car and never hears about the folder the engine
built it in.

> [!IMPORTANT]
> Cloning into something that has been destroyed is an error. When you keep a parent in a variable
> across a reload, look it up again before you clone into it.

<!-- demo:tree-clone -->

## Callbacks

Some classes ask a place a question, like `TextChannel.shouldDeliver`. Assign a function to answer
it, or `nil` to go back to the engine's answer:

```lua
general.shouldDeliver = function(message, source) return true end
general.shouldDeliver = nil
```

A callback that errors is reported and the engine uses its own answer. The callbacks a class has are
listed with that class: `shouldDeliver` is covered in [Chat](chat.md).
