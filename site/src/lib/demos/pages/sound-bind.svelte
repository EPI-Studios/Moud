<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { clamp, pointIn } from "../core/pointer"

  const W = 560
  const H = 230
  const L = 44
  const R = 16
  const T = 26
  const B = 34

  let points = $state([
    { x: 0, y: 0.3 },
    { x: 0.5, y: 0.45 },
    { x: 1, y: 1 },
  ])
  let danger = $state(0.7)
  let shape = $state<"two" | "three">("three")

  let graph = $state<SVGSVGElement | null>(null)
  let dragging = $state<number | null>(null)

  const used = $derived(shape === "three" ? [0, 1, 2] : [0, 2])
  const active = $derived(used.map((index) => points[index]))

  const sx = (v: number) => L + v * (W - L - R)
  const sy = (v: number) => T + (1 - v) * (H - T - B)

  function valueAt(x: number) {
    for (let i = 0; i < active.length - 1; i++) {
      const a = active[i]
      const b = active[i + 1]
      if (x <= b.x) return a.y + (b.y - a.y) * ((x - a.x) / Math.max(0.0001, b.x - a.x))
    }
    return active[active.length - 1].y
  }

  const level = $derived(valueAt(danger))

  const round = (v: number) => Math.round(v * 100) / 100

  const source = $derived(
    `audio.bindBusVolume("danger", "music", { ` +
      active.map((p) => `{ ${round(p.x)}, ${round(p.y)} }`).join(", ") +
      ` })\naudio.setParameter("danger", ${danger})`,
  )

  function grab(event: PointerEvent) {
    if (!graph) return
    const p = pointIn(graph, event, W, H)
    let best: number | null = null
    let reach = 24
    for (const index of used) {
      const d = Math.hypot(sx(points[index].x) - p.x, sy(points[index].y) - p.y)
      if (d < reach) {
        reach = d
        best = index
      }
    }
    if (best === null) return
    dragging = best
    graph.setPointerCapture(event.pointerId)
  }

  function move(event: PointerEvent) {
    if (dragging === null || !graph) return
    const p = pointIn(graph, event, W, H)
    const point = points[dragging]
    point.y = clamp(1 - (p.y - T) / (H - T - B), 0, 1)
    if (dragging === 1) point.x = clamp((p.x - L) / (W - L - R), 0.05, 0.95)
  }
</script>

<Demo label="A parameter drives a bus">
  <svg
    bind:this={graph}
    class="demo-plan demo-plan-wide sound-graph"
    viewBox="0 0 {W} {H}"
    role="img"
    aria-label="curve from danger to music volume"
    onpointerdown={grab}
    onpointermove={move}
    onpointerup={() => (dragging = null)}
    onpointercancel={() => (dragging = null)}
  >
    <rect width={W} height={H} style="fill:var(--bg)" />
    <g font-size="10" style="fill:var(--text-light)">
      {#each [0, 0.5, 1] as v (v)}
        <line x1={L} y1={sy(v)} x2={W - R} y2={sy(v)} style="stroke:var(--line)" />
        <text x={L - 8} y={sy(v) + 3} text-anchor="end">{v}</text>
        <text x={sx(v)} y={H - B + 16} text-anchor="middle">{v}</text>
      {/each}
      <text x={W - R} y={H - 4} text-anchor="end">danger</text>
      <text x={L} y="12">music volume</text>
    </g>
    <polyline
      fill="none"
      stroke-width="2"
      style="stroke:var(--text-main)"
      points={active.map((p) => `${sx(p.x)},${sy(p.y)}`).join(" ")}
    />
    <line
      x1={sx(danger)}
      x2={sx(danger)}
      y1={sy(0)}
      y2={sy(level)}
      stroke-dasharray="3 3"
      style="stroke:var(--text-muted)"
    />
    <line
      x1={sx(0)}
      x2={sx(danger)}
      y1={sy(level)}
      y2={sy(level)}
      stroke-dasharray="3 3"
      style="stroke:var(--text-muted)"
    />
    <g>
      {#each active as p, index (index)}
        <circle
          cx={sx(p.x)}
          cy={sy(p.y)}
          r="7"
          stroke-width="2"
          style="fill:var(--bg);stroke:var(--text-main)"
        />
      {/each}
    </g>
    <circle cx={sx(danger)} cy={sy(level)} r="5" style="fill:var(--accent)" />
  </svg>

  <div class="sound-meter">
    <div class="sound-meter-fill" style="width: {(level * 100).toFixed(1)}%"></div>
    <span class="sound-meter-text">bus "music" volume {level.toFixed(2)}</span>
  </div>

  <Note>
    Drag the points, then move danger. Between two points the volume moves in a straight line, so
    setting the parameter is all the script does.
  </Note>

  <div class="demo-controls">
    <Slider label="danger" min={0} max={1} step={0.01} bind:value={danger} />
    <Choice label="points" options={["two", "three"] as const} bind:value={shape} />
  </div>

  <CodePanel {source} />
</Demo>
