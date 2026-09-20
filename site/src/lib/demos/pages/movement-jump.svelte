<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type PlayerEntity, type Scene } from "../core/mc2d"

  const GRAVITY = { "32": 32, "16": 16, "32 / 6": 32 / 6 }
  const PULLS = ["32", "16", "32 / 6"] as const
  const GROUND = 64
  const PLOT = 4.4

  let canvas = $state<HTMLCanvasElement | null>(null)
  let power = $state(8.4)
  let scale = $state(1)
  let pull = $state<(typeof PULLS)[number]>("32")

  let scene: Scene | null = null
  let steve: PlayerEntity | null = null
  let t = 0
  const frame = { metres: 1, seconds: 1 }

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function numbers(jump: number, gravityScale: number, gravity: (typeof PULLS)[number]) {
    const g = GRAVITY[gravity] * gravityScale
    return { g, v: jump, peak: (jump * jump) / (2 * g), air: (2 * jump) / g }
  }

  type Arc = ReturnType<typeof numbers>

  function height(k: Arc, at: number) {
    return Math.max(0, k.v * at - 0.5 * k.g * at * at)
  }

  const note = $derived.by(() => {
    const n = numbers(power, scale, pull)
    return (
      `The body leaves the ground at ${num(power, 1)} m/s against ${num(GRAVITY[pull], 2)} × ${num(scale)} = ${num(n.g, 2)} m/s² of pull. ` +
      `It peaks ${num(n.peak)} m up and lands after ${num(n.air)} s. The dashed curve is the default; both leave air drag out.`
    )
  })

  const source = $derived(
    "-- server\n" +
      `game.gravity = ${pull}\n` +
      `body.humanoid.jumpPower = ${num(power, 1)}\n` +
      `body.humanoid.gravityScale = ${num(scale)}`,
  )

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const n = numbers(power, scale, pull)
    const base = numbers(8.4, 1, "32")
    const left = PLOT
    const right = v.worldX(v.w - 14, 0.5)
    const gap = [0.5, 1, 2, 5, 10, 20].filter((s) => s * v.unit >= 24)[0] || 50
    for (let m = gap; m < frame.metres; m += gap) {
      const y = v.y(GROUND + m, 0.5)
      v.line(
        [
          [v.x(left, 0.5), y],
          [v.w, y],
        ],
        { width: 1, color: "rgba(255,255,255,0.5)", halo: false },
      )
      v.tag(`${num(m)} m`, v.w - 6, y - 10, { align: "right" })
    }

    const px = (at: number) => v.x(left + (at / frame.seconds) * (right - left), 0.5)
    const path = (k: Arc) => {
      const points: [number, number][] = []
      for (let i = 0; i <= 60; i++) {
        const at = (i / 60) * k.air
        points.push([px(at), v.y(GROUND + height(k, at), 0.5)])
      }
      return points
    }

    v.line(
      [
        [v.x(left, 0.5), v.y(GROUND, 0.5)],
        [v.w, v.y(GROUND, 0.5)],
      ],
      { width: 1, color: "rgba(255,255,255,0.6)", halo: false },
    )
    v.line(path(base), { dash: [3, 4], width: 1.25, color: "rgba(255,255,255,0.75)", halo: false })
    v.line(path(n), { width: 2 })

    const at = Math.min(t, n.air)
    ctx.fillStyle = v.colors.yellow
    ctx.strokeStyle = "rgba(0,0,0,0.5)"
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.arc(px(at), v.y(GROUND + height(n, at), 0.5), 4, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
    v.tag("time", v.x(left, 0.5), v.y(GROUND - 0.7, 0))
    v.tag("1 block", v.x(2.5, 0), v.y(GROUND + 1, 1) - 12, { align: "center" })
  }

  function place(n: Arc) {
    if (steve) steve.y = GROUND + height(n, Math.min(t, n.air))
  }

  function draw() {
    const n = numbers(power, scale, pull)
    const base = numbers(8.4, 1, "32")
    frame.metres = Math.max(n.peak, base.peak, 1.2) * 1.15
    frame.seconds = Math.max(n.air, base.air) * 1.1
    place(n)
    scene?.draw()
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const made = createScene(node, {
      height: 260,
      time: 1800,
      view: (w, h) => {
        const n = numbers(power, scale, pull)
        const tall = Math.max(frame.metres, n.peak + 2.1) + 1.7
        const unit = Math.min(h / tall, w / 12)
        return { x0: -2.4, x1: -2.4 + w / unit, y0: GROUND - 1.3, y1: GROUND - 1.3 + h / unit }
      },
      step: (dt) => {
        const n = numbers(power, scale, pull)
        t += dt
        if (t > n.air + 0.5) t = 0
        place(n)
      },
      overlay,
    })
    for (let x = -12; x <= 170; x++) {
      made.set(x, 63, "grass_block")
      made.set(x, 62, "dirt")
      made.set(x, 61, "dirt")
      for (let y = 52; y <= 60; y++) made.set(x, y, "stone")
    }
    made.set(2, 64, "cobblestone")
    steve = made.player({ x: 0.5, y: GROUND, facing: 1 })
    scene = made
    draw()
    made.start()
    return () => {
      made.destroy()
      scene = null
      steve = null
    }
  })

  $effect(() => {
    void power
    void scale
    void pull
    if (!canvas) return
    t = 0
    draw()
  })
</script>

<Demo label="Jump height">
  <canvas bind:this={canvas} class="mc2d"></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    <Slider label="jumpPower" min={4} max={14} step={0.2} bind:value={power} />
    <Slider label="gravityScale" min={0.5} max={2} step={0.1} bind:value={scale} />
    <Choice label="game.gravity" options={PULLS} bind:value={pull} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
