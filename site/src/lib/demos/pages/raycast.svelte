<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import type { Point } from "../core/pointer"

  type Box = { name: string; x: number; y: number; w: number; h: number }
  type Hit = { t: number; box: Box; normal: { axis: "x" | "y"; sign: number } }

  const BOXES: Box[] = [
    { name: "crate", x: 150, y: 40, w: 70, h: 60 },
    { name: "wall", x: 260, y: 30, w: 26, h: 160 },
    { name: "barrel", x: 150, y: 140, w: 50, h: 50 },
  ]

  let plan = $state<SVGSVGElement | null>(null)
  let from = $state<Point>({ x: 40, y: 110 })
  let aim = $state<Point>({ x: 380, y: 110 })

  let dragging: "from" | "aim" | null = null

  function metres(value: number) {
    return (value / 10).toFixed(1)
  }

  function castAgainst(origin: Point, ux: number, uy: number, length: number, box: Box): Hit | null {
    let near = 0
    let far = length
    let normal: Hit["normal"] = { axis: "x", sign: -1 }
    const axes = [
      { origin: origin.x, dir: ux, lo: box.x, hi: box.x + box.w, axis: "x" as const },
      { origin: origin.y, dir: uy, lo: box.y, hi: box.y + box.h, axis: "y" as const },
    ]
    for (const a of axes) {
      if (Math.abs(a.dir) < 1e-6) {
        if (a.origin < a.lo || a.origin > a.hi) return null
        continue
      }
      let t1 = (a.lo - a.origin) / a.dir
      let t2 = (a.hi - a.origin) / a.dir
      let sign = -1
      if (t1 > t2) {
        const swap = t1
        t1 = t2
        t2 = swap
        sign = 1
      }
      if (t1 > near) {
        near = t1
        normal = { axis: a.axis, sign }
      }
      far = Math.min(far, t2)
      if (near > far) return null
    }
    return { t: near, box, normal }
  }

  const cast = $derived.by(() => {
    const dx = aim.x - from.x
    const dy = aim.y - from.y
    const length = Math.hypot(dx, dy) || 1
    const ux = dx / length
    const uy = dy / length
    let best: Hit | null = null
    for (const box of BOXES) {
      const hit = castAgainst(from, ux, uy, length, box)
      if (hit && (!best || hit.t < best.t)) best = hit
    }
    return {
      best,
      length,
      ux,
      uy,
      endX: best ? from.x + ux * best.t : aim.x,
      endY: best ? from.y + uy * best.t : aim.y,
      nx: best ? (best.normal.axis === "x" ? best.normal.sign : 0) : 0,
      nz: best ? (best.normal.axis === "y" ? best.normal.sign : 0) : 0,
    }
  })

  const source = $derived.by(() => {
    const call =
      "local part, at, distance, normal = world:raycast(\n" +
      `    vec3(${metres(from.x)}, 64, ${metres(from.y)}),\n` +
      `    vec3(${cast.ux.toFixed(2)}, 0, ${cast.uy.toFixed(2)}),\n` +
      `    ${metres(cast.length)}\n` +
      ")\n\n"
    if (!cast.best) return `${call}-- part is nil: the ray reached the end of its length first`
    return (
      `${call}-- part.name is "${cast.best.box.name}"\n` +
      `-- distance is ${metres(cast.best.t)}, normal is vec3(${cast.nx}, 0, ${cast.nz})`
    )
  })
</script>

<Demo label="Raycast">
  <Stage>
    <svg
      bind:this={plan}
      class="demo-plan demo-plan-wide"
      viewBox="0 0 420 220"
      role="img"
      aria-label="top down raycast"
      onpointerdown={(event) => {
        if (!plan) return
        const point = pointIn(plan, event, 420, 220)
        dragging =
          Math.hypot(point.x - from.x, point.y - from.y) < Math.hypot(point.x - aim.x, point.y - aim.y)
            ? "from"
            : "aim"
        plan.setPointerCapture(event.pointerId)
        if (dragging === "from") from = point
        else aim = point
      }}
      onpointermove={(event) => {
        if (!dragging || !plan) return
        const point = pointIn(plan, event, 420, 220)
        const held = { x: clamp(point.x, 6, 414), y: clamp(point.y, 6, 214) }
        if (dragging === "from") from = held
        else aim = held
      }}
      onpointerup={() => (dragging = null)}
      onpointercancel={() => (dragging = null)}
    >
      <rect width="420" height="220" style="fill:var(--bg)" />
      {#each BOXES as box (box.name)}
        <rect
          x={box.x}
          y={box.y}
          width={box.w}
          height={box.h}
          style="fill:var(--surface);stroke:var(--text-main)"
        />
      {/each}
      <line
        x1={from.x}
        y1={from.y}
        x2={cast.endX}
        y2={cast.endY}
        stroke-width="1.5"
        style="stroke:var(--accent)"
      />
      <circle cx={cast.endX} cy={cast.endY} r="4" opacity={cast.best ? 1 : 0} style="fill:var(--accent)" />
      <line
        x1={cast.endX}
        y1={cast.endY}
        x2={cast.endX + cast.nx * 26}
        y2={cast.endY + cast.nz * 26}
        stroke-width="1.5"
        opacity={cast.best ? 1 : 0}
        style="stroke:var(--blue)"
      />
      <circle cx={from.x} cy={from.y} r="7" style="fill:var(--text-main)" />
      <circle cx={aim.x} cy={aim.y} r="6" stroke-width="1.5" style="fill:var(--bg);stroke:var(--text-main)" />
    </svg>
  </Stage>

  <CodePanel {source} />
</Demo>
