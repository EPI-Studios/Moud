# Windows

`window` is the game's own desktop window, and it is a client script global like `camera` and `input`.
You read its size, move it, retitle it and watch it for changes. `window.open` opens further windows
next to it, and `window.overlay` opens one that covers the whole screen.

## The game window

### Title and icon

```lua
window.title = "Crystal Rush"
window.icon = "res://ui/icon.png"        -- an exported game uses icon.png at the place root by default
```

`title` is the text in the title bar and the taskbar. `icon` takes a `res://` image. An exported game
already uses `icon.png` at the place root, so you only set `icon` to change it while the place runs.

### Size

```lua
window.fullscreen = true                 -- for this session; settings.fullscreen is the saved option
window.width, window.height              -- read, in pixels
window:resize(1280, 720)
window.minWidth = 800
window.minHeight = 450
window.resizable = false
```

- **`window.fullscreen`** switches this session only. The player's saved option is
  `settings.fullscreen`, on [the settings page](game-and-screens.md#settings), and writing that one
  outlives the place.
- **`window.width`** and **`window.height`** are read only, in pixels. To change the size, call
  `window:resize(width, height)`.
- **`window.minWidth`** and **`window.minHeight`** stop the player dragging the window smaller than
  your interface can cope with. Leave them alone and any size is allowed.
- **`window.resizable = false`** takes the drag handles away.

### Position

```lua
window.x, window.y                       -- read and write, in desktop pixels
window:moveTo(200, 120)
window:center()
window.canMove                           -- false only where the system refuses to move windows
```

`x` and `y` are in desktop pixels and you can both read and write them. `window:moveTo(x, y)` does the
same in one call, and `window:center()` centres the window. `window.canMove` is false only on a system
that refuses to move windows, and there a move does nothing.

### State, cursor and screen

```lua
window.focused                           -- read
window.minimized                         -- read
window.visible = false                   -- hide it, single player and exported games only
window.opacity = 0.85                    -- needs a desktop that composites windows

window.cursor = "hand"                   -- arrow, hand, text, crosshair, resizeEW, resizeNS, resizeAll,
                                         -- notAllowed, or "res://ui/cursor.png"
window.cursorVisible = false             -- while the pointer is free

window.fps                               -- read
window.displayWidth, window.displayHeight   -- the monitor the window is on
window:monitors()                        -- { { name, x, y, width, height, workX, workY, workWidth, workHeight, refreshRate, primary } }

window:flash()                           -- blinks in the taskbar
window:setClipboard("ABC-123")           -- there is no reading the clipboard
```

- **`window.focused`** and **`window.minimized`** are read only. They tell you whether the player is
  looking at the game right now.
- **`window.visible = false`** hides the game window. It works in single player and exported games
  only.
- **`window.opacity`** makes the whole window see-through, and needs a desktop that composites
  windows.
- **`window.cursor`** takes one of the named cursors or a `res://` image of your own. Set it to
  `"hand"` over a clickable thing and back to `"arrow"` when the pointer leaves.
- **`window.cursorVisible`** applies while the pointer is free.
- **`window.fps`** is the frame rate the game is running at, and **`window.displayWidth`** and
  **`window.displayHeight`** are the size of the monitor the window is on. All three are read only.
- **`window:monitors()`** returns one table per monitor, with its position and size, the work area
  (`workX`, `workY`, `workWidth`, `workHeight`), its `refreshRate` and whether it is the `primary`
  one.
- **`window:flash()`** blinks the window in the taskbar, which gets the attention of a player who has
  alt-tabbed away.
- **`window:setClipboard(text)`** puts text on the system clipboard. There is no reading the
  clipboard, so a place can never look at what the player copied elsewhere.

### Signals

```lua
window.resized:connect(function(width, height) end)
window.moved:connect(function(x, y) end)
window.focusChanged:connect(function(focused) end)
```

`resized` and `moved` fire when the player drags the window and when a script changes it.
`focusChanged` is handed true when the window takes focus and false when it loses it.

Everything here goes back to normal when the scripts reload.

### Moving the window

Moves apply on the frame they are asked for, and `window.x` and `window.y` answer what was set rather
than the position the system last reported, so a script can move the window every frame. There is no
shake function, because a shake is a move:

```lua
local function shake(strength: number, time: number)
    local baseX, baseY = window.x, window.y
    local left = time
    local connection
    connection = game.renderStepped:connect(function(dt)
        left -= dt
        if left <= 0 then
            window:moveTo(baseX, baseY)
            connection:disconnect()
            return
        end
        local power = strength * (left / time)
        window:moveTo(baseX + math.random(-power, power), baseY + math.random(-power, power))
    end)
end
```

- The base position is read once, before the shake starts, so the window comes back to where it began.
- `left` counts down by the frame's `dt`, and `power` shrinks with it, so the shake fades out instead
  of stopping dead.
- The connection disconnects itself on the last frame. Without that, the handler would keep running
  for the rest of the session.

<!-- demo:window-shake -->

> [!NOTE]
> Minecraft runs through XWayland on Wayland desktops, so moving works there too. A player who
> switched Minecraft to native Wayland gets `window.canMove == false`, and moves do nothing for them.

### Closing

`window.closing` fires when the player tries to close the game window. Calling `window:preventClose()`
inside the handler keeps it open:

```lua
window.closing:connect(function()
    window:preventClose()
    saveMenu.enabled = true               -- "quit without saving?"
end)
```

> [!IMPORTANT]
> A place can hold the window open once. Closing it again within 10 seconds always closes it, so a
> game can never trap the player.

<!-- demo:window-closing -->

## More windows

`window.open(properties)` opens another desktop window and returns it. The window is an instance of
the `Window` class, so you set its properties whenever you like and tween them:

```lua
local popup = window.open({
    title = "Warning",
    width = 320, height = 180,
    x = 200, y = 200,
    decorated = false,       -- no title bar
    transparent = true,      -- see-through where nothing is drawn; only when opening
    alwaysOnTop = true,
    resizable = false,
    gui = world:find("WarningGui"),
})
popup:center()
```

```lua
popup:tween({ x = popup.x + 600 }, { time = 0.4, easing = "back", direction = "out" })
popup.opacity = 0.5
popup.title = "Still here"
```

| Property | |
|---|---|
| `title`, `x`, `y`, `width`, `height` | the player dragging or resizing it writes these back |
| `visible`, `decorated`, `alwaysOnTop`, `resizable`, `opacity` | applied straight away |
| `transparent` | only read when the window opens |
| `clickThrough` | the mouse passes through to whatever is behind |
| `clickable` | with `clickThrough`, a gui object inside `gui` that still catches the mouse |
| `focused` | read |
| `gui` | a `ScreenGui` drawn in the window instead of on the game screen, clicks and typing included |
| `camera` | a `Camera` whose view of the world's parts and models is drawn, with `gui` on top |
| `mirror` | shows what the game window shows |

Because the player writes `title`, `x`, `y`, `width` and `height` back by dragging, reading them tells
you where the window ended up, not only where you put it. `transparent` is the one property the window
only reads as it opens, so decide it in the table you pass to `window.open`.

```lua
popup:moveTo(900, 400)
popup:resize(400, 240)
popup:focus()
popup:close()                          -- or popup:destroy()

popup.moved:connect(function() end)    -- the player dragged it
popup.resized:connect(function() end)
popup.closing:connect(function()
    popup:preventClose()               -- same rule as the game window: the second close wins
end)
```

What a window can show:

- A `gui` is cheap: it is drawn once more into the window.
- A `camera` draws the world's parts and models again from that camera, like a `ViewportFrame`.
  Blocks are not drawn: Minecraft can only draw its world once per frame.
- `mirror` is the cheapest: it shows the finished game frame.

Windows a script opens close when the scripts reload.

## Overlays

An overlay is a window that covers the whole primary monitor, has no title bar, is see-through and
always on top, and lets the mouse through. It is how a character walks on the desktop or a hint floats
over other programs.

```lua
local overlay = window.overlay({ gui = world:find("DesktopPet") })
overlay.clickable = world:find("DesktopPet/Pet")    -- only the pet catches clicks
```

`window.overlay(properties)` is `window.open` with those settings filled in first. Anything you pass
wins, so you can take one of them back. Setting `clickable` to a gui object inside `gui` carves a hole
in the click-through: the pet above catches clicks while the rest of the screen still belongs to
whatever program is behind it.

<!-- demo:window-overlay -->

## Limits

- Extra windows, overlays and hiding the game window only work in single player and exported games. On
  a server with other players they are refused with a warning.
- See-through windows and opacity need a desktop that composites windows. Nearly every desktop does.
  Without one they are drawn black where they should be see-through.

> [!WARNING]
> <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>Escape</kbd>, pressed in any of the game's windows, closes
> every extra window and overlay and shows the game window again. It is there for the player, and for
> you when an overlay covers the screen and the script that opened it has stopped answering.
