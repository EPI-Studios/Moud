<script lang="ts">
  import { onMount, untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { clamp } from "../core/pointer"
  import { whileVisible } from "../core/frames"

  type StateName = "calm" | "fight"
  type Move = { name: StateName; from: number[]; to: number[]; begin: number; seconds: number; quantize: "bar" | "beat" }

  const STATES: Record<StateName, number[]> = { calm: [1, 0], fight: [0, 1] }
  const NAMES: StateName[] = ["calm", "fight"]
  const LAYERS = ["calm.ogg", "intense.ogg"]
  const BEAT = 60 / 160
  const BAR = BEAT * 4
  const FIRST = 0.33
  const LOOP = 84

  let seconds = $state(2)
  let quantize = $state<"bar" | "beat">("bar")
  let levels = $state([1, 0])
  let current = $state<StateName>("calm")
  let shown = $state<StateName>("calm")
  let playing = $state(false)
  let status = $state("In calm: the calm layer alone.")
  let fill = $state(0)

  let host = $state<HTMLDivElement | null>(null)
  let calm = $state<HTMLAudioElement | null>(null)
  let intense = $state<HTMLAudioElement | null>(null)

  let clock = 0
  let base = 0
  let lastTime = 0
  let last = 0
  let move: Move | null = null

  const source = $derived(
    "-- client\naudio.tempo(160, 4)\n" +
      'local music = audio.music({ layers = { "res://music/calm.ogg", "res://music/intense.ogg" },\n' +
      '    states = { calm = { 1, 0 }, fight = { 0, 1 } }, bus = "music" })\n' +
      `music:transitionTo("${shown}", ${seconds}, "${quantize}")`,
  )

  function tracks() {
    return calm && intense ? [calm, intense] : []
  }

  function mix() {
    const list = tracks()
    for (let i = 0; i < list.length; i++) list[i].volume = clamp(levels[i], 0, 1)
  }

  function transitionTo(name: StateName) {
    const step = quantize === "bar" ? BAR : BEAT
    move = {
      name,
      from: [...levels],
      to: STATES[name],
      begin: FIRST + Math.ceil((clock - FIRST + 0.0001) / step) * step,
      seconds,
      quantize,
    }
    shown = name
  }

  function start() {
    playing = true
    base = Math.floor(clock / LOOP) * LOOP
    lastTime = clock - base
    mix()
    for (const audio of tracks()) {
      if (audio.preload === "none") {
        audio.preload = "auto"
        audio.load()
      }
      try {
        audio.currentTime = lastTime
      } catch {
        // seeking a track that has not loaded yet throws
      }
      audio.play().catch(() => stop())
    }
  }

  function stop() {
    if (!playing) return
    playing = false
    for (const audio of tracks()) audio.pause()
  }

  function frame(dt: number) {
    const lead = calm
    if (playing && lead && !lead.paused && lead.readyState >= 2) {
      const time = lead.currentTime
      if (time < lastTime - LOOP / 2) base += LOOP
      lastTime = time
      clock = base + time
      if (intense && !intense.seeking && Math.abs(intense.currentTime - time) > 0.08) {
        intense.currentTime = time
      }
    } else if (!playing) {
      clock += dt
    }

    const going = move
    if (going) {
      if (clock < going.begin) {
        status = `Waiting ${(going.begin - clock).toFixed(2)} s for the next ${going.quantize}.`
      } else {
        const t = going.seconds <= 0 ? 1 : Math.min(1, (clock - going.begin) / going.seconds)
        levels = going.from.map((from, i) => from + (going.to[i] - from) * t)
        status = `Moving to ${going.name}, ${Math.round(t * 100)}% of the way.`
        if (t >= 1) {
          current = going.name
          move = null
          status =
            current === "calm"
              ? "In calm: the calm layer alone."
              : "In fight: the intense layer alone."
        }
      }
    }

    mix()
    const step = quantize === "bar" ? BAR : BEAT
    const into = (((clock - FIRST) % step) + step) % step
    fill = (into / step) * 100
  }

  $effect(() => {
    void seconds
    void quantize
    shown = untrack(() => current)
  })

  $effect(() => {
    if (!host || typeof IntersectionObserver === "undefined") return
    const watcher = new IntersectionObserver((entries) => {
      if (!entries[entries.length - 1].isIntersecting) stop()
    })
    watcher.observe(host)
    return () => watcher.disconnect()
  })

  whileVisible(
    () => host,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      frame(dt)
    },
  )

  onMount(() => frame(0))
</script>

<Demo label="Layered music">
  <audio bind:this={calm} preload="none" loop>
    <source src="/art/audio/music-calm.ogg" type="audio/ogg" />
    <source src="/art/audio/music-calm.m4a" type="audio/mp4" />
  </audio>
  <audio bind:this={intense} preload="none" loop>
    <source src="/art/audio/music-intense.ogg" type="audio/ogg" />
    <source src="/art/audio/music-intense.m4a" type="audio/mp4" />
  </audio>

  <div class="sound-layers" bind:this={host}>
    {#each LAYERS as name, i (name)}
      <div class="sound-layer">
        <span class="sound-layer-name">{name}</span>
        <div class="sound-meter sound-meter-small">
          <div class="sound-meter-fill" style="width: {(levels[i] * 100).toFixed(1)}%"></div>
        </div>
        <span class="sound-layer-value">{levels[i].toFixed(2)}</span>
      </div>
    {/each}
  </div>

  <div class="sound-barline">
    <div class="sound-barline-fill" style="width: {fill.toFixed(1)}%"></div>
  </div>

  <p class="demo-note sound-status">{status}</p>

  <Note>
    Press Play, then pick a state. The two files are layers of one piece, so fight crossfades from
    the calm layer to the intense one. The demo assumes 160 BPM in 4/4, measured from the track, so
    a bar is 1.5 seconds and a beat 0.375; the line under the meters fills once per bar or beat.
  </Note>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      aria-pressed={playing}
      onclick={() => (playing ? stop() : start())}
    >
      {playing ? "Stop" : "Play"}
    </button>
    {#each NAMES as name (name)}
      <button type="button" class="demo-pill demo-pill-wide" onclick={() => transitionTo(name)}>
        transitionTo("{name}")
      </button>
    {/each}
  </div>

  <div class="demo-controls">
    <Slider label="seconds" min={0} max={4} step={0.5} bind:value={seconds} />
    <Choice label="quantize" options={["bar", "beat"] as const} bind:value={quantize} />
  </div>

  <CodePanel {source} />

  <p class="demo-note sound-credit">Music: ULTRAKILL soundtrack, Heaven Pierce Her</p>
</Demo>
