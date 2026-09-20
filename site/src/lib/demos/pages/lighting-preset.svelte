<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { clamp } from "../core/pointer"

  type Rgb = [number, number, number]
  type Look = {
    clock: number
    brightness: number
    outdoor: Rgb
    density: number
    color: Rgb
    decay: Rgb
    cover: number
    clouds: Rgb
    kind: string
    intensity: number
  }

  const PRESETS: Record<string, Look> = {
    "clear day": { clock: 13, brightness: 2, outdoor: [0.5, 0.5, 0.5], density: 0.2, color: [0.78, 0.84, 0.95], decay: [0.55, 0.65, 0.8], cover: 0.35, clouds: [1, 1, 1], kind: "clear", intensity: 1 },
    "golden hour": { clock: 17.6, brightness: 2.2, outdoor: [0.55, 0.5, 0.45], density: 0.3, color: [1, 0.82, 0.6], decay: [0.75, 0.55, 0.45], cover: 0.4, clouds: [1, 0.9, 0.8], kind: "clear", intensity: 1 },
    overcast: { clock: 12, brightness: 1.2, outdoor: [0.42, 0.44, 0.48], density: 0.4, color: [0.7, 0.72, 0.75], decay: [0.55, 0.57, 0.6], cover: 1, clouds: [0.75, 0.76, 0.8], kind: "clear", intensity: 1 },
    night: { clock: 22.5, brightness: 1.5, outdoor: [0.4, 0.45, 0.6], density: 0.3, color: [0.55, 0.6, 0.75], decay: [0.35, 0.4, 0.55], cover: 0.3, clouds: [0.7, 0.75, 0.9], kind: "clear", intensity: 1 },
    "foggy dawn": { clock: 6.3, brightness: 1.6, outdoor: [0.5, 0.5, 0.52], density: 0.7, color: [0.85, 0.82, 0.85], decay: [0.7, 0.7, 0.75], cover: 0.6, clouds: [0.95, 0.9, 0.9], kind: "fog", intensity: 0.6 },
    snowfall: { clock: 11, brightness: 1.6, outdoor: [0.5, 0.52, 0.56], density: 0.45, color: [0.85, 0.88, 0.93], decay: [0.75, 0.8, 0.88], cover: 0.9, clouds: [0.9, 0.92, 0.95], kind: "snow", intensity: 0.7 },
    storm: { clock: 15, brightness: 0.9, outdoor: [0.35, 0.37, 0.42], density: 0.5, color: [0.45, 0.47, 0.52], decay: [0.3, 0.32, 0.36], cover: 1, clouds: [0.45, 0.46, 0.5], kind: "storm", intensity: 0.9 },
  }
  const NAMES = ["clear day", "golden hour", "overcast", "night", "foggy dawn", "snowfall", "storm"]

  function copyLook(look: Look): Look {
    return {
      clock: look.clock,
      brightness: look.brightness,
      outdoor: [...look.outdoor],
      density: look.density,
      color: [...look.color],
      decay: [...look.decay],
      cover: look.cover,
      clouds: [...look.clouds],
      kind: look.kind,
      intensity: look.intensity,
    }
  }

  function smoothstep(t: number) {
    return t * t * (3 - 2 * t)
  }

  function fmt(value: number, places = 2) {
    let text = value.toFixed(places)
    if (text.indexOf(".") !== -1) text = text.replace(/0+$/, "").replace(/\.$/, "")
    return text === "-0" ? "0" : text
  }

  function hours(value: number) {
    const wrapped = value % 24
    return wrapped < 0 ? wrapped + 24 : wrapped
  }

  function clockText(value: number) {
    const minutes = Math.round(hours(value) * 60) % 1440
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m
  }

  function shortest(start: number, end: number) {
    const d = hours(end - start)
    return d > 12 ? d - 24 : d
  }

  function rgb(color: Rgb) {
    return "rgb(" + color.map((v) => Math.round(clamp(v, 0, 1) * 255)).join(",") + ")"
  }

  function dialPoint(at: number, radius: number) {
    const angle = (hours(at) / 24) * Math.PI * 2 - Math.PI / 2
    return { x: 80 + Math.cos(angle) * radius, y: 80 + Math.sin(angle) * radius }
  }

  let to = $state("foggy dawn")
  let seconds = $state(8)
  let look = $state(copyLook(PRESETS["golden hour"]))
  let blend = $state<{ from: Look; to: Look; elapsed: number; span: number } | null>(null)
  let note = $state(
    "The place starts at golden hour. Pick a preset and apply it; pressing again mid-blend starts from wherever things had got to.",
  )

  let stage = $state<HTMLDivElement | null>(null)

  const ticks = Array.from({ length: 24 }, (_, h) => {
    const angle = (h / 24) * Math.PI * 2 - Math.PI / 2
    const inner = h % 6 === 0 ? 60 : 66
    return {
      h,
      x1: 80 + Math.cos(angle) * inner,
      y1: 80 + Math.sin(angle) * inner,
      x2: 80 + Math.cos(angle) * 71,
      y2: 80 + Math.sin(angle) * 71,
    }
  })

  const hand = $derived(dialPoint(look.clock, 54))

  const arc = $derived.by(() => {
    if (!blend) return ""
    const start = blend.from.clock
    const d = shortest(start, blend.to.clock)
    const a0 = (hours(start) / 24) * Math.PI * 2 - Math.PI / 2
    const a1 = a0 + (d / 24) * Math.PI * 2
    const large = Math.abs(d) > 12 ? 1 : 0
    const sweep = d > 0 ? 1 : 0
    return (
      "M" +
      (80 + Math.cos(a0) * 64).toFixed(2) +
      " " +
      (80 + Math.sin(a0) * 64).toFixed(2) +
      " A64 64 0 " +
      large +
      " " +
      sweep +
      " " +
      (80 + Math.cos(a1) * 64).toFixed(2) +
      " " +
      (80 + Math.sin(a1) * 64).toFixed(2)
    )
  })

  const source = $derived(
    `lighting:applyPreset("${to}"${seconds > 0 ? ", " + seconds : ""})`,
  )

  function apply() {
    const goal = PRESETS[to]
    const start = copyLook(look)
    look.kind = goal.kind
    look.intensity = goal.intensity
    const d = shortest(start.clock, goal.clock)
    note =
      `From ${clockText(start.clock)} to ${clockText(goal.clock)} the clock runs ` +
      `${fmt(Math.abs(d), 1)} hours ${d < 0 ? "backwards" : "forwards"}, the short way round. ` +
      `The Weather switched at the start; its own transition is set to ${seconds} seconds.`
    if (seconds <= 0) {
      look = copyLook(goal)
      blend = null
      note = "No seconds: everything is set at once, and the Weather eases in over its own transition."
      return
    }
    blend = { from: start, to: goal, elapsed: 0, span: seconds }
  }

  let last = 0

  whileVisible(
    () => stage,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      if (!blend) return
      blend.elapsed += dt
      const t = Math.min(1, blend.elapsed / blend.span)
      const e = smoothstep(t)
      const f = blend.from
      const g = blend.to
      const mix = (x: number, y: number) => x + (y - x) * e
      const mix3 = (x: Rgb, y: Rgb): Rgb => [mix(x[0], y[0]), mix(x[1], y[1]), mix(x[2], y[2])]
      look.clock = hours(f.clock + shortest(f.clock, g.clock) * e)
      look.brightness = mix(f.brightness, g.brightness)
      look.outdoor = mix3(f.outdoor, g.outdoor)
      look.density = mix(f.density, g.density)
      look.color = mix3(f.color, g.color)
      look.decay = mix3(f.decay, g.decay)
      look.cover = mix(f.cover, g.cover)
      look.clouds = mix3(f.clouds, g.clouds)
      if (t >= 1) {
        blend = null
        note = "Done. Apply another preset to blend on from here."
      }
    },
  )
</script>

<Demo label="Blending to a preset">
  <div class="lighting-preset" bind:this={stage}>
    <svg class="lighting-dial" viewBox="0 0 160 180" role="img" aria-label="clock dial">
      <circle cx="80" cy="80" r="72" style="fill:var(--bg);stroke:var(--line-2)" />
      {#each ticks as tick (tick.h)}
        <line x1={tick.x1} y1={tick.y1} x2={tick.x2} y2={tick.y2} stroke-width="1.5" style="stroke:var(--line-2)" />
      {/each}
      <text x="80" y="30" text-anchor="middle" font-size="9" style="fill:var(--text-light)">0</text>
      <text x="136" y="83" text-anchor="middle" font-size="9" style="fill:var(--text-light)">6</text>
      <text x="80" y="137" text-anchor="middle" font-size="9" style="fill:var(--text-light)">12</text>
      <text x="24" y="83" text-anchor="middle" font-size="9" style="fill:var(--text-light)">18</text>
      <path class="lighting-dial-arc" d={arc} fill="none" stroke-width="3" style="stroke:var(--text-light)" />
      <line
        class="lighting-dial-hand"
        x1="80"
        y1="80"
        x2={hand.x.toFixed(2)}
        y2={hand.y.toFixed(2)}
        stroke-width="2.5"
        stroke-linecap="round"
        style="stroke:var(--text-main)"
      />
      <circle cx="80" cy="80" r="3.5" style="fill:var(--text-main)" />
      <text
        class="lighting-dial-time"
        x="80"
        y="174"
        text-anchor="middle"
        font-size="13"
        style="fill:var(--text-main);font-family:var(--font-mono)">{clockText(look.clock)}</text
      >
    </svg>

    <div class="lighting-readout">
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">brightness</span>
        <span class="lighting-mini">
          <span class="lighting-mini-fill" style="width: {Math.min(100, (look.brightness / 3) * 100)}%"></span>
        </span>
        <span class="lighting-readout-value">{fmt(look.brightness)}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">outdoorAmbient</span>
        <span class="lighting-swatch" style="background: {rgb(look.outdoor)}"></span>
        <span class="lighting-readout-value">{look.outdoor.map((v) => fmt(v)).join(", ")}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">Atmosphere density</span>
        <span class="lighting-mini">
          <span class="lighting-mini-fill" style="width: {look.density * 100}%"></span>
        </span>
        <span class="lighting-readout-value">{fmt(look.density)}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">Atmosphere color</span>
        <span class="lighting-swatch" style="background: {rgb(look.color)}"></span>
        <span class="lighting-readout-value">{look.color.map((v) => fmt(v)).join(", ")}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">Atmosphere decay</span>
        <span class="lighting-swatch" style="background: {rgb(look.decay)}"></span>
        <span class="lighting-readout-value">{look.decay.map((v) => fmt(v)).join(", ")}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">Clouds cover</span>
        <span class="lighting-mini">
          <span class="lighting-mini-fill" style="width: {look.cover * 100}%"></span>
        </span>
        <span class="lighting-readout-value">{fmt(look.cover)}</span>
      </div>
      <div class="lighting-readout-row">
        <span class="lighting-readout-name">Weather</span>
        <span></span>
        <span class="lighting-readout-value">"{look.kind}" at {fmt(look.intensity)}</span>
      </div>
    </div>
  </div>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">preset</span>
      <select class="demo-select" bind:value={to}>
        {#each NAMES as name (name)}
          <option value={name}>{name}</option>
        {/each}
      </select>
    </div>
    <Slider label="seconds" min={0} max={20} step={1} bind:value={seconds} />
    <div class="demo-controls demo-row">
      <button type="button" class="demo-pill demo-pill-wide" onclick={apply}>applyPreset</button>
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
