<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"

  type Curve = "smooth" | "linear" | "inverseSquare" | "exponent"

  const CURVES: Curve[] = ["smooth", "linear", "inverseSquare", "exponent"]
  const MAX = 32

  let curve = $state<Curve>("smooth")
  let range = $state(12)
  let exponent = $state(2)

  let graph = $state<HTMLCanvasElement | null>(null)
  let strip = $state<HTMLCanvasElement | null>(null)

  function smoothstep(t: number) {
    return t * t * (3 - 2 * t)
  }

  function falloff(dist: number, reach: number, kind: Curve, power: number) {
    if (dist > reach) return 0
    const t = Math.max(0, Math.min(1, dist / Math.max(reach, 1e-4)))
    if (kind === "smooth") return 1 - smoothstep(t)
    if (kind === "linear") return 1 - t
    if (kind === "inverseSquare") {
      const d2 = dist * dist
      const win = Math.max(0, Math.min(1, 1 - (d2 * d2) / (reach * reach * reach * reach)))
      return (win * win) / (d2 + 1)
    }
    return Math.pow(1 - t, power)
  }

  function fmt(value: number, places = 1) {
    let text = value.toFixed(places)
    if (text.indexOf(".") !== -1) text = text.replace(/0+$/, "").replace(/\.$/, "")
    return text === "-0" ? "0" : text
  }

  const half = $derived(range / 2)

  const note = $derived(
    `Halfway to range, ${fmt(half)} metres out, ${curve} leaves ` +
      `${Math.round(falloff(half, range, curve, exponent) * 100)} percent of the light. ` +
      "The strip is the floor lit from the left.",
  )

  const source = $derived.by(() => {
    let props =
      "    color = color(1, 0.8, 0.5),\n    brightness = 2,\n    range = " +
      range +
      ',\n    falloff = "' +
      curve +
      '",'
    if (curve === "exponent") props += "\n    falloffExponent = " + exponent + ","
    return 'world:add("PointLight", {\n' + props + "\n})"
  })

  function draw() {
    if (!graph || !strip) return
    const c = palette()
    const g = fit(graph, graph.clientHeight)
    const s = fit(strip, strip.clientHeight)
    if (!g || !s) return
    const ctx = g.ctx
    const left = 34
    const right = g.w - 12
    const top = 14
    const bottom = g.h - 26
    const px = (d: number) => left + (d / MAX) * (right - left)
    const py = (v: number) => bottom - v * (bottom - top)

    const plot = (kind: Curve, color: string, width: number) => {
      ctx.beginPath()
      for (let i = 0; i <= 240; i++) {
        const dist = (i / 240) * MAX
        const v = falloff(dist, range, kind, exponent)
        if (i === 0) ctx.moveTo(px(dist), py(v))
        else ctx.lineTo(px(dist), py(v))
      }
      ctx.strokeStyle = color
      ctx.lineWidth = width
      ctx.stroke()
    }

    ctx.clearRect(0, 0, g.w, g.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, g.w, g.h)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1
    ctx.font = "11px " + c.mono
    ctx.fillStyle = c.light
    ctx.textAlign = "center"
    for (let d = 0; d <= MAX; d += 4) {
      ctx.beginPath()
      ctx.moveTo(px(d) + 0.5, top)
      ctx.lineTo(px(d) + 0.5, bottom)
      ctx.strokeStyle = c.line
      ctx.stroke()
      ctx.fillText(String(d), px(d), bottom + 15)
    }
    ctx.textAlign = "right"
    for (const v of [0, 0.5, 1]) {
      ctx.beginPath()
      ctx.moveTo(left, py(v) + 0.5)
      ctx.lineTo(right, py(v) + 0.5)
      ctx.strokeStyle = c.line
      ctx.stroke()
      ctx.fillText(String(v), left - 6, py(v) + 4)
    }
    ctx.strokeStyle = c.muted
    ctx.setLineDash([4, 4])
    ctx.beginPath()
    ctx.moveTo(px(range) + 0.5, top)
    ctx.lineTo(px(range) + 0.5, bottom)
    ctx.stroke()
    ctx.setLineDash([])
    ctx.textAlign = "left"
    ctx.fillStyle = c.muted
    ctx.fillText("range", Math.min(px(range) + 5, right - 40), top + 10)

    for (const other of CURVES) {
      if (other !== curve) plot(other, c.line2, 1.25)
    }
    plot(curve, c.main, 2)

    const sctx = s.ctx
    sctx.fillStyle = "#000"
    sctx.fillRect(0, 0, s.w, s.h)
    const sl = (left / g.w) * s.w
    const sr = (right / g.w) * s.w
    for (let x = Math.floor(sl); x < Math.ceil(sr); x++) {
      const dist = ((x - sl) / (sr - sl)) * MAX
      const v = falloff(dist, range, curve, exponent)
      const r = Math.round(255 * v)
      const gg = Math.round(222 * v)
      const b = Math.round(170 * v)
      sctx.fillStyle = `rgb(${r},${gg},${b})`
      sctx.fillRect(x, 0, 1, s.h)
    }
  }

  let lastExponent = untrack(() => exponent)

  $effect(() => {
    if (exponent === lastExponent) return
    lastExponent = exponent
    curve = "exponent"
  })

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

<Demo label="Falloff">
  <div class="rendering-wide">
    <canvas
      bind:this={graph}
      class="rendering-canvas rendering-graph"
      style="aspect-ratio: 1 / 0.4"
      aria-label="light left against distance"
    ></canvas>
    <canvas
      bind:this={strip}
      class="rendering-canvas rendering-strip"
      style="aspect-ratio: 1 / 0.08"
      aria-label="the floor lit from the left"
    ></canvas>
  </div>

  <div class="demo-controls">
    <Choice label="falloff" options={CURVES} bind:value={curve} />
    <Slider label="range" min={2} max={30} step={1} bind:value={range} />
    <Slider label="falloffExponent" min={0.25} max={6} step={0.25} bind:value={exponent} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
