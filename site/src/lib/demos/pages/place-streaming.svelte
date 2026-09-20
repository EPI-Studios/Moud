<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { palette, type Palette } from "../core/surface"

  type Branch = {
    name: string
    kind: "Model" | "Character"
    x: number
    y: number
    parts?: [number, number][]
    always?: boolean
    sent: boolean
  }

  const W = 720
  const H = 380
  const SCALE = 0.5

  const BRANCHES: Branch[] = [
    { name: "arena", kind: "Model", x: 250, y: 170, parts: [[-18, -10], [14, -14], [0, 16]], sent: true },
    { name: "tower", kind: "Model", x: 470, y: 90, parts: [[-10, 0], [10, 0], [0, -16]], sent: true },
    {
      name: "village",
      kind: "Model",
      x: 610,
      y: 280,
      parts: [[-20, -8], [0, 10], [20, -6], [6, -20]],
      sent: true,
    },
    { name: "mine", kind: "Model", x: 90, y: 320, parts: [[-8, -6], [10, 6]], sent: true },
    { name: "boss", kind: "Character", x: 590, y: 60, always: true, sent: true },
  ]

  let canvas = $state<HTMLCanvasElement | null>(null)
  let ctx: CanvasRenderingContext2D | null = null
  let colors: Palette | null = null
  let scale = 1
  let dragging = false

  let radius = $state(256)
  let enabled = $state("true")
  let boss = $state("true")
  let player = $state({ x: 170, y: 250 })
  let sentCount = $state(BRANCHES.length)
  let lines = $state<Line[]>([
    { id: 0, text: "drag yourself around; each Model is sent or dropped whole" },
  ])
  let nextLine = 1
  let started = false

  const streaming = $derived(enabled === "true")
  const relevant = $derived(boss === "true")

  const note = $derived(
    streaming
      ? `${sentCount} of ${BRANCHES.length} branches are on this client. Values, remotes, containers and the leaderboard have no position and are always sent.`
      : "Streaming is off: every branch is sent to every client, however far away.",
  )

  const source = $derived(
    `[streaming]
enabled = ${streaming}
radius = ${radius}

-- the boss branch, hanging off the root
boss.alwaysRelevant = ${relevant}`,
  )

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 4)
  }

  function isSent(branch: Branch, at: { x: number; y: number }) {
    if (!streaming) return true
    if (branch.always && relevant) return true
    const dx = (branch.x - at.x) / SCALE
    const dy = (branch.y - at.y) / SCALE
    return Math.sqrt(dx * dx + dy * dy) <= radius
  }

  function update(at: { x: number; y: number }, silent: boolean) {
    for (const branch of BRANCHES) {
      const now = isSent(branch, at)
      if (now !== branch.sent && !silent) {
        record(
          now ? `${branch.name} sent again from scratch` : `${branch.name} not sent, destroyed on this client`,
          now ? "in" : "out",
        )
      }
      branch.sent = now
    }
    sentCount = BRANCHES.filter((branch) => branch.sent).length
    draw(at)
  }

  function draw(at: { x: number; y: number }) {
    const c = colors
    if (!ctx || !c) return
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = c.line
    ctx.lineWidth = 1
    for (let gx = 0; gx <= W; gx += 40) {
      ctx.beginPath()
      ctx.moveTo(gx + 0.5, 0)
      ctx.lineTo(gx + 0.5, H)
      ctx.stroke()
    }
    for (let gy = 0; gy <= H; gy += 40) {
      ctx.beginPath()
      ctx.moveTo(0, gy + 0.5)
      ctx.lineTo(W, gy + 0.5)
      ctx.stroke()
    }

    if (streaming) {
      ctx.beginPath()
      ctx.arc(at.x, at.y, radius * SCALE, 0, Math.PI * 2)
      ctx.fillStyle = "rgba(198, 198, 198, 0.06)"
      ctx.fill()
      ctx.setLineDash([5, 5])
      ctx.strokeStyle = c.accent
      ctx.stroke()
      ctx.setLineDash([])
    }

    ctx.font = `${Math.round(12 * scale)}px ${c.mono}`
    ctx.textAlign = "center"
    for (const branch of BRANCHES) {
      const colour = branch.sent ? c.main : c.light
      if (branch.sent && streaming) {
        ctx.strokeStyle = c.line2
        ctx.beginPath()
        ctx.moveTo(at.x, at.y)
        ctx.lineTo(branch.x, branch.y)
        ctx.stroke()
      }
      if (branch.kind === "Model") {
        for (const [dx, dy] of branch.parts ?? []) {
          ctx.fillStyle = branch.sent ? c.bg4 : c.bg
          ctx.strokeStyle = colour
          ctx.setLineDash(branch.sent ? [] : [3, 3])
          ctx.fillRect(branch.x + dx - 8, branch.y + dy - 8, 16, 16)
          ctx.strokeRect(branch.x + dx - 7.5, branch.y + dy - 7.5, 15, 15)
        }
        ctx.setLineDash([])
      } else {
        ctx.beginPath()
        ctx.arc(branch.x, branch.y, 10, 0, Math.PI * 2)
        ctx.fillStyle = branch.sent ? c.purple : c.bg
        ctx.fill()
        ctx.strokeStyle = branch.sent ? c.purple : c.light
        ctx.setLineDash(branch.sent ? [] : [3, 3])
        ctx.stroke()
        ctx.setLineDash([])
      }
      ctx.fillStyle = colour
      const label = branch.name + (branch.always && relevant ? " (alwaysRelevant)" : "")
      ctx.fillText(label, branch.x, branch.y + (branch.kind === "Model" ? 40 : 26))
    }

    ctx.beginPath()
    ctx.arc(at.x, at.y, 9, 0, Math.PI * 2)
    ctx.fillStyle = c.main
    ctx.fill()
    ctx.fillStyle = c.muted
    ctx.fillText("you", at.x, at.y - 16)
  }

  function fit() {
    if (!canvas || !ctx) return
    const width = canvas.clientWidth || W
    scale = Math.max(1, Math.min(1.5, (W / width) * 0.7))
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(((width * H) / W) * ratio))
    ctx.setTransform(canvas.width / W, 0, 0, canvas.height / H, 0, 0)
    draw(player)
  }

  function grab(event: PointerEvent) {
    if (!canvas) return
    const point = pointIn(canvas, event, W, H)
    const dx = point.x - player.x
    const dy = point.y - player.y
    if (dx * dx + dy * dy > 30 * 30) return
    dragging = true
    canvas.setPointerCapture(event.pointerId)
    event.preventDefault()
  }

  function move(event: PointerEvent) {
    if (!dragging || !canvas) return
    const point = pointIn(canvas, event, W, H)
    player = { x: clamp(point.x, 10, W - 10), y: clamp(point.y, 10, H - 10) }
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    ctx = node.getContext("2d")
    colors = palette()
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    update(player, !started)
    started = true
  })
</script>

<Demo label="Streaming">
  <canvas
    bind:this={canvas}
    class="demo-plan demo-plan-wide"
    aria-label="top down view of streaming around a player"
    style="aspect-ratio: {W} / {H}"
    onpointerdown={grab}
    onpointermove={move}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
  ></canvas>

  <div class="demo-controls">
    <Slider label="radius" min={32} max={640} step={8} bind:value={radius} />
    <Choice label="enabled" options={["true", "false"]} bind:value={enabled} />
    <Choice label="alwaysRelevant" options={["true", "false"]} bind:value={boss} />
  </div>

  <Note>{note}</Note>
  <Log {lines} keep={4} />
  <CodePanel {source} />
</Demo>
