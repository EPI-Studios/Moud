<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fitLegacy, palette} from "../core/surface"
  import { whileVisible } from "../core/frames"

  const HEIGHT = 150
  const SPAN = 4

  const SPAWNED = `task.spawn(function()
    local reply = http.get("https://example.com/scores")
    print(reply.status, reply.ok)
end)`

  const STEPPED = `game.stepped:connect(function()
    local reply = http.get("https://example.com/scores")   -- holds up the tick
end)`

  let canvas = $state<HTMLCanvasElement | null>(null)
  let mode = $state("inside task.spawn")
  let latency = $state(1.2)

  const source = $derived(mode === "inside task.spawn" ? SPAWNED : STEPPED)
  const note = $derived(
    mode === "inside task.spawn"
      ? "The script waits for the answer and the tick carries on without it. It resumes on the line after the call once the answer arrives."
      : "Called straight from game.stepped, the request holds up the tick everybody else is on until the website answers.",
  )

  let clock = 0
  let tickAt = 0
  let ticks: number[] = []
  let request: { start: number; end: number; spawn: boolean } | null = null
  let blockedUntil = -1
  let gaps: { start: number; end: number }[] = []

  function send() {
    if (request && request.end > clock) return
    request = { start: clock, end: clock + latency, spawn: mode === "inside task.spawn" }
    if (!request.spawn) {
      blockedUntil = request.end
      gaps.push({ start: clock, end: request.end })
    }
  }

  function draw() {
    if (!canvas) return
    const fit = fitLegacy(canvas, HEIGHT)
    if (!fit) return
    const c = palette()
    const { ctx, width } = fit

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, width, HEIGHT)
    const left = 110
    const x = (t: number) => left + (width - left - 12) * (1 - (clock - t) / SPAN)

    ctx.font = `11.5px ${c.mono}`
    ctx.textBaseline = "middle"
    ctx.fillStyle = c.muted
    ctx.fillText("server tick", 10, 40)
    ctx.fillText("your script", 10, 100)
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(left, 70)
    ctx.lineTo(width - 12, 70)
    ctx.stroke()

    for (const gap of gaps) {
      const a = Math.max(left, x(gap.start))
      const b = Math.min(width - 12, x(Math.min(gap.end, clock)))
      if (b <= a) continue
      ctx.fillStyle = "rgba(255, 106, 92, 0.12)"
      ctx.fillRect(a, 22, b - a, 36)
      ctx.fillStyle = c.red
      if (b - a > 70) ctx.fillText("no ticks", a + 6, 40)
    }

    ctx.fillStyle = c.main
    for (const t of ticks) {
      const px = x(t)
      if (px < left) continue
      ctx.fillRect(px - 1, 28, 2, 24)
    }

    if (request) {
      const a = Math.max(left, x(request.start))
      const b = x(Math.min(request.end, clock))
      if (b > left) {
        ctx.fillStyle = c.bg4
        ctx.fillRect(a, 90, Math.max(2, b - a), 20)
        ctx.fillStyle = c.accent
        if (b - a > 110) ctx.fillText(request.end > clock ? "waiting for the site" : "answered", a + 8, 100)
        if (request.end <= clock) {
          ctx.fillStyle = c.green
          ctx.beginPath()
          ctx.arc(b, 100, 5, 0, Math.PI * 2)
          ctx.fill()
        }
      }
    }

    ctx.fillStyle = c.light
    ctx.fillText(`last ${SPAN} seconds`, left, 132)
  }

  let last: number | null = null
  let sentOnce = false
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      clock += dt
      if (!sentOnce && clock > 0.8) {
        sentOnce = true
        send()
      }
      tickAt += dt
      while (tickAt >= 0.05) {
        tickAt -= 0.05
        const t = clock - tickAt
        if (t >= blockedUntil) ticks.push(t)
      }
      while (ticks.length && ticks[0] < clock - SPAN) ticks.shift()
      gaps = gaps.filter((gap) => gap.end > clock - SPAN)
      draw()
    },
  )
</script>

<Demo label="Waiting without holding up the tick">
  <canvas bind:this={canvas} class="hp-canvas" style="height: {HEIGHT}px"></canvas>

  <div class="demo-controls">
    <Choice
      label="the call runs"
      options={["inside task.spawn", "straight from game.stepped"]}
      bind:value={mode}
    />
    <Slider label="site answers in" min={0.2} max={2.5} step={0.1} bind:value={latency} />
    <div class="demo-choice">
      <span class="demo-slider-name"></span>
      <div class="demo-choice-buttons">
        <button type="button" class="demo-pill demo-pill-wide" onclick={send}>send the request</button>
      </div>
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
