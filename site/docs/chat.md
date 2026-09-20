# Chat

While a place runs, Moud draws the chat window itself. Minecraft keeps the input box, command
suggestions and message history. Messages from the game (command output, deaths, other mods) show
in the same window.

You shape chat with instances in the tree. A `TextChannel` decides who a message reaches, a
`ChatWindow` decides how the lines look, a `ChatInputBar` and `ChatTabs` decide what typing does, and
a `BubbleChat` floats messages over heads. `game.chat` is the global you send, edit and delete
messages through.

## Channels

A `TextChannel` is a stream of messages with its own members. A player is in a channel while a
`TextSource` for them is inside it.

```lua
-- server
local general = world:add("TextChannel", { name = "general", displayName = "General", autoJoin = true })
local team = world:add("TextChannel", { name = "red", autoJoin = false, color = color(1, 0.3, 0.3) })

game.chat:addPlayer(team, body)
game.chat:removePlayer(team, body)
```

- `general` has `autoJoin = true`, so every player who joins is put in it without you doing anything.
- `team` has `autoJoin = false`, so it stays empty until you call `game.chat:addPlayer`. That is how
  you build a channel only some players can read, such as one team's chat.
- `addPlayer` creates the player's `TextSource` inside the channel; `removePlayer` takes it out, and
  the player stops receiving that channel's messages.

| Property | Meaning |
|---|---|
| `autoJoin` | every player who joins is put in it |
| `displayName`, `color` | shown on its tab |
| `slowMode` | least seconds between two messages from one player |
| `maxLength` | longest message a player may send |
| `richText` | the tags members may use in what they type: `"b,i,color"`, or `"*"` for all |

A member is a `TextSource` with `player` (their id), `body` (the body they wear, kept up to date)
`canSend` (false mutes them in that channel only) and `richText` (their own tag list, which wins
over the channel's). You reach a member's `TextSource` to mute one player without touching the rest
of the channel.

A place without any `TextChannel` still has chat: messages go to everyone.

## Deciding who hears what

Channels have three callbacks. A place assigns a function; leaving one unset keeps the engine's
answer.

```lua
-- server: does the message go out at all?
general.shouldSend = function(message)
    return not string.find(message.plain, "badword")
end

-- server: does it reach this member?
general.shouldDeliver = function(message, source)
    return source.body ~= nil and (source.body.position - message.position).magnitude < 30
end

-- server: change text, prefix or metadata before it goes out
-- client: change how it looks in this client's window
general.onIncoming = function(message)
    return { prefix = "<color=#ffaa00>" .. message.prefix .. "</color>" }
end
```

- `shouldSend` runs once per message. Returning false stops the message for everybody.
- `shouldDeliver` runs once per member, with that member's `TextSource` as `source`. The example
  compares the member's body against `message.position`, which is where the sender stood, so only
  players within 30 metres see the line.
- `onIncoming` returns a table of fields to change. What you leave out stays as it was.

Only `false` from `shouldDeliver` keeps a message back; `nil` lets it through. A callback that
returns nothing therefore delivers, which matters when a branch of your function falls off the end.

`game.chat.onIncoming` and `game.chat.shouldSend` do the same for every channel at once. Use those
for a rule that holds everywhere, such as a word filter, and the channel's own for a rule that
belongs to one stream.

### Ready-made checks

The engine ships the common `shouldDeliver` rules, so proximity chat is one line:

```lua
general.shouldDeliver = game.chat.within(30)              -- proximity chat
general.shouldDeliver = game.chat.audible(30)             -- within 30 m and no wall in between
team.shouldDeliver = game.chat.sameTag("red")             -- only players whose body has the tag
general.shouldDeliver = game.chat.all(game.chat.within(80), game.chat.sameTag("alive"))
general.shouldDeliver = game.chat.any(game.chat.sameTag("staff"), game.chat.within(20))

local d = game.chat.distance(message, source)             -- nil when either has no body
```

`all` delivers when every check passes, `any` when one of them does, and both take the same checks
you would have assigned on their own.

<!-- demo:chat-deliver -->

## Messages

A message is a table:

| Field | |
|---|---|
| `id` | number, used to edit or delete it |
| `text` | rich text |
| `prefix` | rich text shown before the text, usually the sender's name |
| `metadata` | any string a place attaches |
| `channel` | the `TextChannel`, or nil |
| `source` | the sender's `TextSource`, or nil |
| `body` | the sender's body, or nil |
| `position` | where the sender stood when they sent it, or nil |
| `timestamp` | milliseconds since 1970 |
| `status` | `Success`, `Sending`, `Blocked`, `Muted`, `TooLong`, `SlowMode`, `Floodchecked`, `NotInChannel`, `System` |
| `plain` | the text with every tag removed |

The fields that can be nil are nil for messages nobody sent: a system message has no `source`, no
`body` and no `position`. Test for them before you read through them, the way `shouldDeliver` above
tests `source.body ~= nil`. Match on `plain` rather than `text` when you filter, so a player cannot
hide a word inside a tag.

What a player types shows exactly as typed unless the channel or their `TextSource` lists tags in
`richText`. Only those tags work; any other tag shows as text. This happens before any hook sees the
message. A player sending too fast, into slow mode or while muted gets a message saying so, and its
`status` says which of those it was.

<!-- demo:chat-status -->

> [!CAUTION]
> Be careful with `click` and `body` in a channel's `richText`. A player given those tags can make
> links that run commands for whoever clicks them.

## Sending, editing and deleting

The server sends into a channel, to one player, or from nobody at all:

```lua
-- server
local id = game.chat:send(general, "Round starts in <b>10</b>", { metadata = "countdown" })
game.chat:send(general, "hi", { from = body })     -- as that player
game.chat:send("just you", { to = body })          -- one player, no channel
game.chat:system("The server restarts soon")       -- a message from nobody
game.chat:edit(id, "Round starts in <b>9</b>")
game.chat:edit(id, { text = "...", prefix = "...", metadata = "..." })
game.chat:delete(id)
game.chat:clear()
game.chat:messages()                                -- the recent messages
```

- `send` returns the message's `id`. Keep it if you mean to edit or delete the line later, the way
  the countdown above does.
- `from = body` makes the message look as though that player sent it, so it carries their prefix.
- `metadata` is your own string. Nothing in the engine reads it, and your `onIncoming` can, which is
  how the countdown line is recognised again later.
- An edit or a delete only reaches the players who got the message.

The client sends as its own player and can write into its own window:

```lua
-- client
game.chat:send(general, "hello")                   -- as this player, through the server
game.chat:system("only you can see this")          -- into this client's window only
game.chat:open("/team ")   game.chat:close()   game.chat:isOpen()
game.chat:setTarget(team)  game.chat:getTarget()
game.chat:messages()       game.chat:clear()
```

`game.chat:open("/team ")` opens the input box with that text already typed, which is how you put a
player halfway into a command. `setTarget` picks which channel typing goes to, the same choice
clicking a tab makes.

> [!NOTE]
> Calling a server function on a client, or the other way round, is an error that says which side it
> belongs to.

## Signals

| Signal | Arguments | Side |
|---|---|---|
| `messageReceived` | message | both |
| `sending` | message | client, also for refused messages |
| `edited` | message | both |
| `deleted` | id | both |
| `linkClicked` | value, message | client, from `<click callback=...>` |
| `bodyClicked` | body, message | client, from `<body id=...>` |
| `messageClicked` | message, button | client |
| `opened`, `closed` | | client |
| `typing` | text | client |

Every `TextChannel` also has its own `messageReceived`. Connect to the channel's when you only care
about one stream, and to `game.chat`'s when you want every message the player receives.

`sending` fires on the client for messages that were refused as well, so it is where you tell a
player why their line did not go out; the reason is in the message's `status`.

## Commands

A `ChatCommand` claims one or more slash commands. The engine parses the line and hands you the
words after the trigger:

```lua
local cmd = world:add("ChatCommand", { triggers = "/team /t" })
cmd.invoked:connect(function(body, line, args)
    -- /team red  ->  args = { "red" }
end)
```

`triggers` is a space-separated list, so `/team` and `/t` both reach this command. `body` is the
body of the player who typed it, `line` is what they typed, and `args` is the line split into words.

## The window

The first `ChatWindow` in the tree shapes it. Without one it looks like the game's chat.

```lua
world:add("ChatWindow", {
    position = udim2(0, 4, 1, -40), size = udim2(0, 320, 0, 180), anchorX = 0, anchorY = 1,
    verticalAlignment = "bottom", horizontalAlignment = "left",

    backdrop = "focused",            -- always, focused, never
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.6, cornerRadius = 4, padding = 2,

    lineBackgroundColor = color(0, 0, 0), lineBackgroundTransparency = 0.5,
    lineCornerRadius = 3, linePaddingX = 2, linePaddingY = 1, lineGap = 1, lineFitsText = true,

    font = "", textSize = 9, textColor = color(1, 1, 1), textShadow = true,
    textStrokeColor = color(0, 0, 0), textStrokeTransparency = 1, prefixColor = color(1, 1, 1),
    timestamps = true, timestampFormat = "HH:mm", timestampColor = color(0.6, 0.6, 0.6),

    visibleTime = 10, fadeTime = 0.5, maxMessages = 100,
    enterAnimation = "slideLeft", exitAnimation = "fade", hideAnimation = "slideDown",
    animationTime = 0.2, easing = "quad",

    visible = true, enabled = true,
    shader = "",
})
```

The properties fall into groups: where the window sits, how the whole box is painted, how each line
is painted, how the text reads, and how long a line stays before it fades.

- `visibleTime` is how long a message stays fully visible; `0` never fades.
- Animations are `none`, `fade`, `slideLeft`, `slideRight`, `slideUp`, `slideDown`, `pop`,
  `typewriter`.
- `visible = false` hides the window with `hideAnimation`; setting it back shows it again.
- While the chat is open, the mouse wheel scrolls.

<!-- demo:chat-window -->

**Per message.** A client `onIncoming` may return any of: `text`, `prefix`, `textColor`,
`textSize`, `font`, `prefixColor`, `backgroundColor`, `backgroundTransparency`, `cornerRadius`,
`textShadow`, `textStrokeColor`, `textStrokeTransparency`, `visibleTime`, `animation`, `icon`
(an image shown before the line). Those override the `ChatWindow` for that one line.

```lua
-- client
game.chat.onIncoming = function(message)
    if message.metadata == "warning" then
        return { backgroundColor = color(0.5, 0.1, 0.1), icon = "res://icons/warn.png", animation = "pop" }
    end
end
```

The function returns nothing for every other message, and those lines keep the window's own styling.

**The window shader.** `shader` draws the whole window through a GLSL file that defines
`vec4 windowColor(vec2 uv, vec2 local, float time)`. `scene(uv)` reads the picture; `local` runs
from 0 to 1 across the window.

```glsl
vec4 windowColor(vec2 uv, vec2 local, float time) {
    vec4 c = scene(uv);
    c.rgb *= 0.9 + 0.1 * sin(local.y * 400.0);
    return c;
}
```

That one darkens and lightens along the height of the window, which reads as scan lines.

## The input bar and tabs

A `ChatInputBar` is the box the player types into, and `ChatTabs` is the row that picks which channel
they type into:

```lua
world:add("ChatInputBar", {
    enabled = true,                 -- false stops the chat from opening
    targetChannel = general,        -- where typing goes; otherwise the first channel the player is in
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.5, textColor = color(1, 1, 1),
    placeholder = "Say something", placeholderColor = color(0.6, 0.6, 0.6),
    maxLength = 256, autocomplete = true,
})

world:add("ChatTabs", { backgroundColor = color(0, 0, 0), selectedColor = color(0.25, 0.25, 0.25),
    textColor = color(1, 1, 1), unreadColor = color(1, 0.8, 0.2) })
```

Tabs show while the chat is open and the player is in two or more channels, with unread counts.
Clicking a tab picks where typing goes.

`enabled = false` on the input bar stops the chat from opening at all, which is what you want in a
place where players are not meant to talk.

## Speech bubbles

With a `BubbleChat` in the tree, a player's message also floats over their head:

```lua
world:add("BubbleChat", {
    visibleTime = 8, maxBubbles = 3, maxDistance = 48, offset = vec3(0, 0.35, 0),
    backgroundColor = color(1, 1, 1), backgroundTransparency = 0.1,
    textColor = color(0.1, 0.1, 0.12), textSize = 9, font = "",
    cornerRadius = 6, padding = 4, tail = true, maxWidth = 180, pixelsPerMetre = 60,
    alwaysOnTop = false, animation = "pop", animationTime = 0.2,
})
```

`maxBubbles` is how many stack over one head, `maxDistance` is how far away a bubble is still drawn,
and `offset` lifts it clear of the head.

```lua
-- client: restyle a bubble, or return false for none
game.chat.onBubble = function(message)
    if message.metadata == "whisper" then return false end
    return { backgroundColor = color(0.1, 0.1, 0.1), textColor = color(1, 1, 1) }
end

-- client: a bubble over anything, with no chat line
game.chat:bubble(shopkeeper, "Welcome!", { visibleTime = 4 })
```

`onBubble` works like `onIncoming`: return a table of fields to change, and return `false` to keep
the bubble from appearing while the chat line still goes out. `game.chat:bubble` goes the other way
and puts a bubble over any instance without sending a message at all, which is how a shopkeeper
talks.

## Text styling

Messages, prefixes and bubbles use [rich text](rich-text.md).

```lua
game.chat.escape(text)                   -- shows exactly as written
game.chat.plain(text)                    -- strips the tags
game.chat.bodyLink(body, "Meek")         -- a clickable name
game.chat.itemLink("minecraft:diamond", 3)
```

> [!TIP]
> Run any text a player wrote through `game.chat.escape` before you put it inside a message of your
> own. Otherwise a tag they typed becomes a tag in your line.
