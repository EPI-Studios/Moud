# Memory stores

`memory` is a global that keeps small, short lived data in the server's memory: who is waiting for a
match, the scores of the current event, a lock that should let go on its own. Everything written to it
has an expiration and goes away when that runs out. Nothing is written to disk.

Use a [store](saving.md) for what has to be there tomorrow. Use `memory` for what only matters for
minutes or days, and for what several scripts need to share quickly.

```lua
local waiting = memory:hashMap("waiting")

waiting:set(player.name, { joined = clock() }, 60)   -- gone after 60 seconds
local entry = waiting:get(player.name)                -- nil once it has expired
```

There are three kinds:

- `memory:hashMap(name)` is keys and values.
- `memory:sortedMap(name)` is keys and values kept in order, which you read in pages.
- `memory:queue(name, invisibilityTimeout?)` is a line of values that scripts take from.

A name is 1 to 128 characters. Every server script that asks for the same kind with the same name
gets the same data. A hash map and a sorted map with the same name are two different things.

## What they share

- **Server only.** `memory` works in a server `Script`. In a `LocalScript` every call errors with
  "memory stores are used from a server Script".
- **Every write has an expiration.** It is a number of seconds, more than 0 and up to 3,888,000 (45
  days). There is no default, and leaving it out is an error. Writing a key again starts its
  expiration over. Expirations count real time, so a paused singleplayer game does not stop them.
- **Keys** are strings of 1 to 128 characters.
- **Values** are what a [store](saving.md) takes: numbers, strings, booleans and tables of them,
  nested up to 32 deep. A value is at most 32 KB once encoded. `nil` is an error; remove the key
  instead. What you read back is a copy, so changing it changes nothing until you write it again.
- **How long it lasts.** Everything is cleared when the place stops and when it goes back to editing.
  A hot reload keeps it, like [`game.persist`](saving.md#gamepersist).
- **One server.** The data lives in this server. Nothing is shared with another server.

<!-- demo:memory-expiration -->

## Hash maps

```lua
local locks = memory:hashMap("locks")

locks:set("door", player.name, 10)
print(locks:get("door"))

local count = locks:update("visits", function(old)
    return (old or 0) + 1
end, 3600)

locks:remove("door")
print(locks:list(50))
```

| Method | What it does |
|---|---|
| `get(key)` | the value, or `nil` when there is none or it has expired |
| `set(key, value, expiration)` | writes the value. Returns `true` |
| `update(key, transform, expiration)` | calls `transform(old)` with the value or `nil`, and writes what it returns. Returns the new value |
| `remove(key)` | deletes the key. Removing a key that is not there is fine |
| `list(pageSize?)` | the keys, in alphabetical order, up to `pageSize` of them. 1 to 1000, default 1000 |

- If `transform` returns `nil`, nothing is written and `update` returns `nil`. Use that to leave the
  value as it is.
- `list` returns only the first page. There is no next page, so a map with more than 1000 keys cannot
  be listed whole.
- Hash maps have no size call.

## Sorted maps

A sorted map keeps each key with a value and an optional sort key, and reads them back in order.

```lua
local scores = memory:sortedMap("event")

scores:set(player.name, { name = player.name }, 3600, 42)
local value, sortKey = scores:get(player.name)

local top = scores:getRange("descending", 10)
for i, row in top do
    print(i, row.key, row.sortKey)
end
```

| Method | What it does |
|---|---|
| `get(key)` | the value and its sort key, or `nil` |
| `set(key, value, expiration, sortKey?)` | writes them. Returns `true` if the key was new, `false` if it replaced one |
| `update(key, transform, expiration)` | calls `transform(value, sortKey)`, and writes the value and sort key it returns. Returns them |
| `remove(key)` | deletes the key |
| `getRange(direction, count, exclusiveLowerBound?, exclusiveUpperBound?)` | up to `count` rows, `"ascending"` or `"descending"`. `count` is 1 to 200 |
| `getSize()` | how many keys there are |

- A sort key is a number or a string of up to 128 characters. A number must be finite.
- If `transform` returns `nil`, nothing is written and `update` returns `nil`. If it returns only a
  value, the key loses its sort key.
- `getRange` returns a list of `{ key = ..., value = ..., sortKey = ... }`. `sortKey` is missing on
  a row that has none.

### The order

Ascending order is:

1. keys with a number sort key, smallest first,
2. then keys with a string sort key, in alphabetical order,
3. then keys with no sort key.

Keys with the same sort key, or with none, are in alphabetical order of the key. Alphabetical order
compares characters, so capitals come before small letters. Descending is the same order backwards.

### Bounds

A bound is a table `{ key = ..., sortKey = ... }` with one or both. The rows returned are strictly
after the lower bound and strictly before the upper bound, in the order above. Use the last row of a
page as the lower bound of the next page:

```lua
local page = scores:getRange("ascending", 100)
local last = page[#page]
local following = scores:getRange("ascending", 100, { key = last.key, sortKey = last.sortKey })
```

- A bound with only a `sortKey` leaves out every key with that sort key.
- A bound with only a `key` counts as having no sort key, so it sits among the keys with none. As a
  lower bound it leaves out every key that has a sort key.

<!-- demo:memory-sorted -->

## Queues

A queue hands values out to whichever script reads first. A value read is hidden, not removed. The
reader removes it once it is done. If the reader never does, the value shows up again after the
invisibility timeout, so another script can try.

```lua
local jobs = memory:queue("jobs", 30)

jobs:addAsync({ map = "desert" }, 600)
jobs:addAsync({ map = "castle" }, 600, 10)    -- higher priority, read first

task.spawn(function()
    while true do
        local values, id = jobs:readAsync(1)
        startMatch(values[1])
        jobs:removeAsync(id)
    end
end)
```

`memory:queue(name, invisibilityTimeout?)` takes the seconds a value stays hidden after it is read,
more than 0 and up to 45 days. The default is 30. The timeout belongs to the object you got, so two
calls with the same name and different timeouts read the same queue with different timeouts.

| Method | What it does |
|---|---|
| `addAsync(value, expiration, priority?)` | adds the value. `priority` is a finite number, default 0 |
| `readAsync(count, allOrNothing?, waitTimeout?)` | reads up to `count` values, 1 to 100. Returns the values and a read id |
| `removeAsync(id)` | removes the values of that read |
| `getSize(excludeInvisible?)` | how many values there are. `true` leaves out the hidden ones |

- Higher priority is read first. The same priority is read in the order it was added.
- `readAsync` takes only values that are not hidden. With `allOrNothing` it takes nothing until
  `count` values are there.
- When nothing can be read, `readAsync` waits. It checks again every tick. `waitTimeout` is how many
  seconds it waits; `-1`, the default, waits until something comes. When it gives up it returns an
  empty table and `nil`. `0` returns at once. It yields, so call it inside `task.spawn` or a function
  that may yield.
- `removeAsync` removes only values still hidden by that read. Once a value shows up again, the old
  id no longer removes it, and a new read gives it a new id.

<!-- demo:memory-queue -->

## Limits

| What | Limit |
|---|---|
| name | 1 to 128 characters |
| key | 1 to 128 characters |
| string sort key | up to 128 characters |
| value | 32 KB once encoded |
| expiration and invisibility | more than 0, up to 45 days |
| `list` page | 1 to 1000 keys |
| `getRange` | 1 to 200 rows |
| `readAsync` | 1 to 100 values |

There is no limit on how many keys a map holds or how often you call it. All of it is in the
server's memory, so keep it small.

Nothing is throttled and nothing counts against a quota, so calls do not fail for being too many,
and there is no need to retry them.
