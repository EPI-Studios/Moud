<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"

  type Part = { name: string; x: number; y: number; w: number; h: number; tag?: string }

  const W = 440
  const H = 240
  const PX = 10

  const PARTS: Part[] = [
    { name: "wall", x: 300, y: 30, w: 16, h: 150 },
    { name: "guard", x: 170, y: 70, w: 14, h: 14, tag: "enemy" },
    { name: "crate", x: 120, y: 120, w: 26, h: 26 },
    { name: "archer", x: 250, y: 150, w: 14, h: 14, tag: "enemy" },
    { name: "barrel", x: 60, y: 60, w: 20, h: 20 },
    { name: "golem", x: 360, y: 100, w: 30, h: 30, tag: "enemy" },
    { name: "chest", x: 200, y: 185, w: 24, h: 16 },
    { name: "lamp", x: 90, y: 190, w: 10, h: 10 },
    { name: "sheep", x: 220, y: 110, w: 18, h: 12 },
  ]

  let plan = $state<SVGSVGElement | null>(null)
  let kind = $state("partsInRadius")
  let at = $state({ x: 190, y: 120 })
  let size = $state(7)
  let sorted = $state("true")
  let limit = $state("none")
  let tag = $state("any")

  function num(value: number, places = 1) {
    const fixed = value.toFixed(places)
    return fixed.includes(".") ? fixed.replace(/0+$/, "").replace(/\.$/, "") : fixed
  }

  function closest(part: Part, x: number, y: number) {
    const cx = Math.max(part.x, Math.min(x, part.x + part.w))
    const cy = Math.max(part.y, Math.min(y, part.y + part.h))
    return Math.hypot(cx - x, cy - y)
  }

  function inside(part: Part) {
    const half = (size * PX) / 2
    if (kind === "partsInRadius") return closest(part, at.x, at.y) <= size * PX
    if (kind === "partsInBox") {
      return (
        part.x <= at.x + half &&
        part.x + part.w >= at.x - half &&
        part.y <= at.y + half &&
        part.y + part.h >= at.y - half
      )
    }
    return at.x >= part.x && at.x <= part.x + part.w && at.y >= part.y && at.y <= part.y + part.h
  }

  function distance(part: Part) {
    return Math.hypot(part.x + part.w / 2 - at.x, part.y + part.h / 2 - at.y) / PX
  }

  const found = $derived.by(() => {
    const matched = PARTS.filter((part) => (tag === "any" ? true : part.tag === "enemy") && inside(part))
    if (sorted === "true") matched.sort((a, b) => distance(a) - distance(b))
    return matched
  })
  const kept = $derived(limit === "none" ? found : found.slice(0, parseInt(limit, 10)))

  const source = $derived.by(() => {
    const options: string[] = []
    if (sorted === "true") options.push("sorted = true")
    if (limit !== "none") options.push(`limit = ${limit}`)
    if (tag !== "any") options.push('tag = "enemy"')
    const tail = options.length ? `, { ${options.join(", ")} })` : ")"
    const x = num((at.x - W / 2) / PX)
    const z = num((at.y - H / 2) / PX)
    if (kind === "partsInRadius") return `local found = world:partsInRadius(vec3(${x}, 65, ${z}), ${size}${tail}`
    if (kind === "partsInBox") {
      return `local found = world:partsInBox(cframe(${x}, 65, ${z}), vec3(${size}, 4, ${size})${tail}`
    }
    return `local found = world:partsAtPoint(vec3(${x}, 65, ${z})${tail}`
  })

  function fill(part: Part) {
    if (kept.includes(part)) return "fill:var(--accent);stroke:var(--text-main)"
    if (found.includes(part)) return "fill:var(--bg-4);stroke:var(--yellow)"
    return "fill:var(--bg-3);stroke:var(--line-2)"
  }

  let dragging = false

  function move(event: PointerEvent) {
    if (!plan) return
    const point = pointIn(plan, event, W, H)
    at = { x: clamp(point.x, 4, W - 4), y: clamp(point.y, 4, H - 4) }
  }
</script>

<Demo label="Overlaps">
  <Stage>
    <svg
      bind:this={plan}
      class="demo-plan demo-plan-wide"
      viewBox="0 0 {W} {H}"
      role="img"
      aria-label="top down view of parts and a query region"
      onpointerdown={(event) => {
        dragging = true
        plan?.setPointerCapture(event.pointerId)
        move(event)
      }}
      onpointermove={(event) => dragging && move(event)}
      onpointerup={() => (dragging = false)}
      onpointercancel={() => (dragging = false)}
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      <g>
        {#if kind === "partsInRadius"}
          <circle
            cx={at.x}
            cy={at.y}
            r={size * PX}
            stroke-dasharray="4 4"
            style="fill:var(--surface);fill-opacity:0.5;stroke:var(--text-main)"
          />
        {:else if kind === "partsInBox"}
          <rect
            x={at.x - (size * PX) / 2}
            y={at.y - (size * PX) / 2}
            width={size * PX}
            height={size * PX}
            stroke-dasharray="4 4"
            style="fill:var(--surface);fill-opacity:0.5;stroke:var(--text-main)"
          />
        {/if}
      </g>
      {#each PARTS as part (part.name)}
        <g>
          <rect
            x={part.x}
            y={part.y}
            width={part.w}
            height={part.h}
            stroke-width="1.5"
            style={fill(part)}
          />
          <text
            x={part.x + part.w / 2}
            y={part.y - 4}
            text-anchor="middle"
            font-size="9.5"
            style="fill:var(--text-muted);font-family:var(--font-mono)"
            >{part.name}{part.tag ? " .enemy" : ""}</text
          >
          {#if kept.includes(part)}
            <g>
              <circle cx={part.x + part.w / 2} cy={part.y + part.h / 2} r="7" style="fill:#111" />
              <text
                x={part.x + part.w / 2}
                y={part.y + part.h / 2 + 3.5}
                text-anchor="middle"
                font-size="9.5"
                style="fill:#fff;font-family:var(--font-mono)">{kept.indexOf(part) + 1}</text
              >
            </g>
          {/if}
        </g>
      {/each}
      <circle
        cx={at.x}
        cy={at.y}
        r="5"
        stroke-width="1.5"
        style="fill:var(--bg);stroke:var(--text-main)"
      />
    </svg>
  </Stage>

  <div class="demo-controls">
    <Choice label="query" options={["partsInRadius", "partsInBox", "partsAtPoint"]} bind:value={kind} />
    <Slider label="radius or size" min={2} max={16} step={1} bind:value={size} />
    <Choice label="sorted" options={["true", "false"]} bind:value={sorted} />
    <Choice label="limit" options={["none", "1", "3"]} bind:value={limit} />
    <Choice label="tag" options={["any", '"enemy"']} bind:value={tag} />
  </div>

  <ul class="demo-log">
    {#if !kept.length}
      <li class="demo-log-row idle">an empty table</li>
    {/if}
    {#each kept as part, i (part.name)}
      <li class="demo-log-row in">
        {`[${i + 1}] ${part.name}${sorted === "true" ? `   centre ${num(distance(part))} m away` : ""}`}
      </li>
    {/each}
    {#if kept.length < found.length}
      <li class="demo-log-row out">
        {`${found.length - kept.length} more matched and were cut by limit${sorted === "true" ? "" : ", and without sorted they need not be the furthest"}`}
      </li>
    {/if}
  </ul>

  <CodePanel {source} />
</Demo>
