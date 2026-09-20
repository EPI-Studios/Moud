<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import type { Line } from "../ui/Log.svelte"

  const ZONE = { x: 110, y: 40, width: 140, height: 100 }

  const SOURCE = `local safe = world:add("Zone", {
    shape = "box",
    size = vec3(20, 6, 14),
    trackPlayers = true,
})

safe.entered:connect(function(thing) end)
safe.left:connect(function(thing) end)`

  let plan = $state<SVGSVGElement | null>(null)
  let body = $state({ x: 50, y: 90 })
  let dragging = $state(false)
  let inside = $state(false)
  let lines = $state<Line[]>([{ id: 0, text: "drag the body across the dashed edge", kind: "idle" }])
  let next = 1

  function contains(point: { x: number; y: number }) {
    return (
      point.x > ZONE.x &&
      point.x < ZONE.x + ZONE.width &&
      point.y > ZONE.y &&
      point.y < ZONE.y + ZONE.height
    )
  }

  function move(event: PointerEvent) {
    if (!plan) return
    const point = pointIn(plan, event, 360, 180)
    body = { x: clamp(point.x, 12, 348), y: clamp(point.y, 12, 168) }

    const now = contains(body)
    if (now === inside) return
    inside = now
    lines = [
      {
        id: next++,
        text: inside ? "entered fired with the body" : "left fired with the body",
        kind: inside ? "in" : "out",
      },
      ...lines,
    ]
  }
</script>

<Demo label="Zone">
  <Stage>
    <svg
      bind:this={plan}
      class="demo-plan"
      viewBox="0 0 360 180"
      role="img"
      aria-label="top down view of a zone"
      onpointerdown={(event) => {
        dragging = true
        plan?.setPointerCapture(event.pointerId)
        move(event)
      }}
      onpointermove={(event) => dragging && move(event)}
      onpointerup={() => (dragging = false)}
      onpointercancel={() => (dragging = false)}
    >
      <rect x="0" y="0" width="360" height="180" style="fill: var(--bg)" />
      <rect
        x={ZONE.x}
        y={ZONE.y}
        width={ZONE.width}
        height={ZONE.height}
        stroke-dasharray="4 4"
        style="fill: var(--surface); stroke: var(--text-main)"
      />
      <text
        x="180"
        y="32"
        text-anchor="middle"
        font-size="10"
        letter-spacing="1.4"
        style="fill: var(--text-muted)"
      >
        ZONE
      </text>
      <circle cx={body.x} cy={body.y} r="11" style="fill: var(--text-main)" />
    </svg>
  </Stage>

  <Log {lines} />
  <CodePanel source={SOURCE} />
</Demo>
