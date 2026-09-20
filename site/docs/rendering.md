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
  pack id works in its place.
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
| `ShadowQuality` | `resolution`, `sunResolution`, `sunCascades`, `sunDistance`, `maxDistance`, `softness`, `contactHardening`, `lightSize`, `entities` |

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
