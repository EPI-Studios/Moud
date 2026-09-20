<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type Point2, type Scene } from "../core/mc2d"
  import { pointIn } from "../core/pointer"

  type Hit = { name: string; x0: number; x1: number; y0: number; y1: number }

  const HEIGHT = 180
  const CYAN = "#4fd6e0"
  const ORANGE = "#f08a3c"
  const WHITE = "#f4f4f4"

  const THINGS: Record<string, string> = {
    floor: "green: a part's box exactly as the physics received it this tick.",
    crate: "green: a part's box exactly as the physics received it this tick.",
    wedge: "green triangles: the faces a shaped part collides as.",
    ghost: "grey: a part with collides = false. Bodies walk through it.",
    blocks: "white: the collision shapes of blocks within 5 blocks of you.",
    body: "yellow: a body's capsule. Players, mobs and the characters a place walks all have one.",
  }

  const FLOOR: Point2[] = [
    [0.5, 0],
    [4.5, 0],
    [4.5, 0.4],
    [0.5, 0.4],
  ]
  const CRATE: Point2[] = [
    [2, 0.4],
    [3.2, 0.4],
    [3.2, 1.6],
    [2, 1.6],
  ]
  const WEDGE: Point2[] = [
    [5, 0],
    [7.5, 0],
    [7.5, 1.6],
  ]
  const GHOST: Point2[] = [
    [13.6, 0],
    [15, 0],
    [15, 2.2],
    [13.6, 2.2],
  ]
  const TILT = -0.42
  const CENTRE = [11, 1.1]
  const RAMP: Point2[] = (
    [
      [-1.9, -0.22],
      [1.9, -0.22],
      [1.9, 0.22],
      [-1.9, 0.22],
    ] as Point2[]
  ).map(([x, y]): Point2 => [
    CENTRE[0] + x * Math.cos(TILT) - y * Math.sin(TILT),
    CENTRE[1] - (x * Math.sin(TILT) + y * Math.cos(TILT)),
  ])
  const BODY = 8.3

  let f7 = $state<"on" | "off">("on")
  let turned = $state<"available" | "unavailable">("available")
  let picked = $state("ramp")
  let canvas = $state<HTMLCanvasElement | null>(null)
  let built = $state<Scene | null>(null)
  let hits: Hit[] = []

  const note = $derived.by(() => {
    if (f7 === "off") return "F7 is off, so you see the world and none of its colliders. Turn it on and click a shape."
    if (picked === "ramp") {
      return turned === "available"
        ? "cyan: a turned part colliding as its real turned box."
        : "orange: a turned part colliding as the upright box around it, because turned collision is unavailable. This is why something slides off it or catches on nothing."
    }
    return THINGS[picked]
  })

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const c = v.colors
    hits = []
    const on = f7 === "on"
    const X = (x: number, d = 0) => v.x(x, d)
    const Y = (y: number, d = 0) => v.y(y, d)
    const solid = (name: string, points: Point2[]) => {
      const xs = points.map((p) => X(p[0]))
      const ys = points.map((p) => Y(p[1]))
      hits.push({
        name,
        x0: Math.min(...xs),
        x1: Math.max(...xs),
        y0: Math.min(...ys),
        y1: Math.max(...ys),
      })
    }
    const outline = (points: Point2[], color: string, dash?: number[]) => {
      ctx.strokeStyle = color
      ctx.lineWidth = 1.6
      ctx.setLineDash(dash ?? [])
      ctx.beginPath()
      points.forEach((p, i) => {
        if (i === 0) ctx.moveTo(X(p[0]), Y(p[1]))
        else ctx.lineTo(X(p[0]), Y(p[1]))
      })
      ctx.closePath()
      ctx.stroke()
      ctx.setLineDash([])
    }
    hits.push({ name: "blocks", x0: X(0), x1: X(16), y0: Y(0), y1: Y(-1) })
    solid("floor", FLOOR)
    solid("crate", CRATE)
    solid("wedge", WEDGE)
    solid("ramp", RAMP)
    solid("ghost", GHOST)
    hits.push({
      name: "body",
      x0: X(BODY - 0.35, 0.5),
      x1: X(BODY + 0.35, 0.5),
      y0: Y(1.9, 0.5),
      y1: Y(0, 0.5),
    })

    if (on) {
      for (let b = 0; b < 16; b++) {
        if (Math.abs(b + 0.5 - BODY) > 5) continue
        ctx.strokeStyle = WHITE
        ctx.lineWidth = 1
        ctx.strokeRect(X(b) + 1, Y(0) + 1, X(b + 1) - X(b) - 2, Y(-1) - Y(0) - 2)
      }
      outline(FLOOR, c.green)
      outline(CRATE, c.green)
      outline(WEDGE, c.green)
      ctx.strokeStyle = c.green
      ctx.beginPath()
      ctx.moveTo(X(7.5), Y(0))
      ctx.lineTo(X(6.25), Y(0.8))
      ctx.stroke()
      if (turned === "available") outline(RAMP, CYAN)
      else {
        const xs = RAMP.map((p) => p[0])
        const ys = RAMP.map((p) => p[1])
        const x0 = Math.min(...xs)
        const x1 = Math.max(...xs)
        const y0 = Math.min(...ys)
        const y1 = Math.max(...ys)
        outline(
          [
            [x0, y0],
            [x1, y0],
            [x1, y1],
            [x0, y1],
          ],
          ORANGE,
        )
      }
      outline(GHOST, "#c8c8c8", [4, 3])
      ctx.strokeStyle = c.yellow
      ctx.lineWidth = 1.6
      const r = 0.32 * v.unit
      ctx.beginPath()
      ctx.arc(X(BODY, 0.5), Y(1.8, 0.5) + r, r, Math.PI, 0)
      ctx.lineTo(X(BODY, 0.5) + r, Y(0, 0.5) - r)
      ctx.arc(X(BODY, 0.5), Y(0, 0.5) - r, r, 0, Math.PI)
      ctx.closePath()
      ctx.stroke()
    }
    v.tag(on ? "F7 on: click a shape" : "F7 off", 8, 16)
  }

  function pick(event: PointerEvent) {
    const node = canvas
    if (!node) return
    const box = node.getBoundingClientRect()
    const at = pointIn(node, event, box.width, box.height)
    let found: string | null = null
    for (const hit of hits) {
      if (at.x >= hit.x0 - 4 && at.x <= hit.x1 + 4 && at.y >= hit.y0 - 4 && at.y <= hit.y1 + 4) {
        found = hit.name
      }
    }
    if (found) picked = found
  }

  $effect(() => {
    turned
    picked = "ramp"
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    const scene = createScene(node, {
      height: HEIGHT,
      time: 3500,
      view: { x0: -0.2, x1: 16.2, y0: -1, y1: 4.3 },
      overlay,
    })
    for (let gx = -12; gx < 28; gx++) {
      scene.set(gx, -1, "grass_block")
      scene.set(gx, -2, "dirt")
      scene.set(gx, -3, "dirt")
      for (let gy = -10; gy < -3; gy++) scene.set(gx, gy, "stone")
    }
    scene.part({ block: "smooth_stone", x: 2.5, y: 0.2, w: 4, h: 0.4 })
    scene.part({ block: "oak_planks", x: 2.6, y: 1, w: 1.2, h: 1.2, stretch: true })
    scene.prism({ block: "cobblestone", points: WEDGE })
    scene.prism({ block: "oak_planks", points: RAMP })
    scene.part({ block: "stone_bricks", x: 14.3, y: 1.1, w: 1.4, h: 2.2, alpha: 0.55 })
    scene.player({ x: BODY, y: 0, facing: -1 })
    built = scene
    return () => {
      built = null
      scene.destroy()
    }
  })

  $effect(() => {
    f7
    turned
    built?.draw()
  })
</script>

<Demo label="Seeing collisions">
  <canvas
    bind:this={canvas}
    class="dbg-canvas dbg-click mc2d"
    aria-label="a scene with its collision shapes drawn over it"
    onpointerdown={pick}
  ></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    <Choice label="F7" options={["on", "off"] as const} bind:value={f7} />
    <Choice label="turned collision" options={["available", "unavailable"] as const} bind:value={turned} />
  </div>

  <Note>{note}</Note>
</Demo>
