<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { whileVisible } from "../core/frames"
  import { pointIn, type Point } from "../core/pointer"
  import { fit, palette } from "../core/surface"

  const HEIGHT = 300
  const U = 70
  const L1 = 1.3
  const L2 = 1.1

  const SOURCE = `model:add("IKControl", {
    type = "position",
    chainRoot = shoulder,
    endEffector = hand,
    target = world:find("handle"),
    pole = world:find("elbowHint"),
})`

  let weight = $state(1)
  let target = $state<Point>({ x: 1.6, y: -0.6 })
  let pole = $state<Point>({ x: 1.2, y: 1.2 })
  let canvas = $state<HTMLCanvasElement | null>(null)
  let held: "target" | "pole" | null = null
  let clock = 0
  const origin = { x: 0, y: 0 }

  function wrapAngle(a: number) {
    let out = a
    while (out > Math.PI) out -= Math.PI * 2
    while (out < -Math.PI) out += Math.PI * 2
    return out
  }

  function toScreen(p: Point) {
    return { x: origin.x + p.x * U, y: origin.y - p.y * U }
  }

  function toWorld(p: Point) {
    return { x: (p.x - origin.x) / U, y: (origin.y - p.y) / U }
  }

  function solve() {
    const d = Math.hypot(target.x, target.y)
    const base = Math.atan2(target.y, target.x)
    const reach = Math.max(Math.abs(L1 - L2) + 1e-4, Math.min(L1 + L2 - 1e-4, d))
    const a = Math.acos(Math.max(-1, Math.min(1, (L1 * L1 + reach * reach - L2 * L2) / (2 * L1 * reach))))
    let best = { upper: base, elbow: { x: 0, y: 0 }, dist: Infinity }
    for (const upper of [base + a, base - a]) {
      const elbow = { x: Math.cos(upper) * L1, y: Math.sin(upper) * L1 }
      const dist = Math.hypot(elbow.x - pole.x, elbow.y - pole.y)
      if (dist < best.dist) best = { upper, elbow, dist }
    }
    let lower = Math.atan2(target.y - best.elbow.y, target.x - best.elbow.x)
    if (d >= L1 + L2) lower = best.upper = base
    return { upper: best.upper, bend: wrapAngle(lower - best.upper) }
  }

  function draw() {
    const node = canvas
    if (!node) return
    const f = fit(node, HEIGHT)
    if (!f) return
    const c = palette()
    const ctx = f.ctx
    origin.x = Math.min(f.w * 0.3, 190)
    origin.y = HEIGHT * 0.4
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, f.w, HEIGHT)
    const animUpper = -Math.PI / 2 + Math.sin(clock * 1.5) * 0.35
    const animBend = 0.3 + Math.sin(clock * 1.5 + 1) * 0.2
    const solved = solve()
    const upper = animUpper + wrapAngle(solved.upper - animUpper) * weight
    const bend = animBend + wrapAngle(solved.bend - animBend) * weight
    const elbow = { x: Math.cos(upper) * L1, y: Math.sin(upper) * L1 }
    const hand = {
      x: elbow.x + Math.cos(upper + bend) * L2,
      y: elbow.y + Math.sin(upper + bend) * L2,
    }
    const s0 = toScreen({ x: 0, y: 0 })
    const s1 = toScreen(elbow)
    const s2 = toScreen(hand)
    const st = toScreen(target)
    const sp = toScreen(pole)

    ctx.fillStyle = c.bg3
    ctx.strokeStyle = c.line2
    ctx.fillRect(s0.x - 40, s0.y - 30, 44, 150)
    ctx.strokeRect(s0.x - 40, s0.y - 30, 44, 150)

    ctx.setLineDash([4, 4])
    ctx.strokeStyle = c.light
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(s2.x, s2.y)
    ctx.lineTo(st.x, st.y)
    ctx.moveTo(s1.x, s1.y)
    ctx.lineTo(sp.x, sp.y)
    ctx.stroke()
    ctx.setLineDash([])

    ctx.strokeStyle = c.main
    ctx.lineCap = "round"
    ctx.lineWidth = 14
    ctx.beginPath()
    ctx.moveTo(s0.x, s0.y)
    ctx.lineTo(s1.x, s1.y)
    ctx.stroke()
    ctx.strokeStyle = c.accent
    ctx.lineWidth = 11
    ctx.beginPath()
    ctx.moveTo(s1.x, s1.y)
    ctx.lineTo(s2.x, s2.y)
    ctx.stroke()
    ctx.lineCap = "butt"
    for (const p of [s0, s1, s2]) {
      ctx.fillStyle = c.bg
      ctx.beginPath()
      ctx.arc(p.x, p.y, 3.5, 0, Math.PI * 2)
      ctx.fill()
    }

    ctx.fillStyle = c.yellow
    ctx.save()
    ctx.translate(st.x, st.y)
    ctx.rotate(Math.PI / 4)
    ctx.fillRect(-7, -7, 14, 14)
    ctx.restore()
    ctx.strokeStyle = c.blue
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.arc(sp.x, sp.y, 8, 0, Math.PI * 2)
    ctx.stroke()
    ctx.font = `11px ${c.mono}`
    ctx.textBaseline = "middle"
    ctx.textAlign = "left"
    ctx.fillStyle = c.yellow
    ctx.fillText("target", st.x + 12, st.y)
    ctx.fillStyle = c.blue
    ctx.fillText("pole", sp.x + 12, sp.y)
    ctx.fillStyle = c.muted
    ctx.fillText("shoulder", s0.x + 10, s0.y - 12)
    ctx.fillText("hand", s2.x + 10, s2.y + 12)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(() => draw())
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    draw()
  })

  let previous = 0
  whileVisible(
    () => canvas,
    (now) => {
      const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0
      previous = now
      clock += dt
      draw()
    },
  )
</script>

<Demo label="A two-bone reach">
  <canvas
    bind:this={canvas}
    class="an-canvas an-drag"
    style="height: {HEIGHT}px"
    aria-label="a two bone arm reaching for a target you can drag"
    onpointerdown={(event) => {
      const node = canvas
      if (!node) return
      const point = pointIn(node, event, node.clientWidth, HEIGHT)
      const st = toScreen(target)
      const sp = toScreen(pole)
      const dt = Math.hypot(point.x - st.x, point.y - st.y)
      const dp = Math.hypot(point.x - sp.x, point.y - sp.y)
      if (Math.min(dt, dp) > 40) return
      held = dt <= dp ? "target" : "pole"
      node.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => {
      const node = canvas
      if (!held || !node) return
      const point = toWorld(pointIn(node, event, node.clientWidth, HEIGHT))
      if (held === "target") target = point
      else pole = point
    }}
    onpointerup={() => (held = null)}
    onpointercancel={() => (held = null)}
  ></canvas>

  <div class="demo-controls">
    <Slider label="weight" min={0} max={1} step={0.05} bind:value={weight} />
  </div>

  <Note>
    Drag the diamond, the target, and the circle, the pole. The elbow bends toward the pole; out of reach,
    the arm points straight at the target.
  </Note>
  <CodePanel source={SOURCE} />
</Demo>
