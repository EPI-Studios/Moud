<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { whileVisible } from "../core/frames"
  import { fit, label, num, palette } from "../core/surface"

  type Sample = { t: number; target: number; a: number; b: number }

  const HEIGHT = 240
  const TICK = 0.05
  const SPAN = 5

  function smoothDamp(current: number, target: number, velocity: number, smoothTime: number, dt: number) {
    const omega = 2 / Math.max(0.0001, smoothTime)
    const x = omega * dt
    const exp = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x)
    const change = current - target
    const goal = current - change
    const temp = (velocity + omega * change) * dt
    let speed = (velocity - omega * temp) * exp
    let out = goal + (change + temp) * exp
    if ((target - current > 0) === (out > target)) {
      out = target
      speed = (out - target) / dt
    }
    return [out, speed] as const
  }

  let canvas = $state<HTMLCanvasElement | null>(null)
  let speed = $state(8)
  let smooth = $state(0.3)
  let source = $state("")

  let target = 8
  let a = 0
  let b = 0
  let velocity = 0
  let history: Sample[] = []
  let clock = 0
  let tickTime = 0
  let nextJump = 2.5
  let held = false
  let dragging = false

  function write() {
    source =
      "local velocity = 0\n" +
      "game.stepped:connect(function(dt)\n" +
      `    a = math.approach(a, target, ${speed} * dt)\n` +
      `    b, velocity = math.smoothDamp(b, target, velocity, ${smooth}, dt)\n` +
      "end)\n\n" +
      `-- target ${num(target, 1)}   a ${num(a)}   b ${num(b)}   velocity ${num(velocity)}`
  }

  function tick() {
    a = a < target ? Math.min(a + speed * TICK, target) : Math.max(a - speed * TICK, target)
    const [next, nextVelocity] = smoothDamp(b, target, velocity, smooth, TICK)
    b = next
    velocity = nextVelocity
    history.push({ t: clock, target, a, b })
    while (history.length && history[0].t < clock - SPAN - 0.2) history.shift()
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx

    const y = (value: number) => view.h - 22 - (value / 10) * (view.h - 44)
    const x = (t: number) => view.w - 70 - ((clock - t) / SPAN) * (view.w - 90)

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, view.w, view.h)
    ctx.strokeStyle = c.line
    ctx.lineWidth = 1
    for (let g = 0; g <= 10; g += 2) {
      ctx.beginPath()
      ctx.moveTo(0, y(g))
      ctx.lineTo(view.w, y(g))
      ctx.stroke()
    }

    function line(key: "target" | "a" | "b", color: string, width: number, dash: number[] = []) {
      ctx.strokeStyle = color
      ctx.lineWidth = width
      ctx.setLineDash(dash)
      ctx.beginPath()
      history.forEach((p, i) => {
        if (i === 0) ctx.moveTo(x(p.t), y(p[key]))
        else if (key === "target") {
          ctx.lineTo(x(p.t), y(history[i - 1][key]))
          ctx.lineTo(x(p.t), y(p[key]))
        } else ctx.lineTo(x(p.t), y(p[key]))
      })
      ctx.stroke()
      ctx.setLineDash([])
    }

    line("target", c.light, 1.5, [5, 4])
    line("a", c.main, 2)
    line("b", c.blue, 2)

    const edge = view.w - 70
    ctx.font = `11px ${c.mono}`
    const tags: [string, number, string][] = [
      ["b", b, c.blue],
      ["a", a, c.main],
      ["target", target, c.muted],
    ]
    for (const [, value, color] of tags) {
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.arc(edge, y(value), 4, 0, Math.PI * 2)
      ctx.fill()
    }
    let lastY = -100
    for (const [name, value, color] of [...tags].sort((p, q) => q[1] - p[1])) {
      const ty = Math.max(y(value), lastY + 14)
      lastY = ty
      label(ctx, name, edge + 10, ty, color)
    }
    label(ctx, `last ${SPAN} seconds`, 8, 12, c.light)
  }

  function at(event: PointerEvent) {
    return pointIn(canvas!, event, canvas!.clientWidth, canvas!.clientHeight)
  }

  function move(point: { x: number; y: number }) {
    const value = ((HEIGHT - 22 - point.y) / (HEIGHT - 44)) * 10
    target = clamp(Math.round(value * 2) / 2, 0, 10)
  }

  function release() {
    if (!dragging) return
    dragging = false
    held = false
    nextJump = clock + 3
  }

  let last: number | null = null
  let sinceWrite = 0

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      clock += dt
      if (!held && clock >= nextJump) {
        target = target > 5 ? 1 + Math.round(Math.random() * 6) / 2 : 6 + Math.round(Math.random() * 7) / 2
        nextJump = clock + 2.5
      }
      tickTime += dt
      while (tickTime >= TICK) {
        tickTime -= TICK
        tick()
      }
      draw()
      sinceWrite += dt
      if (sinceWrite > 0.15) {
        sinceWrite = 0
        write()
      }
    },
  )

  $effect(() => {
    speed
    smooth
    write()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="approach and smoothDamp">
  <canvas
    bind:this={canvas}
    class="mx-canvas"
    style="height: {HEIGHT}px"
    onpointerdown={(event) => {
      dragging = true
      held = true
      canvas?.setPointerCapture(event.pointerId)
      event.preventDefault()
      move(at(event))
    }}
    onpointermove={(event) => dragging && move(at(event))}
    onpointerup={release}
    onpointercancel={release}
  ></canvas>

  <div class="demo-controls">
    <Slider label="approach speed" min={1} max={30} step={1} bind:value={speed} />
    <Slider label="smoothTime" min={0.05} max={1.5} step={0.05} bind:value={smooth} />
  </div>

  <Note>
    Both run in game.stepped, twenty times a second. Drag on the graph to move the target. approach walks at a
    fixed speed and stops dead on it; smoothDamp speeds up, then eases in.
  </Note>
  <CodePanel {source} />
</Demo>
