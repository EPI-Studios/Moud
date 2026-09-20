<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { whileVisible } from "../core/frames"
  import { clamp, pointIn } from "../core/pointer"
  import { fit, palette } from "../core/surface"

  type Interp = "linear" | "catmullrom" | "bezier" | "step"
  type Key = { t: number; v: number; interp: Interp }

  const HEIGHT = 240
  const INTERPS: Interp[] = ["linear", "catmullrom", "bezier", "step"]

  let keys = $state<Key[]>([
    { t: 0, v: 30, interp: "catmullrom" },
    { t: 0.3, v: -10, interp: "bezier" },
    { t: 0.55, v: -30, interp: "linear" },
    { t: 0.8, v: 0, interp: "step" },
    { t: 1, v: 30, interp: "linear" },
  ])
  let selected = $state(1)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let held = false
  let clock = 0
  const geom = { left: 40, right: 0, top: 16, bottom: HEIGHT - 26, leg: 0 }

  const onLastKey = $derived(selected === keys.length - 1)

  const source = $derived.by(() => {
    const rows = keys.map((key, i) => {
      const at = `${key.t},`.padEnd(5, " ")
      const tail = i === keys.length - 1 ? "]" : `, "${key.interp}"],`
      return `            [${at} [${Math.round(key.v)}, 0, 0]${tail}`
    })
    return `"joints": {\n    "rightLeg": {\n        "rotation": [\n${rows.join("\n")}\n        ]\n    }\n}`
  })

  function sampleCurve(list: Key[], time: number) {
    let at = -1
    for (let n = 0; n < list.length && list[n].t <= time; n++) at = n
    if (at < 0) return list[0].v
    if (at === list.length - 1) return list[list.length - 1].v
    const from = list[at]
    const to = list[at + 1]
    const alpha = (time - from.t) / (to.t - from.t)
    if (from.interp === "step") return from.v
    if (from.interp === "linear") return from.v + (to.v - from.v) * alpha
    if (from.interp === "catmullrom") {
      const p0 = at > 0 ? list[at - 1].v : from.v
      const p1 = from.v
      const p2 = to.v
      const p3 = at + 2 < list.length ? list[at + 2].v : to.v
      const t2 = alpha * alpha
      const t3 = t2 * alpha
      return 0.5 * (2 * p1 + (p2 - p0) * alpha + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (3 * p1 - p0 - 3 * p2 + p3) * t3)
    }
    const span = to.t - from.t
    const x0 = from.t
    const x1 = from.t + span / 3
    const x2 = to.t - span / 3
    const x3 = to.t
    const cubic = (a: number, b: number, c: number, d: number, s: number) => {
      const r = 1 - s
      return r * r * r * a + 3 * r * r * s * b + 3 * r * s * s * c + s * s * s * d
    }
    let low = 0
    let high = 1
    for (let i = 0; i < 32; i++) {
      const mid = (low + high) / 2
      if (cubic(x0, x1, x2, x3, mid) < time) low = mid
      else high = mid
    }
    return cubic(from.v, from.v, to.v, to.v, (low + high) / 2)
  }

  function gx(t: number) {
    return geom.left + t * (geom.right - geom.left)
  }

  function gy(v: number) {
    return geom.top + ((45 - v) / 90) * (geom.bottom - geom.top)
  }

  function limb(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    angle: number,
    length: number,
    width: number,
    color: string,
    fill: string,
  ) {
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(angle)
    ctx.fillStyle = fill
    ctx.strokeStyle = color
    ctx.lineWidth = 1.5
    ctx.fillRect(-width / 2, 0, width, length)
    ctx.strokeRect(-width / 2, 0, width, length)
    ctx.restore()
  }

  function draw() {
    const node = canvas
    if (!node) return
    const f = fit(node, HEIGHT)
    if (!f) return
    const c = palette()
    const ctx = f.ctx
    const legRoom = f.w > 460 ? 120 : 0
    geom.right = f.w - 16 - legRoom
    geom.leg = f.w - legRoom / 2 - 8
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, f.w, HEIGHT)
    ctx.font = `11px ${c.mono}`
    ctx.textBaseline = "middle"
    ctx.textAlign = "right"
    for (const v of [-30, 0, 30]) {
      ctx.strokeStyle = v === 0 ? c.line2 : c.line
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(geom.left, gy(v))
      ctx.lineTo(geom.right, gy(v))
      ctx.stroke()
      ctx.fillStyle = c.light
      ctx.fillText(`${v}°`, geom.left - 6, gy(v))
    }
    ctx.textAlign = "center"
    for (const t of [0, 0.25, 0.5, 0.75, 1]) {
      ctx.fillStyle = c.light
      ctx.fillText(`${t} s`, gx(t), HEIGHT - 12)
    }
    ctx.strokeStyle = c.main
    ctx.lineWidth = 2
    ctx.beginPath()
    for (let i = 0; i <= 200; i++) {
      const t = i / 200
      const v = sampleCurve(keys, t)
      if (i === 0) ctx.moveTo(gx(t), gy(v))
      else ctx.lineTo(gx(t), gy(v))
    }
    ctx.stroke()
    keys.forEach((key, i) => {
      ctx.fillStyle = i === selected ? c.yellow : c.bg
      ctx.strokeStyle = i === selected ? c.yellow : c.main
      ctx.lineWidth = 2
      ctx.save()
      ctx.translate(gx(key.t), gy(key.v))
      ctx.rotate(Math.PI / 4)
      ctx.fillRect(-5, -5, 10, 10)
      ctx.strokeRect(-5, -5, 10, 10)
      ctx.restore()
    })
    const time = Math.min(1, clock % 1.4)
    ctx.strokeStyle = c.accent
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(gx(time), geom.top - 6)
    ctx.lineTo(gx(time), geom.bottom)
    ctx.stroke()
    const angle = sampleCurve(keys, time)
    ctx.fillStyle = c.accent
    ctx.beginPath()
    ctx.arc(gx(time), gy(angle), 4, 0, Math.PI * 2)
    ctx.fill()
    if (legRoom) {
      const hx = geom.leg
      const hy = 50
      ctx.fillStyle = c.bg3
      ctx.strokeStyle = c.line2
      ctx.fillRect(hx - 14, hy - 60, 28, 60)
      ctx.strokeRect(hx - 14, hy - 60, 28, 60)
      limb(ctx, hx - 1, hy, (-angle * Math.PI) / 180, 110, 22, c.main, c.bg4)
      ctx.fillStyle = c.muted
      ctx.textAlign = "center"
      ctx.fillText(`rightLeg ${Math.round(angle)}°`, hx, HEIGHT - 12)
    }
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

<Demo label="Interpolation">
  <canvas
    bind:this={canvas}
    class="an-canvas an-drag"
    style="height: {HEIGHT}px"
    aria-label="a rotation curve with keys you can drag"
    onpointerdown={(event) => {
      const node = canvas
      if (!node) return
      const point = pointIn(node, event, node.clientWidth, HEIGHT)
      let best = -1
      let nearest = 24
      keys.forEach((key, i) => {
        const d = Math.hypot(point.x - gx(key.t), point.y - gy(key.v))
        if (d < nearest) {
          nearest = d
          best = i
        }
      })
      if (best === -1) return
      selected = best
      held = true
      node.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => {
      const node = canvas
      if (!held || !node) return
      const point = pointIn(node, event, node.clientWidth, HEIGHT)
      const v = 45 - ((point.y - geom.top) / (geom.bottom - geom.top)) * 90
      keys[selected].v = clamp(Math.round(v), -45, 45)
    }}
    onpointerup={() => (held = false)}
    onpointercancel={() => (held = false)}
  ></canvas>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">{onLastKey ? "last key" : `key ${selected + 1} to next`}</span>
      <div class="demo-choice-buttons">
        {#each INTERPS as option (option)}
          <button
            type="button"
            class="demo-pill"
            disabled={onLastKey}
            aria-pressed={option === keys[selected].interp}
            onclick={() => (keys[selected].interp = option)}
          >
            {option}
          </button>
        {/each}
      </div>
    </div>
  </div>

  <Note>
    Click a key to pick it and drag it up or down. Its interpolation shapes the segment to the next key.
  </Note>
  <CodePanel {source} />
</Demo>
