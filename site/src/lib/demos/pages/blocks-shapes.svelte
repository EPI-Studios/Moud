<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"

  type Shape = "sphere" | "cylinder" | "hollowBox" | "line"
  type Cell = [number, number]

  const N = 21
  const C = 10
  const CELL = 16
  const SHAPES: Shape[] = ["sphere", "cylinder", "hollowBox", "line"]
  const MID = C * CELL + CELL / 2
  const NEAR = C * CELL + 3
  const CROSS = "M" + MID + "," + NEAR + " v" + (CELL - 6) + " M" + NEAR + "," + MID + " h" + (CELL - 6)

  let shape = $state<Shape>("sphere")
  let radius = $state(5)
  let hollow = $state(false)
  let layer = $state(0)
  let from = $state<Cell>([-7, -3])
  let to = $state<Cell>([6, 5])

  let grid: SVGSVGElement | null = $state(null)
  let dragging: "from" | "to" | null = null

  function num(value: number) {
    return String(Math.round(value * 100) / 100)
  }

  function has(x: number, y: number, z: number) {
    if (shape === "sphere") {
      const d = Math.sqrt(x * x + y * y + z * z)
      return !(d > radius || (hollow && d < radius - 1))
    }
    if (shape === "cylinder") {
      if (y < 0 || y >= 5) return false
      const e = Math.sqrt(x * x + z * z)
      return !(e > radius || (hollow && e < radius - 1))
    }
    if (shape === "hollowBox") {
      const n = Math.floor(radius)
      if (Math.abs(x) > n || Math.abs(y) > n || Math.abs(z) > n) return false
      return Math.abs(x) === n || Math.abs(y) === n || Math.abs(z) === n
    }
    return false
  }

  const linePoints = $derived.by(() => {
    const steps = Math.ceil(Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1])))
    const found: Cell[] = []
    let last: Cell | null = null
    for (let n = 0; n <= steps; n++) {
      const t = steps === 0 ? 0 : n / steps
      const x = Math.floor(from[0] + (to[0] - from[0]) * t)
      const z = Math.floor(from[1] + (to[1] - from[1]) * t)
      if (last && last[0] === x && last[1] === z) continue
      found.push([x, z])
      last = [x, z]
    }
    return found
  })

  const total = $derived.by(() => {
    if (shape === "line") return linePoints.length
    const r = Math.ceil(radius)
    let count = 0
    for (let x = -r; x <= r; x++) {
      for (let y = -r; y <= Math.max(r, 5); y++) {
        for (let z = -r; z <= r; z++) {
          if (has(x, y, z)) count++
        }
      }
    }
    return count
  })

  const filled = $derived.by(() => {
    const set = new Set<string>()
    if (shape === "line") {
      for (const point of linePoints) set.add(point[0] + "," + point[1])
    } else {
      for (let x = -C; x <= C; x++) {
        for (let z = -C; z <= C; z++) if (has(x, layer, z)) set.add(x + "," + z)
      }
    }
    return set
  })

  const cells = $derived.by(() => {
    const out: { x: number; z: number; on: boolean }[] = []
    for (let x = -C; x <= C; x++) {
      for (let z = -C; z <= C; z++) out.push({ x, z, on: filled.has(x + "," + z) })
    }
    return out
  })

  const here = $derived(cells.reduce((sum, cell) => sum + (cell.on ? 1 : 0), 0))

  const source = $derived.by(() => {
    const r = num(radius)
    const n = Math.floor(radius)
    const codes: Record<Shape, string> = {
      sphere: 'b:sphere(vec3(0, 70, 0), ' + r + ', "minecraft:stone", ' + hollow + ")",
      cylinder: 'b:cylinder(vec3(0, 70, 0), ' + r + ', 5, "minecraft:stone", ' + hollow + ")",
      hollowBox:
        "b:hollowBox(vec3(" + -n + ", " + (70 - n) + ", " + -n +
        "), vec3(" + n + ", " + (70 + n) + ", " + n + '), "minecraft:stone")',
      line:
        "b:line(vec3(" + from[0] + ", 70, " + from[1] + "), vec3(" + to[0] + ", 70, " + to[1] +
        '), "minecraft:gold_block")',
    }
    return "local b = game.blocks\n" + codes[shape]
  })

  function cellAt(event: PointerEvent): Cell {
    if (!grid) return [0, 0]
    const point = pointIn(grid, event, N, N)
    return [clamp(Math.round(point.x) - C, -C, C), clamp(Math.round(point.y) - C, -C, C)]
  }

  function grab(event: PointerEvent) {
    if (shape !== "line" || !grid) return
    const point = cellAt(event)
    const toFrom = Math.hypot(point[0] - from[0], point[1] - from[1])
    const toTo = Math.hypot(point[0] - to[0], point[1] - to[1])
    dragging = toFrom < toTo ? "from" : "to"
    grid.setPointerCapture(event.pointerId)
    if (dragging === "from") from = point
    else to = point
  }

  function drag(event: PointerEvent) {
    if (!dragging) return
    if (dragging === "from") from = cellAt(event)
    else to = cellAt(event)
  }
</script>

<Demo label="Shapes, one layer at a time">
  <div class="demo-stage blk-stage">
    <svg
      bind:this={grid}
      class="blk-grid"
      class:blk-drag={shape === "line"}
      viewBox="0 0 {N * CELL} {N * CELL}"
      role="img"
      aria-label="one layer of the shape seen from above"
      onpointerdown={grab}
      onpointermove={drag}
      onpointerup={() => (dragging = null)}
      onpointercancel={() => (dragging = null)}
    >
      <g>
        {#each cells as cell (cell.x + "," + cell.z)}
          <rect
            x={(cell.x + C) * CELL + 0.5}
            y={(cell.z + C) * CELL + 0.5}
            width={CELL - 1}
            height={CELL - 1}
            style={cell.on
              ? shape === "line"
                ? "fill:var(--yellow);fill-opacity:0.75"
                : "fill:var(--text-muted)"
              : "fill:var(--bg-3)"}
          />
        {/each}
      </g>
      <g>
        {#if shape === "line"}
          {#each [from, to] as end, index (index)}
            <circle
              cx={(end[0] + C) * CELL}
              cy={(end[1] + C) * CELL}
              r="5"
              stroke-width="1.5"
              style="fill:var(--bg);stroke:var(--text-main)"
            />
          {/each}
        {:else}
          <path d={CROSS} style="stroke:var(--accent)" stroke-width="1.5" />
        {/if}
      </g>
    </svg>
    <div class="blk-side">
      <span class="blk-big">{total}</span>
      <span class="blk-small">blocks in the whole shape</span>
      {#if shape !== "line"}
        <span class="blk-big">{here}</span>
        <span class="blk-small">in the layer at y = {70 + layer}</span>
      {/if}
    </div>
  </div>

  <p class="demo-note blk-note">
    Seen from above, one layer at a time. hollowBox uses the radius as its half size; drag the two
    ends when drawing a line.
  </p>

  <div class="demo-controls">
    <Choice label="shape" options={SHAPES} bind:value={shape} />
    <Slider label="radius" min={1} max={9} step={0.5} bind:value={radius} />
    <Choice label="hollow" options={[false, true]} bind:value={hollow} />
    <Slider label="layer" min={-9} max={9} bind:value={layer} />
  </div>

  <CodePanel {source} />
</Demo>
