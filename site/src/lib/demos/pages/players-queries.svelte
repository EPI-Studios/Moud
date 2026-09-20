<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Slider from "../ui/Slider.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { palette } from "../core/surface"

  type Body = { name: string; x: number; y: number; me?: boolean }
  type Query = "near" | "nearest" | "inCone" | "sortedByDistance"

  const W = 720
  const H = 340
  const UNIT = 5

  let bodies = $state<Body[]>([
    { name: "me", x: 200, y: 170, me: true },
    { name: "Alex", x: 330, y: 110 },
    { name: "Sam", x: 300, y: 240 },
    { name: "Kai", x: 470, y: 170 },
    { name: "Noor", x: 120, y: 80 },
    { name: "Lin", x: 560, y: 280 },
    { name: "Ada", x: 620, y: 90 },
  ])
  let query = $state<Query>("near")
  let radius = $state(30)
  let degrees = $state(0)
  let except = $state<"me" | "nothing">("me")
  let canvas = $state<HTMLCanvasElement | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let dragging = $state<Body | null>(null)

  const me = $derived(bodies[0])
  const look = $derived((degrees * Math.PI) / 180)

  const found = $derived(results())

  const note = $derived(
    found.length
      ? `Returns ${found.map((body) => body.name).join(", ")}${found.length > 1 ? ", nearest first." : "."}`
      : "Returns nothing.",
  )

  const source = $derived.by(() => {
    const tail = except === "me" ? ", me" : ""
    if (query === "near") return `local p = game.players\np:near(me.position, ${radius}${tail})`
    if (query === "nearest") return `local p = game.players\nlocal body, distance = p:nearest(me.position, ${radius}${tail})`
    if (query === "inCone") return `local p = game.players\np:inCone(eye, me:lookDirection(), 30, 50${tail})`
    return "local p = game.players\np:sortedByDistance(me.position)"
  })

  function metres(a: Body, b: Body) {
    const dx = (a.x - b.x) / UNIT
    const dy = (a.y - b.y) / UNIT
    return Math.sqrt(dx * dx + dy * dy)
  }

  function results() {
    let pool = bodies.filter((body) => !(except === "me" && body.me))
    if (query === "sortedByDistance") pool = [...bodies]
    let list: Body[]
    if (query === "near") {
      list = pool.filter((body) => metres(body, me) <= radius)
    } else if (query === "nearest") {
      list = pool.filter((body) => metres(body, me) <= radius)
      list.sort((a, b) => metres(a, me) - metres(b, me))
      list = list.slice(0, 1)
    } else if (query === "inCone") {
      const lx = Math.cos(look)
      const ly = Math.sin(look)
      list = pool.filter((body) => {
        const distance = metres(body, me)
        if (distance === 0) return true
        if (distance > 50) return false
        const cos = ((body.x - me.x) * lx + (body.y - me.y) * ly) / (distance * UNIT)
        return cos >= Math.cos((30 * Math.PI) / 180)
      })
    } else {
      list = pool
    }
    list.sort((a, b) => metres(a, me) - metres(b, me))
    return list
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
    for (let gx = 0; gx <= W; gx += 50) {
      ctx.beginPath()
      ctx.moveTo(gx + 0.5, 0)
      ctx.lineTo(gx + 0.5, H)
      ctx.stroke()
    }
    for (let gy = 0; gy <= H; gy += 50) {
      ctx.beginPath()
      ctx.moveTo(0, gy + 0.5)
      ctx.lineTo(W, gy + 0.5)
      ctx.stroke()
    }

    ctx.fillStyle = "rgba(198, 198, 198, 0.06)"
    ctx.strokeStyle = c.accent
    ctx.setLineDash([5, 5])
    if (query === "near" || query === "nearest") {
      ctx.beginPath()
      ctx.arc(me.x, me.y, radius * UNIT, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
    } else if (query === "inCone") {
      ctx.beginPath()
      ctx.moveTo(me.x, me.y)
      ctx.arc(me.x, me.y, 50 * UNIT, look - Math.PI / 6, look + Math.PI / 6)
      ctx.closePath()
      ctx.fill()
      ctx.stroke()
    }
    ctx.setLineDash([])

    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.textAlign = "center"
    for (const body of bodies) {
      const at = found.indexOf(body)
      ctx.beginPath()
      ctx.arc(body.x, body.y, 10, 0, Math.PI * 2)
      ctx.fillStyle = at !== -1 ? c.green : body.me ? c.main : c.bg4
      ctx.fill()
      ctx.strokeStyle = body.me ? c.main : c.light
      ctx.stroke()
      ctx.fillStyle = at !== -1 ? c.main : c.muted
      ctx.fillText(body.name + (at !== -1 && found.length > 1 ? `  ${at + 1}` : ""), body.x, body.y - 16)
    }
    ctx.strokeStyle = c.main
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(me.x, me.y)
    ctx.lineTo(me.x + Math.cos(look) * 22, me.y + Math.sin(look) * 22)
    ctx.stroke()
    ctx.lineWidth = 1
    ctx.textAlign = "left"
    ctx.fillStyle = c.light
    ctx.fillText("grid: 10 m", 12, H - 12)
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

<Demo label="Finding players">
  <canvas
    bind:this={canvas}
    class="demo-plan demo-plan-wide"
    style="aspect-ratio: {W} / {H}"
    aria-label="top down view of bodies and the ones a query finds"
    onpointerdown={(event) => {
      if (!canvas) return
      const point = pointIn(canvas, event, W, H)
      dragging = null
      for (const body of bodies) {
        const dx = point.x - body.x
        const dy = point.y - body.y
        if (dx * dx + dy * dy < 20 * 20) dragging = body
      }
      if (!dragging) return
      canvas.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => {
      if (!dragging || !canvas) return
      const point = pointIn(canvas, event, W, H)
      dragging.x = clamp(point.x, 10, W - 10)
      dragging.y = clamp(point.y, 14, H - 10)
    }}
    onpointerup={() => (dragging = null)}
    onpointercancel={() => (dragging = null)}
  ></canvas>

  <div class="demo-controls">
    <Choice label="query" options={["near", "nearest", "inCone", "sortedByDistance"] as const} bind:value={query} />
    <Slider label="radius" min={5} max={60} bind:value={radius} />
    <Slider label="look" min={-180} max={180} bind:value={degrees} />
    <Choice label="except" options={["me", "nothing"] as const} bind:value={except} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
