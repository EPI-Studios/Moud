<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { arrow, fit, label, num, palette } from "../core/surface"

  const ORDERS = [
    "part.cframe * cframe.angles(0, dt, 0)",
    "cframe.angles(0, dt, 0) * part.cframe",
  ] as const

  const HEIGHT = 300

  let canvas = $state<HTMLCanvasElement | null>(null)
  let order = $state<(typeof ORDERS)[number]>(ORDERS[0])
  let speed = $state(1)
  let source = $state("")

  let yaw = 0
  let px = 5
  let pz = 0
  let trail: { x: number; z: number }[] = []

  const local = $derived(order.startsWith("part.cframe *"))
  const note = $derived(
    local
      ? "The turn is read in the part's own axes, so it spins around its own up axis and stays where it stands."
      : "The turn is read in the world's axes, so the part swings around the world's origin and its position turns with it.",
  )

  function reset() {
    yaw = 0
    px = 5
    pz = 0
    trail = []
  }

  function write() {
    const turn = speed === 1 ? order : order.replace("dt", `dt * ${speed}`)
    source =
      "game.stepped:connect(function(dt)\n" +
      `    part.cframe = ${turn}\n` +
      "end)\n\n" +
      `part.cframe.position      -- vec3(${num(px)}, 65, ${num(pz)})\n` +
      `part.cframe.lookVector    -- vec3(${num(-Math.sin(yaw))}, 0, ${num(-Math.cos(yaw))})`
  }

  function step(dt: number) {
    const a = dt * speed
    if (local) {
      yaw += a
    } else {
      const x = px * Math.cos(a) + pz * Math.sin(a)
      const z = -px * Math.sin(a) + pz * Math.cos(a)
      px = x
      pz = z
      yaw += a
    }
    trail.push({ x: px, z: pz })
    if (trail.length > 160) trail.shift()
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx
    const scale = Math.min(view.w / 16, view.h / 14)
    const ox = view.w / 2
    const oy = view.h / 2

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, view.w, view.h)
    ctx.strokeStyle = c.line
    ctx.lineWidth = 1
    for (let r = 1; r <= 7; r++) {
      ctx.beginPath()
      ctx.arc(ox, oy, r * scale, 0, Math.PI * 2)
      ctx.stroke()
    }
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(ox - 8, oy)
    ctx.lineTo(ox + 8, oy)
    ctx.moveTo(ox, oy - 8)
    ctx.lineTo(ox, oy + 8)
    ctx.stroke()
    ctx.font = `11px ${c.mono}`
    label(ctx, "world origin", ox - 10, oy + 14, c.light, "right")

    if (trail.length > 1) {
      ctx.strokeStyle = c.light
      ctx.lineWidth = 1.5
      ctx.beginPath()
      trail.forEach((p, i) => {
        const sx = ox + p.x * scale
        const sy = oy + p.z * scale
        if (i === 0) ctx.moveTo(sx, sy)
        else ctx.lineTo(sx, sy)
      })
      ctx.stroke()
    }

    const cx = ox + px * scale
    const cy = oy + pz * scale
    ctx.save()
    ctx.translate(cx, cy)
    ctx.rotate(-yaw)
    ctx.fillStyle = c.bg3
    ctx.strokeStyle = c.main
    ctx.lineWidth = 1.5
    ctx.fillRect(-scale * 1.1, -scale * 0.7, scale * 2.2, scale * 1.4)
    ctx.strokeRect(-scale * 1.1, -scale * 0.7, scale * 2.2, scale * 1.4)
    ctx.restore()

    const lx = -Math.sin(yaw)
    const lz = -Math.cos(yaw)
    arrow(ctx, cx, cy, cx + lx * scale * 2.4, cy + lz * scale * 2.4, c.accent, 2)
    const rx = Math.cos(yaw)
    const rz = -Math.sin(yaw)
    arrow(ctx, cx, cy, cx + rx * scale * 1.8, cy + rz * scale * 1.8, c.blue, 1.5)
    ctx.font = `11px ${c.mono}`
    label(ctx, "lookVector", cx + lx * scale * 2.4 + 6, cy + lz * scale * 2.4, c.accent)
    label(ctx, "rightVector", cx + rx * scale * 1.8 + 6, cy + rz * scale * 1.8, c.blue)
  }

  let last: number | null = null
  let sinceWrite = 0

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      step(dt)
      draw()
      sinceWrite += dt
      if (sinceWrite > 0.2) {
        sinceWrite = 0
        write()
      }
    },
  )

  $effect(() => {
    order
    reset()
  })

  $effect(() => {
    order
    speed
    write()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="f * g against g * f">
  <canvas bind:this={canvas} class="mx-canvas" style="height: {HEIGHT}px"></canvas>

  <div class="demo-controls">
    <Choice label="order" options={ORDERS} bind:value={order} />
    <Slider label="radians a second" min={0.2} max={3} step={0.1} bind:value={speed} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
