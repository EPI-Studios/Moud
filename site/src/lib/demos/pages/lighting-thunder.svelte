<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { fit, palette } from "../core/surface"
  import { whileVisible } from "../core/frames"

  const MAXD = 2000
  const PAD_LEFT = 30

  let distance = $state(40)
  let player = $state("outdoors")
  let flash = $state(0)

  const indoors = $derived(player === "indoors")
  let lines = $state<Line[]>([{ id: 0, text: "drag the bolt, then strike", kind: "idle" }])
  let next = 1

  let view = $state<HTMLCanvasElement | null>(null)
  let held = false
  let timer = 0

  function toX(d: number, w: number) {
    return PAD_LEFT + Math.sqrt(d / MAXD) * (w - PAD_LEFT - 16)
  }

  function fromX(x: number, w: number) {
    const t = clamp((x - PAD_LEFT) / (w - PAD_LEFT - 16), 0, 1)
    return Math.round(t * t * MAXD)
  }

  const delay = $derived(Math.min(5, distance / 343))
  const volume = $derived(clamp(1.2 - distance / 400, 0.25, 1) * (indoors ? 0.35 : 1))

  const source = $derived(
    `weather:strike(vec3(${distance}, 70, 0))\n` +
      "-- a player at 0, 70, 0 sees the flash at once\n" +
      `-- thunder ${delay.toFixed(2)} s later at volume ${volume.toFixed(2)}` +
      (distance < 48 ? "\n-- with the crack, from where it struck" : ""),
  )

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function draw() {
    if (!view) return
    const c = palette()
    const f = fit(view, view.clientHeight)
    if (!f) return
    const ctx = f.ctx
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, f.w, f.h)
    if (flash > 0) {
      ctx.fillStyle = "rgba(220,230,255," + (flash * 0.35).toFixed(3) + ")"
      ctx.fillRect(0, 0, f.w, f.h)
    }
    const ground = f.h - 28
    ctx.beginPath()
    ctx.moveTo(0, ground + 0.5)
    ctx.lineTo(f.w, ground + 0.5)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1
    ctx.stroke()
    ctx.font = "10px " + c.mono
    ctx.textAlign = "center"
    for (const d of [16, 48, 343, 1000, 1715]) {
      const x = toX(d, f.w)
      ctx.beginPath()
      ctx.moveTo(x + 0.5, ground)
      ctx.lineTo(x + 0.5, ground + 5)
      ctx.strokeStyle = c.muted
      ctx.stroke()
      ctx.fillStyle = c.light
      if (d !== 16 || f.w >= 460) ctx.fillText(String(d), x, ground + 17)
    }
    const crack = toX(48, f.w)
    ctx.fillStyle = "rgba(255,255,255,0.04)"
    ctx.fillRect(PAD_LEFT, 8, crack - PAD_LEFT, ground - 8)
    ctx.textAlign = "left"
    ctx.fillStyle = c.light
    ctx.fillText("crack", PAD_LEFT + 4, 20)
    ctx.textAlign = "right"
    ctx.fillText(f.w >= 460 ? "blocks from the player" : "blocks", f.w - 10, 20)
    ctx.fillStyle = c.main
    ctx.fillRect(PAD_LEFT - 4, ground - 16, 8, 16)
    if (indoors) {
      ctx.strokeStyle = c.muted
      ctx.beginPath()
      ctx.moveTo(PAD_LEFT - 12, ground)
      ctx.lineTo(PAD_LEFT - 12, ground - 24)
      ctx.lineTo(PAD_LEFT + 12, ground - 24)
      ctx.lineTo(PAD_LEFT + 12, ground)
      ctx.stroke()
    }
    const sx = toX(distance, f.w)
    ctx.beginPath()
    ctx.moveTo(sx + 4, 6)
    ctx.lineTo(sx - 5, (ground - 6) * 0.45)
    ctx.lineTo(sx + 3, (ground - 6) * 0.5)
    ctx.lineTo(sx - 3, ground)
    ctx.strokeStyle = flash > 0 ? c.main : c.blue
    ctx.lineWidth = 2
    ctx.stroke()
  }

  function scrub(event: PointerEvent) {
    if (!view) return
    const p = pointIn(view, event, view.clientWidth, view.clientHeight)
    distance = Math.max(1, fromX(p.x, view.clientWidth))
  }

  function strike() {
    const d = distance
    const wait = delay
    const loud = volume
    const near = d < 48
    flash = 1
    record(`flash, ${d} blocks away`, "idle")
    clearTimeout(timer)
    timer = window.setTimeout(() => {
      record(`thunder after ${wait.toFixed(2)} s at volume ${loud.toFixed(2)}`, "out")
      if (near) record(`crack from where it struck, volume ${indoors ? "0.35" : "1"}`, "in")
    }, wait * 1000)
  }

  let last = 0

  whileVisible(
    () => view,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      if (flash > 0) flash = Math.max(0, flash - dt * 2.5)
    },
  )

  $effect(() => {
    draw()
  })

  $effect(() => {
    const node = view
    if (!node) return
    let width = 0
    const watcher = new ResizeObserver(() => {
      if (node.clientWidth === width) return
      width = node.clientWidth
      draw()
    })
    watcher.observe(node)
    return () => {
      watcher.disconnect()
      clearTimeout(timer)
    }
  })
</script>

<Demo label="Thunder after a strike">
  <div class="lighting-wide">
    <canvas
      bind:this={view}
      class="lighting-canvas lighting-drag lighting-thunder"
      style="aspect-ratio: 1 / 0.26"
      aria-label="a bolt at a distance from the player"
      onpointerdown={(event) => {
        held = true
        view?.setPointerCapture(event.pointerId)
        scrub(event)
      }}
      onpointermove={(event) => held && scrub(event)}
      onpointerup={() => (held = false)}
      onpointercancel={() => (held = false)}
    ></canvas>
  </div>

  <div class="demo-controls">
    <Slider label="distance" min={1} max={MAXD} step={1} bind:value={distance} />
    <Choice label="player" options={["outdoors", "indoors"]} bind:value={player} />
    <div class="demo-controls demo-row">
      <button type="button" class="demo-pill demo-pill-wide" onclick={strike}>weather:strike</button>
    </div>
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
