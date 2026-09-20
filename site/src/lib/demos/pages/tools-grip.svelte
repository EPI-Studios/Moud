<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Slider from "../ui/Slider.svelte"
  import { palette } from "../core/surface"

  const W = 720
  const H = 300
  const UNIT = 110
  const HAND = { x: 360, y: 190 }

  let y = $state(-0.5)
  let angle = $state(0)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })

  const source = $derived(
    `local sword = body.backpack:add("Tool", {\n    name = "sword",\n    grip = cframe(0, ${fmt(y)}, 0)${
      angle ? ` * cframe.angles(${fmt(angle)}, 0, 0)` : ""
    },\n})\nsword:add("Part", { name = "Handle", size = vec3(0.1, 1.2, 0.1) })`,
  )

  function fmt(value: number) {
    const rounded = Math.round(value * 100) / 100
    return String(Object.is(rounded, -0) ? 0 : rounded)
  }

  function fit() {
    const node = canvas
    if (!node) return
    const width = node.clientWidth || W
    const ratio = window.devicePixelRatio || 1
    box = {
      w: Math.max(1, Math.round(width * ratio)),
      h: Math.max(1, Math.round(((width * H) / W) * ratio)),
      scale: Math.max(1, Math.min(1.5, (W / width) * 0.7)),
    }
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)

    ctx.save()
    ctx.translate(HAND.x, HAND.y)
    ctx.rotate(angle)
    ctx.translate(0, y * UNIT)
    ctx.fillStyle = c.accent
    ctx.fillRect(-5, -0.6 * UNIT, 10, 1.2 * UNIT)
    ctx.beginPath()
    ctx.arc(0, 0, 3, 0, Math.PI * 2)
    ctx.fillStyle = c.bg
    ctx.fill()
    ctx.restore()

    ctx.fillStyle = c.bg4
    ctx.fillRect(HAND.x - 20, HAND.y - 16, 40, 32)
    ctx.strokeStyle = c.muted
    ctx.strokeRect(HAND.x - 20, HAND.y - 16, 40, 32)

    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.fillStyle = c.muted
    ctx.textAlign = "left"
    ctx.fillText("right hand", HAND.x + 30, HAND.y + 4)
    ctx.fillText("the dot is the Handle's middle", 16, 24)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    const node = canvas
    const ctx = node?.getContext("2d")
    if (!node || !ctx || !box.w) return
    node.width = box.w
    node.height = box.h
    ctx.setTransform(box.w / W, 0, 0, box.h / H, 0, 0)
    draw(ctx)
  })
</script>

<Demo label="grip">
  <canvas
    bind:this={canvas}
    style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
    aria-label="side view of a hand holding a Handle at its grip"
  ></canvas>

  <div class="demo-controls">
    <Slider label="grip y" min={-0.6} max={0.6} step={0.05} bind:value={y} format={fmt} />
    <Slider label="cframe.angles x" min={-1.57} max={1.57} step={0.01} bind:value={angle} format={fmt} />
  </div>

  <CodePanel {source} />
</Demo>
