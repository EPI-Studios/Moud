# Interface

An interface in Moud is made of instances, the same as everything else in a place. You add a
container to the tree, add widgets inside it, and set their properties. The engine reads the whole
interface every frame, so a property you write shows on the next frame and there is nothing to
refresh or redraw by hand.

## Three containers

Every widget sits inside one of three containers. The container decides where the interface is drawn
and what its sizes are measured against.

| Class | Where it draws | Sized by |
|---|---|---|
| `ScreenGui` | over the screen | the screen |
| `BillboardGui` | floating over `adornee`, always facing the camera | its `size` |
| `SurfaceGui` | flat on one `face` of the part `adornee` | that face |

```lua
-- client
local hud = world:add("ScreenGui", { name = "hud", displayOrder = 1 })

-- server or client
local tag = world:add("BillboardGui", {
    adornee = body, offset = vec3(0, 2.3, 0),
    size = udim2(2, 0, 0.4, 0),        -- metres: 2 wide, 0.4 tall
    pixelsPerMetre = 100, maxDistance = 40,
})

local sign = world:add("SurfaceGui", { adornee = deck, face = "top", pixelsPerMetre = 100 })
```

- **`adornee`** is the instance a world gui hangs on. The billboard above follows `body` wherever it
  walks, and the sign stays on `deck`.
- **`offset`** shifts a billboard away from its adornee in metres, so `vec3(0, 2.3, 0)` puts a name
  tag above a head instead of inside it.
- **`face`** picks which side of the part a `SurfaceGui` lies on, `"top"` here.
- **`pixelsPerMetre`** is how many pixels of interface go into a metre of the world. It turns the
  pixel offsets you write inside the gui into a size on the wall, and a higher number fits more
  interface into the same square of world.
- **`maxDistance`** is how far away a player can be and still see a billboard.
- **`displayOrder`** decides which `ScreenGui` draws over another when a place has several.

`alwaysOnTop` draws a world gui through walls. `enabled` hides a whole container. In-world guis are
drawn straight into the world at screen resolution, so text stays sharp at any distance.

> [!IMPORTANT]
> A gui kept out of the world does not draw, whatever `enabled` says: one in `ServerStorage`,
> `ServerScriptService`, `ReplicatedStorage`, `StarterPack`, `StarterCharacterScripts` or a backpack
> is a template until a copy lands somewhere else. `StarterGui` and `StarterPlayerScripts` are
> ordinary containers, so a `ScreenGui` in either of them draws. See [Containers](containers.md).

## Widgets

A widget is one piece of interface inside a container: a rectangle, a line of text, a button, a box
to type into.

| Class | What it is |
|---|---|
| `Frame` | a rectangle |
| `TextLabel` | text |
| `TextButton` | text you can click; fires `activated`, and reads `hovered` and `pressed` |
| `TextBox` | text the player types into |
| `ImageLabel` | an image, from `image`, with no background unless you set `backgroundTransparency`. See [Images](images.md) |
| `ImageButton` | a texture you can click, with an image for hovered and one for held |
| `ScrollingFrame` | a frame whose children sit on a bigger canvas you scroll |
| `CanvasGroup` | a frame drawn with everything inside it as one picture, so it fades as one |

Every widget is a `GuiObject`, so all of them share the same placement, colour and mouse properties
whatever class they are:

```lua
local panel = hud:add("Frame", {
    position = udim2(0, 12, 0, 12),   -- xScale, xOffset, yScale, yOffset
    size = udim2(0, 220, 0, 64),
    anchorX = 0, anchorY = 0,         -- which point of the widget sits on position
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.4,
    cornerRadius = 6, borderSize = 1, borderColor = color(1, 1, 1),
    clipsDescendants = true, zIndex = 1,
})

local score = panel:add("TextLabel", {
    size = udim2(1, 0, 0, 20), text = "Score: 0",
    textSize = 9, textColor = color(1, 1, 1), textShadow = true,
    textXAlignment = "left", textYAlignment = "center", textWrapped = true,
})

local button = panel:add("TextButton", { position = udim2(0, 0, 0, 30), size = udim2(1, 0, 0, 24), text = "Swing" })
button.activated:connect(function() score.text = "Swing!" end)
button.mouseEnter:connect(function() button.textColor = color(1, 0.8, 0.2) end)
button.mouseLeave:connect(function() button.textColor = color(1, 1, 1) end)
```

- **`position`** and **`size`** are `UDim2`s: a fraction of the parent plus a number of pixels, on each
  axis. The panel above sits 12 pixels in from the top left of the screen and is 220 by 64 pixels,
  while the label inside it is `udim2(1, 0, 0, 20)`, the full width of the panel and 20 pixels tall.
- **`anchorX`** and **`anchorY`** say which point of the widget lands on `position`. They start at
  `0`, the top left corner. Set both to `0.5` and `position` is the widget's centre, which is how you
  centre something in the middle of the screen whatever size it is.

<!-- demo:udim2 -->
- **`backgroundColor`** and **`backgroundTransparency`** fill the rectangle. Transparency runs from
  `0`, solid, to `1`, gone. Set it to `1` on a label so only the text shows.
- **`cornerRadius`**, **`borderSize`** and **`borderColor`** round and outline the rectangle. For
  more control, add a `UICorner` or a `UIStroke` child instead.
- **`clipsDescendants`** cuts off anything that pokes out past the widget's edges. Leave it off and a
  child that is bigger than its parent is drawn in full.
- **`zIndex`** decides which widget draws on top when two overlap, highest last.
- **`text`**, **`textSize`**, **`textColor`** and **`textShadow`** set what a label says and how it
  looks. **`textXAlignment`** and **`textYAlignment`** place the text inside the widget, and
  **`textWrapped`** breaks a long line at the widget's width instead of letting it run off the end.
- `activated` fires when the button is clicked, and `mouseEnter` and `mouseLeave` fire as the pointer
  comes and goes, which is how the button above lights up under the pointer.

A client script may change any interface instance, even one the server made: the change is its own
and never sent anywhere. Clicks and hovers happen on the client, so react to them in a `LocalScript`
and tell the server through a [Remote](talking.md).

`textScaled = true` grows or shrinks the text to fill the widget; `textSize` is then ignored.

**Rich text.** `richText = true` reads a label's text as [rich text](rich-text.md), the same tags the
chat takes: `<b>`, `<color=gold>`, `<gradient>`, `<wave>`, `<img>` and the rest. A rich text label
wraps inside its size.

```lua
hud:add("TextLabel", { text = "<b>Score</b> <color=gold>120</color>", richText = true })
```

## Layouts

A layout is an instance you put inside a frame to place that frame's children for you. It ignores
their `position`; their `size` still counts.

| Class | What it does to its parent |
|---|---|
| `UIListLayout` | lines the children up in a column or a row |
| `UIGridLayout` | puts every child in a cell of the same size |
| `UIPadding` | keeps space free inside the edges |
| `UIAspectRatioConstraint` | keeps the width a fixed multiple of the height |
| `UISizeConstraint` | keeps the size between a smallest and a biggest |
| `UIScale` | draws the parent and everything inside it bigger or smaller |

They are children like any other, and they work inside a `ScreenGui` too. Only the first
`UIListLayout` or `UIGridLayout` in a frame counts. Inside a gui, **Insert** in the editor lists them.

### A menu that grows with its buttons

```lua
-- client
local hud = world:add("ScreenGui", { name = "menu" })

local menu = hud:add("Frame", {
    position = udim2(0, 20, 0.5, 0), anchorY = 0.5,
    size = udim2(0, 180, 0, 0), automaticSize = "y",
    backgroundColor = color(0, 0, 0), backgroundTransparency = 0.3,
})
menu:add("UIListLayout", { padding = udim(0, 6) })
menu:add("UIPadding", {
    paddingTop = udim(0, 10), paddingBottom = udim(0, 10),
    paddingLeft = udim(0, 10), paddingRight = udim(0, 10),
})

for order, label in ipairs({ "Play", "Settings", "Quit" }) do
    menu:add("TextButton", { name = label, text = label, layoutOrder = order, size = udim2(1, 0, 0, 24) })
end
```

The menu is 180 pixels wide and 104 tall: 10 of padding, three buttons of 24 with 6 between them, and
10 more. Add a fourth button and it grows. Each button is `udim2(1, 0, ...)` wide, so it fills the
160 pixels the padding leaves.

- `fillDirection` is `"vertical"` or `"horizontal"`.
- `layoutOrder` sorts the children, lowest first. `sortOrder = "name"` sorts them by name instead.
- A child with `visible = false` takes no room.
- `horizontalAlignment` (`"left"`, `"center"`, `"right"`) and `verticalAlignment` (`"top"`,
  `"center"`, `"bottom"`) move the children inside the frame.
- `wraps = true` starts a new column or row when the next child would not fit.
- `padding` is the gap between children. A vertical list reads its y, a horizontal one its x. The
  other axis is the gap between wrapped lines.

<!-- demo:interface-list -->

### Growing to fit

`automaticSize` grows a widget along `"x"`, `"y"` or `"xy"` to fit what it holds: its text, or its
children with the layout and padding counted. It never shrinks below its `size`, which is why the
menu above starts at a height of `0`.

- With no layout, a frame grows until it reaches its farthest child.
- A label with `textWrapped` and `automaticSize = "y"` wraps at its width and grows downward. With
  `"x"` or `"xy"` it grows sideways and does not wrap.

### An inventory grid that scrolls

```lua
-- client
local bag = hud:add("ScrollingFrame", {
    position = udim2(0.5, 0, 0.5, 0), anchorX = 0.5, anchorY = 0.5,
    size = udim2(0, 236, 0, 180),
    canvasSize = udim2(1, 0, 0, 0), automaticCanvasSize = "y",
    scrollingDirection = "y", scrollBarThickness = 4,
    backgroundColor = color(0.1, 0.1, 0.1),
})
bag:add("UIGridLayout", { cellSize = udim2(0, 40, 0, 40), cellPadding = udim2(0, 4, 0, 4) })
bag:add("UIPadding", {
    paddingTop = udim(0, 6), paddingBottom = udim(0, 6),
    paddingLeft = udim(0, 6), paddingRight = udim(0, 6),
})

for slot = 1, 40 do
    bag:add("ImageButton", {
        name = "slot" .. slot, layoutOrder = slot, image = "res://ui/slot.png",
        backgroundColor = color(0, 0, 0), backgroundTransparency = 0.5,
    })
end
```

Five cells fit across, so the forty slots make eight rows. That is taller than the frame, so the
canvas grows to fit them and the wheel scrolls it.

- Every child is given `cellSize`. Its own `size` is ignored.
- `fillDirection` starts as `"horizontal"`: cells fill a row, left to right, then the next row.
  `"vertical"` fills columns instead.
- As many cells go in a row as there is room for. `fillDirectionMaxCells` caps that; `0` does not.
- `startCorner` is the corner the first cell goes in: `"topLeft"`, `"topRight"`, `"bottomLeft"` or
  `"bottomRight"`.
- `horizontalAlignment` and `verticalAlignment` move the whole block of cells.
- The scale part of `cellSize` and `cellPadding` is a fraction of the frame, inside its padding.

<!-- demo:interface-grid -->

### Scrolling

A `ScrollingFrame` shows a window onto a canvas. Its children are placed on the canvas, and whatever
falls outside the window is clipped.

- `canvasSize` is how big the canvas is. Its scale is a fraction of the frame. It starts at
  `udim2(0, 0, 2, 0)`, twice as tall as the frame.
- The canvas is never smaller than the window, so a child at `size = udim2(1, 0, 0, 40)` or a grid
  gets the frame's width whatever `canvasSize` says across.
- `automaticCanvasSize` grows the canvas along `"x"`, `"y"` or `"xy"` to fit the children.
- `canvasPosition` is how far it is scrolled, in pixels, as a `Vector3` whose x and y count. Write it
  to scroll. It is clamped to the canvas: as you write it, once the frame has been drawn once, and
  again whenever the canvas or the window changes size. Reading it back gives where it really went.
- The wheel scrolls 40 pixels a notch, down and up when the canvas is taller than the frame and
  sideways otherwise. Dragging the canvas moves it. Dragging within `scrollBarThickness` of the right
  or bottom edge moves the bar.
- `scrollingDirection` is `"x"`, `"y"` or `"xy"`.
- `scrollingEnabled = false` stops the wheel and dragging. A script can still write
  `canvasPosition`.
- `scrollBarThickness` is in pixels; `0` hides the bars. `scrollBarImageColor` and
  `scrollBarImageTransparency` colour them.
- `absoluteCanvasSize` and `absoluteWindowSize` read how big the canvas and the window ended up, in
  screen pixels like `absoluteSize`.

To keep a chat log or a console pinned to its newest line, scroll to the bottom after you add to it:

```lua
log.canvasPosition = vec3(0, log.absoluteCanvasSize.y, 0)   -- scroll to the bottom
```

That works because writing past the end stops at the end. `canvasPosition` counts in the canvas's
own pixels, before the player's gui scale; `absoluteCanvasSize` and `absoluteWindowSize` are what
ended up on the screen.

### Aspect ratio, size limits and scale

```lua
local portrait = panel:add("ImageLabel", { size = udim2(1, 0, 1, 0), image = "res://ui/hero.png" })
portrait:add("UIAspectRatioConstraint", { aspectRatio = 1 })

local bar = hud:add("Frame", { size = udim2(0.5, 0, 0, 12) })
bar:add("UISizeConstraint", { minSize = vec3(120, 0, 0), maxSize = vec3(400, 0, 0) })
```

- `aspectRatio` is the width divided by the height. With `aspectType = "fitWithinMaxSize"` the
  widget shrinks along one side until it fits inside its size. With `"scaleWithParentSize"` it keeps
  the side `dominantAxis` names (`"width"` or `"height"`) and works out the other.
- `minSize` and `maxSize` are pixels, x and y. `0` in `maxSize` does not limit that axis.
- Automatic size comes first, then the aspect ratio, then the size limits.
- Children of a `UIGridLayout` take `cellSize` whatever these say.

`UIScale` draws its parent `scale` times bigger, around the parent's anchor point. Layouts and the
widgets around it still use the size it had before scaling, so it never pushes anything aside.
Clicks and hovers follow what is drawn. That makes it the one to use for a button that swells under
the pointer:

```lua
local grow = button:add("UIScale")
button.mouseEnter:connect(function() grow:tween({ scale = 1.1 }, { time = 0.1 }) end)
button.mouseLeave:connect(function() grow:tween({ scale = 1 }, { time = 0.1 }) end)
```

## Corners, outlines and gradients

These three are children you add to a widget to change how its rectangle is drawn.

| Class | What it does to its parent |
|---|---|
| `UICorner` | rounds the corners |
| `UIStroke` | draws an outline |
| `UIGradient` | shades the background from one colour to another |

```lua
-- client
local card = hud:add("Frame", {
    position = udim2(0.5, 0, 0.5, 0), anchorX = 0.5, anchorY = 0.5,
    size = udim2(0, 200, 0, 120),
    backgroundColor = color(1, 1, 1),
})
card:add("UICorner", { cornerRadius = udim(0, 12) })
card:add("UIStroke", { color = color(1, 0.8, 0.3), thickness = 2 })
card:add("UIGradient", { startColor = color(0.3, 0.2, 0.6), endColor = color(0.05, 0.05, 0.15), rotation = 90 })

local title = card:add("TextLabel", {
    size = udim2(1, 0, 0, 30), text = "Legendary",
    textColor = color(1, 0.8, 0.3), backgroundTransparency = 1,
})
title:add("UIStroke", { color = color(0, 0, 0), thickness = 1 })
```

The card's background is white because a gradient multiplies the colour underneath it, so leaving the
frame white is what makes the gradient's own colours come out as written.

**UICorner.** `cornerRadius` reads the x of its `UDim2`. The offset is pixels; the scale is a fraction
of the shorter side, so `udim(0.5, 0)` makes a pill. A corner is never rounder than half the shorter
side. A `UICorner` wins over the widget's own `cornerRadius`.

**UIStroke.** With `applyStrokeMode = "contextual"` it outlines the text of a `TextLabel`,
`TextButton` or `TextBox`, and the edge of anything else. `"border"` always outlines the edge. An
edge outline is drawn outside the widget and follows its corners. `thickness` is pixels;
`transparency` fades it and `enabled = false` turns it off.

**UIGradient.** It only shades the background: text, images and children are left alone.

- Its colours multiply `backgroundColor`, so keep the background white to see them as written.
- `startTransparency` and `endTransparency` fade it on top of `backgroundTransparency`.
- `rotation` is degrees. `0` runs left to right, `90` top to bottom.
- `offset` slides it, as a fraction of the size, x and y.
- `enabled = false` draws the plain background.

<!-- demo:interface-gradient -->

For more than two colours, give it keypoints. Each is a time from `0` to `1`, a colour and an optional
transparency. They win over the start and end values.

```lua
local rainbow = bar:add("UIGradient")
rainbow:setKeypoints({
    { 0, color(1, 0, 0) },
    { 0.5, color(1, 1, 0), 0.2 },
    { 1, color(0, 1, 0) },
})
print(rainbow.keypoints)   -- 0 1 0 0 0, 0.5 1 1 0 0.2, 1 0 1 0 0
```

`keypoints` is the same list written as text, "time r g b transparency" entries split by commas;
that is what the editor and scene files hold. It takes at least two. `getKeypoints()` hands the list
back, and with no keypoints it hands back the start and the end.

## Text boxes

A `TextBox` is a `TextLabel` the player types into.

What the player types only changes this client's copy of the box, so anything the rest of the game
has to know about goes to the server. Here the server owns the names and decides whether one is
allowed:

```lua
-- server
local setName = world:add("RemoteFunction", { name = "setName", accepts = "string" })
local names = {}

setName.onServerInvoke = function(player, name)
    if #name < 3 or #name > 16 then return false, "3 to 16 letters" end
    names[player.id] = name
    return true, "hello, " .. name
end
```

The client draws the box, sends what was typed when the player presses Enter, and shows the answer:

```lua
-- client
local entry = hud:add("TextBox", {
    position = udim2(0.5, 0, 0.5, 0), anchorX = 0.5, anchorY = 0.5,
    size = udim2(0, 200, 0, 20),
    placeholderText = "Your name", clearTextOnFocus = false, textXAlignment = "left",
    backgroundColor = color(0.1, 0.1, 0.1), textColor = color(1, 1, 1),
})
local answer = hud:add("TextLabel", {
    position = udim2(0.5, 0, 0.5, 16), anchorX = 0.5,
    size = udim2(0, 200, 0, 12), backgroundTransparency = 1, textColor = color(1, 1, 1),
})

entry.focusLost:connect(function(enterPressed)
    if not enterPressed then return end
    task.spawn(function()
        local ok, message = game.world.setName:invokeServer(entry.text)
        answer.text = message
        answer.textColor = if ok then color(0.5, 1, 0.5) else color(1, 0.4, 0.4)
    end)
end)

entry:captureFocus()
```

- `focusLost` hands you whether Enter finished the edit, so the handler returns early when the player
  clicked away instead.
- `invokeServer` waits for the answer, which is why the call sits inside a `task.spawn`. Send it with
  a [RemoteFunction](talking.md#asking-and-waiting-for-the-answer) and check it on the server.
- `entry:captureFocus()` puts the caret in the box as soon as it appears, so the player can type
  without clicking it first.

**Focus.**

- Clicking into a box or calling `box:captureFocus()` focuses it and fires `focused`.
- Enter finishes and fires `focusLost(true)`. Escape, clicking anywhere else or `box:releaseFocus()`
  fire `focusLost(false)`.
- Tab moves to the next text box shown on the screen, or in the same `SurfaceGui` or `BillboardGui`.
- `box:isFocused()` answers whether it has focus.
- `clearTextOnFocus` starts on, and empties the box each time it is focused.

> [!IMPORTANT]
> While a box has focus it takes every key. The character stands still, bindings do not fire and
> Escape does not open the pause menu. Release the focus when you are done with the box.

**Properties.**

- `placeholderText` shows in `placeholderColor` while the box is empty.
- `textEditable = false` lets the player select and copy the text, but not change it.
- `multiLine = true` makes Enter start a new line instead of finishing. Only a multi-line box wraps,
  and only with `textWrapped`. A single-line box slides sideways to keep the caret in view, and a
  pasted line break becomes a space.
- `cursorPosition` is where the caret is, counting from 1, and `-1` while the box has no focus.
  `selectionStart` is the other end of the selection, and `-1` when nothing is selected. Both can be
  written: setting `cursorPosition` moves the caret, and setting `selectionStart` selects from there
  to the caret. `-1` clears the selection.

```lua
box:captureFocus()
box.cursorPosition = #box.text + 1       -- caret at the end
box.selectionStart = 1                   -- and everything selected
```

**Keys.** The player gets the editing keys they expect, and you do not connect anything to get them:

| Keys | What they do |
|---|---|
| Left, Right | move a character, or a word with Ctrl |
| Up, Down | move a line, keeping the column |
| Home, End | go to the start or end of the line, or of the whole text with Ctrl |
| Shift with any of those | grows the selection |
| Backspace, Delete | remove a character, or a word with Ctrl |
| Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V | select all, copy, cut, paste |
| double click, drag | select a word, select what you drag over |

On a Mac, Cmd takes Ctrl's place in shortcuts and Option moves by words. Shortcuts follow the
keyboard layout: on AZERTY, Ctrl+A is the key marked A.

## Image buttons

An `ImageButton` is a button whose face is a texture, and it can carry a second and a third texture
for when the pointer is over it and while it is held down.

```lua
-- client
local play = hud:add("ImageButton", {
    size = udim2.fromOffset(64, 64),
    image = "res://ui/play.png",
    hoverImage = "res://ui/play_hover.png",
    pressedImage = "res://ui/play_down.png",
})
play.activated:connect(function() menu.visible = false end)
```

- `activated` fires when the left button is pressed on the button and let go over it.
- `hoverImage` shows while the pointer is over it, `pressedImage` while it is held. Either can stay
  empty.
- `autoButtonColor` starts on and darkens `image` while hovered, and more while held. It leaves
  `hoverImage` and `pressedImage` as they are.
- `hovered` and `pressed` read its state.

## Images

[Images](images.md) has its own page: where an image can come from, how it fits its box, and how a
script draws one. On an `ImageLabel` or `ImageButton`:

- `scaleType` is `"stretch"`, `"fit"`, `"crop"`, `"tile"` or `"slice"`. `tileSize` sizes a tile.
- `sliceMin`, `sliceMax` and `sliceScale` set up a nine-slice.
- `imageRectOffset` and `imageRectSize` show one part of the image, for sprite sheets.
- `resampleMode = "pixelated"` keeps pixel art sharp when it is scaled up.

```lua
-- client
hud:add("ImageLabel", {
    size = udim2.fromOffset(400, 200),
    image = "res://ui/frame.png",
    scaleType = "slice",
    sliceMin = vec3(8, 8, 0),
    sliceMax = vec3(16, 16, 0),
})
```

## Canvas groups

Fade a frame's children one by one and you see them through each other where they overlap. A
`CanvasGroup` draws itself and everything inside it into one picture first, then fades that. That is
what you want for a panel that slides in and out:

```lua
-- client
local shop = hud:add("CanvasGroup", {
    position = udim2(0.5, 0, 0.5, 0), anchorX = 0.5, anchorY = 0.5,
    size = udim2(0, 300, 0, 200),
    groupTransparency = 1, visible = false,
})

local function open()
    shop.visible = true
    shop:tween({ groupTransparency = 0 }, { time = 0.25, easing = "quad", direction = "out" })
end

local function close()
    local fade = shop:tween({ groupTransparency = 1 }, { time = 0.25 })
    fade.completed:connect(function(finished)
        if finished then shop.visible = false end
    end)
end
```

`open` makes the group visible before the tween starts, and `close` waits for the tween to report
that it finished before hiding it, so the panel is never switched off halfway through the fade.

- `groupTransparency` fades the whole picture. `groupColor` tints it.
- A group always clips its children to its rectangle.
- At `groupTransparency = 1` the group is gone, so clicks and the wheel go through it to whatever is
  behind. Anything below 1 takes them as usual. Setting `visible = false` as well is still tidier,
  because a group that is out of the way stops being drawn at all.

## Mouse events

Every widget has these. Frames and labels too, not only buttons.

| Event | Fires when | Handler gets |
|---|---|---|
| `mouseEnter` | the pointer comes over it | the widget |
| `mouseLeave` | the pointer leaves it | the widget |
| `mouseMoved` | the pointer moves while over it | `x, y` |
| `mouseButton1Down` | the left button goes down over it | `x, y` |
| `mouseButton1Up` | the left button comes up over it | `x, y` |
| `mouseButton2Click` | the right button comes up over it | `x, y` |
| `mouseWheelForward` | the wheel turns forward over it | `x, y` |
| `mouseWheelBackward` | the wheel turns back over it | `x, y` |

`x` and `y` are in the same pixels as `absolutePosition`: from the top left of the screen, or of the
`SurfaceGui` or `BillboardGui`. You can hand them straight to `udim2.fromOffset` to put something
where the player clicked, which is how a context menu opens under the pointer:

```lua
-- client
slot.mouseButton2Click:connect(function(x, y)
    menu.position = udim2.fromOffset(x, y)
    menu.visible = true
end)
```

- `mouseEnter`, `mouseLeave` and `mouseMoved` fire for everything under the pointer: the button, the
  frame it sits in, and the frame that one sits in.
- Buttons and the wheel start at the widget drawn on top and go down through what is under it. They
  stop at the first one that takes input: a `TextButton`, an `ImageButton`, a `TextBox`, or a
  `ScrollingFrame` that can scroll. Frames and labels never stop them, and there is no `active`
  property to make them.
- On the screen they only fire while the pointer is free. See
  [Clicking with the pointer locked](#clicking-with-the-pointer-locked).

<!-- demo:interface-mouse -->

## Measured size and position

Once a widget is laid out, it can tell you where it ended up. A `UDim2` says what you asked for;
these five say what the screen got after the layout, the constraints and the player's gui scale had
their say.

| Property | What it reads |
|---|---|
| `absolutePosition` | its top left corner, in pixels |
| `absoluteSize` | its size, in pixels, after automatic size, constraints and `UIScale` |
| `textBounds` | on a label, the size its text takes |
| `absoluteCanvasSize` | on a `ScrollingFrame`, the size of the canvas |
| `absoluteWindowSize` | on a `ScrollingFrame`, the size of the window onto it |

They are `Vector3`s whose x and y count, and all five are measured on the screen, after the player's
gui scale.

`textBounds` measures the text the way the box draws it. A `TextBox` that is not `multiLine` never
wraps, so its bounds are the whole text on one line however narrow the box is.

Read them when you want to place one widget against another, such as a tooltip under a button:

```lua
-- client
button.mouseEnter:connect(function()
    local at, size = button.absolutePosition, button.absoluteSize
    tip.position = udim2.fromOffset(at.x, at.y + size.y + 4)
    tip.visible = true
end)
```

- They are read-only. Writing one raises `Frame.absoluteSize is read-only`, and tweening one raises
  the same thing rather than easing towards nothing.
- The client works them out as it draws. They stay at zero on the server, and on a widget made this
  frame until it has been drawn once.
- `changed` fires when they move, so `getPropertyChangedSignal("absoluteSize")` watches one.
- They are never saved into a scene and never sent anywhere. The editor shows them greyed out.

> [!NOTE]
> A widget you created this frame reads zero for all five, because nothing has been drawn yet. Wait
> a frame, or connect `getPropertyChangedSignal("absoluteSize")` and place the other widget when the
> real size arrives.

## Previewing screens in the editor

ScreenGuis draw in the editor viewport as they will in game. A place usually has several that are
off until a script turns them on (a pause menu, a death screen, a shop), so the viewport toolbar has
a **Screens** menu:

- Tick or untick a ScreenGui to show or hide it **only while editing**. The scene keeps its own
  `enabled`; the menu says "(saved off)" next to one shown against its saved value.
- **Only** shows that one and hides the others. **As saved** goes back to the scene's values.
- **Engine screens** draws the loading screen from `[loading]` in place.toml, the pause menu an
  exported game shows, or the disconnected screen, over the viewport.

Play mode always uses the saved values, so nothing you tick here changes what a player sees.

## UDim2

A `UDim2` is how you say where a widget goes and how big it is. Each axis is a fraction of the parent
plus a number of pixels, so one value can say "half way across, and 50 pixels back from there".

```lua
udim2(0.5, -50, 1, -30)      -- centre minus 50 px, bottom minus 30 px
udim2.fromScale(1, 1)        -- the whole parent
udim2.fromOffset(200, 40)    -- 200 by 40 pixels
udim(0, 8)                   -- udim2(0, 8, 0, 8), the same on both axes
local moved = a + b
```

The fraction is what keeps an interface the right shape on a screen you have not seen, and the pixels
are what keeps a border a border. A panel at `udim2.fromScale(1, 1)` fills its parent at any window
size, while a 24 pixel button stays 24 pixels tall.

### Details

- There is no `UDim`. A property that is one length takes a `UDim2` and reads the axis that fits
  it:

  | Property | Reads |
  |---|---|
  | `UIPadding.paddingLeft`, `paddingRight` | x |
  | `UIPadding.paddingTop`, `paddingBottom` | y |
  | `UICorner.cornerRadius` | x, with the scale a fraction of the shorter side |
  | `UIListLayout.padding` | y in a vertical list and x in a horizontal one; the other axis between wrapped lines |

  `udim(scale, offset)` sets both axes, so you need not remember which one is read.
- There is no `Vector2`. `canvasPosition`, `minSize`, `maxSize`, `UIGradient.offset` and the
  measured values are `Vector3`s, and z is ignored.

## Text and fonts

- With no `font`, text uses **Minecraft's own font**, pixel for pixel, including every character
  a resource pack adds.
- `textSize` is the line height. `9` is the game's menu text; multiples of 9 keep pixels even.
- `font` takes another game font like `minecraft:uniform` or `minecraft:alt`, a `.ttf` in the
  place (`res://fonts/title.ttf`) or a `.ttf` in a resource pack.
- Set `font` once on a `ScreenGui`, `BillboardGui` or `SurfaceGui` and every text inside uses it.
  A label's own `font` wins.

## 3D inside an interface

A `ViewportFrame` draws the parts and models inside it through its own camera. What is inside a
viewport has left the world: it is not drawn there, does not collide, is never touched, and world
queries and zones do not see it.

```lua
local view = hud:add("ViewportFrame", { size = udim2.fromOffset(240, 180), backgroundTransparency = 0 })
local sword = view:add("MeshPart", { meshId = "res://models/sword.glb", size = vec3(1, 3, 0.3) })
local eye = view:add("Camera", { cframe = cframe.lookAt(vec3(3, 2, 3), vec3(0, 0, 0)) })
view.camera = eye

game.renderStepped:connect(function(dt)
    sword.cframe = sword.cframe * cframe.angles(0, dt, 0)
end)
```

The sword and the camera are added to the viewport rather than to the world, `view.camera` says which
camera to look through, and multiplying the sword's `cframe` by a small rotation each frame turns it
on the spot.

- With no `camera` it looks through the first `Camera` inside it, and with none frames what it holds.
- `ambient`, `lightColor` and `lightDirection` light it; world lights do not reach inside.
- `imageColor` and `imageTransparency` tint and fade what it drew.
- `updateRate` draws it that many times a second, `0` every frame. `resolutionScale` below `1` draws
  fewer pixels, above `1` more.
- A screen effect directly inside it runs on its 3D, with depth, so `OutlineEffect`, `FogEffect` and
  `DepthOfFieldEffect` work there.
- `view:pick(x, y)` answers the part and point under a spot of the viewport, `x` and `y` from `0` to
  `1`, top left first. `view:screenRay(x, y)` answers the ray itself, and `view:raycast(from, direction)`
  looks only at what is inside.

## Effects on an interface

A screen effect (`FilmGrainEffect`, `BlurEffect`, `PixelateEffect`, a `PostShader`...) placed inside a
`SurfaceGui` or `BillboardGui` only touches that interface. Inside a `Frame` it only touches that
frame's rectangle, never the see-through parts. `order` sorts them, as on the screen.

```lua
local screen = wall:add("SurfaceGui", { pixelsPerMetre = 100 })
local panel = screen:add("Frame", { size = udim2.fromScale(1, 1) })
panel:add("FilmGrainEffect", { amount = 0.2, size = 3 })
```

An interface with effects is drawn into a texture first, so its text is only as sharp as
`pixelsPerMetre`. Inside a `ScreenGui` an effect still covers the whole screen.

## Clicking a screen in the world

A `SurfaceGui` or `BillboardGui` is aimed at with the camera's centre, or with the mouse cursor while
the pointer is free.

## Clicking with the pointer locked

While the mouse is captured, hold the **pointer** key (left Alt by default, rebindable under
*Moud* in Controls) to free the pointer and click buttons. `input:down("pointer")` tells a script
whether it is held.
