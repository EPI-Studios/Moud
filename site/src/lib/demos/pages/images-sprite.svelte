<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"
  import { checker } from "../core/imageRaster"
  import { whileVisible } from "../core/frames"
  import { pointIn } from "../core/pointer"
  import { ASSETS } from "../core/assets"

  const F = 24

  let frame = $state(0)
  let frames = $state("playing")
  let resample = $state("pixelated")

  let canvas = $state<HTMLCanvasElement | null>(null)
  let sheet = $state<HTMLImageElement | null>(null)
  let loaded = $state(false)

  const playing = $derived(frames === "playing")
  const pixelated = $derived(resample === "pixelated")

  let zoom = 4

  const source = $derived(
    'local sprite = hud:add("ImageLabel", {\n' +
      "    size = udim2.fromOffset(72, 72),\n" +
      '    image = "res://ui/bat.png",\n' +
      "    imageRectSize = vec3(24, 24, 0),\n" +
      (pixelated ? '    resampleMode = "pixelated",\n' : "") +
      "})\n\n" +
      `sprite.imageRectOffset = vec3(${frame * F}, 0, 0)   -- frame ${frame}`,
  )

  function draw() {
    if (!canvas) return
    const c = palette()
    const s = fit(canvas, 196)
    if (!s) return
    const ctx = s.ctx
    ctx.clearRect(0, 0, s.w, s.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, s.w, s.h)
    if (!loaded || !sheet || !sheet.naturalWidth) return

    zoom = s.w < 520 ? 3 : 5
    const sx = 12
    const sy = 30
    checker(ctx, sx, sy, 4 * F * zoom, F * zoom, zoom * 2)
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(sheet, sx, sy, 4 * F * zoom, F * zoom)
    ctx.strokeStyle = c.main
    ctx.lineWidth = 2
    ctx.strokeRect(sx + frame * F * zoom + 1, sy + 1, F * zoom - 2, F * zoom - 2)
    ctx.lineWidth = 1
    ctx.fillStyle = c.light
    ctx.font = "11px " + c.mono
    ctx.fillText("bat.png, 96 x 24", sx, sy - 10)
    ctx.fillText("imageRectOffset x = " + frame * F, sx, sy + F * zoom + 20)

    let ox = Math.min(sx + 4 * F * zoom + 30, s.w - 84)
    const oy = sx + 4 * F * zoom + 30 > s.w - 84 ? sy + F * zoom + 34 : sy
    if (oy !== sy) ox = s.w - 84
    checker(ctx, ox, oy, 72, 72, 8)
    ctx.imageSmoothingEnabled = !pixelated
    ctx.drawImage(sheet, frame * F, 0, F, F, ox, oy, 72, 72)
  }

  function pick(event: PointerEvent) {
    if (!canvas) return
    const p = pointIn(canvas, event, canvas.clientWidth, canvas.clientHeight)
    const x = p.x - 12
    const y = p.y - 30
    if (x >= 0 && x < 4 * F * zoom && y >= 0 && y < F * zoom) frame = Math.floor(x / (F * zoom))
  }

  let last = 0
  let wait = 0

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      if (!playing) return
      wait += dt
      if (wait >= 0.12) {
        wait -= 0.12
        frame = (frame + 1) % 4
      }
    },
  )

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

<Demo label="Sprite sheet">
  <canvas
    bind:this={canvas}
    class="images-canvas"
    style="height: 196px"
    aria-label="a sprite sheet and the frame it is showing"
    onpointerdown={pick}
  ></canvas>

  <Note>
    Stop it and click a frame on the sheet. Only the 24 by 24 part at imageRectOffset is drawn in the
    label. Art: Kenney, CC0.
  </Note>

  <div class="demo-controls">
    <Choice label="frames" options={["playing", "stopped"]} bind:value={frames} />
    <Choice label="resampleMode" options={["default", "pixelated"]} bind:value={resample} />
  </div>

  <CodePanel {source} />

  <img bind:this={sheet} src={ASSETS.bat} alt="" hidden onload={() => (loaded = true)} />
</Demo>
