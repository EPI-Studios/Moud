# Images

An image in Moud is a string that says where the pixels come from. An `ImageLabel` shows one, a
`Decal` puts one on a part, a particle flies one around. This page covers where images can go, how
an image fills the box it is given, and how a script draws its own images, pixel by pixel, while the
game runs.

## Showing an image

`ImageLabel` shows an image. `ImageButton` is an `ImageLabel` you can click. See
[Image buttons](interface.md#image-buttons) for its hover and pressed images.

```lua
-- client
local hud = game.world:add("ScreenGui", { name = "hud" })

hud:add("ImageLabel", {
    position = udim2.fromOffset(16, 16),
    size = udim2.fromOffset(64, 64),
    image = "res://ui/heart.png",
})
```

An `ImageLabel` has no background until you set `backgroundTransparency`. `imageColor` tints the
image and `imageTransparency` fades it.

### Where an image can go

Every one of these takes the same kind of image string:

| Where | Property |
|---|---|
| `ImageLabel`, `ImageButton` in a `ScreenGui`, `SurfaceGui` or `BillboardGui` | `image`, `hoverImage`, `pressedImage` |
| a `ScreenGui` shown in a [window](window.md) | same as above |
| `Decal`, `Texture` on a part | `texture` |
| `ParticleEmitter`, `Beam`, `Trail` | `texture` |
| `Sky` | `skyboxBk` and the other faces, `sunTextureId`, `moonTextureId` |
| [rich text](rich-text.md) | `<img src="...">` |

Only an `ImageLabel` or `ImageButton` can fit, tile, slice or crop its image. The rest draw the
whole image. See [Particles, beams, trails, highlights and decals](effects.md#images) for how effects
read theirs.

### Sources

| Value | Image |
|---|---|
| `"res://ui/heart.png"` | a file in the place |
| `"minecraft:item/diamond"` | a Minecraft texture. `textures/` and `.png` are added, and the full id `"minecraft:textures/item/diamond.png"` works too |
| `canvas.uri` | an image a script drew or loaded, see [Drawing](#drawing) |

Particles also take a bare particle name such as `"flame"`, which is looked up under
`textures/particle/`.

## Fitting an image in its box

`scaleType` says how the image fills the widget.

| `scaleType` | What happens |
|---|---|
| `"stretch"` | the image is stretched to the box. This is the default |
| `"fit"` | the whole image shows, as large as fits, centred. The rest of the box stays empty |
| `"crop"` | the image covers the whole box, centred. What sticks out is cut off |
| `"tile"` | the image repeats, one copy every `tileSize` |
| `"slice"` | nine-slice: the corners keep their size and the middle stretches |

`fit` and `crop` keep the image's shape. `stretch` does not.

### Tiles

`tileSize` is a `UDim2`. Its offset is pixels and its scale is a fraction of the widget's size. The
default, `udim2.fromScale(1, 1)`, is one tile as big as the box.

```lua
hud:add("ImageLabel", {
    size = udim2.fromOffset(300, 120),
    image = "res://ui/checker.png",
    scaleType = "tile",
    tileSize = udim2.fromOffset(32, 32),
})
```

Tiles start at the top left. The last row and column are cut where the box ends. A box that would
need more than 4096 tiles gets bigger tiles instead.

<!-- demo:images-scale -->

### Nine-slice

A nine-slice image is cut into a 3 by 3 grid. The four corners keep their size, the four edges
stretch one way, and the middle stretches both ways. That keeps a rounded frame's corners round at
any size.

There is no `Rect` type in Moud. The middle of the grid is given by two points, in image pixels:

- `sliceMin` is the top left corner of the stretchy middle.
- `sliceMax` is its bottom right corner.

Both are `Vector3`s. Only x and y are read.

```lua
-- a 24 by 24 frame with 8 pixel corners
hud:add("ImageLabel", {
    size = udim2.fromOffset(400, 200),
    image = "res://ui/frame.png",
    scaleType = "slice",
    sliceMin = vec3(8, 8, 0),
    sliceMax = vec3(16, 16, 0),
    sliceScale = 1,
})
```

- `sliceScale` scales the corners. `2` draws them twice as big. It is at least `0.01`.
- When the box is smaller than the corners, the corners shrink so they still fit.
- When `sliceMax` is not past `sliceMin` on both axes, the whole image is stretched instead.

<!-- demo:images-slice -->

### Sprite sheets

`imageRectOffset` and `imageRectSize` pick a part of the image to show, in pixels. Everything else,
fitting and slicing included, works on that part only. Both are `Vector3`s and z is ignored.

- `imageRectOffset` is the top left of the part.
- `imageRectSize` is its size. `0` on an axis means "to the edge of the image". The default shows
  the whole image.

Move `imageRectOffset` to play a sprite sheet. Here the sheet has four 16 pixel frames side by side:

```lua
-- client
local sprite = hud:add("ImageLabel", {
    size = udim2.fromOffset(64, 64),
    image = "res://ui/coin.png",
    imageRectSize = vec3(16, 16, 0),
    resampleMode = "pixelated",
})

local frame = 0
task.every(0.12, function()
    frame = (frame + 1) % 4
    sprite.imageRectOffset = vec3(frame * 16, 0, 0)
end)
```

<!-- demo:images-sprite -->

### Pixel art

`resampleMode = "pixelated"` keeps each pixel a sharp square when the image is drawn bigger than it
is. The default, `"default"`, smooths it. It only changes `ImageLabel` and `ImageButton`.

## Drawing

`images` is a global that makes images a script can draw on. Each one is an `EditableImage`.

```lua
-- client
local canvas = images.create(128, 128, color(1, 1, 1))
canvas:drawCircle(64, 64, 20, color(0.9, 0.2, 0.2), { smooth = true })

hud:add("ImageLabel", { size = udim2.fromOffset(128, 128), image = canvas.uri })
```

- `images.create(width, height, color?, transparency?)` makes a blank image. Without a colour every
  pixel is fully see-through.
- `images.fromPixels(width, height, pixels)` makes one from a list of numbers, laid out like
  [`readPixels`](#reading-and-writing-pixels).

`canvas.width` and `canvas.height` read its size.

### Showing it

`canvas.uri` is the image's string, such as `"editable://3"`. Put it anywhere an image goes, from
the [table above](#where-an-image-can-go).

Assigning the image object itself, `label.image = canvas`, also works while the game runs. Strict
mode reports it as an error, because `image` is a `string`. Use `.uri`.

What you draw shows up the next time the image is drawn. The pixels go to the graphics card at most
once a frame, however many changes you make, so a thousand `setPixel` calls cost one upload.

> [!IMPORTANT]
> Show images from a client script. An image lives in the memory of the side that made it. A
> server `Script` can make, load and read images, but its `uri` means nothing on a player's
> machine, so players do not see it.

### Coordinates

Coordinates are pixels. `(0, 0)` is the top left corner, x goes right and y goes down.

Pixel `(x, y)` is the square from `x` to `x + 1` and from `y` to `y + 1`. The calls read that in two
ways:

- `drawRectangle`, `drawGradient`, `drawPolygon` and `drawImage` take edges. A rectangle at
  `(0, 0)` with size `24, 24` covers exactly the pixels 0 to 23.
- `drawCircle`, `drawEllipse` and `drawLine` take pixels. The point is the centre of that pixel, so
  `drawCircle(10, 10, ...)` is centred in pixel 10, 10.

`setPixel`, `getPixel`, `floodFill`, `crop`, `readPixels` and `writePixels` take whole pixels.
Fractions are dropped.

Drawing past the edge of the image is fine. The part outside is left out.

### Transparency

Transparency is `0` for solid and `1` for gone, as everywhere else in Moud. A colour's own alpha,
from `color(r, g, b, a)`, counts too: the two multiply.

`readPixels` and `writePixels` are the exception. They work with alpha, `1` for solid.

### Limits

An image is 1 to 1024 pixels on each side. `create`, `fromPixels` and `resize` error outside that.
A loaded image bigger than that is scaled down to fit.

### Colours and pixels

| Call | What it does |
|---|---|
| `canvas:setPixel(x, y, color, transparency?)` | sets one pixel exactly, transparency included |
| `canvas:getPixel(x, y)` | returns the pixel's `Color` and its transparency. Outside the image it is black and fully see-through |
| `canvas:fill(color, transparency?)` | sets every pixel exactly |
| `canvas:clear()` | makes every pixel fully see-through |

```lua
local c, transparency = canvas:getPixel(3, 4)
```

`setPixel` and `fill` replace what was there. They do not mix with it.

### Shapes

```lua
canvas:drawRectangle(x, y, width, height, color, options?)
canvas:drawCircle(x, y, radius, color, options?)
canvas:drawEllipse(x, y, radiusX, radiusY, color, options?)
canvas:drawLine(x1, y1, x2, y2, color, options?)
canvas:drawPolygon(points, color, options?)
```

Every shape takes the same options table, `DrawOptions`:

| Option | Default | |
|---|---|---|
| `transparency` | `0` | how see-through the paint is |
| `blend` | `"over"` | how the paint mixes with what is there, see [Blends](#blends) |
| `smooth` | `false` | soft edges instead of hard ones |
| `filled` | `true` | `false` draws only the outline |
| `thickness` | `1` | the outline's width, or the line's width, in pixels. At least 1 |
| `cornerRadius` | `0` | rounds a rectangle's corners, up to half its shorter side |

```lua
canvas:drawRectangle(8, 8, 48, 32, color(0.2, 0.3, 0.8), { cornerRadius = 6, smooth = true })
canvas:drawRectangle(8, 8, 48, 32, color(1, 1, 1), { cornerRadius = 6, smooth = true, filled = false, thickness = 2 })
canvas:drawLine(0, 0, 63, 63, color(0, 0, 0), { thickness = 3, smooth = true })
```

- An outline grows inwards from the shape's edge, so a 2 pixel outline on a rectangle stays inside
  the rectangle.
- A line is `thickness` wide in total, with round ends.
- `cornerRadius` is only read by `drawRectangle`.
- `drawPolygon` with `filled = false` draws the outline, closing the last point back to the first, with `thickness`.

**Points.** `drawPolygon` takes its corners as a list of `Vector3`s, where z is ignored, or as a flat
list of numbers, x then y:

```lua
canvas:drawPolygon({ 32, 4, 60, 60, 4, 60 }, color(1, 0.8, 0.2), { smooth = true })
canvas:drawPolygon({ vec3(32, 4, 0), vec3(60, 60, 0), vec3(4, 60, 0) }, color(1, 0.8, 0.2))
```

The polygon needs at least three corners. Its sides may cross; a spot is inside when a line from it
crosses the sides an odd number of times.

**Hard and smooth edges.** Without `smooth`, a pixel is painted when its centre is inside the shape,
and not at all otherwise. That is what pixel art wants. With `smooth = true`, pixels on the edge are
painted partly, by how much of them the shape covers. That is what a drawing at normal size wants.

<!-- demo:images-edges -->

### Gradients

```lua
canvas:drawGradient(x, y, width, height, from, to, options?)
```

`from` is the colour at one side and `to` the colour at the other. `GradientOptions`:

| Option | Default | |
|---|---|---|
| `rotation` | `0` | the direction, in degrees. `0` runs left to right, `90` top to bottom |
| `fromTransparency` | `0` | transparency at the `from` side |
| `toTransparency` | `0` | transparency at the `to` side |
| `blend` | `"over"` | see [Blends](#blends) |

```lua
canvas:drawGradient(0, 0, 128, 128, color(0.1, 0.1, 0.3), color(0.9, 0.5, 0.2), { rotation = 90 })
```

A gradient fills a plain rectangle. It has no smooth edges or rounded corners.

### Drawing one image onto another

```lua
canvas:drawImage(other, x, y, options?)
```

`x` and `y` are where the top left of `other` lands. `ImageDrawOptions`:

| Option | Default | |
|---|---|---|
| `sourceX`, `sourceY` | `0` | the top left of the part of `other` to copy |
| `sourceWidth`, `sourceHeight` | to the edge of `other` | the size of that part |
| `width`, `height` | the part's size | the size it is drawn at; different sizes scale it |
| `transparency` | `0` | fades it |
| `blend` | `"over"` | see [Blends](#blends) |
| `smooth` | `false` | smooths it when it is scaled; without it, pixels stay square |

An image may be drawn onto itself.

### Flood fill

```lua
local painted = canvas:floodFill(x, y, color, options?)
```

Paints the pixel at `x, y` and every pixel joined to it that looks the same, like a paint bucket.
Pixels join up, down, left and right, not across corners. It returns how many pixels it painted.
`FillOptions`:

| Option | Default | |
|---|---|---|
| `tolerance` | `0` | from 0 to 1. How far a pixel's red, green, blue and alpha may each be from the first pixel's and still count as the same. `0` only takes exact matches |
| `transparency` | `0` | how see-through the paint is |
| `blend` | `"over"` | see [Blends](#blends) |

### Text

```lua
-- client
local width, height = canvas:drawText("Score: 12", 4, 4, color(1, 1, 1), { shadow = true, size = 2 })
```

`drawText(text, x, y, color, options?)` writes text in Minecraft's own font and returns the width
and height of what it drew, in pixels. `TextOptions`:

| Option | Default | |
|---|---|---|
| `size` | `1` | how many pixels one pixel of the font takes. Fractions work. Above 0 |
| `shadow` | `false` | draws the text again first, one font pixel right and down, in a quarter of the colour, as Minecraft does |
| `align` | `"left"` | `"left"`, `"center"` or `"right"`. Which part of each line `x` is |
| `wrap` | `0` | the widest a line may be, in pixels. `0` never wraps |
| `lineHeight` | `0` | pixels from the top of one line to the next. `0` is the font's, 9 font pixels |
| `transparency` | `0` | how see-through the text is |
| `blend` | `"over"` | see [Blends](#blends) |
| `bold` | `false` | draws each letter twice, one font pixel apart, and adds a pixel after each |
| `italic` | `false` | leans each letter, as Minecraft does |
| `smooth` | `false` | smooths the letters when `size` scales them; without it, pixels stay square |

- `x` and `y` are where the text starts. With `"center"` each line is centred on `x`, and with
  `"right"` each line ends at `x`.
- `y` is the top of the tallest letter in the font, not the top of the letters you wrote. In
  Minecraft's font the accented capitals are tallest, so plain ASCII lands 3 font pixels lower, 3
  pixels at size 1 and 6 at size 2.
- `"\n"` starts a new line. With `wrap`, a line breaks at its last space that fits, and the spaces at
  the break are dropped. A word wider than `wrap` breaks between two letters.
- The colour multiplies the letter's own colour. The font's letters are white, so they come out in
  your colour.
- A character the font does not have draws as an empty box, 5 by 8 font pixels.

`canvas:measureText(text, options?)` and `images.measureText(text, options?)` return the same width
and height without drawing anything, so you can place text before you draw it:

```lua
-- client
local options = { size = 2, shadow = true }
local w, h = images.measureText("Game over", options)
canvas:drawText("Game over", (canvas.width - w) / 2, (canvas.height - h) / 2, color(1, 0.3, 0.3), options)
```

- The width is the widest line, and counts the one pixel gap after the last letter.
- The height runs from `y` to the lowest pixel a letter of the font can reach on the last line. At
  size 1 in Minecraft's font one line is 12 pixels tall, and each line after it adds 9.
- `shadow` adds `size` to both.
- `italic` is not counted, so leaning text reaches up to about 3 font pixels past the width.

The font is read from the game on the player's machine, with its resource packs, so a pack that
changes the font changes the text. Letters come from the font's bitmap files. Characters Minecraft
only has in its unifont fallback, such as most Chinese, Japanese and Korean, draw as the box.

> [!IMPORTANT]
> Text needs Minecraft's font, which only a player's game has. Draw and measure text in a
> `LocalScript`. A server `Script` can do it only when the server runs inside a player's game, as
> in singleplayer. On a dedicated server it is an error.

### Blends

`blend` says how new paint mixes with the pixels already there.

| Blend | What happens |
|---|---|
| `"over"` | the paint goes on top, as a brush would. See-through paint lets what is under it show. The default |
| `"replace"` | the pixel becomes the paint, transparency included. Painting with transparency 1 cuts a hole |
| `"add"` | the paint's colour is added, which brightens. Good for glows |
| `"multiply"` | the colour is multiplied by the paint, which darkens. Transparency stays as it was |
| `"erase"` | the paint's colour is ignored and its strength wipes the pixel away. A solid paint makes it fully see-through |

On a smooth edge every blend is applied only as much as the shape covers the pixel.

<!-- demo:images-blend -->

### Reading and writing pixels

```lua
local pixels = canvas:readPixels(x?, y?, width?, height?)
canvas:writePixels(x, y, width, height, pixels)
```

Pixels are a flat list of numbers from 0 to 1, four per pixel: red, green, blue, alpha. They go row
by row, left to right, starting at the top left. Pixel `(px, py)` of the area starts at index
`(py * width + px) * 4 + 1`.

- `readPixels()` with no arguments reads the whole image. Pixels outside the image read as `0, 0, 0,
  0`. It reads at most 1024 by 1024 pixels at once.
- `writePixels` needs exactly `width * height * 4` numbers. Numbers below 0 or above 1 are clamped.
  Pixels outside the image are skipped. It replaces the pixels; it does not blend.

One `writePixels` call is much faster than many `setPixel` calls. Keep the list and fill it again
each frame, as the [plasma example](#a-plasma-animation) does.

### Changing an image

| Call | What it does |
|---|---|
| `canvas:resize(width, height, smooth?)` | scales the image to a new size. `smooth` smooths it, otherwise pixels stay square |
| `canvas:crop(x, y, width, height)` | returns a new image cut out of this one. Parts outside this image come out see-through |
| `canvas:copy()` | returns a new image with the same pixels |
| `canvas:flip("horizontal")` | mirrors it left to right. `"vertical"` mirrors it top to bottom |
| `canvas:rotate(quarterTurns)` | turns it clockwise by quarter turns. Negative turns go the other way. An odd number swaps width and height |

`resize`, `flip` and `rotate` change the image in place, so everything showing it changes too. `crop`
and `copy` leave it alone.

### Destroying

`canvas:destroy()` throws the pixels away. Anything showing it stops showing it, and every other call
on it errors with "this image was destroyed". `width`, `height` and `uri` can still be read.

You rarely need to. Every image a script made is thrown away when its scripts stop, which includes
every reload.

Images made by `crop` and `copy` are thrown away with the others.

## Loading

`images.load` and `images.skin` return an `EditableImage` like any other, ready to draw on or read.
Both wait for the image, so call them inside `task.spawn` or a function that may yield. If the image
cannot be read they raise an error, which `pcall` catches.

```lua
-- client
task.spawn(function()
    local logo = images.load("res://ui/logo.png")
    logo:drawRectangle(0, 0, logo.width, logo.height, color(1, 1, 1), { filled = false, thickness = 2 })
    badge.image = logo.uri
end)
```

`images.load(source)` reads:

| Source | Where it works |
|---|---|
| `"res://ui/logo.png"` | a file in the place. Scripts and client scripts |
| `"https://example.com/cat.png"` | a web address. Scripts and client scripts, with `httpRequests` on |
| `"minecraft:block/stone"`, `"item/diamond"` | a Minecraft texture. Client scripts only |

- Files may be PNG, JPEG, GIF or BMP. A GIF gives its first frame.
- An image bigger than 1024 pixels on a side is scaled down, keeping its shape, until it fits.
- A web address needs `httpRequests = true` under `[features]` in `place.toml`, as for
  [http](http.md#turning-it-on). The switch is read on the machine the script runs on. The
  download stops after 20 seconds, or if the file is bigger than 16 MB, or if the address answers
  with anything but success.
- A Minecraft texture is looked up under `textures/`, and `.png` is added. Both may be written out:
  `"minecraft:textures/block/stone.png"` works too. A namespace left out is `minecraft`.

### Skins

`images.skin(player, part?)` reads a player's skin. It works in client scripts only.

```lua
-- client
task.spawn(function()
    local ok, head = pcall(function() return images.skin(game.players:me(), "head") end)
    if ok then face.image = head.uri end
end)
```

- `player` is a `Player`, or a body that belongs to one.
- `part` is `"full"` for the whole skin sheet, which is the default, or `"head"` for an 8 by 8 face
  with the hat layer drawn over it.
- A skin is downloaded after the player joins. `skin` waits up to 2 seconds for it. A player whose
  skin has still not arrived gets the default skin. A player who is not in the game after 2 seconds
  is an error.

Show a head with `resampleMode = "pixelated"`, or its 8 pixels are smoothed into a blur.

## Examples

These come from the image showcase place. They all run in `client/main.luau`, and `screen` is a
`ScreenGui` made at the top of it.

### A paint canvas, mirrored in the world

The canvas is one image. The button on the screen and the decal on a part show the same image, so
what you paint on the screen appears on the easel in the world at the same moment.

```lua
local PAINT = 256
local canvas = images.create(PAINT, PAINT, color(1, 1, 1))

local board = screen:add("ImageButton", {
    position = udim2.fromOffset(20, 36), size = udim2.fromOffset(PAINT, PAINT),
    image = canvas.uri, autoButtonColor = false, backgroundTransparency = 1,
}) :: ImageButton

local easel = game.world:waitForChild("Easel") :: Part
easel:add("Decal", { texture = canvas.uri, face = "back" })

local brush = color(0.1, 0.1, 0.1)
local drawing = false
local lastX, lastY = 0, 0

local function toCanvas(x: number, y: number): (number, number)
    local at, size = board.absolutePosition, board.absoluteSize
    return (x - at.x) / size.x * PAINT, (y - at.y) / size.y * PAINT
end

board.mouseButton1Down:connect(function(x, y)
    drawing = true
    lastX, lastY = toCanvas(x, y)
    canvas:drawCircle(lastX, lastY, 3, brush, { smooth = true })
end)
board.mouseMoved:connect(function(x, y)
    if not drawing then return end
    local cx, cy = toCanvas(x, y)
    canvas:drawLine(lastX, lastY, cx, cy, brush, { thickness = 6, smooth = true })
    lastX, lastY = cx, cy
end)
board.mouseButton1Up:connect(function() drawing = false end)
board.mouseLeave:connect(function() drawing = false end)

-- right click fills like a paint bucket
board.mouseButton2Click:connect(function(x, y)
    local cx, cy = toCanvas(x, y)
    canvas:floodFill(math.floor(cx), math.floor(cy), brush, { tolerance = 0.1 })
end)
```

`toCanvas` turns a mouse position on the screen into a pixel of the canvas. Joining each mouse
position to the last one with a thick line keeps a fast stroke from breaking into dots.

### A plasma animation

A 48 by 48 image computed in Luau every frame and written in one call. The list of pixels is made
once and reused.

```lua
local SIZE = 48
local plasma = images.create(SIZE, SIZE)
screen:add("ImageLabel", {
    size = udim2.fromOffset(144, 144),
    image = plasma.uri, resampleMode = "pixelated",
})

local pixels: { number } = table.create(SIZE * SIZE * 4, 1)
local clock = 0

game.renderStepped:connect(function(dt: number)
    clock += dt
    local at = 1
    for y = 0, SIZE - 1 do
        for x = 0, SIZE - 1 do
            local v = math.sin(x / 5 + clock) + math.sin(y / 7 - clock * 1.3)
                + math.sin((x + y) / 9 + clock * 0.7)
            local t = v / 3 * math.pi
            pixels[at] = 0.5 + 0.5 * math.sin(t)
            pixels[at + 1] = 0.5 + 0.5 * math.sin(t + 2.1)
            pixels[at + 2] = 0.5 + 0.5 * math.sin(t + 4.2)
            pixels[at + 3] = 1
            at += 4
        end
    end
    plasma:writePixels(0, 0, SIZE, SIZE, pixels)
end)
```

### Your own face

```lua
local face = screen:add("ImageLabel", {
    size = udim2.fromOffset(80, 80),
    resampleMode = "pixelated",
}) :: ImageLabel

task.spawn(function()
    local me = game.players:me()
    if me == nil then return end
    local ok, head = pcall(function() return images.skin(me, "head") end)
    if ok then face.image = head.uri end
end)
```

The label starts empty and gets its image once the skin has been read.

### A frame drawn in code, stretched with nine-slice

A 24 by 24 image is enough for a panel of any size. The 8 pixel corners hold the rounding, and
nine-slice stretches only the middle.

```lua
local frameArt = images.create(24, 24)
frameArt:drawRectangle(0, 0, 24, 24, color(0.08, 0.09, 0.12), { cornerRadius = 8, smooth = true, transparency = 0.1 })
frameArt:drawRectangle(0, 0, 24, 24, color(0.45, 0.55, 0.95), { cornerRadius = 8, smooth = true, filled = false, thickness = 2 })

local panel = screen:add("ImageLabel", {
    position = udim2.fromOffset(20, 20),
    size = udim2.fromOffset(560, 340),
    image = frameArt.uri,
    scaleType = "slice",
    sliceMin = vec3(8, 8, 0),
    sliceMax = vec3(16, 16, 0),
})
```
