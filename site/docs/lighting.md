# Lighting, sky, weather and presets

A `Lighting` is an instance you put in the world that takes the time of day, the sunlight, the
weather and the sky over from Minecraft. There is no lighting service to look up and no lighting
until you add one: a place with no `Lighting` in it keeps the game's own clock, sky and weather.

The first `Lighting` the engine finds in the world is the one it reads. A `Lighting` kept in
`ServerStorage` or `ReplicatedStorage` is ignored, because nothing in those containers is in the
world, as [Containers](containers.md) explains. Destroy the one in the world and the game takes its
sky, clock and weather back.

```lua
-- server: a place that is always just after dusk
local lighting = game.world:add("Lighting", {
    clockTime = 20.5,
    timeScale = 0,
    brightness = 1.2,
    ambient = color(0.05, 0.06, 0.1),
    outdoorAmbient = color(0.3, 0.32, 0.45),
})
```

- `clockTime = 20.5` puts the clock at half past eight in the evening.
- `timeScale = 0` freezes the clock there, so the light never moves on while players are in the
  place.
- `brightness`, `ambient` and `outdoorAmbient` set how strong the sunlight is and what light
  everything gets when the sun is not on it.

You add this from a server script, so every player is sent it and every player sees the same sky.

Four more classes go inside the `Lighting` and shape parts of what it draws: a `Sky` for what is
overhead, an `Atmosphere` for the air between the camera and the world, a `Clouds` for the clouds
and a `Weather` for rain, snow, fog and storms. They may hang anywhere underneath the `Lighting`, not
only straight under it, and the first one the engine finds is the one it reads.

In the Explorer, **Insert > Lighting** lists all five. A `Lighting` inserted there starts with
[`dayCycle`](#colours-of-the-day) on; one a script adds starts with it off.

Rather than setting every property by hand, a `Lighting` can take a whole look at once from a
[preset](#presets), such as a golden hour or a storm.

## The clock

| Property | Default | |
|---|---|---|
| `clockTime` | 14 | the time of day in hours, 0 to 24 |
| `timeScale` | 1 | how fast the day runs, up to 60; 0 freezes it, 1 is a twenty minute day |
| `geographicLatitude` | 41.733 | how far north the place sits, in degrees |

`clockTime` drives the game's own clock, so the sun, the sky colour and the block light all follow
it. While `timeScale` is above 0 the engine writes `clockTime` back as the day runs, so reading it
always gives the time now. Writing it moves the clock at once, and the day keeps the date it was on.

Two methods read and write the same clock in minutes, which is easier when you are counting from
midnight:

```lua
print(lighting:getMinutesAfterMidnight())    -- 1230 at 20.5
lighting:setMinutesAfterMidnight(6 * 60)     -- sunrise
```

| | |
|---|---|
| `lighting:getMinutesAfterMidnight()` | `clockTime` in minutes, 0 to 1440 |
| `lighting:setMinutesAfterMidnight(minutes)` | sets `clockTime` from minutes |
| `lighting:getSunDirection()` | a unit vector from the world towards the sun |
| `lighting:getMoonDirection()` | the other way |

`geographicLatitude` tilts the sun's path: 0 sends it straight overhead at noon, 90 keeps it near the
horizon all day. It turns the direction those two methods give, and with it the light and the shadows
on mesh parts. It does not move the game's own sun sprite, which keeps the path it always had; a
`sunTextureId` is drawn along the tilted path instead.

`getSunDirection` is what you point things at when they have to follow the sun:

```lua
-- server: a panel that keeps facing the sun
local lighting = game.world:find("Lighting") :: Lighting
local panel = game.world:find("panel") :: Part
game.stepped:connect(function()
    panel.cframe = cframe.lookAt(panel.position, panel.position + lighting:getSunDirection())
end)
```

`game.stepped` is the server's loop, so the panel is turned once a tick and every player sees it
pointing the same way. Adding the sun direction to the panel's own position gives a point in the sky
for `cframe.lookAt` to aim at.

## Colours of the day

| Property | Default | |
|---|---|---|
| `dayCycle` | false | the light, the air and the sky take the colours of the hour |

With `dayCycle` off, the colours you set are the colours you get at every hour. Turn it on and the
engine tints them by how high the sun stands: blue at night, violet in the twilight, orange as the sun
crosses the horizon, and back to what you set once the sun is well up.

```lua
--!strict
-- server: a day that runs twice as fast and takes the colours of the hour
local lighting = game.world:add("Lighting", {
    clockTime = 17,
    timeScale = 2,
    dayCycle = true,
}) :: Lighting
lighting:add("Atmosphere", { density = 0.25 })
```

The clock starts an hour before sunset and runs at twice the speed, so within a few minutes the
light goes gold, then orange, then blue.

Four things follow the hour:

| | What happens to it |
|---|---|
| `outdoorAmbient` | multiplied by the tint, so the sky's light turns warm at sunrise and sunset and blue at night |
| `ambient` | never lower than a dark blue at night and a faint grey by day; an `ambient` brighter than that wins |
| the `Atmosphere`'s `color` and `decay` | multiplied by the tint, dark blue at night and orange at the horizon |
| the game's sky colour | multiplied by a paler version of the same tint |

- The tint changes with the sun's height: full night with the sun 20 degrees or more below the
  horizon, twilight at 7 below, the warmest colours at the horizon, and no tint at all once the sun is
  20 degrees up. The colours in between fade smoothly, so nothing jumps.
- Before noon the engine uses a morning set of colours, pinker than the evening one.
- The colours you set are the daytime ones. Between the sun reaching 20 degrees and dropping back to
  it, `outdoorAmbient` and the `Atmosphere` look exactly as the properties say.
- `geographicLatitude` moves the sun, so it moves the colours too. Beyond about 70 degrees north or
  south the sun never climbs 20 degrees, and the whole day keeps some of the evening colour.
- Only the game's own sky is tinted. A [`Sky`](#sky) with its faces filled draws your images as they
  are.

<!-- demo:lighting-sun -->

The tint is applied when the frame is drawn. Reading `outdoorAmbient` or the `Atmosphere`'s `color`
gives back what you set, not the tinted colour.

## Light

| Property | Default | |
|---|---|---|
| `brightness` | 2 | how strong the sunlight is; 2 is what the game gives on its own |
| `ambient` | black | the least light anything gets, indoors and at night |
| `outdoorAmbient` | grey (0.5) | tints the light the sky gives; grey leaves it as the game has it |
| `exposureCompensation` | 0 | brightens or darkens the whole picture in stops; 1 is twice as bright |
| `shadowSoftness` | 0.2 | how blurry the edges of the sun's shadows are |
| `shadowStrength` | 0.8 | how dark the sun's shadows are; 1 takes shadowed ground to black |
| `globalShadows` | true | the sun casts shadows |
| `shadowBias` | 0.0005 | how far a sun shadow is pushed off the surface it lands on |
| `shadowNormalBias` | 0.05 | the same, along the surface's own direction |
| `shadowFade` | 0.8 | where shadows start fading out towards the edge of the shadowed range |
| `blockShadowDistance` | 48 | how far out the Minecraft terrain is drawn into the shadow map, in blocks |
| `environmentDiffuseScale` | 1 | how much of the surroundings' light reaches meshes |
| `environmentSpecularScale` | 1 | how much the surroundings show in the shine on meshes |

Raise `ambient` when a cave or a night in your place is too dark to play in: it is a floor under the
light, so it lifts the parts the sun never reaches without touching the ones it does. Leave it black
and unlit corners stay black.

Two of these need something else in the place before they do anything:

- `globalShadows` turns the sun shadows on by itself. A [`ShadowQuality`](rendering.md#post-effects)
  effect is only needed to change how good they look.
- The two environment scales only reach `MeshPart`s drawn from a glTF file. Blocks and plain parts
  keep the game's own light.
- `exposureCompensation` rides on top of a [`ColorGradeEffect`](rendering.md#post-effects), so put
  one in the place when you use it.

> [!NOTE]
> With no `ColorGradeEffect` in the place, `exposureCompensation` leaves the picture as it was. That
> is the usual reason a lighting property looks like it was ignored.

`shadowBias` and `shadowNormalBias` decide how the sun shadow sits on a surface. Too little and a
surface shadows itself in stripes; too much and a shadow slides away from whatever casts it. The
defaults are set for block-sized geometry, so thin parts are the ones that need them lowered.
`shadowFade` is where the shadows start fading out towards the far edge of the shadowed range, and
`blockShadowDistance` is how far out the Minecraft terrain itself is drawn into the shadow map:
lower it to save time in a scene where the terrain is not what casts the interesting shadows.

Each part decides for itself whether it throws a shadow. `castShadow` is on by default; turn it off
on the parts whose shadow buys you nothing, such as a thin trim, a railing or anything the player
never sees against the ground. The part is still lit and still receives shadows, it only stops being
drawn into the shadow pass, which is the pass that costs the most in a busy scene.

## Sky

A `Sky` inside the `Lighting` replaces what is drawn overhead: the six faces of a skybox, the sun,
the moon and the stars.

```lua
lighting:add("Sky", {
    skyboxFt = "res://sky/front.png",
    skyboxBk = "res://sky/back.png",
    skyboxLf = "res://sky/left.png",
    skyboxRt = "res://sky/right.png",
    skyboxUp = "res://sky/top.png",
    skyboxDn = "res://sky/bottom.png",
    sunTextureId = "res://sky/sun.png",
    sunAngularSize = 40,
    starCount = 800,
})
```

| Property | Default | |
|---|---|---|
| `skyboxFt`, `skyboxBk` | `""` | the faces looking north and south |
| `skyboxLf`, `skyboxRt` | `""` | the faces looking west and east |
| `skyboxUp`, `skyboxDn` | `""` | the top and the bottom |
| `sunTextureId`, `moonTextureId` | `""` | images drawn in place of the sun and the moon |
| `sunAngularSize`, `moonAngularSize` | 21, 11 | how wide each looks, in degrees |
| `starCount` | 3000 | how many stars come out at night; 3000 is every star the game has |
| `celestialBodiesShown` | true | off: no sun, no moon and no stars |
| `skyboxOrientation` | 0, 0, 0 | turns the box of images, in degrees |

- The game's own sun is about 33 degrees across and its moon about 23, so the defaults are smaller
  than what it draws. The sample above sets `sunAngularSize = 40` to get a sun larger than the
  game's.
- A `sunTextureId` hides the game's sun and draws the image along the path `getSunDirection` gives.
  An empty one keeps the game's sun, and the game's moon keeps its phases.
- `skyboxOrientation` turns the images only. The sun and the moon keep following the clock.

> [!IMPORTANT]
> Filling any one face puts the whole box in place of the game's sky, and the faces you left empty
> are holes. Fill all six.

## Atmosphere

An `Atmosphere` inside the `Lighting` is the distance fog: the air between the camera and what it
looks at.

| Property | Default | |
|---|---|---|
| `density` | 0.3 | how thick the air is, 0 to 1 |
| `offset` | 0.25 | pushes the near edge of the fog out, -1 to 1 |
| `color` | light grey | the colour of the air near the camera |
| `decay` | blue grey | the colour it falls back to away from the sun |
| `glare` | 0 | how much the air lights up when the sun is ahead, 0 to 10 |
| `haze` | 0 | how far the air reaches into the sky, 0 to 10 |

`density` pulls the fog in: 0 leaves the view open to the render distance, 1 closes it to a few
metres. The far edge never comes nearer than 8 metres. `haze` also trades `decay` for `color`, so
with no haze the fog takes `decay` alone.

The [`FogEffect`](rendering.md#post-effects) is a separate screen pass and draws on top of this. It
is the one that knows about height, so a valley that fills with mist wants both:

```lua
lighting:add("Atmosphere", {
    density = 0.45,
    offset = 0.3,
    color = color(0.72, 0.76, 0.8),
    decay = color(0.4, 0.45, 0.55),
    haze = 4,
})

game.world:add("FogEffect", {
    color = color(0.75, 0.78, 0.82),
    start = 2,
    density = 0.4,
    baseHeight = 70,
    heightFalloff = 0.35,
})
```

- The `Atmosphere` thickens the air everywhere at once, at any height.
- The `FogEffect` sits in the world rather than in the `Lighting`, and its `baseHeight` and
  `heightFalloff` are what keep the mist in the valley floor instead of over the hilltops.

Two things change the air on top of these properties without writing to them: with
[`dayCycle`](#colours-of-the-day) on, `color` and `decay` take the colour of the hour, and a
[`Weather`](#weather) thickens and greys the air. A script that reads or tweens `density` keeps
working through a storm, because the storm is added when the frame is drawn.

## Clouds

A `Clouds` inside the `Lighting` tints and thins the game's clouds.

| Property | Default | |
|---|---|---|
| `enabled` | true | off: no clouds are drawn |
| `cover` | 0.5 | how much sky they take, 0 to 1 |
| `density` | 0.7 | how solid they look, 0 to 1 |
| `color` | white | the colour they are tinted |

The game's clouds keep their shape, so `cover` and `density` thin them rather than opening holes in
them. Either at 0 draws nothing at all, except that a [`Weather`](#weather) that clouds the sky over
raises the cover it draws with.

```lua
lighting:add("Clouds", { cover = 0.9, density = 0.4, color = color(0.5, 0.45, 0.55) })
```

That gives clouds over most of the sky, thin enough to see through, tinted towards violet.

## Weather

A `Weather` inside the `Lighting` brings rain, snow, fog or a storm. It draws its own rain and snow,
plays their sound, thickens the air, fills and darkens the clouds, and in a storm throws lightning. A
`Weather` anywhere else in the tree does nothing.

```lua
--!strict
-- server: light rain drifting east, easing in over ten seconds
local lighting = game.world:add("Lighting", { dayCycle = true }) :: Lighting
local weather = lighting:add("Weather", {
    kind = "rain",
    intensity = 0.5,
    wind = vec3(3, 0, 0),
    transition = 10,
}) :: Weather

task.delay(120, function()
    weather.kind = "snow"
    weather.intensity = 0.8
end)
```

The rain comes in over ten seconds for every player. Two minutes later the script turns it to snow,
and the change takes another ten seconds: the rain thins out while the snow thickens.

| Property | Default | |
|---|---|---|
| `kind` | `"clear"` | `"clear"`, `"rain"`, `"snow"`, `"storm"` or `"fog"` |
| `intensity` | 1 | how heavy it is, 0 to 1 |
| `wind` | 2, 0, 1 | which way and how fast the rain and snow drift, in blocks a second |
| `transition` | 5 | seconds a change of `kind` or `intensity` takes, 0 to 600; 0 switches at once |
| `strikes` | 0 | how many times lightning has struck; the server counts it |
| `strikePosition` | 0, 0, 0 | where the last lightning struck; the server writes it |

`strikes` and `strikePosition` are read-only: a script that writes either gets an error, the same as
writing any other read-only property. They are the server's record of its lightning and reach every
player like any other property, but they are not saved with the scene. To make lightning, call
[`strike`](#lightning).

### What each kind brings

Each kind is a mix of five ingredients, and `intensity` scales all of them:

| `kind` | Rain | Snow | Storm | Fog | Overcast |
|---|---|---|---|---|---|
| `"clear"` | | | | | |
| `"rain"` | 1 | | | 0.15 | 1 |
| `"snow"` | | 1 | | 0.25 | 0.8 |
| `"storm"` | 1 | | 1 | 0.3 | 1 |
| `"fog"` | | | | 1 | 0.3 |

`"rain"` at an `intensity` of 0.5 is half the rain, a little fog and a half-grey sky. `"clear"` with any
`intensity` is no weather at all.

When `kind` or `intensity` changes, the five ingredients ease from where they are to the new mix over
`transition` seconds, slow at both ends. A change that arrives halfway through starts from where the
last one had got to, so nothing jumps. `transition` is read at the moment of the change; changing it
on its own does not restart anything. `wind` is not eased: the drops change direction at once.

<!-- demo:lighting-weather -->

### Air, clouds and sky

The weather is added when the frame is drawn. It never writes to the `Atmosphere` or the `Clouds`,
so their properties keep the values you gave them.

- **Air.** Fog, rain and snow thicken the `Atmosphere` on top of its own `density`: full fog adds
  most, then snow, then rain, and the weather never closes more than nine tenths of the view the
  `Atmosphere` left open. Fog and an overcast sky raise `haze` towards 10, so the sky disappears into
  the fog. Overcast fades out the `glare` and greys `color` and `decay`, and a storm darkens them by
  up to 40 percent.
- **No `Atmosphere`.** Rain, snow or fog still turn the distance fog on, starting from the game's own
  fog colour, even when the `fog` switch in `place.toml` is off.
- **Clouds.** With a `Clouds`, the cover drawn is at least the overcast amount, so a full storm fills
  the sky even when `cover` is low. Overcast greys the clouds by up to 20 percent and a storm darkens
  them by up to 45 percent more. Without a `Clouds`, the game's own clouds are darkened the same way,
  if the `clouds` switch lets them show.
- **Sky.** The weather sets the game's own rain and thunder levels, below, and the game dims its sky
  and sunlight for those as it always does.

### Rain and snow

Each player's game draws the rain and snow around its own camera: rain within 18 blocks, snow within
16. Drops fall at about 16 blocks a second and flakes at about 2, swaying as they go, and `wind`
carries both. A full storm brings half as much rain again as full rain.

Rain and snow stop at the highest thing above them, so they do not fall indoors:

- the world's blocks count, and so does every **anchored** part that is not see-through
  (`transparency` below 0.99);
- an unanchored part, such as a car or a crate, does not shelter anything;
- a part shelters the whole box it fills along the world's axes, so a beam turned at 45 degrees
  covers a square;
- only what lies within 24 blocks sideways of the camera, from 24 blocks below it to 48 above,
  is looked at, a few times a second.

A player with a roof more than half a block above their head is indoors. Indoors the rain, the wind
and the thunder drop to about a third of their loudness, and rain seen through a window is lit like
the outside.

### Sound

| Sound | When | How loud |
|---|---|---|
| rain (`minecraft:weather.rain`) | while it rains | up to 0.55 in rain, 0.9 in a full storm |
| wind (`minecraft:item.elytra.flying`, lowered) | in snow, fog and storms | up to 0.25 |
| thunder (`minecraft:entity.lightning_bolt.thunder`) | after each strike | 1 close by, down to 0.25 far off |
| crack (`minecraft:entity.lightning_bolt.impact`) | after a strike within 48 blocks | 1, from where it struck |

Every one of these is about a third as loud for a player indoors.

The rain and the wind loop, and fade in and out over a second and a half. Thunder comes after the
flash, one second for every 343 blocks between the player and the strike, and never more than five
seconds late.

<!-- demo:lighting-thunder -->

### Lightning

A `"storm"` throws lightning on its own. At full storm a strike comes about every nine seconds on
average, and less often as the storm weakens. Each one lands on the ground 16 to 72 blocks from a
player picked at random. With no player on the server there is no one to strike near, so a storm
throws nothing until someone joins. While the editor is editing a scene, storms do not throw
lightning.

A strike flashes the sky, lights up the world and the fog for a moment with a flicker or two, and
brings the thunder above. It does nothing else: no damage, no fire, no broken blocks. What a strike
does to the game is up to your scripts, through the `struck` signal:

```lua
--!strict
-- server: lightning that blasts whatever it lands on
local weather = game.world:findFirstDescendant("Weather") :: Weather

weather.struck:connect(function(position: Vector3)
    game.world:add("Explosion", { position = position, blastRadius = 4 })
end)

weather:strike(vec3(0, 70, 0))
weather:strike()
```

| | |
|---|---|
| `weather:strike(position)` | strikes at that position |
| `weather:strike()` | strikes where a storm would: on the ground near a random player, or at 0, 0, 0 on the ground when nobody is on |
| `weather.struck` | fires with the position every time lightning strikes, on the server |

- `strike` works in any `kind`, not only in a storm. On the server the strike happens on the next
  tick.
- Called on the server, every player sees the flash and hears the thunder. `struck` fires,
  `strikePosition` is set and `strikes` counts up, whether a script asked for the strike or the storm
  threw it.
- Called on a client, the strike is for that player alone. With no position it lands on the ground
  24 to 80 blocks from the camera. `struck` does not fire and `strikes` does not change.
- `struck` only fires on the server. A client that wants to know about lightning watches `strikes`:

```lua
--!strict
-- client: hear about the server's lightning
local weather = game.world:findFirstDescendant("Weather") :: Weather?
if weather then
    local found = weather
    found:getPropertyChangedSignal("strikes"):connect(function()
        print("lightning at", found.strikePosition)
    end)
end
```

> [!NOTE]
> Strikes that land in the same server tick reach each player together, with only the position of the
> last one. Each still flashes and thunders on its own, a few frames apart, all at that last
> position, and `strikes` counts every one. `struck` fires once for each with its own position.

### The game's rain and thunder

The `Lighting` has two weather properties of its own, older than `Weather`:

| Property | Default | |
|---|---|---|
| `rain` | 0 | how hard the game's own rain falls, 0 to 1 |
| `thunder` | 0 | how dark and stormy the game's own sky is, 0 to 1 |

These set Minecraft's own rain and thunder levels, which the game uses to dim the sky and the
sunlight. Rain has to be above 0 for thunder to show. `thunder` only darkens the sky and, with a
`Weather` in the `Lighting`, makes the rain it draws heavier: it never throws lightning. A `Lighting`
in the world takes the game's weather over: its own cycle no longer starts or stops rain, so a place
that wants a storm sets one.

```lua
--!strict
-- server: a storm that blows over in a minute
local lighting = game.world:findFirstDescendant("Lighting") :: Lighting
lighting.rain = 1
lighting.thunder = 0.8
task.delay(60, function()
    lighting.rain = 0
    lighting.thunder = 0
end)
```

The rain starts for every player as soon as the property is written, because the server owns the
`Lighting` and the change replicates. A minute later `task.delay` runs the function that clears both
and the sky goes back to what the clock says it should be.

A `Weather` sets the same two levels. The game's rain level is the larger of `rain` and the
`Weather`'s rain or snow, and its thunder level the larger of `thunder` and the storm. Neither
property is written, so `lighting.rain` still reads what you set. Snow counts as rain here, so
anything in the game that checks whether it is raining sees rain in a snowfall.

With a `Weather` in the `Lighting`, the game's own rain and snow are no longer drawn and its rain
sounds and splashes stop. The `Weather` draws the rain instead, as hard as the larger of
`lighting.rain` and its own rain, and plays the rain sound to match. `lighting.rain = 1` with a
`"clear"` `Weather` still rains in full, and `thunder` makes that rain heavier the way a storm does.

The game's own lightning never strikes while a place runs, with or without a `Weather`, and whatever
`rain` and `thunder` say. The only lightning is a `Weather`'s: a storm's strikes and the ones scripts
ask for with `strike`, which flash and thunder but never set fires or hurt anyone.

## Presets

A preset sets the `Lighting`, its `Atmosphere`, its `Clouds` and its `Weather` to a finished look in
one call. There are seven:

```lua
--!strict
local lighting = game.world:add("Lighting", {}) :: Lighting
for _, name in lighting:getPresetNames() do
    print(name)
end
```

That prints `clear day`, `golden hour`, `overcast`, `night`, `foggy dawn`, `snowfall` and `storm`.

| | |
|---|---|
| `lighting:getPresetNames()` | the names of every preset, in the order above |
| `lighting:applyPreset(name, seconds)` | moves to that preset over `seconds`, or at once when `seconds` is left out or 0 |

```lua
--!strict
-- server: start on a clear afternoon, then let a storm roll in over half a minute
local lighting = game.world:add("Lighting", {}) :: Lighting
lighting:applyPreset("clear day")

task.delay(60, function()
    lighting:applyPreset("storm", 30)
end)
```

The first call adds an `Atmosphere`, a `Clouds` and a `Weather` under the `Lighting`, because it has
none, and sets everything at once. A minute later the second call eases all of it towards the storm
over 30 seconds: the clock moves on to three in the afternoon, the light dims, the air thickens and
the rain comes in.

- The name is matched without regard to case or spaces at either end. A name that is not a preset is
  an error that lists the real ones. In the editor's types `name` is one of the seven names written
  exactly as above, so a misspelt name is a type error before the script runs. A name taken from a
  loop over `getPresetNames()` reads as a plain `string` to the type checker, so a strict script
  passes it on as `name :: any`.
- `seconds` must be 0 or more.
- Any `Atmosphere`, `Clouds` or `Weather` the `Lighting` lacks is added under it, named after its
  class. With no `seconds` they appear at the preset's values at once. With `seconds`, a new
  `Atmosphere` starts clear and thickens over the blend, and a new `Clouds` starts with a `cover` of
  0 and fills in over it.
- Every preset turns `dayCycle` on and turns the `Clouds` on.
- A preset sets the time; it does not stop it. With `timeScale` above 0 the day runs on from the
  preset's hour. Presets leave `timeScale`, `geographicLatitude`, the shadow and environment
  settings, the `Lighting`'s own `rain` and `thunder`, and the `Sky` as they were.

### What each preset sets

The `Lighting`:

| Preset | `clockTime` | `brightness` | `ambient` | `outdoorAmbient` | `exposureCompensation` |
|---|---|---|---|---|---|
| `clear day` | 13 | 2 | black | 0.5, 0.5, 0.5 | 0 |
| `golden hour` | 17.6 | 2.2 | black | 0.55, 0.5, 0.45 | 0.1 |
| `overcast` | 12 | 1.2 | black | 0.42, 0.44, 0.48 | 0 |
| `night` | 22.5 | 1.5 | 0.02, 0.025, 0.05 | 0.4, 0.45, 0.6 | 0 |
| `foggy dawn` | 6.3 | 1.6 | black | 0.5, 0.5, 0.52 | 0 |
| `snowfall` | 11 | 1.6 | black | 0.5, 0.52, 0.56 | 0 |
| `storm` | 15 | 0.9 | black | 0.35, 0.37, 0.42 | -0.2 |

The `Atmosphere`:

| Preset | `density` | `offset` | `color` | `decay` | `glare` | `haze` |
|---|---|---|---|---|---|---|
| `clear day` | 0.2 | 0.3 | 0.78, 0.84, 0.95 | 0.55, 0.65, 0.8 | 1 | 1 |
| `golden hour` | 0.3 | 0.2 | 1, 0.82, 0.6 | 0.75, 0.55, 0.45 | 4 | 2 |
| `overcast` | 0.4 | 0.1 | 0.7, 0.72, 0.75 | 0.55, 0.57, 0.6 | 0 | 4 |
| `night` | 0.3 | 0.25 | 0.55, 0.6, 0.75 | 0.35, 0.4, 0.55 | 0 | 1 |
| `foggy dawn` | 0.7 | -0.2 | 0.85, 0.82, 0.85 | 0.7, 0.7, 0.75 | 2 | 8 |
| `snowfall` | 0.45 | 0.1 | 0.85, 0.88, 0.93 | 0.75, 0.8, 0.88 | 0 | 5 |
| `storm` | 0.5 | 0 | 0.45, 0.47, 0.52 | 0.3, 0.32, 0.36 | 0 | 5 |

The `Clouds` and the `Weather`:

| Preset | `cover` | `density` | `color` | `kind` | `intensity` | `wind` |
|---|---|---|---|---|---|---|
| `clear day` | 0.35 | 0.65 | white | `"clear"` | 1 | 1.5, 0, 0.8 |
| `golden hour` | 0.4 | 0.6 | 1, 0.9, 0.8 | `"clear"` | 1 | 1, 0, 0.5 |
| `overcast` | 1 | 0.9 | 0.75, 0.76, 0.8 | `"clear"` | 1 | 2.5, 0, 1.2 |
| `night` | 0.3 | 0.5 | 0.7, 0.75, 0.9 | `"clear"` | 1 | 1, 0, 0.5 |
| `foggy dawn` | 0.6 | 0.6 | 0.95, 0.9, 0.9 | `"fog"` | 0.6 | 0.5, 0, 0.3 |
| `snowfall` | 0.9 | 0.8 | 0.9, 0.92, 0.95 | `"snow"` | 0.7 | 1, 0, 0.5 |
| `storm` | 1 | 1 | 0.45, 0.46, 0.5 | `"storm"` | 0.9 | 6, 0, 3 |

Colours are red, green and blue from 0 to 1. `overcast` is a grey sky with no rain: its `Weather` is
clear, and the look comes from the clouds and the air.

### How a blend moves

Given `seconds`, the preset eases from where things are to where it wants them, slow at both ends:

- Numbers, colours and vectors on the `Lighting`, the `Atmosphere` and the `Clouds` move a little
  every tick, so halfway through the time they are halfway there.
- `clockTime` goes the short way round the dial. From `golden hour` at 17.6 to `foggy dawn` at 6.3 is
  11.3 hours backwards against 12.7 forwards, so the clock runs backwards through the day.
- A switch the preset turns on is on from the start; a switch it turns off stays on until the end.
- The `Weather` switches to the new `kind`, `intensity` and `wind` at the start, and its `transition`
  is set to `seconds` while the blend runs, so the weather's own easing takes the same time as the
  rest. When the blend ends, or a newer `applyPreset` stops it, `transition` goes back to what it was.
  With no `seconds`, `transition` is not touched, so the weather eases into the preset over its own
  `transition`.
- While a blend runs it writes its properties every tick, so a script that sets one of them in the
  meantime is overwritten until the blend ends. Calling `applyPreset` again stops the running blend
  and starts the new one from wherever things had got to.

<!-- demo:lighting-preset -->

A preset applied on the server replicates like any other change, and every player sees the same
blend. A client may apply a preset only to a `Lighting` it made itself; on the server's `Lighting` it
is an error, because a client cannot write the server's properties.

### In the editor

Select a `Lighting` and **Apply a preset** at the top of the Properties panel lists the seven. Picking
one sets everything at once, with no blend, and adds any `Atmosphere`, `Clouds` or `Weather` that is
missing. The whole change is one step in the history, so one undo takes it back. Like a script
applying a preset with no `seconds`, the editor leaves the `Weather`'s `transition` as it is, and a
`Weather` it adds keeps the default of 5.

The button only shows when a single `Lighting` from the scene is selected. Parts the engine made
rather than the scene file are left as they are.

## What a Lighting takes over

`place.toml` has a `[features]` switch for each of these, see [Places](place.md#placetoml). A
`Lighting` in the world wins over the switch, so what you set in a script decides no matter what the
file says.

| Switch | Off, with no Lighting | With a Lighting |
|---|---|---|
| `dayNightCycle` | the clock stands still | `timeScale` says how fast the day runs, 0 freezes it |
| `weather` | no rain, no thunder | `rain` and `thunder` say what the weather is, and a `Weather` adds its own |
| `sky` | no sky colour, no sun, no sunrise | a `Sky` with a face filled replaces it; without one the switch still decides |
| `clouds` | no clouds | a `Clouds` decides; without one the switch still decides, and a `Weather` only darkens what it lets through |
| `fog` | no distance fog | an `Atmosphere` turns the fog back on and shapes it; a `Weather` with rain, snow or fog turns it on too |

`stars` is its own switch, and `celestialBodiesShown` off hides the stars whatever it says.

## Explosions

An `Explosion` is a blast that pushes parts and kills bodies. It is in
[Effects](effects.md#explosions).
