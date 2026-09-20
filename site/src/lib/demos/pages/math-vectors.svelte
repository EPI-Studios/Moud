<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { arrow, fit, label, num, palette } from "../core/surface"

  type Ground = { x: number; z: number }

  const HEIGHT = 300

  let canvas = $state<HTMLCanvasElement | null>(null)
  let v = $state<Ground>({ x: 3, z: 4 })
  let w = $state<Ground>({ x: -2, z: 5 })
  let t = $state(0.5)

  let scale = 1
  let origin = { x: 0, y: 0 }
  let held: Ground = v
  let dragging = false

  const readout = $derived.by(() => {
    const lv = Math.hypot(v.x, v.z)
    const lw = Math.hypot(w.x, w.z)
    const dot = lv > 0 && lw > 0 ? (v.x * w.x + v.z * w.z) / (lv * lw) : 0
    const angleTo = Math.acos(clamp(dot, -1, 1))
    const crossY = v.z * w.x - v.x * w.z
    const dist = Math.hypot(v.x - w.x, v.z - w.z)
    const m = { x: v.x + (w.x - v.x) * t, z: v.z + (w.z - v.z) * t }
    return (
      `local v = vec3(${num(v.x, 1)}, 0, ${num(v.z, 1)})\n` +
      `local w = vec3(${num(w.x, 1)}, 0, ${num(w.z, 1)})\n\n` +
      `v.magnitude            -- ${num(lv)}\n` +
      `v.unit                 -- vec3(${num(lv ? v.x / lv : 0)}, 0, ${num(lv ? v.z / lv : 0)})\n` +
      `v.unit:dot(w.unit)     -- ${num(dot)}\n` +
      `v:angleTo(w)           -- ${num(angleTo)} radians\n` +
      `v:distance(w)          -- ${num(dist)}\n` +
      `v:lerp(w, ${t})         -- vec3(${num(m.x)}, 0, ${num(m.z)})\n` +
      `v:cross(w)             -- vec3(0, ${num(crossY)}, 0)`
    )
  })

  function toScreen(p: Ground) {
    return { x: origin.x + p.x * scale, y: origin.y + p.z * scale }
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx

    scale = Math.min(view.w / 18, view.h / 13)
    origin = { x: view.w / 2, y: view.h / 2 - 1.5 * scale }

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, view.w, view.h)
    ctx.strokeStyle = c.line
    ctx.lineWidth = 1
    for (let gx = -12; gx <= 12; gx++) {
      const sx = origin.x + gx * scale
      ctx.beginPath()
      ctx.moveTo(sx, 0)
      ctx.lineTo(sx, view.h)
      ctx.stroke()
    }
    for (let gz = -10; gz <= 10; gz++) {
      const sy = origin.y + gz * scale
      ctx.beginPath()
      ctx.moveTo(0, sy)
      ctx.lineTo(view.w, sy)
      ctx.stroke()
    }
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(0, origin.y)
    ctx.lineTo(view.w, origin.y)
    ctx.moveTo(origin.x, 0)
    ctx.lineTo(origin.x, view.h)
    ctx.stroke()
    ctx.font = `11px ${c.mono}`
    label(ctx, "+x", view.w - 8, origin.y - 9, c.light, "right")
    label(ctx, "+z", origin.x + 8, view.h - 10, c.light)

    const lv = Math.hypot(v.x, v.z)
    const lw = Math.hypot(w.x, w.z)
    const av = Math.atan2(v.z, v.x)
    const aw = Math.atan2(w.z, w.x)
    if (lv > 0 && lw > 0) {
      let diff = aw - av
      while (diff > Math.PI) diff -= Math.PI * 2
      while (diff < -Math.PI) diff += Math.PI * 2
      ctx.strokeStyle = c.yellow
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.arc(origin.x, origin.y, scale * 1.4, av, av + diff, diff < 0)
      ctx.stroke()
    }

    const sv = toScreen(v)
    const sw = toScreen(w)
    ctx.setLineDash([4, 4])
    ctx.strokeStyle = c.light
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(sv.x, sv.y)
    ctx.lineTo(sw.x, sw.y)
    ctx.stroke()
    ctx.setLineDash([])

    arrow(ctx, origin.x, origin.y, sw.x, sw.y, c.blue, 2)
    arrow(ctx, origin.x, origin.y, sv.x, sv.y, c.main, 2)
    if (lv > 0) {
      const su = toScreen({ x: v.x / lv, z: v.z / lv })
      arrow(ctx, origin.x, origin.y, su.x, su.y, c.green, 3)
    }

    const sm = toScreen({ x: v.x + (w.x - v.x) * t, z: v.z + (w.z - v.z) * t })
    ctx.fillStyle = c.accent
    ctx.beginPath()
    ctx.arc(sm.x, sm.y, 5, 0, Math.PI * 2)
    ctx.fill()

    const ends: [{ x: number; y: number }, string, string][] = [
      [sv, c.main, "v"],
      [sw, c.blue, "w"],
    ]
    for (const [at, color, name] of ends) {
      ctx.fillStyle = c.bg
      ctx.strokeStyle = color
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.arc(at.x, at.y, 8, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      ctx.font = `600 12px ${c.mono}`
      label(ctx, name, at.x + 13, at.y - 10, color)
    }
    ctx.font = `11px ${c.mono}`
    label(ctx, "lerp", sm.x + 9, sm.y + 10, c.accent)
    label(ctx, "unit", origin.x + 6, origin.y + 14, c.green)
  }

  function at(event: PointerEvent) {
    return pointIn(canvas!, event, canvas!.clientWidth, canvas!.clientHeight)
  }

  function grab(point: { x: number; y: number }) {
    const sv = toScreen(v)
    const sw = toScreen(w)
    held = Math.hypot(point.x - sv.x, point.y - sv.y) <= Math.hypot(point.x - sw.x, point.y - sw.y) ? v : w
  }

  function move(point: { x: number; y: number }) {
    held.x = clamp(Math.round(((point.x - origin.x) / scale) * 2) / 2, -8, 8)
    held.z = clamp(Math.round(((point.y - origin.y) / scale) * 2) / 2, -5, 7)
  }

  $effect(() => {
    draw()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="Two vectors on the ground">
  <canvas
    bind:this={canvas}
    class="mx-canvas"
    style="height: {HEIGHT}px"
    onpointerdown={(event) => {
      dragging = true
      canvas?.setPointerCapture(event.pointerId)
      event.preventDefault()
      const point = at(event)
      grab(point)
      move(point)
    }}
    onpointermove={(event) => dragging && move(at(event))}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
  ></canvas>

  <div class="demo-controls">
    <Slider label="lerp t" min={0} max={1} step={0.05} bind:value={t} />
  </div>

  <Note>Seen from above: x runs across, z runs down the screen. Drag the ends of v and w.</Note>
  <CodePanel source={readout} />
</Demo>
