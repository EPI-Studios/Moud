<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"
  import { checker } from "../core/imageRaster"
  import { ASSETS } from "../core/assets"

  const N = 64

  let min = $state(16)
  let max = $state(48)
  let scale = $state(1)
  let width = $state(260)
  let height = $state(140)

  let canvas = $state<HTMLCanvasElement | null>(null)
  let panel = $state<HTMLImageElement | null>(null)
  let loaded = $state(false)

  const source = $derived(
    'hud:add("ImageLabel", {\n' +
      `    size = udim2.fromOffset(${width}, ${height}),\n` +
      '    image = "res://ui/panel.png",\n' +
      '    scaleType = "slice",\n' +
      `    sliceMin = vec3(${min}, ${min}, 0),\n` +
      `    sliceMax = vec3(${max}, ${max}, 0),\n` +
      `    sliceScale = ${scale},\n` +
      "})",
  )

  function draw() {
    if (!canvas) return
    const c = palette()
    const s = fit(canvas, 240)
    if (!s) return
    const ctx = s.ctx
    ctx.clearRect(0, 0, s.w, s.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, s.w, s.h)
    ctx.imageSmoothingEnabled = false
    if (!loaded || !panel || !panel.naturalWidth) return

    const z = s.w < 520 ? 2 : 3
    const sx = 12
    const sy = 12
    checker(ctx, sx, sy, N * z, N * z, z * 2)
    ctx.drawImage(panel, sx, sy, N * z, N * z)
    ctx.strokeStyle = c.main
    ctx.setLineDash([3, 3])
    for (const v of [min, max]) {
      ctx.beginPath()
      ctx.moveTo(sx + v * z + 0.5, sy - 6)
      ctx.lineTo(sx + v * z + 0.5, sy + N * z + 6)
      ctx.moveTo(sx - 6, sy + v * z + 0.5)
      ctx.lineTo(sx + N * z + 6, sy + v * z + 0.5)
      ctx.stroke()
    }
    ctx.setLineDash([])
    ctx.fillStyle = c.light
    ctx.font = "11px " + c.mono
    ctx.fillText("panel.png, 64 x 64", sx, sy + N * z + 22)

    const left = sx + N * z + 28
    const room = s.w - left - 10
    const k = Math.min(1, room / width, (s.h - 30) / height)
    const bw = width * k
    const bh = height * k
    const bx = left
    const by = 12
    checker(ctx, bx, by, bw, bh, 8)
    const stretch = !(max > min)
    if (stretch) {
      ctx.imageSmoothingEnabled = false
      ctx.drawImage(panel, Math.round(bx), Math.round(by), Math.round(bw), Math.round(bh))
    } else {
      let cl = min * scale
      let cr = (N - max) * scale
      let ct = cl
      let cb = cr
      const f =
        k * Math.min(1, width / Math.max(0.0001, cl + cr), height / Math.max(0.0001, ct + cb))
      cl *= f
      cr *= f
      ct *= f
      cb *= f
      const srcX = [0, min, max, N]
      const dstX = [bx, bx + cl, bx + bw - cr, bx + bw].map(Math.round)
      const dstY = [by, by + ct, by + bh - cb, by + bh].map(Math.round)
      ctx.imageSmoothingEnabled = false
      for (let j = 0; j < 3; j++) {
        for (let i = 0; i < 3; i++) {
          const sw = srcX[i + 1] - srcX[i]
          const sh = srcX[j + 1] - srcX[j]
          const dw = dstX[i + 1] - dstX[i]
          const dh = dstY[j + 1] - dstY[j]
          if (sw > 0 && sh > 0 && dw > 0 && dh > 0) {
            ctx.drawImage(panel, srcX[i], srcX[j], sw, sh, dstX[i], dstY[j], dw, dh)
          }
        }
      }
      ctx.strokeStyle = "rgba(240,240,240,0.35)"
      ctx.setLineDash([3, 3])
      ctx.beginPath()
      for (const x of [dstX[1], dstX[2]]) {
        ctx.moveTo(x + 0.5, by)
        ctx.lineTo(x + 0.5, by + bh)
      }
      for (const y of [dstY[1], dstY[2]]) {
        ctx.moveTo(bx, y + 0.5)
        ctx.lineTo(bx + bw, y + 0.5)
      }
      ctx.stroke()
      ctx.setLineDash([])
    }
    ctx.fillStyle = c.light
    ctx.fillText(
      (stretch ? "sliceMax is not past sliceMin: stretched" : `${width} x ${height}`) +
        (k < 1 ? ", drawn at " + Math.round(k * 100) + "%" : ""),
      bx,
      Math.min(s.h - 6, by + bh + 16),
    )
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

<Demo label="Nine-slice">
  <canvas
    bind:this={canvas}
    class="images-canvas"
    style="height: 240px"
    aria-label="a nine-slice panel drawn into a box"
  ></canvas>

  <Note>
    The dashed lines on the image are sliceMin and sliceMax. The corners keep their size times
    sliceScale; shrink the box below them and they shrink to fit. Art: Kenney, CC0.
  </Note>

  <div class="demo-controls">
    <Slider label="sliceMin" min={0} max={32} step={1} bind:value={min} />
    <Slider label="sliceMax" min={32} max={64} step={1} bind:value={max} />
    <Slider label="sliceScale" min={0.5} max={4} step={0.5} bind:value={scale} />
    <Slider label="width" min={16} max={400} step={4} bind:value={width} />
    <Slider label="height" min={16} max={200} step={4} bind:value={height} />
  </div>

  <CodePanel {source} />

  <img bind:this={panel} src={ASSETS.panel} alt="" hidden onload={() => (loaded = true)} />
</Demo>
