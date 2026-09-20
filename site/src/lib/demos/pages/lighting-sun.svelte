<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { fit, palette } from "../core/surface"

  type Rgb = [number, number, number]

  const KEYS = [-0.35, -0.12, 0, 0.12, 0.35]
  const DUSK: Rgb[] = [
    [0.45, 0.55, 0.95],
    [0.7, 0.55, 0.8],
    [1, 0.62, 0.45],
    [1, 0.85, 0.68],
    [1, 1, 1],
  ]
  const DAWN: Rgb[] = [
    [0.45, 0.55, 0.95],
    [0.65, 0.6, 0.85],
    [1, 0.7, 0.62],
    [1, 0.9, 0.8],
    [1, 1, 1],
  ]

  const BANDS = [
    { from: -90, to: -20, label: "night" },
    { from: -20, to: -7, label: "twilight" },
    { from: -7, to: 20, label: "horizon" },
    { from: 20, to: 90, label: "no tint" },
  ]

  let clock = $state(20.5)
  let latitude = $state(41.733)
  let dayCycle = $state(true)

  let graph = $state<HTMLCanvasElement | null>(null)
  let strip = $state<HTMLCanvasElement | null>(null)
  let held = false

  const layout = { left: 34, right: 0, top: 10, bottom: 0 }

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

  function sunDirection(at: number, tiltDegrees: number) {
    const hourAngle = ((hours(at) - 12) / 24) * 2 * Math.PI
    const tilt = (clamp(tiltDegrees, -90, 90) * Math.PI) / 180
    const east = -Math.sin(hourAngle)
    const up = Math.cos(tilt) * Math.cos(hourAngle)
    const south = Math.sin(tilt) * Math.cos(hourAngle)
    const len = Math.sqrt(east * east + up * up + south * south) || 1
    return { x: east / len, y: up / len, z: south / len }
  }

  function tintAt(at: number, tiltDegrees: number): Rgb {
    const y = sunDirection(at, tiltDegrees).y
    const colors = hours(at) < 12 ? DAWN : DUSK
    if (y <= KEYS[0]) return colors[0]
    if (y >= KEYS[4]) return colors[4]
    let i = 0
    while (y > KEYS[i + 1]) i++
    const t = smoothstep((y - KEYS[i]) / (KEYS[i + 1] - KEYS[i]))
    return [0, 1, 2].map((c) => colors[i][c] + (colors[i + 1][c] - colors[i][c]) * t) as Rgb
  }

  function rgb(color: Rgb) {
    return "rgb(" + color.map((v) => Math.round(clamp(v, 0, 1) * 255)).join(",") + ")"
  }

  function elevation(at: number) {
    return (Math.asin(clamp(sunDirection(at, latitude).y, -1, 1)) * 180) / Math.PI
  }

  const here = $derived(elevation(clock))
  const peak = $derived((Math.asin(Math.cos((latitude * Math.PI) / 180)) * 180) / Math.PI)

  const note = $derived(
    `The sun is ${fmt(Math.abs(here), 1)} degrees ${here >= 0 ? "above" : "below"} the horizon and ` +
      `climbs to ${fmt(peak, 1)} degrees at noon. The strip is the tint dayCycle multiplies ` +
      "outdoorAmbient by over the whole day" +
      (dayCycle ? "." : ", which is none with dayCycle off."),
  )

  const source = $derived.by(() => {
    const dir = sunDirection(clock, latitude)
    const tint: Rgb = dayCycle ? tintAt(clock, latitude) : [1, 1, 1]
    return (
      `lighting.clockTime = ${fmt(clock, 2)}\n` +
      `lighting.geographicLatitude = ${fmt(latitude, 3)}\n` +
      `lighting.dayCycle = ${dayCycle}\n\n` +
      `print(lighting:getMinutesAfterMidnight())   -- ${fmt(hours(clock) * 60, 1)}\n` +
      `print(lighting:getSunDirection())           -- ${fmt(dir.x)}, ${fmt(dir.y)}, ${fmt(dir.z)}\n` +
      `-- outdoorAmbient is drawn times ${tint.map((v) => fmt(v)).join(", ")}`
    )
  })

  function draw() {
    if (!graph || !strip) return
    const c = palette()
    const g = fit(graph, graph.clientHeight)
    const s = fit(strip, strip.clientHeight)
    if (!g || !s) return
    const ctx = g.ctx
    layout.right = g.w - 10
    layout.bottom = g.h - 22
    const px = (h: number) => layout.left + (h / 24) * (layout.right - layout.left)
    const py = (e: number) => layout.top + ((90 - e) / 180) * (layout.bottom - layout.top)

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, g.w, g.h)
    BANDS.forEach((band, index) => {
      ctx.fillStyle = index % 2 ? "rgba(255,255,255,0.035)" : "rgba(255,255,255,0)"
      ctx.fillRect(layout.left, py(band.to), layout.right - layout.left, py(band.from) - py(band.to))
      ctx.font = "10px " + c.mono
      ctx.fillStyle = c.light
      ctx.textAlign = "right"
      if (dayCycle && g.w >= 460) ctx.fillText(band.label, layout.right - 6, py(band.to) + 14)
    })
    ctx.font = "11px " + c.mono
    ctx.textAlign = "right"
    for (const e of [-90, -20, -7, 0, 20, 90]) {
      ctx.beginPath()
      ctx.moveTo(layout.left, Math.round(py(e)) + 0.5)
      ctx.lineTo(layout.right, Math.round(py(e)) + 0.5)
      ctx.strokeStyle = e === 0 ? c.muted : c.line2
      ctx.setLineDash(e === 0 || Math.abs(e) === 90 ? [] : [3, 4])
      ctx.lineWidth = 1
      ctx.stroke()
      ctx.setLineDash([])
      ctx.fillStyle = c.light
      if (e !== -7) ctx.fillText(String(e), layout.left - 5, py(e) + 4)
    }
    ctx.textAlign = "center"
    for (let h = 0; h <= 24; h += 3) {
      ctx.fillStyle = c.light
      ctx.fillText(String(h), px(h), layout.bottom + 15)
    }
    ctx.beginPath()
    for (let i = 0; i <= 240; i++) {
      const hh = (i / 240) * 24
      if (i === 0) ctx.moveTo(px(hh), py(elevation(hh)))
      else ctx.lineTo(px(hh), py(elevation(hh)))
    }
    ctx.strokeStyle = c.main
    ctx.lineWidth = 2
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(Math.round(px(clock)) + 0.5, layout.top)
    ctx.lineTo(Math.round(px(clock)) + 0.5, layout.bottom)
    ctx.strokeStyle = c.muted
    ctx.lineWidth = 1
    ctx.stroke()
    ctx.beginPath()
    ctx.arc(px(clock), py(here), 6, 0, Math.PI * 2)
    ctx.fillStyle = c.yellow
    ctx.fill()

    const sl = (layout.left / g.w) * s.w
    const sr = (layout.right / g.w) * s.w
    for (let x = 0; x < s.w; x++) {
      if (x < sl || x > sr) {
        s.ctx.fillStyle = c.bg
      } else {
        const at = ((x - sl) / (sr - sl)) * 24
        s.ctx.fillStyle = dayCycle ? rgb(tintAt(at, latitude)) : "rgb(255,255,255)"
      }
      s.ctx.fillRect(x, 0, 1, s.h)
    }
  }

  function scrub(event: PointerEvent) {
    if (!graph) return
    const p = pointIn(graph, event, graph.clientWidth, graph.clientHeight)
    const hour = ((p.x - layout.left) / (layout.right - layout.left)) * 24
    clock = Math.round(clamp(hour, 0, 23.9) * 10) / 10
  }

  $effect(() => {
    draw()
  })

  $effect(() => {
    const node = graph
    if (!node) return
    let last = 0
    const watcher = new ResizeObserver(() => {
      if (node.clientWidth === last) return
      last = node.clientWidth
      draw()
    })
    watcher.observe(node)
    return () => watcher.disconnect()
  })
</script>

<Demo label="The sun's path">
  <div class="lighting-wide">
    <canvas
      bind:this={graph}
      class="lighting-canvas lighting-drag lighting-tall"
      style="aspect-ratio: 1 / 0.5"
      aria-label="the sun's height through the day"
      onpointerdown={(event) => {
        held = true
        graph?.setPointerCapture(event.pointerId)
        scrub(event)
      }}
      onpointermove={(event) => held && scrub(event)}
      onpointerup={() => (held = false)}
      onpointercancel={() => (held = false)}
    ></canvas>
    <canvas
      bind:this={strip}
      class="lighting-canvas lighting-strip"
      style="aspect-ratio: 1 / 0.05"
      aria-label="the tint over the whole day"
    ></canvas>
  </div>

  <div class="demo-controls">
    <Slider label="clockTime" min={0} max={23.9} step={0.1} bind:value={clock} />
    <Slider label="geographicLatitude" min={-90} max={90} step={1} bind:value={latitude} />
    <Choice label="dayCycle" options={[true, false]} bind:value={dayCycle} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
