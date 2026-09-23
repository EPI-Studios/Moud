# Textures, models, lights, post effects and held items

Everything on this page is an instance you add to the tree, like a part. A `MeshPart` is a part drawn
as a model file, a light is an instance that lights the world around it, a post effect is an instance
that changes the finished picture, and a body's hands hold whatever you write into two properties. You
add them with `add`, change them by writing properties, and take them away with `destroy`.

## A texture on a part

A part takes a texture of its own, on every face at once:

```lua
world:add("Part", {
    size = vec3(12, 1, 12),
    cframe = cframe(0, 64, 0),
    texture = "minecraft:block/oak_planks",
    studsPerTile = 1,
    anchored = true,
})
```

`texture` takes what every other image property takes: a file in the place, an image a script drew,
or any texture the game itself has, as [Images](images.md) lists. `studsPerTile` is how many metres
one copy of the image covers, so the floor above repeats the planks once a metre and widening it
adds copies instead of stretching the ones it has. `color` still tints what is drawn, so one image
serves a whole palette of parts.

The image is laid on in world space, from three directions at once, which means it needs no texture
coordinates and lands correctly on a ball, a cylinder or a wedge — the shapes a `Decal` was never
able to cover. Use a [`Decal` or a `Texture`](effects.md#a-sign-and-a-tiled-floor) when you want one
face of a part to differ from the others; use `texture` when the whole part is made of the same
thing.

Parts that share a texture are drawn together, so the cost is counted per texture and not per part.
A part with no `texture` is drawn exactly as before.

`textureOffset` slides the image across the part, in metres along each world axis. Tween it and a
tiled texture scrolls, which is a conveyor belt, a river or a sign that crawls:

```lua
local belt = world:add("Part", {
    size = vec3(12, 0.4, 3),
    cframe = cframe(0, 64, 6),
    texture = "minecraft:block/oak_planks",
    studsPerTile = 2,
    anchored = true,
})
belt:tween({ textureOffset = vec3(20, 0, 0) }, { time = 10, easing = "linear", repeats = -1 })
```

An offset of one `studsPerTile` along an axis brings the image back to where it started, so a loop
that moves by a whole number of tiles never jumps.

## Shading a part

```lua
world:add("Part", {
    shape = "ball",
    size = vec3(3, 3, 3),
    cframe = cframe(0, 66, 0),
    color = color(0.8, 0.2, 0.15),
    shading = "pbr",
    roughness = 0.2,
    metallic = 0,
    anchored = true,
})
```

A part is drawn the way Minecraft draws a block unless you say otherwise: each face a flat shade
brighter or darker by which way it points. `shading = "pbr"` lights it the way a glTF `MeshPart` is
lit instead, from the sun and the sky around it, with a highlight and a reflection. A ball or a
cylinder is then smooth all the way round instead of faceted.

- **`shading`** is `"flat"` (the default, the Minecraft look) or `"pbr"`.
- **`roughness`** runs from 0, a mirror, to 1, chalk. It only matters with `"pbr"`. Default 0.5.
- **`metallic`** from 0 to 1. A metal takes its reflection's colour from `color` and has no dull
  colour of its own, so gold is a yellow `color` with `metallic = 1`.

The reflection and the soft light come from the sky, and from the [`Sky`'s `hdri`](lighting.md#an-hdri-sky)
when there is one, scaled by the `Lighting`'s `environmentDiffuseScale` and `environmentSpecularScale`.
Torchlight and night still darken a `pbr` part the way they darken a flat one, and it casts and
takes shadows the same way. It works with `texture` too: the image becomes the colour the light
falls on.

Flat and `pbr` parts are drawn together, so switching one part over costs nothing extra.

## MeshPart

A `MeshPart` is a part that is drawn as a 3D model instead of a box:

```lua
world:add("MeshPart", {
    meshId = "res://models/crate.glb",    -- .gltf, .glb, .ammesh or .bbmodel, or a resource pack id
    size = vec3(1, 1, 1),
    cframe = cframe(0, 65, 0),
    animation = "idle", animationSpeed = 1, animationLooped = true,
})
```

- **`meshId`** is the model file. Moud reads `.gltf`, `.glb`, `.ammesh` and `.bbmodel`, and a resource
  pack id works in its place. `#` and a name after it draws one piece of the file, see
  [One piece of a file](#one-piece-of-a-file).
- **`size`** works as it does on any part, and the model is stretched to fill it. That means a
  `MeshPart` moves, scales and turns like a part, and you never scale the model file itself.
- **`animation`** names a clip inside the file, with **`animationSpeed`** and **`animationLooped`**
  deciding how it plays. A name the file does not have is logged once, along with the names it does
  have.
- A `MeshPart` with `Bone`s inside it is drawn posed by them instead, piece by piece, and ignores
  `animation`. See [Bones](animation.md#bone) and [importing a Blockbench model](animation.md#importing-a-blockbench-model).
- A `MeshPart` collides as its box, not as the shape of the model.
- Saving the model file reloads it while the game runs, so you can export from your modelling program
  and see the new shape without touching the place.
- **`transparency`** fades the whole model, as it fades a part. See
  [See-through models](#see-through-models).

### One piece of a file

A scene exported as one file holds many objects. Add `#` and a name to `meshId` to draw one of them:

```lua
world:add("MeshPart", {
    meshId = "res://models/tower.glb#Cube_018",
    size = vec3(1.2, 2, 1.2),
    cframe = cframe(4, 65, 0),
})
```

The name is a node of the file, as your modelling program named the object, and the node is drawn
with everything under it, so naming a group draws the whole group. When no node has that name, a
mesh with that name is used instead. A name the file does not have is logged once, along with the
names it does have.

The piece is drawn in its own frame, not where it stood in the file: its position and its turn in the
scene are taken off, and `size` fills the piece alone. So one chair of a room lands at the part's
`cframe`, upright, the same way a file holding only that chair would. The file is loaded once however
many pieces of it you place.

### Changing a model's materials

A `MaterialOverride` inside a `MeshPart` changes the model's materials for that part only, with no
shader:

```lua
local rack = world:add("MeshPart", { meshId = "res://models/rack.glb", size = vec3(3, 8, 3) })
rack:add("MaterialOverride", { color = color(1, 0.3, 0.2) })                   -- every material
rack:add("MaterialOverride", { material = "Metal", metallic = 1, roughness = 0.1 })
rack:add("MaterialOverride", {
    material = "Floor",
    texture = "minecraft:block/oak_planks",
    filter = "nearest",
    repeatU = 4, repeatV = 4,
})
```

- **`material`** is the name of the material in the file. Left empty, the override changes every
  material. Overrides for every material apply first, then the named ones on top. A name the file
  does not have is logged once, along with the names it does have.
- **`color`** multiplies the material's colour, and white leaves it as it is.
- **`texture`** replaces the material's colour image, and takes what every other image property
  takes. See [Images](images.md).
- **`roughness`** and **`metallic`** replace the material's own, and `-1`, the default, keeps them.
- **`emission`** replaces the colour the material glows with, and black, the default, keeps the
  file's. **`emissionStrength`** multiplies the glow, so `0` turns it off and `4` makes it bloom. See
  [Glowing models](#glowing-models).
- **`transparency`** makes the material see-through, on top of any transparency it already has.
- **`doubleSided`** draws both sides of every face, for leaves and flat sheets exported one-sided.
  Off keeps the file's setting.
- **`filter`** is `default`, `nearest` or `linear`. `nearest` keeps pixel-art images sharp up close.
- **`offsetU`**, **`offsetV`**, **`repeatU`** and **`repeatV`** move and repeat the material's images.
  See [Scrolling a texture](#scrolling-a-texture).

Two parts showing the same file each keep their own overrides, so one model file serves a red crate
and a blue one.

### See-through models

A material exported as blended, a material with an override's `transparency`, and a `MeshPart` with
`transparency` are drawn after every solid model, the farthest first, without hiding what is behind
them. A window in one model shows another model through it, whichever was added first. A material
exported with an alpha cutoff (glTF `MASK`) stays solid and cuts its holes instead, which suits
leaves, fences and grilles. A double-sided material is lit correctly from both sides.

### Scrolling a texture

`offsetU` and `offsetV` slide a material's images, in whole images, and a tween moves them:

```lua
local screen = world:add("MeshPart", { meshId = "res://models/screen.glb", size = vec3(4, 3, 0.2) })
local scroll = screen:add("MaterialOverride", { material = "Display" })
scroll:tween({ offsetU = 1 }, { time = 4, easing = "linear", repeats = -1 })
```

Images repeat, so sliding by exactly `1` comes back to where it started, and the loop above scrolls
forever without a jump. `repeatU` and `repeatV` tile the image more times across the material. The
colour, the normal, the roughness and the glow images all move together. Fog planes and water sheets
work the same way.

### Glowing models

A material that glows in the file keeps glowing in the dark, and with a
[`BloomEffect`](#post-effects) in the world, bright glow spills light around it:

```lua
world:add("BloomEffect", { intensity = 1.2 })
local lamp = world:add("MeshPart", { meshId = "res://models/lamp.glb" })
lamp:add("MaterialOverride", { material = "Bulb", emission = color(1, 0.8, 0.5), emissionStrength = 4 })
```

Glow comes from the file's emission (with its glTF emission strength), from an override's
`emission` and `emissionStrength`, and from `s.emission` in a [surface shader](#shaders-on-a-model).
The brighter it is, the wider it blooms: above the bloom's `threshold` it spreads.

### Shaders on a model

A `MeshPart` takes three GLSL files of your own, each one run at a different moment of drawing it:

| Property | When it runs | What it changes |
|---|---|---|
| `shader` | while the model is drawn, before any light | the surface: colour, transparency, glow, roughness, metallic, normal |
| `lightingShader` | after the lights | the final colour |
| `vertexShader` | before anything is drawn | where each vertex is |

Like a [light shader](#custom-light-shaders), each file is the body of a function: no `main`, and
saving the file redraws the model while the game runs.

**`shader`** is given `s`, the surface about to be lit, and changes what it needs: `s.uv`, `s.albedo`,
`s.alpha`, `s.emission`, `s.roughness`, `s.metallic`, `s.normal`. It can read `s.worldPos` and
`s.time`, in seconds.

A file may declare its own `uniform`s at the top. Each one is filled from a child of the part with
the same name: a `NumberValue` for a `float` or an `int`, a `BoolValue` for a `bool`, a `Vector3Value`
for a `vec3` or a colour, and a `StringValue` holding an image for a `sampler2D`.

```glsl
// res://shaders/glow_strip.glsl
uniform float progress;
uniform vec3 tint;
uniform sampler2D mask;

float lit = 1.0 - step(0.06, abs(progress - texture(mask, s.uv).r));
s.emission = tint * lit * 3.0;
```

```lua
local rack = world:add("MeshPart", {
    meshId = "res://models/rack.glb",
    size = vec3(2, 6, 2),
    shader = "res://shaders/glow_strip.glsl",
})
local progress = rack:add("NumberValue", { name = "progress" })
rack:add("Vector3Value", { name = "tint", value = vec3(0.2, 1, 0.3) })
rack:add("StringValue", { name = "mask", value = "res://textures/strip_mask.png" })

game.renderStepped:connect(function()
    progress.value = (os.clock() * 0.5) % 1
end)
```

The mask's red channel says when each spot of the model lights up, and moving `progress` from 0 to 1
sweeps a band of light across the model. Every part has its own values, so ten racks with the same
shader can each be at a different point of the sweep. A file takes at most sixteen values and four
images.

**`lightingShader`** is given `s` once the lights have landed: `s.albedo`, `s.normal`, `s.fragPos`,
`s.roughness`, `s.metallic`, `s.lightmap`, `s.time`, and `s.radiance`, the colour the model would
have had. It has to `return` a colour:

```glsl
// res://shaders/bands.glsl
return floor(s.radiance * 4.0) / 4.0;
```

**`vertexShader`** is given `v`: `v.localPos`, `v.worldPos`, `v.origin`, `v.uv` and `v.time`. It
returns how far to move the vertex, in world space:

```glsl
// res://shaders/sway.glsl
return vec3(sin(v.time * 2.0 + v.worldPos.y) * 0.05 * v.localPos.y, 0.0, 0.0);
```

The model's lighting follows the moved shape, so a flag that ripples is shaded along its folds.

## Lights

A light is an instance you put in the world to light what is around it. There are four classes, and
each one has a shape:

```lua
world:add("PointLight", { color = color(1, 0.8, 0.5), brightness = 2, range = 12 })
world:add("SpotLight", { innerAngle = 20, outerAngle = 35, cframe = cframe.lookAt(from, to) })
world:add("AreaLight", { shape = "rectangle", width = 2, height = 1 })   -- or "disc", width is the diameter
world:add("TubeLight", { length = 3 })                                    -- along its right axis
```

- A `SpotLight` points down its own frame, so `cframe.lookAt(from, to)` aims it at something.
- An `AreaLight` takes `shape = "rectangle"` with `width` and `height`, or `shape = "disc"`, where
  `width` is the diameter.
- A `TubeLight` runs along its right axis for `length` metres.

<!-- demo:rendering-spot -->

Every light has the same set of properties, whichever class it is: `enabled`, `color`, `temperature`
(kelvin; replaces `color` when above 0), `brightness`, `range`, `falloff` (`smooth`, `linear`,
`inverseSquare`, `exponent`), `falloffExponent`, `shadows`, `shadowStrength`, `godrays`.

`falloff` is `inverseSquare` by default, which is how a real lamp behaves: bright within a metre or
two of the source and dim well before `range`. Raise `brightness` to reach further rather than
widening `range`, which only moves the point where the light is cut off. `smooth` and `linear` spread
the light evenly all the way to `range` instead, which reads as a fill light rather than a bulb, and
`exponent` shapes the curve with `falloffExponent`.

A light only stops at walls when `shadows` is on, and it is off by default. A light left without
shadows shines through whatever is in the way.

A light lights the colour of what it lands on, not what the daylight already made of it. A lamp in a
room the sun never reaches shows the room in its own colours, where the rest stays dark.

<!-- demo:rendering-falloff -->

A light parented to a part moves with it, and faces down its frame's forward. Parent one to a lantern
part and the light follows the lantern wherever physics or a tween takes it.

> [!NOTE]
> The sun, the sky, the weather and the distance fog are not lights, and none of them have the
> properties above. They belong to a `Lighting` instance. See
> [Lighting, sky, weather and presets](lighting.md).

### Custom light shaders

`shader` names a GLSL file that changes how the light lands. The file is the body of a function that
may change `color`, `atten` and `p`, and can read `lightPos`, `lightDir`, `stage` and `LightTime`:

```glsl
// res://lights/flicker.glsl
color *= 0.8 + 0.2 * sin(LightTime * 23.0) * sin(LightTime * 7.0);
```

```lua
world:add("PointLight", { shader = "res://lights/flicker.glsl" })
```

There is no `main` and no uniform block to write. The file is the body, so the one line above is a
whole shader. Editing the file relights the scene while the game runs.

## Post effects

Post effects are instances. They apply while they are in the tree and `enabled`, on every client. Add
one to the world to turn it on, and destroy it to turn it off.

**Built on Amnetic.** The first enabled one of each class is used. Removing it puts the setting back
to how it was.

| Class | Properties |
|---|---|
| `BloomEffect` | `intensity`, `threshold`, `knee`, `size`, `resolution`, `occlude` |
| `ColorGradeEffect` | `exposure`, `contrast`, `saturation`, `brightness`, `temperature`, `tint`, `gamma`, `lut`, `lutSize`, `lutIntensity` |
| `AmbientOcclusionEffect` | `radius`, `intensity`, `bias`, `power`, `resolution`, `temporal` |
| `GlobalIlluminationEffect` | `radius`, `intensity`, `resolution`, `history` |
| `ReflectionEffect` | `intensity`, `reflectivity`, `maxDistance`, `steps`, `thickness`, `edgeFade`, `resolution`, `temporal` |
| `AntiAliasingEffect` | `history`, `sharpness`, `clip` |
| `VolumetricEffect` | `strength`, `density`, `anisotropy`, `steps`, `resolution`, `shadows` |
| `ContactShadowEffect` | `distance`, `thickness`, `steps` |
| `ShadowQuality` | `resolution`, `sunResolution`, `sunCascades`, `sunDistance`, `maxDistance`, `softness`, `contactHardening`, `lightSize`, `entities`, `timeBudget`, `reduction` |

> [!NOTE]
> Adding a second `BloomEffect` does nothing while the first one is enabled. To swap settings, either
> write the properties of the one that is already there, or disable it so the next one takes over.

**Screen effects.** Any number of these stack. They are drawn in `order`, lowest first, and every one
has `intensity` from 0 to 1.

| Class | Properties | Default order |
|---|---|---|
| `FogEffect` | `color`, `start`, `density`, `heightFalloff`, `baseHeight`, `sky` | 0 |
| `OutlineEffect` | `color`, `thickness`, `threshold` | 5 |
| `DepthOfFieldEffect` | `focusDistance`, `focusRange`, `falloff`, `size` | 10 |
| `MotionBlurEffect` | `strength`, `samples` | 20 |
| `TonemapEffect` | `tonemapper` (`aces`, `agx`, `reinhard`, `uncharted`, `none`), `exposure` | 30 |
| `BlurEffect` | `size` | 40 |
| `ChromaticAberrationEffect` | `amount` | 50 |
| `VignetteEffect` | `radius`, `softness`, `color` | 60 |
| `PixelateEffect` | `pixelSize` | 70 |
| `PosterizeEffect` | `levels` | 75 |
| `SharpenEffect` | `amount` | 80 |
| `FilmGrainEffect` | `amount`, `size` | 90 |

Because effects are instances, a whole look is a folder you can throw away in one call:

```lua
local night = world:add("Folder", { name = "night" })
night:add("ColorGradeEffect", { exposure = 0.8, saturation = 0.7, temperature = -0.3 })
night:add("VignetteEffect", { radius = 0.6, softness = 0.5 })
night:add("FogEffect", { color = color(0.1, 0.12, 0.2), start = 8, density = 0.05 })
-- night:destroy() takes all three away again
```

Destroying the folder destroys the three effects inside it, and each one puts its setting back as it
goes.

### Effects over the interface

A screen effect normally touches only the world, and every `ScreenGui` is drawn on top of it
untouched. Turn on `coversInterface` and the effect runs after the screen guis are drawn, so it
reaches the menu text too:

```lua
-- client
world:add("VignetteEffect", { radius = 0.5, softness = 0.4, coversInterface = true })
world:add("PosterizeEffect", { levels = 6, coversInterface = true })
```

- Effects with `coversInterface` run after all the others, still in `order` among themselves.
- The first-person hand and every `ScreenGui` are under them. The chat, the pause menu and other
  game screens are not.
- `FogEffect`, `OutlineEffect` and `DepthOfFieldEffect` read the world's depth, which the interface
  does not write, so they treat a label like whatever is behind it. The effects that only look at
  colour, such as a vignette, posterize, pixelate, blur, grain or a `PostShader` that only calls
  `sceneColor`, are the ones to use here.

### A time budget for shadows

```lua
world:add("ShadowQuality", { sunResolution = 4096, sunCascades = 4, timeBudget = 2 })
```

`timeBudget` is how many milliseconds a frame may spend drawing shadow maps. 0, the default, means
no limit. When the shadow pass keeps running over it, the sun's shadow is made cheaper a step at a
time: a shorter reach first, then a lower resolution, then fewer cascades. When there is room again
it climbs back a step at a time, more slowly, and it never goes above what the `ShadowQuality` asks
for. A setting that keeps tipping it over waits longer before it is tried again, so the shadows do
not flicker between two qualities.

`reduction` reads how many steps down it is right now, from 0 to 6, on the client. The time measured
is the time the game spends sending the shadow work, which follows the real cost closely but is not
a GPU timer.

### Your own screen shader

`PostShader` runs a GLSL fragment shader over the picture. It takes its place among the screen effects
by `order`, so the one below is drawn between `SharpenEffect` and `FilmGrainEffect`:

```lua
local crt = world:add("PostShader", { shader = "res://post/scanlines.glsl", order = 85 })
crt:add("NumberValue", { name = "Lines", value = 240 })
```

```glsl
// res://post/scanlines.glsl
uniform float Lines;

void main() {
    vec4 c = sceneColor(vUV);
    float line = 0.85 + 0.15 * sin(vUV.y * Lines * 3.14159);
    FragColor = vec4(mix(c.rgb, c.rgb * line, Intensity), c.a);
}
```

- The shader gets `sceneColor(uv)`, `sceneDepth(uv)`, `isSky(uv)`, `viewPosition(uv)`,
  `worldPosition(uv)` and `linearDepth(uv)`.
- Uniforms provided: `Time`, `ScreenSize`, `Intensity`, `CameraPosition`, `PrevCameraPosition`,
  `ViewProj`, `InvViewProj`, `PrevViewProj`.
- Every `NumberValue`, `BoolValue` and `Vector3Value` child is a uniform with the same name. The
  `NumberValue` named `Lines` above is the `uniform float Lines` the shader reads, so writing
  `crt:find("Lines").value` from a script changes the picture.
- A file that starts with `#version` is used as written, with none of the above added.
- Saving the file recompiles it.

<!-- demo:rendering-stack -->

## See-through parts

A part with `transparency` between 0 and 1 is drawn after everything solid, the furthest first, and
does not hide what is behind it. Both its sides are drawn, so standing inside a tinted box tints the
view. It casts no shadow.

## Held items

A body draws what each hand holds, with the game's own item renderer. A player's body shows their real
items. For bodies a place owns, you write the item id into `rightItem` and `leftItem`:

```lua
npc.rightItem = "minecraft:diamond_sword"
npc.leftItem = "minecraft:shield"
npc.rightItem = ""        -- empty hand
```

A player's `rightItem` and `leftItem` come off their own inventory, so writing them on a worn body does
nothing. The server overrides what a player is shown holding instead:

```lua
body.rightItemOverride = "minecraft:blaze_rod"   -- drawn whatever they hold
body.leftItemOverride = "minecraft:air"          -- an empty hand
body.rightItemOverride = ""                      -- back to their inventory
```

An override works on any body, and wins over `rightItem` / `leftItem` while it is set.

> [!IMPORTANT]
> If you write `rightItem` on a player's body and nothing changes, that is why. Use
> `rightItemOverride`, and set it back to `""` to hand the player their own inventory again.

<!-- demo:rendering-held -->
