# Talking across the boundary

The server owns the tree and each client holds a mirror of it. Property changes travel one way on
their own, server to client, and nothing a client writes to its own mirror goes anywhere. This page
is the other direction: how a client tells the server something, how one side asks the other a
question and waits for the answer, how scripts on one side talk to each other, and how you share a
value that is not a property of anything.

## A channel is an instance

A `Remote` is a channel you send messages down. You create one the way you create anything else:

```lua
-- server
local hit = world:add("Remote", { name = "hit" })
```

It has a name, a parent and a lifetime. "Does this channel exist" is the same question as "does this
part exist", and destroying whatever it hangs off closes it. You find it the way you find anything:
`game.world:find("hit")`.

## What a message may carry

A remote refuses any argument it was not told about. `accepts` lists them in order, and `?` marks one
that may be left out:

```lua
local pressed = world:add("Remote", { name = "pressed", accepts = "string" })
local aim = world:add("Remote", { name = "aim", accepts = "vec3, number?" })
```

- `pressed` takes one string and nothing else. A client that sends a number down it is refused.
- `aim` takes a `vec3` and then a number the sender may leave out.

Kinds: `bool`, `number`, `string`, `vec3`, `quat`, `cframe`, `color`, `udim2`, `instance`, `list`,
`table`. An empty `accepts` means the message carries nothing.

<!-- demo:talking-accepts -->

## Sending a message and hearing one

Each side has its own verb for saying something and its own signal for hearing it, and the two
directions are not the same shape:

| | says | hears |
|---|---|---|
| client | `remote:fireServer(...)` | `remote.onClient:connect(function(...) end)` |
| server | `remote:fireClient(body, ...)`<br>`remote:fireAllClients(...)` | `remote.onServer:connect(function(body, ...) end)` |

Each verb is refused on the side it doesn't belong to, by name. Calling `fireServer` on the server is
a mistake, and being told beats a message that goes nowhere.

The sender is handed over, never passed. `onServer` receives the **body** of whoever sent it as its
first argument, supplied by the engine. A player is addressed by its body here, the same way
`game.players:me()` answers with one. A client cannot claim to be somebody else, because it never
says who it is.

A player can have no body: before `player:spawn()` with `game.players.autoSpawn = false`, or while
dead. `onServer` then hands over `nil`. `onServerPlayer` fires for the same message with the
**Player** instead, which always exists:

```lua
play.onServerPlayer:connect(function(player, choice)
    if choice == "start" then player:spawn() end
end)
```

Use `onServerPlayer` whenever the message is about a player rather than about a body, such as a menu
choice, a settings change, or anything sent before the player has spawned.

<!-- demo:talking-directions -->

`game.players:list()` gives every `Player`, `game.players:byId(player.id)` finds one again later, and
`game.players:playerOf(body)` turns a body into its player.

> [!IMPORTANT]
> Everything *after* the sender is a claim by the client. Check it before you act on it:
>
> ```lua
> hit.onServer:connect(function(from, what, howHard)
>     if typeof(what) ~= "string" or typeof(howHard) ~= "number" then return end
>     if howHard <= 0 or howHard > 10 then return end
>     from.humanoid.health -= howHard
> end)
> ```

## When you don't care if it arrives

An `UnreliableRemote` is a channel that trades ordering and delivery for costing nothing:

```lua
local looking = world:add("UnreliableRemote", { name = "looking" })
```

Use it for something that is continuously replaced, where the next one is along shortly: a cursor, a
look direction, a held key. A message that is lost is a frame of staleness and no more.

Unreliable delivery is its own class rather than a flag on `Remote`, so a purchase cannot go down an
unreliable channel because somebody passed the wrong boolean.

> [!WARNING]
> Never send a score, a payment or anything else that must arrive exactly once down an
> `UnreliableRemote`. The engine will not tell you when one goes missing.

<!-- demo:talking-unreliable -->

## Asking and waiting for the answer

A `Remote` says something and moves on. A `RemoteFunction` asks, waits, and hands back what the
other side returned:

```lua
-- server
local buy = world:add("RemoteFunction", { name = "buy", accepts = "string" })
local prices = { sword = 50, shield = 30 }
local coins = {}

buy.onServerInvoke = function(player, item)
    local price = prices[item]
    if price == nil then return false, "that is not for sale" end
    local have = coins[player.id] or 0
    if have < price then return false, "you need " .. price - have .. " more coins" end
    coins[player.id] = have - price
    return true, "you bought a " .. item
end
```

```lua
-- client
task.spawn(function()
    local bought, message = game.world.buy:invokeServer("sword")
    print(bought, message)
end)
```

- `onServerInvoke` is assigned, not connected. It is one function.
- The handler is handed the `Player` first, then the arguments the client sent. Both of its returns
  arrive on the client as the two values `invokeServer` gives back.
- The client's call sits inside `task.spawn` because it waits for the answer.
- The prices and the coins live on the server only. The client learns that a sword costs 50 by being
  told, which is the point of asking rather than deciding locally.

| | asks | answers |
|---|---|---|
| client | `remote:invokeServer(...)` | `remote.onClientInvoke = function(...) end` |
| server | `remote:invokeClient(player, ...)` | `remote.onServerInvoke = function(player, ...) end` |

`accepts` works the way it does on a `Remote`, and is checked on both sides. What the handler returns
is not checked against anything, but it has to be something that [may cross](#what-may-cross).

The server's handler is handed the Player, the same way `onServer` is handed the body. A call that
did not come from a player is refused before your handler runs.

`invokeClient` takes a `Player` or a body, and asks that one client. An answer only counts when it
comes from the player that was asked.

Invoking waits, so it runs inside a function started with `task.spawn`. A handler can wait too, with
`task.wait` or a signal's `wait`, and the caller keeps waiting until it returns.

A handler is one function, not a signal: assigning another replaces it, and `nil` removes it.

### When there is no answer

Each of these raises an error in the caller, where `pcall` catches it:

- the handler errors, and the caller gets its message
- nothing is assigned to `onServerInvoke` or `onClientInvoke` on the other side
- the arguments don't match `accepts`, or the answer can't cross
- no answer arrives within `timeout` seconds, 30 by default; `0` waits forever
- the remote function is destroyed while the caller waits
- the script that was answering stops first
- `invokeClient` is asked about a player who has left, or one who leaves before answering. That
  holds with `timeout = 0` too: waiting forever means waiting for an answer, not for somebody who
  is gone.
- the call is over the rate limit, which fails at once with "too many calls" rather than waiting
  out the timeout

A client is told **what** went wrong, never where. A server handler that errors sends its message
across without the file and line it failed on; the server log keeps those.

A client can close the game or never answer, so the server gives it a timeout and a `pcall`. Ask each
player from their own `task.spawn`, so one slow answer doesn't hold up the rest:

```lua
-- server
local ready = world:add("RemoteFunction", { name = "ready", accepts = "string", timeout = 10 })

for _, player in game.players:list() do
    task.spawn(function()
        local ok, said = pcall(function()
            return ready:invokeClient(player, "Start the round?")
        end)
        if not ok then
            print(player.name, "did not answer:", said)
        elseif said then
            player:spawn()
        end
    end)
end
```

```lua
-- client: label is a TextLabel and yes a TextButton on a screen gui
game.world.ready.onClientInvoke = function(question)
    label.text = question
    return yes.activated:wait(8) ~= nil
end
```

The client's handler waits up to eight seconds for the button. `wait(8)` returns `nil` when nobody
presses it, so the handler answers `false` and the server spawns nobody. The server's own
`timeout = 10` covers the case where the client never answers at all.

<!-- demo:talking-ready -->

Calls count against the same limit as messages: 6 per remote per tick from one client. A call past
it fails straight away with "too many calls", rather than leaving the caller to wait out its
timeout. Answering a call the server asked for does not count, so a client that is asked ten times
in a tick can reply to all ten.

## What may cross

A number, text, a flag, nothing, a `Vector3`, a `Quat`, a `CFrame`, a `Color`, an **instance**, and
lists or text-keyed tables of those.

An instance crosses as the id it is and is resolved on the far side; one that was destroyed on the way
arrives as `nil`, which is the ordinary race and not an error.

Anything else is an error that names what was allowed: a function, a table with number keys mixed
with text keys, or an instance one side made locally and the other has never heard of. None of these
is dropped quietly.

Limits: 16 arguments, 8 levels of nesting, 256 values per delivery, and 6 deliveries per channel per
tick from any one client. Past the rate the rest of the tick is dropped and counted.

## Messages between scripts on one side

`messaging` is a topic board. A script publishes to a name, and every script that subscribed to that
name hears it. Use it when one part of your code wants to say that something happened without
knowing who is listening.

```lua
local subscription = messaging:subscribe("rounds", function(message)
    print("round", message.name, "in", message.seconds)
end)

messaging:publish("rounds", { name = "final", seconds = 90 })

subscription:unsubscribe()
```

- Handlers run **in order of subscription, straight away**, inside the `publish` call. There is no
  queue and no tick of delay.
- A message is any Luau value: it never leaves the machine, so none of the
  [rules about what may cross](#what-may-cross) apply.
- A topic is one to 1024 letters. An empty one is an error.
- Subscribing twice with the same handler subscribes twice, and each `unsubscribe` drops one.
  Unsubscribing something already gone does nothing.
- A subscription made by a `Script` ends when it is destroyed or disabled, like its signal
  connections and its `task` threads.

> [!NOTE]
> `messaging` does not cross the boundary. The server has one board and each client has its own, so
> a `publish` on the server is heard by server scripts only. Use a channel for the other side.

Moud has one server, so `messaging` never carries anything between servers. It is only the loose
coupling: one script saying a thing happened without knowing who is listening.

## Shared state

A value instance holds one value in the tree, which is how you share a number or a piece of text that
is not a property of anything:

```lua
local score = world:add("NumberValue", { name = "score" })
local phase = world:add("StringValue", { name = "phase", value = "waiting" })

score.value += 1
score.changed:connect(function(property) print(score.value) end)
```

There are five of them: `NumberValue`, `StringValue`, `BoolValue`, `Vector3Value` and `ObjectValue`.
They replicate because every property does, so a client reads one straight off the tree without being
told anything, and hears it change without a channel.

> [!NOTE]
> `changed` gives you the **property name**, like every other instance, so you read `.value` off the
> thing that changed. It does not hand over the new value.
