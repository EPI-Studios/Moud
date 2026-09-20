<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"
  import { Raster, paint } from "../core/imageRaster"
  import { clamp, pointIn } from "../core/pointer"

  const GW = 24
  const GH = 14

  let shape = $state("drawCircle")
  let x = $state(8)
  let y = $state(6)
  let radius = $state(4.5)
  let size = $state(7)
  let smooth = $state(false)

  let canvas = $state<HTMLCanvasElement | null>(null)
  let held = false

  const layout = { cell: 10, ox: 0, oy: 0 }

  const source = $derived.by(() => {
    const call =
      shape === "drawCircle"
        ? `canvas:drawCircle(${x}, ${y}, ${radius}`
        : `canvas:drawRectangle(${x}, ${y}, ${size}, ${size}`
    const tail = smooth ? ", { smooth = true })" : ")"
    const why =
      shape === "drawCircle"
        ? `-- x and y name a pixel: the circle is centred in pixel ${Math.floor(x)}, ${Math.floor(y)}`
        : x % 1 === 0 && y % 1 === 0 && size % 1 === 0
          ? `-- x and y are edges: it covers pixels ${x} to ${x + size - 1} across`
          : "-- x and y are edges; this one falls between them"
    return call + ", color(1, 1, 1)" + tail + "\n" + why
  })

  function draw() {
    if (!canvas) return
    const c = palette()
    const s = fit(canvas, 250)
    if (!s) return
    const ctx = s.ctx
    const cell = Math.floor(Math.min((s.w - 20) / GW, (s.h - 20) / GH))
    const ox = Math.round((s.w - cell * GW) / 2)
    const oy = Math.round((s.h - cell * GH) / 2)
    layout.cell = cell
    layout.ox = ox
    layout.oy = oy

    const grid = new Raster(GW, GH)
    const ink = paint(0.94, 0.94, 0.94, 0)
    if (shape === "drawCircle") grid.circle(x, y, radius, ink, { smooth })
    else grid.rectangle(x, y, size, size, ink, { smooth })

    ctx.clearRect(0, 0, s.w, s.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, s.w, s.h)
    for (let py = 0; py < GH; py++) {
      for (let px = 0; px < GW; px++) {
        const a = grid.px[(py * GW + px) * 4 + 3]
        const v = Math.round(38 + a * (230 - 38))
        ctx.fillStyle = `rgb(${v},${v},${v})`
        ctx.fillRect(ox + px * cell, oy + py * cell, cell - 1, cell - 1)
        ctx.fillStyle = a > 0.5 ? "rgba(0,0,0,0.45)" : "rgba(255,255,255,0.25)"
        ctx.fillRect(ox + px * cell + cell / 2 - 1, oy + py * cell + cell / 2 - 1, 2, 2)
      }
    }
    ctx.strokeStyle = c.blue
    ctx.lineWidth = 1.5
    ctx.beginPath()
    if (shape === "drawCircle") {
      ctx.arc(ox + (x + 0.5) * cell, oy + (y + 0.5) * cell, radius * cell, 0, Math.PI * 2)
      ctx.stroke()
      ctx.fillStyle = c.blue
      ctx.beginPath()
      ctx.arc(ox + (x + 0.5) * cell, oy + (y + 0.5) * cell, 3, 0, Math.PI * 2)
      ctx.fill()
    } else {
      ctx.rect(ox + x * cell, oy + y * cell, size * cell, size * cell)
      ctx.stroke()
      ctx.fillStyle = c.blue
      ctx.beginPath()
      ctx.arc(ox + x * cell, oy + y * cell, 3, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.lineWidth = 1
  }

  function move(event: PointerEvent) {
    if (!canvas) return
    const p = pointIn(canvas, event, canvas.clientWidth, canvas.clientHeight)
    let gx = (p.x - layout.ox) / layout.cell
    let gy = (p.y - layout.oy) / layout.cell
    if (shape === "drawCircle") {
      gx -= 0.5
      gy -= 0.5
    }
    x = clamp(Math.round(gx * 4) / 4, -2, GW)
    y = clamp(Math.round(gy * 4) / 4, -2, GH)
  }

  $effect(() => {
    draw()
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(() => draw())
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  let lastRadius = untrack(() => radius)

  $effect(() => {
    if (radius === lastRadius) return
    lastRadius = radius
    size = radius * 2
  })
</script>

<Demo label="Pixels, centres and edges">
  <canvas
    bind:this={canvas}
    class="images-canvas images-grab"
    style="height: 250px"
    aria-label="a grid of pixels with a shape drawn into it"
    onpointerdown={(event) => {
      held = true
      canvas?.setPointerCapture(event.pointerId)
      move(event)
    }}
    onpointermove={(event) => held && move(event)}
    onpointerup={() => (held = false)}
    onpointercancel={() => (held = false)}
  ></canvas>

  <Note>
    Drag the blue point; it moves in quarter pixels. Without smooth a pixel is painted only when its
    centre, the small dot, is inside the shape. With smooth an edge pixel is painted by how much of it
    the shape covers.
  </Note>

  <div class="demo-controls">
    <Choice label="call" options={["drawCircle", "drawRectangle"]} bind:value={shape} />
    <Slider label="radius / size" min={1} max={10} step={0.5} bind:value={radius} />
    <Choice label="smooth" options={[false, true]} bind:value={smooth} />
  </div>

  <CodePanel {source} />
</Demo>
