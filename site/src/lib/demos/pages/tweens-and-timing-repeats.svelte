<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"
  import { fit, palette } from "../core/surface"
  import { EASING_NAMES, ease, type Direction, type EasingName } from "../core/easing"

  type Status = "playing" | "paused" | "completed" | "cancelled"

  const HEIGHT = 180
  const DIRECTIONS: Direction[] = ["in", "out", "inOut"]

  let canvas = $state<HTMLCanvasElement | null>(null)
  let time = $state(1)
  let repeats = $state(1)
  let reverses = $state(true)
  let delay = $state(0.5)
  let easing = $state<EasingName>("quad")
  let direction = $state<Direction>("out")

  let status = $state<Status>("playing")
  let pauseLabel = $state("t:pause()")
  let source = $state("")
  let lines = $state<Line[]>([])
  let next = 0

  let clock = 0
  let rest = 0

  const rounds = $derived((repeats + 1) * (reverses ? 2 : 1))
  const total = $derived(delay + time * rounds)

  function valueAt(t: number) {
    const active = t - delay
    if (active < 0) return 0
    const round = Math.floor(active / time)
    if (round >= rounds) return reverses ? 0 : 1
    const local = (active - round * time) / time
    const back = reverses && round % 2 === 1
    return ease(easing, direction, back ? 1 - local : local)
  }

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function write() {
    const info = [`    time = ${time},`]
    info.push(`    easing = "${easing}", direction = "${direction}",`)
    if (repeats) info.push(`    repeats = ${repeats},`)
    if (reverses) info.push("    reverses = true,")
    if (delay) info.push(`    delay = ${delay},`)
    source =
      `local t = door:tween({ transparency = 1 }, {\n${info.join("\n")}\n})\n` +
      "t.completed:connect(function(finished) end)\n" +
      `-- ${total.toFixed(2).replace(/\.?0+$/, "")} seconds in all: ` +
      (delay ? `${delay} of delay, then ` : "") +
      `${rounds} run${rounds > 1 ? "s" : ""} of ${time}` +
      `\nprint(t:state())   -- ${status}`
  }

  function restart() {
    clock = 0
    status = "playing"
    rest = 0
    pauseLabel = "t:pause()"
    write()
  }

  function togglePause() {
    if (status === "playing") {
      status = "paused"
      pauseLabel = "t:resume()"
    } else if (status === "paused") {
      status = "playing"
      pauseLabel = "t:pause()"
    }
    write()
  }

  function cancel() {
    if (status !== "playing" && status !== "paused") return
    status = "cancelled"
    record("completed(false): cancelled, the door stays where it got to", "out")
    write()
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx
    const left = 40
    const right = view.w - 70
    const top = 22
    const bottom = 122
    const span = total
    const x = (t: number) => left + (t / span) * (right - left)
    const y = (v: number) => bottom - v * (bottom - top)

    ctx.clearRect(0, 0, view.w, view.h)
    ctx.font = `11px ${c.mono}`
    ctx.lineWidth = 1
    if (delay) {
      ctx.fillStyle = "rgba(255,255,255,0.04)"
      ctx.fillRect(x(0), top, x(delay) - x(0), bottom - top)
      ctx.fillStyle = c.light
      ctx.fillText("delay", x(0) + 4, bottom - 8)
    }
    for (let r = 0; r <= rounds; r++) {
      const at = x(delay + r * time)
      ctx.strokeStyle = c.line2
      ctx.setLineDash(r === 0 || r === rounds ? [] : [3, 3])
      ctx.beginPath()
      ctx.moveTo(at, top)
      ctx.lineTo(at, bottom)
      ctx.stroke()
      if (r < rounds) {
        ctx.fillStyle = c.light
        const back = reverses && r % 2 === 1
        ctx.fillText(back ? "back" : `run ${reverses ? r / 2 + 1 : r + 1}`, at + 4, bottom + 14)
      }
    }
    ctx.setLineDash([])
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(left, bottom)
    ctx.lineTo(right, bottom)
    ctx.stroke()
    ctx.fillStyle = c.light
    ctx.fillText("1", left - 16, top + 4)
    ctx.fillText("0", left - 16, bottom + 4)
    ctx.strokeStyle = c.main
    ctx.lineWidth = 1.75
    ctx.beginPath()
    for (let i = 0; i <= 240; i++) {
      const t = (i / 240) * span
      const px = x(t)
      const py = y(valueAt(t))
      if (i === 0) ctx.moveTo(px, py)
      else ctx.lineTo(px, py)
    }
    ctx.stroke()

    const now = Math.min(clock, span)
    const value = valueAt(now)
    const done = status === "completed" || status === "cancelled"
    ctx.strokeStyle = status === "cancelled" ? c.red : c.accent
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x(now), top - 8)
    ctx.lineTo(x(now), bottom)
    ctx.stroke()
    ctx.fillStyle = status === "cancelled" ? c.red : c.accent
    ctx.beginPath()
    ctx.arc(x(now), y(value), 4, 0, Math.PI * 2)
    ctx.fill()
    if (status === "completed") {
      ctx.fillStyle = c.green
      ctx.fillText("completed(true)", Math.min(x(span) - 100, view.w - 110), top - 8)
    }

    const doorX = right + 22
    ctx.fillStyle = c.muted
    ctx.fillText("door", doorX - 2, top - 8)
    ctx.strokeStyle = c.line2
    ctx.strokeRect(doorX, top, 30, bottom - top)
    ctx.globalAlpha = 1 - value
    ctx.fillStyle = c.main
    ctx.fillRect(doorX + 3, top + 3, 24, bottom - top - 6)
    ctx.globalAlpha = 1
    ctx.fillStyle = c.muted
    ctx.fillText(`transparency ${value.toFixed(2)}`, left, 160)
    ctx.fillText(done ? "" : `t = ${now.toFixed(2)} s`, right - 60, 160)
  }

  let last: number | null = null

  whileVisible(
    () => canvas,
    (now) => {
      const seconds = now / 1000
      const dt = last === null ? 0 : Math.min(0.1, seconds - last)
      last = seconds
      if (status === "playing") {
        clock += dt
        if (clock >= total) {
          clock = total
          status = "completed"
          record("completed(true): the tween ran to the end", "in")
          write()
        }
      } else if (status === "completed" || status === "cancelled") {
        rest += dt
        if (rest > 1.6) restart()
      }
      draw()
    },
  )

  $effect(() => {
    time
    repeats
    delay
    reverses
    direction
    easing
    restart()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="Repeats, reverses and delay">
  <canvas bind:this={canvas} class="tw-graph"></canvas>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide tw-mono" onclick={togglePause}>{pauseLabel}</button>
    <button type="button" class="demo-pill demo-pill-wide tw-mono" onclick={cancel}>t:cancel()</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={restart}>start again</button>
  </div>

  <div class="demo-controls">
    <Slider label="time" min={0.25} max={2} step={0.25} bind:value={time} />
    <Slider label="repeats" min={0} max={3} step={1} bind:value={repeats} />
    <Slider label="delay" min={0} max={1.5} step={0.25} bind:value={delay} />
    <Choice label="reverses" options={[false, true]} bind:value={reverses} />
    <Choice label="direction" options={DIRECTIONS} bind:value={direction} />
    <div class="demo-choice">
      <span class="demo-slider-name">easing</span>
      <select class="demo-select" bind:value={easing}>
        {#each EASING_NAMES as name (name)}
          <option value={name}>{name}</option>
        {/each}
      </select>
    </div>
  </div>

  <Log {lines} keep={3} />
  <CodePanel {source} />
</Demo>
