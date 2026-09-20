<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { pointIn } from "../core/pointer"
  import { fit, label, num, palette } from "../core/surface"

  const HEIGHT = 280

  function wrap(angle: number) {
    const turn = Math.PI * 2
    return ((((angle + Math.PI) % turn) + turn) % turn) - Math.PI
  }

  let canvas = $state<HTMLCanvasElement | null>(null)
  let a = $state(2.8)
  let b = $state(-2.9)
  let t = $state(0.5)

  let cx = 0
  let cy = 0
  let radius = 0
  let held: "a" | "b" | null = null

  const readout = $derived.by(() => {
    const naive = b - a
    const delta = wrap(b - a)
    const shortWay = a + delta * t
    const longWay = a + naive * t
    return (
      `local a, b = ${num(a)}, ${num(b)}\n\n` +
      `b - a                    -- ${num(naive)}, the long way round\n` +
      `angle.delta(a, b)        -- ${num(delta)}\n` +
      `angle.lerp(a, b, ${t})    -- ${num(shortWay)}\n` +
      `math.lerp(a, b, ${t})     -- ${num(longWay)}, back through zero\n` +
      `angle.wrap(${num(shortWay)})        -- ${num(wrap(shortWay))}`
    )
  })

  function onCircle(angle: number, r: number) {
    return { x: cx + Math.cos(angle) * r, y: cy - Math.sin(angle) * r }
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx

    cx = view.w / 2
    cy = view.h / 2
    radius = Math.min(view.h / 2 - 34, view.w / 2 - 40)

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, view.w, view.h)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0, Math.PI * 2)
    ctx.stroke()
    ctx.font = `11px ${c.mono}`
    const marks: [number, string][] = [
      [0, "0"],
      [Math.PI / 2, "pi/2"],
      [Math.PI, "pi | -pi"],
      [-Math.PI / 2, "-pi/2"],
    ]
    for (const [angle, text] of marks) {
      const p = onCircle(angle, radius + 16)
      label(ctx, text, p.x, p.y, c.light, "center")
    }
    ctx.strokeStyle = c.red
    ctx.setLineDash([3, 3])
    const seam = onCircle(Math.PI, radius)
    ctx.beginPath()
    ctx.moveTo(seam.x - 10, seam.y)
    ctx.lineTo(seam.x + 10, seam.y)
    ctx.stroke()
    ctx.setLineDash([])

    const naive = b - a
    const delta = wrap(b - a)
    ctx.lineWidth = 6
    ctx.strokeStyle = c.bg4
    ctx.beginPath()
    ctx.arc(cx, cy, radius - 18, -a, -b, naive > 0)
    ctx.stroke()
    ctx.strokeStyle = c.main
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.arc(cx, cy, radius - 30, -a, -(a + delta), delta > 0)
    ctx.stroke()

    const shortWay = a + delta * t
    const longWay = a + naive * t
    const ps = onCircle(shortWay, radius - 30)
    const pl = onCircle(longWay, radius - 18)
    ctx.fillStyle = c.light
    ctx.beginPath()
    ctx.arc(pl.x, pl.y, 5, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = c.accent
    ctx.beginPath()
    ctx.arc(ps.x, ps.y, 5, 0, Math.PI * 2)
    ctx.fill()
    ctx.strokeStyle = c.accent
    ctx.lineWidth = 1
    const spoke = onCircle(shortWay, radius - 36)
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(spoke.x, spoke.y)
    ctx.stroke()

    const handles: [string, number, string][] = [
      ["a", a, c.main],
      ["b", b, c.blue],
    ]
    for (const [name, angle, color] of handles) {
      const p = onCircle(angle, radius)
      ctx.fillStyle = c.bg
      ctx.strokeStyle = color
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.arc(p.x, p.y, 9, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      ctx.font = `600 12px ${c.mono}`
      label(ctx, name, p.x, p.y, color, "center")
    }
  }

  function at(event: PointerEvent) {
    return pointIn(canvas!, event, canvas!.clientWidth, canvas!.clientHeight)
  }

  function grab(point: { x: number; y: number }) {
    const pa = onCircle(a, radius)
    const pb = onCircle(b, radius)
    const da = Math.hypot(point.x - pa.x, point.y - pa.y)
    const db = Math.hypot(point.x - pb.x, point.y - pb.y)
    if (Math.min(da, db) > 40) return null
    return da <= db ? "a" : "b"
  }

  function move(point: { x: number; y: number }) {
    const angle = Math.round(Math.atan2(cy - point.y, point.x - cx) * 20) / 20
    if (held === "a") a = angle
    else if (held === "b") b = angle
  }

  $effect(() => {
    draw()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="The short way round">
  <canvas
    bind:this={canvas}
    class="mx-canvas"
    style="height: {HEIGHT}px"
    onpointerdown={(event) => {
      held = grab(at(event))
      if (!held) return
      canvas?.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => held && move(at(event))}
    onpointerup={() => (held = null)}
    onpointercancel={() => (held = null)}
  ></canvas>

  <div class="demo-controls">
    <Slider label="t" min={0} max={1} step={0.05} bind:value={t} />
  </div>

  <Note>
    Drag the two handles. The grey arc is b - a, the long way through zero. The white one is angle.delta, the
    shortest turn.
  </Note>
  <CodePanel source={readout} />
</Demo>
