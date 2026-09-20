<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fit, palette } from "../core/surface"

  let type = $state("fit")
  let width = $state(220)
  let height = $state(120)
  let tile = $state(32)
  let resample = $state("pixelated")

  let canvas = $state<HTMLCanvasElement | null>(null)
  let photo = $state<HTMLImageElement | null>(null)
  let loaded = $state(false)
  let stageWidth = $state(0)

  const pixelated = $derived(resample === "pixelated")
  const boxWidth = $derived(Math.min(width, stageWidth - 20))

  const source = $derived.by(() => {
    const lines = [
      'hud:add("ImageLabel", {',
      `    size = udim2.fromOffset(${boxWidth}, ${height}),`,
      '    image = "res://ui/landscape.png",',
      `    scaleType = "${type}",`,
    ]
    if (type === "tile") lines.push(`    tileSize = udim2.fromOffset(${tile}, ${tile}),`)
    if (pixelated) lines.push('    resampleMode = "pixelated",')
    lines.push("})")
    return lines.join("\n")
  })

  function draw() {
    if (!canvas) return
    const c = palette()
    const s = fit(canvas, 230)
    if (!s) return
    const ctx = s.ctx
    ctx.clearRect(0, 0, s.w, s.h)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, s.w, s.h)
    if (!loaded || !photo || !photo.naturalWidth) return

    const bw = boxWidth
    const bh = height
    const bx = Math.round((s.w - bw) / 2)
    const by = Math.round((s.h - bh) / 2)
    ctx.imageSmoothingEnabled = !pixelated
    ctx.save()
    ctx.beginPath()
    ctx.rect(bx, by, bw, bh)
    ctx.clip()
    const iw = photo.naturalWidth
    const ih = photo.naturalHeight
    if (type === "stretch") {
      ctx.drawImage(photo, bx, by, bw, bh)
    } else if (type === "fit" || type === "crop") {
      const k = type === "fit" ? Math.min(bw / iw, bh / ih) : Math.max(bw / iw, bh / ih)
      ctx.drawImage(photo, bx + (bw - iw * k) / 2, by + (bh - ih * k) / 2, iw * k, ih * k)
    } else {
      for (let ty = 0; ty < bh; ty += tile) {
        for (let tx = 0; tx < bw; tx += tile) ctx.drawImage(photo, bx + tx, by + ty, tile, tile)
      }
    }
    ctx.restore()
    ctx.strokeStyle = c.muted
    ctx.setLineDash([4, 4])
    ctx.strokeRect(bx + 0.5, by + 0.5, bw - 1, bh - 1)
    ctx.setLineDash([])
    ctx.fillStyle = c.light
    ctx.font = "11px " + c.mono
    ctx.fillText(`${iw} x ${ih} image in a ${bw} x ${bh} box`, 10, s.h - 10)
  }

  $effect(() => {
    draw()
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    stageWidth = node.clientWidth
    const watcher = new ResizeObserver(() => (stageWidth = node.clientWidth))
    watcher.observe(node)
    return () => watcher.disconnect()
  })
</script>

<Demo label="scaleType">
  <canvas
    bind:this={canvas}
    class="images-canvas"
    style="height: 230px"
    aria-label="an image drawn into a box"
  ></canvas>
  <Note>Photo: Andrew Ridley, Unsplash.</Note>

  <div class="demo-controls">
    <Choice label="scaleType" options={["stretch", "fit", "crop", "tile"]} bind:value={type} />
    <Slider label="width" min={40} max={320} step={4} bind:value={width} />
    <Slider label="height" min={40} max={200} step={4} bind:value={height} />
    <Slider label="tileSize" min={8} max={96} step={4} bind:value={tile} />
    <Choice label="resampleMode" options={["default", "pixelated"]} bind:value={resample} />
  </div>

  <CodePanel {source} />

  <img bind:this={photo} src="/art/photos/landscape.jpg" alt="" hidden onload={() => (loaded = true)} />
</Demo>
