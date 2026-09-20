<script lang="ts">
  import { onMount } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { whileVisible } from "../core/frames"
  import { cssVar, fitScaled, mono } from "../core/surface"
  import { tinted, type Rgb } from "../core/tint"

  const W = 420
  const H = 170
  const SCALE = 40

  type TextureName = "spark_4" | "beacon_beam"
  type Mode = "stretch" | "wrap" | "static"

  const TEXTURES: Record<TextureName, string> = {
    spark_4: "spark_4",
    beacon_beam: "minecraft:entity/beacon/beacon_beam",
  }
  const TEXTURE_NAMES = Object.keys(TEXTURES) as TextureName[]

  const NOTES: Record<Mode, string> = {
    stretch:
      "stretch fits the image to the whole beam: textureLength is how many times it repeats, so dragging an end stretches the markings.",
    wrap: "wrap repeats the image every textureLength metres, so a long beam and a short one have the same sized markings, scrolling from attachment0.",
    static: "static repeats it the same way but holds it still from attachment0, and ignores textureSpeed.",
  }

  const colorStart: Rgb = [1, 0.1, 0.1]
  const colorEnd: Rgb = [1, 0.4, 0.2]

  let texture = $state<TextureName>("spark_4")
  let mode = $state<Mode>("wrap")
  let length = $state(0.5)
  let speed = $state(4)
  let w0 = $state(0.4)
  let w1 = $state(0.4)

  let canvas = $state<HTMLCanvasElement | null>(null)
  let a0 = { x: 50, y: 120 }
  let a1 = { x: 370, y: 50 }
  let dragging: { x: number; y: number } | null = null
  let clock = 0
  let last = 0

  const num = (v: number) => String(Math.round(v * 100) / 100)
  const colorText = (c: Rgb) => `color(${num(c[0])}, ${num(c[1])}, ${num(c[2])})`
  const mix = (a: number, b: number, t: number) => a + (b - a) * t

  const source = $derived(
    'turret:add("Beam", {\n' +
      '    attachment0 = turret:add("Attachment", { cframe = cframe(0, 0, -0.5) }),\n' +
      '    attachment1 = target:add("Attachment", {}),\n' +
      `    texture = "${TEXTURES[texture]}",\n` +
      `    textureMode = "${mode}",\n` +
      `    textureLength = ${num(length)},\n` +
      `    textureSpeed = ${num(speed)},\n` +
      `    colorStart = ${colorText(colorStart)}, colorEnd = ${colorText(colorEnd)},\n` +
      "    transparencyStart = 0, transparencyEnd = 0,\n" +
      `    width0 = ${num(w0)}, width1 = ${num(w1)},\n` +
      "    faceCamera = true,\n" +
      "    lightEmission = 1,\n" +
      "})",
  )

  function draw(dt: number) {
    if (!canvas) return
    clock += dt
    const view = fitScaled(canvas, W, H)
    if (!view) return
    const { ctx, unit } = view

    ctx.fillStyle = cssVar("--bg")
    ctx.fillRect(0, 0, W, H)

    const dx = a1.x - a0.x
    const dy = a1.y - a0.y
    const pixels = Math.sqrt(dx * dx + dy * dy) || 1
    const metres = pixels / SCALE
    const scroll = mode === "static" ? 0 : speed * clock
    const steps = Math.max(2, Math.ceil(pixels * 2))
    const slice = pixels / steps

    ctx.save()
    ctx.translate(a0.x, a0.y)
    ctx.rotate(Math.atan2(dy, dx))
    ctx.imageSmoothingEnabled = false
    ctx.globalCompositeOperation = "lighter"
    for (let i = 0; i < steps; i++) {
      const tm = (i + 0.5) / steps
      const u = mode === "stretch" ? tm * length - scroll : (tm * metres) / length - scroll
      const band = Math.round(tm * 16) / 16
      const color: Rgb = [
        mix(colorStart[0], colorEnd[0], band),
        mix(colorStart[1], colorEnd[1], band),
        mix(colorStart[2], colorEnd[2], band),
      ]
      const image = tinted(TEXTURES[texture], color, true)
      if (!image) break
      const column = Math.min(image.width - 1, Math.floor((u - Math.floor(u)) * image.width))
      const half = (mix(w0, w1, tm) * SCALE) / 2
      ctx.drawImage(image, column, 0, 1, image.height, i * slice, -half, slice + 0.02, half * 2)
    }
    ctx.restore()

    for (const [index, p] of [a0, a1].entries()) {
      ctx.fillStyle = cssVar("--bg")
      ctx.strokeStyle = cssVar("--text-main")
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.arc(p.x, p.y, 7, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
      ctx.fillStyle = cssVar("--text-muted")
      ctx.font = mono(unit, 11)
      ctx.fillText(`attachment${index}`, p.x - 28, p.y + (p.y > H - 30 ? -14 : 22))
    }

    ctx.fillStyle = cssVar("--text-light")
    ctx.fillText(`${metres.toFixed(1)} m`, 8, 16)
  }

  function grab(event: PointerEvent) {
    if (!canvas) return
    const p = pointIn(canvas, event, W, H)
    dragging = Math.hypot(p.x - a0.x, p.y - a0.y) < Math.hypot(p.x - a1.x, p.y - a1.y) ? a0 : a1
    canvas.setPointerCapture(event.pointerId)
    dragging.x = p.x
    dragging.y = p.y
  }

  function move(event: PointerEvent) {
    if (!dragging || !canvas) return
    const p = pointIn(canvas, event, W, H)
    dragging.x = clamp(p.x, 10, W - 10)
    dragging.y = clamp(p.y, 10, H - 10)
  }

  onMount(() => draw(0))

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.05) : 0
      last = now
      draw(dt)
    },
  )
</script>

<Demo label="Beam texture">
  <Stage>
    <canvas
      bind:this={canvas}
      class="effects-canvas effects-drag"
      style="aspect-ratio: {W} / {H}"
      onpointerdown={grab}
      onpointermove={move}
      onpointerup={() => (dragging = null)}
      onpointercancel={() => (dragging = null)}
    ></canvas>
  </Stage>

  <p class="demo-note">{NOTES[mode]}</p>

  <div class="demo-controls">
    <Choice label="texture" options={TEXTURE_NAMES} bind:value={texture} />
    <Choice label="textureMode" options={["stretch", "wrap", "static"] as const} bind:value={mode} />
    <Slider label="textureLength" min={0.25} max={4} step={0.25} bind:value={length} />
    <Slider label="textureSpeed" min={0} max={8} step={0.5} bind:value={speed} />
    <Slider label="width0" min={0.05} max={1} step={0.05} bind:value={w0} />
    <Slider label="width1" min={0.05} max={1} step={0.05} bind:value={w1} />
  </div>

  <CodePanel {source} />

  <p class="demo-note effects-credit">Textures: Minecraft (Mojang)</p>
</Demo>
