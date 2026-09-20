# Saving

A store is a table of values that outlives the server. Everything you put in one is written to a
SQLite file in the world folder, so it is still there after a restart, after a crash, and after you
stop the game and come back tomorrow. Stores are server only: a client script cannot open one,
because a player's machine is not where the save lives.

You open a store by name. The name is what the data is filed under, so every script in the place can
call `store("players")` and read and write the same rows.

```lua
local players = store("players")

players:set(id, { coins = 10, level = 2 })
local data = players:get(id)              -- nil when there is nothing
players:remove(id)
players:keys("guild:", 100)               -- keys starting with a prefix

-- read and write in one step, so two changes at once never lose each other
local new = players:update(id, function(old)
    old = old or { coins = 0 }
    old.coins += 5
    return old
end)
```

- `set(key, value)` writes the value under that key, replacing whatever was there.
- `get(key)` reads it back, and returns `nil` when nothing has ever been written under that key. The
  usual shape is `local data = players:get(id) or { coins = 0 }`.
- `remove(key)` deletes the row.
- `keys(prefix, limit)` lists the keys that start with a prefix, which is how you walk a group of
  saves such as every key beginning with `guild:`.
- `update(key, fn)` reads the value, hands it to your function, and writes back what the function
  returns, all in one step. It returns the new value.

Values are anything a table can hold: numbers, text, booleans and nested tables.

> [!IMPORTANT]
> Use `update` whenever the new value depends on the old one. A `get`, then some arithmetic, then a
> `set` is two separate steps, and a second change landing between them is lost. `update` does the
> read and the write together, so both changes count.

<!-- demo:saving-update -->

## Sessions

A session is a lock on one key, held by this server, for as long as a player is on it. While the
session is held, another server asking for the same key is told no, so two servers never write over
each other's copy of the same save.

```lua
game.players.joined:connect(function(player)
    local session = players:session(player.name)   -- errors when another server holds it
    session.data.coins = (session.data.coins or 0) + 1
    session:save()
end)

-- on leave
session:release()      -- saves and lets go
```

- `players:session(key)` takes the lock and loads the value. It raises an error when another server
  is already holding that key, so wrap it in `pcall` if the player should be told rather than
  dropped.
- `session.data` is the loaded value as a plain table. Change it like any table.
- `session:save()` writes `data` back without letting the lock go. Call it at checkpoints, such as
  after a round.
- `session:release()` saves and lets go, which is what you do when the player leaves.

A session renews itself while it lives. A server that crashes loses its hold after a minute, so a
save is never stuck locked by a server that is gone.

<!-- demo:saving-sessions -->

## Ordered stores

An ordered store keeps one number per key and sorts them for you, so a leaderboard is a read rather
than a pass over every save. Server only, like `store`.

```lua
local scores = orderedStore("scores")

scores:set("meek", 12)
scores:increment("meek", 5)          -- 17, and hands it back
scores:increment("meek")             -- adds 1 when you leave the amount out
scores:get("meek")                   -- nil when there is nothing
scores:remove("meek")

for place, row in scores:getSorted(false, 10) do
    print(place, row.key, row.value)
end
```

`increment` is the call to use while a game is running: it reads, adds and writes in one step, and
returns the number it wrote, so you never have to fetch a score before changing it.

`getSorted` hands back a list of `{ key = ..., value = ... }`:

| Argument | |
|---|---|
| `ascending` | `false` is highest first, `true` is lowest first |
| `limit` | how many rows; 100 when left out, 1000 at most |
| `min`, `max` | only values in this range |

Because it is a list, the index you loop over is the position on the board: row 1 is the top score
when you pass `false`.

Two keys with the same value come back in key order. Only finite numbers go in: `1 / 0` and a NaN are
an error.

<!-- demo:saving-ordered -->

> [!NOTE]
> An ordered store and a plain store never share a name. `store("scores")` and
> `orderedStore("scores")` are two different stores, and writing one does not change the other.

### A top ten on the screen

The board is one gui the server writes, so every player reads the same ten rows without being sent
anything.

```lua
-- server
local scores = orderedStore("scores")

local screen = game.world:add("StarterGui"):add("ScreenGui", { name = "board" })
local panel = screen:add("Frame", {
    position = udim2(1, -12, 0, 12), anchorX = 1,
    size = udim2(0, 190, 0, 230),
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.4,
})
panel:add("UIListLayout", { padding = udim(0, 2) })
panel:add("UIPadding", { paddingTop = udim(0, 6), paddingLeft = udim(0, 8) })

local rows = {}
for place = 1, 10 do
    rows[place] = panel:add("TextLabel", {
        layoutOrder = place,
        size = udim2(1, 0, 0, 20),
        backgroundTransparency = 1,
        textColor = color(1, 1, 1),
        textXAlignment = "left",
        textSize = 14,
        text = "",
    })
end

task.every(5, function()
    local top = scores:getSorted(false, 10)
    for place, label in rows do
        local row = top[place]
        label.text = if row then place .. ". " .. row.key .. "  " .. row.value else ""
    end
end)

-- somewhere in the game, when a round is won
scores:increment(winner.name, 1)
```

- The gui goes in a `StarterGui`, so every player who joins gets a copy of it. See
  [Containers](containers.md) and [Interface](interface.md).
- The ten labels are made once and kept in `rows`. The refresh only writes their `text`, which is
  cheaper than building the list again every five seconds.
- `task.every(5, ...)` runs the refresh every five seconds. The server writes the labels and the
  change reaches every player the way any other property change does, with no message of your own.
- A row that does not exist yet, because fewer than ten people have scored, gets an empty string, so
  the panel keeps its shape.

## game.persist

`game.persist` is a plain table the engine hands back after a hot reload. Saving a file destroys
everything the place created and runs it again, and `game.persist` is what survives that. It does not
survive the server stopping.

Use it for things that only matter while the server is up, such as the round number or how many times
someone has joined this session. Anything that has to be there tomorrow goes in a store. For data several scripts share, that should
expire on its own, see [Memory stores](memory.md).

<!-- demo:saving-persist -->
