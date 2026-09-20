<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"
  import { Raster, checker, paint, type BlendMode } from "../core/imageRaster"
  import { ASSETS } from "../core/assets"

  const COLORS: Record<string, { rgb: [number, number, number]; code: string }> = {
    white: { rgb: [1, 1, 1], code: "color(1, 1, 1)" },
    red: { rgb: [1, 0.2, 0.2], code: "color(1, 0.2, 0.2)" },
    black: { rgb: [0, 0, 0], code: "color(0, 0, 0)" },
  }

  let blend = $state<BlendMode>("over")
  let transparency = $state(0.3)
  let color = $state("white")

  let canvas = $state<HTMLCanvasElement | null>(null)
  let out = $state<HTMLCanvasElement | null>(null)
  let reader = $state<HTMLCanvasElement | null>(null)
  let photo = $state<HTMLImageElement | null>(null)
  let pixels = $state<ImageData | null>(null)

  const source = $derived(
    "local canvas = images.create(128, 80)\n" +
      'canvas:drawImage(images.load("res://ui/landscape.png"), 0, 0, { width = 96, height = 80 })\n' +
      `canvas:drawCircle(96, 40, 28, ${COLORS[color].code}, {\n` +
      `    blend = "${blend}", transparency = ${transparency}, smooth = true,\n` +
      "})",
  )

  function read() {
    if (!reader || !photo) return
    reader.width = photo.naturalWidth
    reader.height = photo.naturalHeight
    const ctx = reader.getContext("2d")
    if (!ctx) return
    ctx.drawImage(photo, 0, 0)
    pixels = ctx.getImageData(0, 0, reader.width, reader.height)
  }

  function draw() {
    if (!canvas || !out) return
    const c = palette()
    const grid = new Raster(128, 80)
    if (pixels) {
      for (let y = 0; y < 80; y++) {
        for (let x = 0; x < 96; x++) {
          const from = (y * pixels.width + x) * 4
          const to = (y * 128 + x) * 4
          grid.px[to] = pixels.data[from] / 255
          grid.px[to + 1] = pixels.data[from + 1] / 255
          grid.px[to + 2] = pixels.data[from + 2] / 255
          grid.px[to + 3] = 1
        }
      }
    }
    const ink = COLORS[color]
    grid.circle(96, 40, 28, paint(ink.rgb[0], ink.rgb[1], ink.rgb[2], transparency, blend), {
      smooth: true,
    })
    out.width = 128
    out.height = 80
    const octx = out.getContext("2d")
    if (!octx) return
    octx.putImageData(grid.toImageData(), 0, 0)

    const s = fit(canvas, 260)
    if (!s) return
    const ctx = s.ctx
    ctx.clearRect(0, 0, s.w, s.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, s.w, s.h)
    const z = Math.max(1, Math.floor(Math.min((s.w - 20) / 128, (s.h - 20) / 80)))
    const ox = Math.round((s.w - 128 * z) / 2)
    const oy = Math.round((s.h - 80 * z) / 2)
    checker(ctx, ox, oy, 128 * z, 80 * z, z * 2)
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(out, ox, oy, 128 * z, 80 * z)
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
</script>

<Demo label="Blends">
  <canvas
    bind:this={canvas}
    class="images-canvas"
    style="height: 260px"
    aria-label="a circle blended over an image"
  ></canvas>

  <Note>
    The right quarter of the image is fully see-through, shown as the checks. Watch what each blend
    does there: add paints colour into it, multiply and erase leave it see-through, replace with
    transparency 1 cuts a hole. Photo: Andrew Ridley, Unsplash.
  </Note>

  <div class="demo-controls">
    <Choice
      label="blend"
      options={["over", "replace", "add", "multiply", "erase"] as BlendMode[]}
      bind:value={blend}
    />
    <Slider label="transparency" min={0} max={1} step={0.05} bind:value={transparency} />
    <Choice label="color" options={["white", "red", "black"]} bind:value={color} />
  </div>

  <CodePanel {source} />

  <canvas bind:this={out} hidden></canvas>
  <canvas bind:this={reader} hidden></canvas>
  <img bind:this={photo} src={ASSETS.photo} alt="" hidden onload={read} />
</Demo>
