<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { palette, type Palette } from "../core/surface"

  const W = 720
  const H = 300
  const UNIT = 70
  const OX = 360
  const OZ = 170

  let canvas = $state<HTMLCanvasElement | null>(null)
  let ctx: CanvasRenderingContext2D | null = null
  let colors: Palette | null = null
  let scale = 1

  let hinged = $state("hinge")
  let angle = $state(60)

  const pivot = $derived(hinged === "hinge" ? -1 : 0)

  const source = $derived(
    `local door = world:add("Part", {
    name = "door", size = vec3(2, 3, 0.2), cframe = cframe(0, 65, 0),
})
door.pivot = vec3(${pivot}, 0, 0)
door.cframe = cframe(0, 65, 0) * cframe.angles(0, math.rad(${angle}), 0)`,
  )

  function draw(at: number, turn: number) {
    const c = colors
    if (!ctx || !c) return
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)

    ctx.fillStyle = c.bg3
    ctx.fillRect(OX - 4.2 * UNIT, OZ - 6, 3 * UNIT, 12)
    ctx.fillRect(OX + 1.2 * UNIT, OZ - 6, 3 * UNIT, 12)

    ctx.save()
    ctx.translate(OX, OZ)
    ctx.strokeStyle = c.line2
    ctx.setLineDash([4, 4])
    ctx.strokeRect(-UNIT, -0.1 * UNIT, 2 * UNIT, 0.2 * UNIT)
    ctx.setLineDash([])
    const px = at * UNIT
    ctx.translate(px, 0)
    ctx.rotate((-turn * Math.PI) / 180)
    ctx.translate(-px, 0)
    ctx.fillStyle = c.bg4
    ctx.fillRect(-UNIT, -0.1 * UNIT, 2 * UNIT, 0.2 * UNIT)
    ctx.strokeStyle = c.main
    ctx.lineWidth = 1.5
    ctx.strokeRect(-UNIT, -0.1 * UNIT, 2 * UNIT, 0.2 * UNIT)
    ctx.beginPath()
    ctx.arc(0, 0, 3, 0, Math.PI * 2)
    ctx.fillStyle = c.light
    ctx.fill()
    ctx.restore()

    ctx.beginPath()
    ctx.arc(OX + at * UNIT, OZ, 6, 0, Math.PI * 2)
    ctx.fillStyle = c.accent
    ctx.fill()
    ctx.font = `${Math.round(12 * scale)}px ${c.mono}`
    ctx.textAlign = "center"
    ctx.fillStyle = c.muted
    ctx.fillText("pivot", OX + at * UNIT, OZ + 26)
    ctx.fillText(
      at === 0
        ? "turns about its middle, and swings into both walls"
        : "turns at its hinge, like a door",
      360,
      284,
    )
  }

  function fit() {
    if (!canvas || !ctx) return
    const width = canvas.clientWidth || W
    scale = Math.max(1, Math.min(1.5, (W / width) * 0.7))
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(((width * H) / W) * ratio))
    ctx.setTransform(canvas.width / W, 0, 0, canvas.height / H, 0, 0)
    draw(pivot, angle)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    ctx = node.getContext("2d")
    colors = palette()
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    draw(pivot, angle)
  })
</script>

<Demo label="pivot">
  <canvas
    bind:this={canvas}
    aria-label="top down view of a door turning about its pivot"
    style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
  ></canvas>

  <div class="demo-controls">
    <Choice label="pivot" options={["hinge", "middle"]} bind:value={hinged} />
    <Slider label="turn" min={0} max={90} step={1} bind:value={angle} />
  </div>

  <CodePanel {source} />
</Demo>
