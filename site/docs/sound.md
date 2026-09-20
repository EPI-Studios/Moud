# Sound

There are two ways to make noise in a place. A `Sound` is an instance you put in the tree, which
means the server can create one and every player hears it. The `audio` global is a set of calls that
exist on the client only, for sound that belongs to one player's own game: their music, their
footsteps, the click of their menu.

Start with `Sound` instances. Reach for `audio` when you want layered music, a tempo to line effects
up with, or a mix you move around during play.

## Sound instances

You create a `Sound` with `add`, like any other instance, and play it with `play()`:

```lua
local bell = deck:add("Sound", {
    soundId = "res://sounds/bell.ogg",   -- or a resource pack sound like minecraft:block.note_block.bell
    volume = 0.8, pitch = 1, looped = false,
    minDistance = 8, maxDistance = 48, rollOff = 1,
    bus = "sfx", priority = 0, fadeIn = 0, stream = false,
})
bell:play()
bell:stop()
bell.played:connect(function() end)
bell.ended:connect(function() end)
```

- `soundId` is the file to play. It takes a `res://` path in your place, or the name of a sound the
  resource pack already has.
- `minDistance`, `maxDistance` and `rollOff` decide how the sound fades with distance when it is
  playing from somewhere in the world.
- `bus` puts it on a named bus, which is how you turn all the sound effects down at once without
  touching the music. See [Buses](#buses).
- `fadeIn` is how many seconds the sound takes to come up to `volume`, and `stream = true` reads the
  file off disk as it plays instead of decoding it first.
- `played` fires when the sound starts and `ended` when it finishes.

Where you parent the sound is what decides where it is heard from. `deck` above is whatever instance
you put it in:

- Inside a part or a body it plays **from there** and follows it. Anywhere else it plays for
  everyone at the same volume.
- `event` plays a named event (see `audio.defineEvent`) instead of `soundId`.
- `play()` on a sound that is already playing restarts it on every client.
- Sounds replicate like any instance, so the server can play one for everybody.

> [!WARNING]
> A sound kept out of the world never plays: one in `ServerStorage`, `ReplicatedStorage`, a starter
> folder or a backpack is only stored, and `play()` on it does nothing until a copy is in the world.
> See [Containers](containers.md).

## Pausing, seeking and how far along it is

`pause()` and `resume()` leave the sound where it is, and `timePosition` is both where it has got to
and where you send it:

```lua
bell:pause()
bell:resume()
bell.timePosition = 2.5        -- seek
bell.loaded:connect(function() print(bell.timeLength, "seconds") end)
```

| Property | |
|---|---|
| `timePosition` | where the sound is, in seconds. Write it to seek |
| `timeLength` | how long the file is, in seconds, once it is loaded. Read-only |
| `isLoaded` | whether the file is decoded and ready to play. Read-only |
| `playbackLoudness` | how loud it is coming out right now, 0 to 1000. Read-only |
| `paused` | true between `pause()` and `resume()`, false again after `stop()` |

- `pause()` keeps the sound where it is; `resume()` carries on from there. `stop()` unpauses it and
  forgets the position, so the next `play()` starts at the beginning.
- `loaded` fires on each client once the file is decoded, which is when `timeLength` is known.
  Changing `soundId` clears both and fires it again for the new file.
- `pitch` moves the sound's speed and its pitch together.

`playbackLoudness` is how you make something react to the sound the player is hearing, such as a
light that pulses with the music. Read it on the client, once a frame.

> [!NOTE]
> The three read-only properties and the real `timePosition` are worked out by the client that plays
> the sound. On the server `timePosition` is only what a script last wrote there, `timeLength` and
> `playbackLoudness` are 0 and `isLoaded` is false, because no sound comes out of a server.

## Buses

A `SoundBus` is an instance that owns a named bus. Every `Sound` whose `bus` matches the name goes
through it, so one bus turns a whole group of sounds down at once:

```lua
world:add("SoundBus", { bus = "music", volume = 0.5, lowPass = 1, muted = false })
```

`volume` scales every sound on the bus and `muted` silences them all. `lowPass` below 1 muffles the
whole bus, which is what you use for sound heard through a wall or under water.

## Sound effects

An effect is an instance you put **inside** a `Sound` or a `SoundBus`. Inside a sound it treats that
one sound; inside a bus it treats every sound on that bus.

```lua
local horn = deck:add("Sound", { soundId = "res://sounds/horn.ogg" })
horn:add("ReverbSoundEffect", { decayTime = 4, wetLevel = -3 })
horn:add("EchoSoundEffect", { delay = 0.25, feedback = 0.4, wetLevel = -6 })
horn:play()
```

The horn now plays through a four second reverb tail and a quarter second echo, because both effects
are children of that one sound. No other sound in the place is touched.

Every effect has `enabled` (default true) and `priority` (default 0).

| Class | Property | Default | Range | |
|---|---|---|---|---|
| `ReverbSoundEffect` | `decayTime` | 1.5 | 0.1 to 20 | how long the tail lasts, in seconds |
| | `density` | 1 | 0 to 1 | how thick the tail is |
| | `diffusion` | 1 | 0 to 1 | how smeared the reflections are |
| | `dryLevel` | 0 | -80 to 10 | the untreated sound, in decibels |
| | `wetLevel` | -6 | -80 to 10 | the treated sound, in decibels |
| `EqualizerSoundEffect` | `lowGain` | 0 | -80 to 10 | decibels below `midLow` |
| | `midGain` | 0 | -80 to 10 | decibels between the two |
| | `highGain` | 0 | -80 to 10 | decibels above `midHigh` |
| | `midLow` | 500 | 20 to 20000 | where the middle band starts, in hertz |
| | `midHigh` | 3000 | 20 to 20000 | where it ends |
| `DistortionSoundEffect` | `level` | 0.5 | 0 to 1 | how hard the signal is driven |
| `EchoSoundEffect` | `delay` | 1 | 0.01 to 5 | seconds between repeats |
| | `feedback` | 0.5 | 0 to 1 | how much of a repeat comes back round |
| | `dryLevel` | 0 | -80 to 10 | decibels |
| | `wetLevel` | 0 | -80 to 10 | decibels |
| `PitchShiftSoundEffect` | `octave` | 1 | 0.5 to 2 | 2 is an octave up, 0.5 an octave down |
| `CompressorSoundEffect` | `threshold` | -20 | -80 to 0 | decibels above which it pushes down |
| | `ratio` | 5 | 1 to 50 | how hard it pushes |
| | `attack` | 0.1 | 0 to 1 | seconds to start |
| | `release` | 0.1 | 0 to 5 | seconds to let go |
| | `makeupGain` | 0 | -80 to 10 | decibels put back afterwards |
| `ChorusSoundEffect` | `depth` | 0.25 | 0 to 1 | how far the copy drifts |
| | `mix` | 0.5 | 0 to 1 | how much of it you hear |
| | `rate` | 5 | 0 to 20 | hertz |
| `FlangeSoundEffect` | `depth` | 0.25 | 0 to 1 | |
| | `mix` | 0.5 | 0 to 1 | |
| | `rate` | 5 | 0 to 20 | hertz |
| `TremoloSoundEffect` | `depth` | 1 | 0 to 1 | how far the volume swings |
| | `duty` | 0.5 | 0.01 to 0.99 | how much of each swing is loud |
| | `frequency` | 5 | 0.01 to 20 | hertz |

In the editor, **Explorer > Insert > Sound effects** lists all nine.

### The order they run in

Effects run sorted by `priority`, lowest first, and two with the same priority keep the order they
sit in as children. A sound's own effects run first, then its bus's.

```lua
local bus = world:add("SoundBus", { bus = "sfx" })
bus:add("CompressorSoundEffect", { threshold = -18 })       -- after every sound's own

horn:add("DistortionSoundEffect", { level = 0.3, priority = 1 })
horn:add("EqualizerSoundEffect", { highGain = -12, priority = 0 })   -- this one first
```

The equaliser has the lower priority, so the horn is equalised, then distorted, and only then hits
the compressor on the bus, even though the compressor was written first.

<!-- demo:sound-chain -->

A bus effect runs **per sound**, not once over the mixed bus: ten sounds on a reverberating bus are
ten reverbs. Put an effect on a bus to say "everything here sounds like this", not to save work.

### Limits

- `ReverbSoundEffect` is a Freeverb-style approximation, not a model of a room. Its numbers are
  tuned by ear.
- `PitchShiftSoundEffect` is granular, so it goes grainy when pushed towards the ends of its range.
  A whole octave on a long sustained sound is where you hear it.

Adding the **first** effect to a playing sound, or removing the **last** one, rebuilds the voice: it
stops and starts again at the same position, and you hear one frame's gap. Adding a second effect, or
changing a property, does not. Set the effects up before `play()` when it matters.

> [!IMPORTANT]
> Effects are ignored when the sound has `stream = true`. A streamed sound is read off disk as it
> plays and never passes through the chain.

## The audio global (clients)

`audio` is a global that exists in client scripts only. It plays sound for the player whose game the
script is running in, and nobody else hears any of it.

```lua
local voice = audio.play("res://sounds/click.ogg", { volume = 1, pitch = 1.2, at = part.position })
voice:setVolume(0.5)  voice:setPitch(0.9)  voice:fade(0, 1)  voice:fadeOut(0.5)
voice:setPosition(vec3(0, 65, 0))  voice:isPlaying()  voice:stop()
```

- `audio.play` starts the file straight away and hands you back a voice, which is the handle to the
  one sound now playing.
- `at` places it at a point in the world, and `setPosition` moves it from there while it plays.
- `setVolume` and `setPitch` change it as it goes, and `fade` and `fadeOut` move the volume over
  time instead of jumping.
- `isPlaying` tells you whether it is still running, and `stop` ends it.

### Events

An event is a name you give to a group of sounds, so a script asks for "footstep" and the engine
picks a different variation each time:

```lua
audio.defineEvent("footstep", { sounds = { "res://sounds/step1.ogg", "res://sounds/step2.ogg" },
    bus = "sfx", volume = { 0.8, 1 }, pitch = { 0.9, 1.1 } })
audio.playEvent("footstep", body.position)
```

`volume` and `pitch` are given as two numbers rather than one: each play picks a value between them,
so the same two files stop sounding like the same two files. `playEvent` takes the position to play
from.

A `Sound` instance can use the same events: set its `event` property to the name instead of setting
`soundId`.

### Tempo and stingers

The tempo clock lets you line sounds up with the beat of your music:

```lua
audio.tempo(100, 4)                    -- bpm, beats per bar
audio.beat:connect(function(n) end)
audio.bar:connect(function(n) end)
audio.stinger("res://sounds/hit.ogg", "bar")   -- starts on the next beat or bar
audio.stopTempo()
```

- `audio.tempo` starts the clock at a hundred beats a minute, four beats to the bar, and
  `audio.stopTempo` ends it.
- `beat` and `bar` fire with the number of the beat or bar, which is what you use to flash a light
  or move something in time.
- `audio.stinger` holds a sound back until the next beat or bar, so a hit lands on the music instead
  of across it.

<!-- demo:sound-tempo -->

### Parameters, switches and LFOs

These three drive the mix from your game state, instead of you setting volumes by hand:

```lua
audio.setParameter("danger", 0.7)
audio.bindBusVolume("danger", "music", { { 0, 0.3 }, { 1, 1 } })
audio.setSwitch("surface", "grass")
audio.lfo("wobble", "sine", 0.5, 0, 1)       -- sine, triangle, saw, square
```

- A parameter is a named number you set from your own code. `danger` here is at 0.7.
- `audio.bindBusVolume` ties that parameter to a bus volume through a list of points: `danger` at 0
  leaves the music bus at 0.3, `danger` at 1 puts it at 1, and values between move between them. Set
  the parameter and the music comes up on its own.
- A switch is a named choice rather than a number, such as which surface the player is walking on.
- `audio.lfo` makes a named value that moves on its own, using one of sine, triangle, saw or square.

<!-- demo:sound-bind -->

### Mix

These calls act on the whole mix at once:

```lua
audio.snapshot({ music = 0.2, sfx = 1 }, 0.5)
audio.clearSnapshot(0.5)
audio.sidechain("voice", "music", 0.6)
audio.reverb(2.5, 0.3)
audio.hrtf(true)
audio.occlusion(true)        -- blocks and solid parts muffle sounds behind them
```

- `audio.snapshot` moves the buses you name to those volumes over half a second, and
  `audio.clearSnapshot` takes the same time to put them back. Use it for a pause menu that ducks the
  game and a menu screen that does not.
- `audio.sidechain` gets one bus out of another's way: sound on `voice` pushes `music` down by 0.6.
- `audio.reverb` puts a reverb over everything, `audio.hrtf` places sounds for headphones, and
  `audio.occlusion` lets blocks and solid parts muffle what is behind them.

### Layered music

Layered music is one piece written as several files that play together, where the mix of layers says
what is happening in the game:

```lua
local music = audio.music({ layers = { "res://music/drums.ogg", "res://music/bass.ogg" },
    states = { calm = { 1, 0 }, fight = { 1, 1 } }, bus = "music" })
music:transitionTo("fight", 2, "bar")
music:setLayer(2, 0.5)
music:blend(0.8)
music:stop(1)
```

- Each state is a volume for each layer in order. `calm` plays the drums alone, `fight` brings the
  bass in with them.
- `transitionTo` moves to a state over two seconds and waits for the next bar before it starts, so
  the change lands on the music.
- `setLayer` sets one layer's volume by its number, and `blend` moves the mix by hand when you want
  something no state covers.
- `stop(1)` takes a second to fade the whole thing out.

<!-- demo:sound-music -->

### Voice chat filters

Proximity voice chat runs through filters you set the same way:

```lua
audio.voiceChat({ enabled = true, strength = 1, filters = { { kind = "lowpass", frequency = 3000 } } })
```

Each filter has a `kind` and its own settings. The `lowpass` above cuts everything over 3000 hertz,
which is what makes a voice sound like it is coming through a radio.

Each time the client place loads, including hot reloads, the mix is reset to silence. Anything you
set with `audio` has to be set again by the script that runs after the reload, which is why these
calls usually sit at the top of a client script rather than behind a one-off condition.
