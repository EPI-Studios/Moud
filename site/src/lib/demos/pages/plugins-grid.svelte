<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Log, { type Line } from "../ui/Log.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import {
    createScene,
    type OverlayView,
    type Part,
    type Point2,
    type Scene,
    type SceneView,
  } from "../core/mc2d"

  type Block = { x: number; y: number; s: number; name: string; part?: Part }
  type Hit = { hit: { x: number; y: number }; normal: [number, number]; target: Block; depth: number }
  type Faces = { front: Point2[]; top: Point2[]; east: Point2[] }

  const LOOK: Record<string, string> = {
    floor: "stone_bricks",
    crate: "oak_planks",
    barrel: "barrel",
    block: "cobblestone",
  }

  let canvas = $state<HTMLCanvasElement | null>(null)
  let step = $state(1)
  let stamping = $state(true)
  let lines = $state<Line[]>([
    { id: 0, text: "move over a face and click to stamp; blue outlines are selected", kind: "idle" },
  ])

  let scene: Scene | null = null
  let blocks: Block[] = []
  let selection: Block[] = []
  let ghost: { x: number; y: number } | null = null
  let hover: Hit | null = null
  let nextLine = 1

  function reset() {
    blocks = []
    for (let x = -7; x <= 7; x++) blocks.push({ x, y: 0, s: 1, name: "floor" })
    const crate: Block = { x: -3.3, y: 1.35, s: 1, name: "crate" }
    const barrel: Block = { x: 2.6, y: 1.2, s: 0.8, name: "barrel" }
    blocks.push(crate, barrel)
    selection = [crate, barrel]
  }
  reset()

  function round(v: number) {
    return v < 0 ? -Math.floor(-v + 0.5) : Math.floor(v + 0.5)
  }

  function snap(v: number) {
    return round(v / step) * step
  }

  function fmt(v: number) {
    return String(Math.round(v * 1000) / 1000)
  }

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 3)
  }

  function front(b: Block) {
    return 0.5 - b.s / 2
  }

  function hitAt(px: number, py: number): Hit | null {
    const v = scene?.view
    if (!v) return null
    const sx = v.worldX(px, 0)
    const sy = v.worldY(py, 0)
    let best: { d: number; b: Block; face: { kind: string; x: number; y: number } } | null = null
    const take = (d: number, b: Block, face: { kind: string; x: number; y: number }) => {
      if (!best || d < best.d - 1e-6) best = { d, b, face }
    }
    for (const b of blocks) {
      const half = b.s / 2
      const z0 = front(b)
      const z1 = z0 + b.s
      const fx = sx - z0 * v.ox
      const fy = sy - z0 * v.oy
      if (fx >= b.x - half && fx <= b.x + half && fy >= b.y - half && fy <= b.y + half) {
        take(z0, b, { kind: "front", x: fx, y: fy })
      }
      const d = (sy - (b.y + half)) / v.oy
      if (d >= z0 && d <= z1) {
        const tx = sx - d * v.ox
        if (tx >= b.x - half && tx <= b.x + half) take(d, b, { kind: "top", x: tx, y: b.y + half })
      }
      if (v.ox > 0) {
        const e = (sx - (b.x + half)) / v.ox
        if (e >= z0 && e <= z1) {
          const ey = sy - e * v.oy
          if (ey >= b.y - half && ey <= b.y + half) take(e, b, { kind: "east", x: b.x + half, y: ey })
        }
      }
    }
    if (!best) return null
    const found: { d: number; b: Block; face: { kind: string; x: number; y: number } } = best
    const b = found.b
    const f = found.face
    if (f.kind === "top") return { hit: { x: f.x, y: f.y }, normal: [0, 1], target: b, depth: found.d }
    if (f.kind === "east") return { hit: { x: f.x, y: f.y }, normal: [1, 0], target: b, depth: found.d }
    const half = b.s / 2
    const faces: { d: number; n: [number, number]; at: { x: number; y: number } }[] = [
      { d: b.x + half - f.x, n: [1, 0], at: { x: b.x + half, y: f.y } },
      { d: f.x - (b.x - half), n: [-1, 0], at: { x: b.x - half, y: f.y } },
      { d: b.y + half - f.y, n: [0, 1], at: { x: f.x, y: b.y + half } },
      { d: f.y - (b.y - half), n: [0, -1], at: { x: f.x, y: b.y - half } },
    ]
    faces.sort((a, c) => a.d - c.d)
    return { hit: faces[0].at, normal: faces[0].n, target: b, depth: found.d }
  }

  function stampAt(mouse: Hit | null) {
    if (!mouse) return null
    return {
      x: snap(mouse.hit.x + mouse.normal[0] * (step / 2)),
      y: snap(mouse.hit.y + mouse.normal[1] * (step / 2)),
    }
  }

  function sync() {
    if (!scene) return
    for (const b of blocks) {
      if (!b.part) b.part = scene.part({ block: LOOK[b.name], stretch: true })
      b.part.x = b.x
      b.part.y = b.y
      b.part.w = b.part.h = b.part.d = b.s
    }
  }

  function draw() {
    sync()
    scene?.draw()
  }

  function box(v: SceneView, x: number, y: number, s: number): Faces {
    const z0 = 0.5 - s / 2
    const z1 = 0.5 + s / 2
    const h = s / 2
    const p = (px: number, py: number, d: number): Point2 => [v.x(px, d), v.y(py, d)]
    return {
      front: [p(x - h, y + h, z0), p(x + h, y + h, z0), p(x + h, y - h, z0), p(x - h, y - h, z0)],
      top: [p(x - h, y + h, z0), p(x + h, y + h, z0), p(x + h, y + h, z1), p(x - h, y + h, z1)],
      east: [p(x + h, y + h, z0), p(x + h, y - h, z0), p(x + h, y - h, z1), p(x + h, y + h, z1)],
    }
  }

  function shape(ctx: CanvasRenderingContext2D, points: Point2[]) {
    ctx.beginPath()
    points.forEach((q, i) => (i ? ctx.lineTo(q[0], q[1]) : ctx.moveTo(q[0], q[1])))
    ctx.closePath()
  }

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    ctx.strokeStyle = "rgba(255,255,255,0.22)"
    ctx.lineWidth = 1
    const stepPx = step * v.unit
    const x0 = v.x(0, 0.5) + stepPx / 2
    for (let gx = x0 - Math.ceil(x0 / stepPx) * stepPx; gx < v.w; gx += stepPx) {
      ctx.beginPath()
      ctx.moveTo(Math.round(gx) + 0.5, 0)
      ctx.lineTo(Math.round(gx) + 0.5, v.h)
      ctx.stroke()
    }
    const y0 = v.y(0, 0.5) + stepPx / 2
    for (let gy = y0 - Math.ceil(y0 / stepPx) * stepPx; gy < v.h; gy += stepPx) {
      ctx.beginPath()
      ctx.moveTo(0, Math.round(gy) + 0.5)
      ctx.lineTo(v.w, Math.round(gy) + 0.5)
      ctx.stroke()
    }

    for (const b of selection) {
      const faces = box(v, b.x, b.y, b.s)
      ctx.strokeStyle = v.colors.blue
      ctx.lineWidth = 2
      for (const f of [faces.front, faces.top, faces.east]) {
        shape(ctx, f)
        ctx.stroke()
      }
    }

    if (ghost && stamping) {
      const g = box(v, ghost.x, ghost.y, step)
      ctx.fillStyle = "rgba(255,255,255,0.42)"
      for (const f of [g.top, g.east, g.front]) {
        shape(ctx, f)
        ctx.fill()
      }
      ctx.setLineDash([4, 3])
      ctx.strokeStyle = "#ffffff"
      ctx.lineWidth = 1
      for (const f of [g.front, g.top, g.east]) {
        shape(ctx, f)
        ctx.stroke()
      }
      ctx.setLineDash([])
    }

    if (hover && stamping) {
      const hx = v.x(hover.hit.x, hover.depth)
      const hy = v.y(hover.hit.y, hover.depth)
      v.line(
        [
          [hx, hy],
          [hx + hover.normal[0] * 18, hy - hover.normal[1] * 18],
        ],
        { color: v.colors.yellow, width: 1.75 },
      )
      ctx.fillStyle = v.colors.yellow
      ctx.beginPath()
      ctx.arc(hx, hy, 3, 0, Math.PI * 2)
      ctx.fill()
    }

    v.tag(
      stamping
        ? ghost
          ? `ghost at vec3(${fmt(ghost.x)}, ${fmt(ghost.y)}, 0)`
          : "the mouse hits nothing: no ghost"
        : "Stamp is off: clicks select",
      8,
      16,
    )
    v.tag(`side view, step ${step}`, 8, v.h - 14)
  }

  function at(event: PointerEvent) {
    const box = (event.currentTarget as HTMLCanvasElement).getBoundingClientRect()
    return { x: event.clientX - box.left, y: event.clientY - box.top }
  }

  function move(event: PointerEvent) {
    const p = at(event)
    hover = hitAt(p.x, p.y)
    ghost = stampAt(hover)
    draw()
  }

  function leave() {
    hover = null
    ghost = null
    draw()
  }

  function down(event: PointerEvent) {
    const p = at(event)
    hover = hitAt(p.x, p.y)
    if (!stamping) {
      selection = hover && hover.target.name !== "floor" ? [hover.target] : []
      record(selection.length ? `${selection[0].name} selected` : "selection cleared", "idle")
      draw()
      return
    }
    const spot = stampAt(hover)
    if (!spot) {
      record("the ray hit nothing, so nothing is stamped", "idle")
      return
    }
    blocks.push({ x: spot.x, y: spot.y, s: step, name: "block" })
    record(`Stamp block: added at vec3(${fmt(spot.x)}, ${fmt(spot.y)}, 0), size ${step}`, "in")
    sync()
    hover = hitAt(p.x, p.y)
    ghost = stampAt(hover)
    draw()
  }

  function toggleStamp() {
    stamping = !stamping
    record(
      stamping
        ? "stamping: plugin:activate(true), clicks are the plugin's"
        : "not stamping: plugin:activate(false), clicks select again",
      "idle",
    )
    draw()
  }

  function align() {
    const moved: string[] = []
    for (const b of selection) {
      const nx = snap(b.x)
      const ny = snap(b.y)
      if (nx !== b.x || ny !== b.y) moved.push(`${b.name} to ${fmt(nx)}, ${fmt(ny)}`)
      b.x = nx
      b.y = ny
    }
    record(
      moved.length ? `Align to grid: ${moved.join("; ")}` : "Align to grid: already on the grid",
      moved.length ? "in" : "idle",
    )
    draw()
  }

  function again() {
    for (const b of blocks) if (b.part) scene?.remove(b.part)
    reset()
    lines = []
    draw()
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const made = createScene(node, {
      height: 240,
      time: 2800,
      view: (w, h) => {
        const unit = Math.min(44, w / 17)
        return { x0: -w / 2 / unit, x1: w / 2 / unit, y0: -46 / unit, y1: (h - 46) / unit }
      },
      overlay,
    })
    scene = made
    draw()
    return () => {
      made.destroy()
      scene = null
      for (const b of blocks) b.part = undefined
    }
  })

  $effect(() => {
    void step
    if (canvas) draw()
  })
</script>

<Demo label="The grid tool">
  <canvas
    bind:this={canvas}
    class="pl-grid mc2d"
    onpointermove={move}
    onpointerleave={leave}
    onpointerdown={down}
  ></canvas>
  <Mc2dCredit />

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" aria-pressed={stamping} onclick={toggleStamp}>
      Stamp
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={align}>
      {"Align selection  Ctrl+Shift+G"}
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={again}>reset</button>
  </div>

  <div class="demo-controls">
    <Slider label="Step" min={0.25} max={2} step={0.25} bind:value={step} />
  </div>

  <Log {lines} keep={3} />
</Demo>
