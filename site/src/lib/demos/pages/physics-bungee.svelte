<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log, { type Line } from "../ui/Log.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import {
    createScene,
    type OverlayView,
    type Part,
    type Point2,
    type Rope,
    type Scene,
  } from "../core/mc2d"

  const W = 440
  const H = 250
  const PANEL = 160
  const BEAM = 80
  const HANG = BEAM - 0.15
  const GRAPH = { x: 170, y: 22, w: 256, h: 206, span: 5 }
  const SPEEDS = ["real time", "quarter speed"] as const

  let canvas = $state<HTMLCanvasElement | null>(null)
  let length = $state(6)
  let restitution = $state(0.5)
  let speed = $state<(typeof SPEEDS)[number]>("real time")
  let trace = $state<Point2[]>([])
  let lines = $state<Line[]>([
    { id: 0, text: "the weight falls from under the beam with the rope slack", kind: "idle" },
  ])

  let scene: Scene | null = null
  let weight: Part | null = null
  let rope: Rope | null = null
  let nextLine = 1
  const sim = { d: 0, v: 0, t: 0, rest: 0 }

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function record(message: string, kind: Line["kind"]) {
    lines = [{ id: nextLine++, text: message, kind }, ...lines].slice(0, 3)
  }

  function drop() {
    sim.d = 0
    sim.v = 0
    sim.t = 0
    sim.rest = 0
    trace = []
  }

  function physics(dt: number) {
    sim.v += 32 * dt
    sim.d += sim.v * dt
    if (sim.d < length || sim.v <= 0) return
    const hit = sim.v
    sim.d = length
    if (hit >= 0.5 && restitution > 0) {
      sim.v = -restitution * hit
      record(`taut at ${num(hit, 1)} m/s, thrown back up at ${num(-sim.v, 1)} m/s`, "in")
    } else {
      sim.v = 0
      if (hit >= 0.5) record(`taut at ${num(hit, 1)} m/s, stopped dead`, "out")
    }
  }

  function draw() {
    if (!scene || !weight || !rope) return
    const top = HANG - sim.d
    weight.y = top - 0.3
    const slack = Math.sqrt(Math.max(0, length * length - sim.d * sim.d))
    const bend = Math.min(60 / 26, slack * 0.45)
    const points: Point2[] = []
    for (let i = 0; i <= 16; i++) {
      const t = i / 16
      points.push([
        2 * (1 - t) * t * bend,
        (1 - t) * (1 - t) * HANG + (2 * (1 - t) * t * (HANG + top)) / 2 + t * t * top,
      ])
    }
    rope.points = points
    scene.draw()
  }

  function step(dt: number) {
    const scaled = dt * (speed === "real time" ? 1 : 0.25)
    const steps = Math.max(1, Math.ceil(scaled / (1 / 480)))
    for (let i = 0; i < steps; i++) physics(scaled / steps)
    sim.t += scaled
    trace.push([sim.t, sim.d])
    if (sim.t > GRAPH.span) drop()
    const resting = sim.d >= length - 1e-6 && sim.v === 0
    sim.rest = resting ? sim.rest + scaled : 0
    if (sim.rest > 1.2) drop()
    draw()
  }

  const limitY = $derived(GRAPH.y + 8 + (length / 7.5) * (GRAPH.h - 16))

  const curve = $derived.by(() => {
    if (!trace.length) return ""
    return (
      "M" +
      trace
        .map((sample) => {
          const x = GRAPH.x + (sample[0] / GRAPH.span) * GRAPH.w
          const y = GRAPH.y + 8 + (sample[1] / 7.5) * (GRAPH.h - 16)
          return `${x.toFixed(1)},${y.toFixed(1)}`
        })
        .join(" L")
    )
  })

  const source = $derived(
    `world:add("RopeConstraint", {
    attachment0 = beam:add("Attachment", { cframe = cframe(0, -0.15, 0) }),
    attachment1 = weight:add("Attachment", { cframe = cframe(0, 0.3, 0) }),
    length = ${num(length)}, restitution = ${num(restitution)}, visible = true,
})`,
  )

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const y = v.y(HANG - length, 0.5)
    v.line(
      [
        [0, y],
        [v.w, y],
      ],
      { dash: [4, 4], width: 1, color: "rgba(255,255,255,0.7)", halo: false },
    )
    v.tag(`length ${num(length)}`, 6, y + 12)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const made = createScene(node, {
      time: 2200,
      view: { x0: -1.7, x1: 1.7, y0: HANG - 8.3, y1: BEAM + 0.9 },
      step,
      overlay,
    })
    made.part({ block: "oak_planks", x: 0, y: BEAM, w: 2, h: 0.3, d: 0.3 })
    weight = made.part({ block: "iron_block", x: 0, y: HANG - 0.3, w: 0.6, h: 0.6, d: 0.6, stretch: true })
    rope = made.rope([])
    scene = made
    draw()
    made.start()
    return () => {
      made.destroy()
      scene = null
      weight = null
      rope = null
    }
  })

  $effect(() => {
    void length
    void restitution
    if (!canvas) return
    drop()
    draw()
  })
</script>

<Demo label="Bungee cord">
  <Stage>
    <div style="position: relative; width: 100%">
      <svg
        viewBox="0 0 {W} {H}"
        class="phys-view"
        role="img"
        aria-label="a weight dropped on a rope, and its distance below the beam over time"
      >
        <rect width={W} height={H} style="fill:var(--bg)" />
        <rect
          x={GRAPH.x}
          y={GRAPH.y}
          width={GRAPH.w}
          height={GRAPH.h}
          fill="none"
          style="stroke:var(--line-2)"
        />
        <line
          x1={GRAPH.x}
          x2={GRAPH.x + GRAPH.w}
          y1={limitY}
          y2={limitY}
          stroke-dasharray="4 4"
          style="stroke:var(--text-light)"
        />
        <text
          x={GRAPH.x + GRAPH.w - 4}
          y={limitY - 5}
          font-size="10.5"
          text-anchor="end"
          style="fill:var(--text-muted);font-family:var(--font-mono)">length</text
        >
        <text
          x={GRAPH.x + 4}
          y={GRAPH.y - 8}
          font-size="10.5"
          style="fill:var(--text-muted);font-family:var(--font-mono)"
          >distance below the beam, last {GRAPH.span} s</text
        >
        <path d={curve} fill="none" stroke-width="1.5" style="stroke:var(--text-main)" />
      </svg>
      <canvas
        bind:this={canvas}
        class="mc2d"
        style="position:absolute;left:1px;top:1px;width:calc({((PANEL / W) * 100).toFixed(
          3,
        )}% - 1px);height:calc(100% - 2px);border:0;border-radius:5px 0 0 5px"
      ></canvas>
    </div>
  </Stage>
  <Mc2dCredit />

  <div class="demo-controls">
    <Slider label="length" min={2} max={7} step={0.5} bind:value={length} />
    <Slider label="restitution" min={0} max={1} step={0.05} bind:value={restitution} />
    <Choice label="speed" options={SPEEDS} bind:value={speed} />
  </div>

  <Log {lines} keep={3} />
  <CodePanel {source} />
</Demo>
