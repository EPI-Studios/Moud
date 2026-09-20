<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { whileVisible } from "../core/frames"
  import { palette } from "../core/surface"

  type Body = { name: string; x: number; y: number; face: number; tone: "main" | "blue" }

  const W = 720
  const H = 280
  const UNIT = 40

  let bodies = $state<Body[]>([
    { name: "you", x: 200, y: 140, face: 0, tone: "main" },
    { name: "Sam", x: 560, y: 140, face: Math.PI, tone: "blue" },
  ])
  let tool = $state<{ carrier: Body | null; x: number; y: number; age: number; dropper: Body | null }>({
    carrier: bodies[0],
    x: 0,
    y: 0,
    age: 0,
    dropper: null,
  })
  let lines = $state<Line[]>([{ id: 0, text: "drop the sword, then drag either body into the dashed ring" }])
  let canvas = $state<HTMLCanvasElement | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let dragging = $state<Body | null>(null)
  let last = { x: 0, y: 0 }
  let next = 1

  let previous = 0

  function since(now: number) {
    const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0
    previous = now
    return dt
  }

  whileVisible(
    () => canvas,
    (now) => {
      const dt = since(now)
      if (tool.carrier) return
      tool.age += dt
      check()
    },
  )

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function metres(a: { x: number; y: number }, b: { x: number; y: number }) {
    const dx = (a.x - b.x) / UNIT
    const dy = (a.y - b.y) / UNIT
    return Math.sqrt(dx * dx + dy * dy)
  }

  function wait(body: Body) {
    return body === tool.dropper ? 4 : 1
  }

  function check() {
    if (tool.carrier) return
    for (const body of bodies) {
      if (tool.carrier) return
      if (metres(body, tool) <= 2.5 && tool.age >= wait(body)) {
        tool.carrier = body
        record(
          `${body.name} walked within 2.5 m: the sword went into ${body.name === "you" ? "your" : "their"} backpack`,
          "in",
        )
      }
    }
  }

  function dropFrom(body: Body) {
    if (tool.carrier !== body) {
      record(`${body.name} does not carry the sword`)
      return
    }
    tool.carrier = null
    tool.dropper = body
    tool.age = 0
    tool.x = clamp(body.x + Math.cos(body.face) * 2 * UNIT, 10, W - 10)
    tool.y = clamp(body.y + Math.sin(body.face) * 2 * UNIT, 10, H - 10)
    record(`${body.name} dropped it: the Handle lands 2 m in front, unequipped fires`, "out")
  }

  function fit() {
    const node = canvas
    if (!node) return
    const width = node.clientWidth || W
    const ratio = window.devicePixelRatio || 1
    box = {
      w: Math.max(1, Math.round(width * ratio)),
      h: Math.max(1, Math.round(((width * H) / W) * ratio)),
      scale: Math.max(1, Math.min(1.5, (W / width) * 0.7)),
    }
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = c.line
    for (let gx = 0; gx <= W; gx += UNIT) {
      ctx.beginPath()
      ctx.moveTo(gx + 0.5, 0)
      ctx.lineTo(gx + 0.5, H)
      ctx.stroke()
    }
    for (let gy = 0; gy <= H; gy += UNIT) {
      ctx.beginPath()
      ctx.moveTo(0, gy + 0.5)
      ctx.lineTo(W, gy + 0.5)
      ctx.stroke()
    }
    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.textAlign = "center"

    if (!tool.carrier) {
      const ready = bodies.some((body) => tool.age >= wait(body))
      ctx.beginPath()
      ctx.arc(tool.x, tool.y, 2.5 * UNIT, 0, Math.PI * 2)
      ctx.strokeStyle = ready ? c.accent : c.line2
      ctx.setLineDash([5, 5])
      ctx.stroke()
      ctx.setLineDash([])
      ctx.save()
      ctx.translate(tool.x, tool.y)
      ctx.rotate(0.6)
      ctx.fillStyle = c.accent
      ctx.fillRect(-3, -24, 6, 48)
      ctx.restore()
      const waits = bodies.map((body) => {
        const left = Math.max(0, wait(body) - tool.age)
        return body.name + (left > 0 ? ` waits ${left.toFixed(1)} s` : " may pick it up")
      })
      const above = tool.y - 2.5 * UNIT - 20 >= 12
      ctx.fillStyle = c.muted
      ctx.fillText(waits[0], tool.x, above ? tool.y - 2.5 * UNIT - 20 : tool.y + 2.5 * UNIT + 18)
      ctx.fillText(waits[1], tool.x, above ? tool.y - 2.5 * UNIT - 6 : tool.y + 2.5 * UNIT + 34)
    }

    for (const body of bodies) {
      const colour = body.tone === "main" ? c.main : c.blue
      ctx.beginPath()
      ctx.arc(body.x, body.y, 12, 0, Math.PI * 2)
      ctx.fillStyle = colour
      ctx.fill()
      ctx.strokeStyle = colour
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(body.x, body.y)
      ctx.lineTo(body.x + Math.cos(body.face) * 24, body.y + Math.sin(body.face) * 24)
      ctx.stroke()
      ctx.lineWidth = 1
      ctx.fillStyle = c.text
      ctx.fillText(body.name + (tool.carrier === body ? "  carries the sword" : ""), body.x, body.y + 30)
    }
    ctx.textAlign = "left"
    ctx.fillStyle = c.light
    ctx.fillText("grid: 1 m", 12, H - 12)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    const node = canvas
    const ctx = node?.getContext("2d")
    if (!node || !ctx || !box.w) return
    node.width = box.w
    node.height = box.h
    ctx.setTransform(box.w / W, 0, 0, box.h / H, 0, 0)
    draw(ctx)
  })
</script>

<Demo label="Dropping and picking up">
  <canvas
    bind:this={canvas}
    class="demo-plan demo-plan-wide"
    style="aspect-ratio: {W} / {H}"
    aria-label="top down view of two bodies and a dropped tool"
    onpointerdown={(event) => {
      if (!canvas) return
      const point = pointIn(canvas, event, W, H)
      dragging = null
      for (const body of bodies) {
        const dx = point.x - body.x
        const dy = point.y - body.y
        if (dx * dx + dy * dy < 24 * 24) dragging = body
      }
      last = point
      if (!dragging) return
      canvas.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => {
      if (!dragging || !canvas) return
      const point = pointIn(canvas, event, W, H)
      const dx = point.x - last.x
      const dy = point.y - last.y
      if (dx * dx + dy * dy > 4) dragging.face = Math.atan2(dy, dx)
      last = point
      dragging.x = clamp(point.x, 14, W - 14)
      dragging.y = clamp(point.y, 14, H - 14)
      check()
    }}
    onpointerup={() => (dragging = null)}
    onpointercancel={() => (dragging = null)}
  ></canvas>

  <div class="demo-controls demo-row">
    {#each bodies as body (body.name)}
      <button type="button" class="demo-pill demo-pill-wide" onclick={() => dropFrom(body)}>
        {body.name}: hold it, press drop
      </button>
    {/each}
  </div>

  <Log {lines} />
</Demo>
