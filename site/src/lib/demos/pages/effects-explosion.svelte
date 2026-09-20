<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { cssVar, fitScaled, mono } from "../core/surface"

  const W = 420
  const H = 240
  const SCALE = 12

  type Thing =
    | { name: string; kind: "part"; x: number; y: number; w: number; h: number; anchored: boolean }
    | { name: string; kind: "body"; x: number; y: number; player: boolean }

  const THINGS: Thing[] = [
    { name: "crate", kind: "part", x: 21, y: 7, w: 2, h: 2, anchored: false },
    { name: "wall", kind: "part", x: 9, y: 10, w: 1, h: 10, anchored: true },
    { name: "barrel", kind: "part", x: 17, y: 15, w: 1.2, h: 1.2, anchored: false },
    { name: "plank", kind: "part", x: 28, y: 12, w: 4, h: 0.6, anchored: false },
    { name: "player", kind: "body", player: true, x: 13, y: 5 },
    { name: "npc", kind: "body", player: false, x: 25, y: 16 },
  ]

  let radius = $state(8)
  let pressure = $state(500000)
  let percent = $state(0.4)
  let centre = $state({ x: 15, y: 10 })

  let canvas = $state<HTMLCanvasElement | null>(null)
  let dragging = false

  const num = (v: number) => String(Math.round(v * 100) / 100)

  function nearest(t: Thing & { kind: "part" }) {
    const ox = centre.x - clamp(centre.x, t.x - t.w / 2, t.x + t.w / 2)
    const oy = centre.y - clamp(centre.y, t.y - t.h / 2, t.y + t.h / 2)
    return Math.sqrt(ox * ox + oy * oy)
  }

  function speedAt(distance: number) {
    return (Math.max(0, pressure) * clamp(1 - distance / radius, 0, 1)) / 25000
  }

  const results = $derived(
    THINGS.map((t) => {
      const fromCentre = Math.hypot(t.x - centre.x, t.y - centre.y)
      if (t.kind === "part") {
        const distance = nearest(t)
        const caught = distance <= radius
        return { t, fromCentre, distance, caught, speed: caught && !t.anchored ? speedAt(fromCentre) : 0, dies: false }
      }
      const caught = fromCentre <= radius
      return {
        t,
        fromCentre,
        distance: fromCentre,
        caught,
        speed: caught && t.player ? speedAt(fromCentre) : 0,
        dies: fromCentre <= radius * percent,
      }
    }),
  )

  const rows = $derived(
    results.map((r) => {
      let what: string
      if (!r.caught) what = "-"
      else if (r.t.kind === "part" && r.t.anchored) what = "anchored, not pushed"
      else if (r.t.kind === "body" && !r.t.player) what = "not a player, not pushed"
      else what = `${r.speed.toFixed(1)} m/s`
      if (r.t.kind === "body" && r.dies) what += ", dies"
      return {
        name: r.t.name,
        distance: `${r.distance.toFixed(1)} m`,
        hit: r.t.kind === "part" ? (r.caught ? "fires" : "-") : "not for bodies",
        what,
        dies: r.t.kind === "body" && r.dies,
      }
    }),
  )

  const source = $derived(
    'local blast = world:add("Explosion", {\n' +
      `    position = vec3(${num(centre.x)}, 64, ${num(centre.y)}),\n` +
      `    blastRadius = ${num(radius)},\n` +
      `    blastPressure = ${pressure},\n` +
      `    destroyJointRadiusPercent = ${num(percent)},\n` +
      '    explosionType = "noCraters",\n' +
      "})\n" +
      "blast.hit:connect(function(part, distance) end)\n" +
      `-- kills inside ${num(radius * percent)} m`,
  )

  function draw() {
    if (!canvas) return
    const view = fitScaled(canvas, W, H)
    if (!view) return
    const { ctx, unit } = view

    ctx.fillStyle = cssVar("--bg")
    ctx.fillRect(0, 0, W, H)

    const cx = centre.x * SCALE
    const cy = centre.y * SCALE
    ctx.fillStyle = "rgba(255,106,92,0.14)"
    ctx.beginPath()
    ctx.arc(cx, cy, radius * percent * SCALE, 0, Math.PI * 2)
    ctx.fill()
    ctx.strokeStyle = cssVar("--text-muted")
    ctx.setLineDash([4, 4])
    ctx.beginPath()
    ctx.arc(cx, cy, radius * SCALE, 0, Math.PI * 2)
    ctx.stroke()
    ctx.setLineDash([])

    for (const r of results) {
      const t = r.t
      const x = t.x * SCALE
      const y = t.y * SCALE
      const color = r.caught ? cssVar("--text-main") : cssVar("--text-light")
      if (t.kind === "part") {
        ctx.fillStyle = t.anchored ? cssVar("--bg-4") : cssVar("--surface")
        ctx.strokeStyle = color
        ctx.fillRect(x - (t.w * SCALE) / 2, y - (t.h * SCALE) / 2, t.w * SCALE, t.h * SCALE)
        ctx.strokeRect(x - (t.w * SCALE) / 2 + 0.5, y - (t.h * SCALE) / 2 + 0.5, t.w * SCALE, t.h * SCALE)
      } else {
        ctx.fillStyle = r.dies ? cssVar("--red") : color
        ctx.beginPath()
        ctx.arc(x, y, 6, 0, Math.PI * 2)
        ctx.fill()
      }

      if (r.speed > 0) {
        const len = Math.hypot(t.x - centre.x, t.y - centre.y) || 1
        const ax = (t.x - centre.x) / len
        const ay = (t.y - centre.y) / len
        const reach = 6 + r.speed * 3
        ctx.strokeStyle = cssVar("--yellow")
        ctx.lineWidth = 1.5
        ctx.beginPath()
        ctx.moveTo(x, y)
        ctx.lineTo(x + ax * reach, y + ay * reach)
        ctx.stroke()
        ctx.lineWidth = 1
      }

      ctx.fillStyle = cssVar("--text-muted")
      ctx.font = mono(unit, 11)
      const labelX = t.kind === "part" ? x + (t.w * SCALE) / 2 + 4 : x + 8
      const labelY = t.kind === "part" ? y - (t.h * SCALE) / 2 + 10 : y - 8
      ctx.fillText(t.name, labelX, labelY)
    }

    ctx.fillStyle = cssVar("--red")
    ctx.beginPath()
    ctx.arc(cx, cy, 5, 0, Math.PI * 2)
    ctx.fill()
    ctx.strokeStyle = cssVar("--text-main")
    ctx.beginPath()
    ctx.arc(cx, cy, 8, 0, Math.PI * 2)
    ctx.stroke()
  }

  function grab(event: PointerEvent) {
    if (!canvas) return
    dragging = true
    canvas.setPointerCapture(event.pointerId)
    const p = pointIn(canvas, event, W, H)
    centre = { x: p.x / SCALE, y: p.y / SCALE }
  }

  function move(event: PointerEvent) {
    if (!dragging || !canvas) return
    const p = pointIn(canvas, event, W, H)
    centre = { x: clamp(p.x, 0, W) / SCALE, y: clamp(p.y, 0, H) / SCALE }
  }

  $effect(() => {
    draw()
  })

  $effect(() => {
    const redraw = () => draw()
    window.addEventListener("resize", redraw)
    return () => window.removeEventListener("resize", redraw)
  })
</script>

<Demo label="Explosion">
  <Stage>
    <canvas
      bind:this={canvas}
      class="effects-canvas effects-drag"
      style="aspect-ratio: {W} / {H}"
      onpointerdown={grab}
      onpointermove={move}
      onpointerup={() => (dragging = false)}
      onpointercancel={() => (dragging = false)}
    ></canvas>
  </Stage>

  <p class="demo-note">
    Drag the blast. The dashed ring is blastRadius and the red disc is the share of it that kills. A
    part counts from its nearest face, a body from where it stands, and the push fades to nothing at
    the edge. Of the bodies, only a player's is thrown.
  </p>

  <table class="demo-matrix effects-table">
    <tbody>
      <tr>
        <th>thing</th>
        <th>distance</th>
        <th>hit</th>
        <th>thrown at</th>
      </tr>
      {#each rows as row (row.name)}
        <tr>
          <th>{row.name}</th>
          <td>{row.distance}</td>
          <td>{row.hit}</td>
          <td class={row.dies ? "effects-dies" : ""}>{row.what}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="demo-controls">
    <Slider label="blastRadius" min={1} max={14} step={0.5} bind:value={radius} />
    <Slider label="blastPressure" min={0} max={1000000} step={50000} bind:value={pressure} />
    <Slider label="destroyJoint..." min={0} max={1} step={0.05} bind:value={percent} />
  </div>

  <CodePanel {source} />
</Demo>
