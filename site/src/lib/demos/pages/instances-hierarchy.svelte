<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { palette, type Palette } from "../core/surface"

  const W = 720
  const H = 340
  const UNIT = 30
  const CX = 360
  const CZ = 170

  function fmt(n: number) {
    const rounded = Math.round(n * 100) / 100
    return String(Object.is(rounded, -0) ? 0 : rounded)
  }

  let canvas = $state<HTMLCanvasElement | null>(null)
  let ctx: CanvasRenderingContext2D | null = null
  let colors: Palette | null = null
  let scale = 1

  let deckX = $state(0)
  let angle = $state(0)
  let stepped = $state("off")

  function place(point: { x: number; y: number; z: number }, turn: number, shift: number) {
    const ca = Math.cos(turn)
    const sa = Math.sin(turn)
    return {
      x: shift + point.x * ca + point.z * sa,
      y: 65 + point.y,
      z: 12 - point.x * sa + point.z * ca,
    }
  }

  function screen(point: { x: number; z: number }) {
    return { x: CX + point.x * UNIT, y: CZ + (point.z - 12) * UNIT }
  }

  const post = $derived(place({ x: 2, y: 1.5, z: 2 }, angle, deckX))
  const lantern = $derived(place({ x: 2, y: 2.9, z: 2 }, angle, deckX))

  const readout = $derived(
    `print(post.cframe.position)            -- 2, 1.5, 2   never rewritten
print(post.worldCframe.position)       -- ${fmt(post.x)}, ${fmt(post.y)}, ${fmt(post.z)}
print(lantern.worldCframe.position)    -- ${fmt(lantern.x)}, ${fmt(lantern.y)}, ${fmt(lantern.z)}`,
  )

  const source = $derived(
    `local deck = world:add("Part", {
    name = "deck", size = vec3(7, 0.5, 7), cframe = cframe(${fmt(deckX)}, 65, 12),
})
local post = deck:add("Part", { name = "post", cframe = cframe(2, 1.5, 2) })
post:add("Part", { name = "lantern", cframe = cframe(0, 1.4, 0) })

deck.cframe = cframe(${fmt(deckX)}, 65, 12) * cframe.angles(0, ${fmt(angle)}, 0)`,
  )

  function draw(shift: number, turn: number) {
    const c = colors
    if (!ctx || !c) return
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = c.line
    ctx.lineWidth = 1
    for (let gx = CX % UNIT; gx <= W; gx += UNIT) {
      ctx.beginPath()
      ctx.moveTo(gx + 0.5, 0)
      ctx.lineTo(gx + 0.5, H)
      ctx.stroke()
    }
    for (let gy = CZ % UNIT; gy <= H; gy += UNIT) {
      ctx.beginPath()
      ctx.moveTo(0, gy + 0.5)
      ctx.lineTo(W, gy + 0.5)
      ctx.stroke()
    }

    const deck = screen({ x: shift, z: 12 })
    ctx.save()
    ctx.translate(deck.x, deck.y)
    ctx.rotate(-turn)
    ctx.fillStyle = c.bg3
    ctx.fillRect(-3.5 * UNIT, -3.5 * UNIT, 7 * UNIT, 7 * UNIT)
    ctx.strokeStyle = c.muted
    ctx.strokeRect(-3.5 * UNIT, -3.5 * UNIT, 7 * UNIT, 7 * UNIT)
    ctx.strokeStyle = c.line2
    ctx.setLineDash([4, 4])
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(2 * UNIT, 0)
    ctx.lineTo(2 * UNIT, 2 * UNIT)
    ctx.stroke()
    ctx.setLineDash([])
    ctx.fillStyle = c.bg4
    ctx.fillRect(2 * UNIT - 10, 2 * UNIT - 10, 20, 20)
    ctx.strokeStyle = c.main
    ctx.strokeRect(2 * UNIT - 10, 2 * UNIT - 10, 20, 20)
    ctx.restore()

    const lamp = screen(place({ x: 2, y: 2.9, z: 2 }, turn, shift))
    ctx.beginPath()
    ctx.arc(lamp.x, lamp.y, 16, 0, Math.PI * 2)
    ctx.fillStyle = "rgba(242, 193, 78, 0.12)"
    ctx.fill()
    ctx.beginPath()
    ctx.arc(lamp.x, lamp.y, 5, 0, Math.PI * 2)
    ctx.fillStyle = c.yellow
    ctx.fill()

    ctx.beginPath()
    ctx.arc(deck.x, deck.y, 3, 0, Math.PI * 2)
    ctx.fillStyle = c.muted
    ctx.fill()

    ctx.font = `${Math.round(12 * scale)}px ${c.mono}`
    ctx.fillStyle = c.muted
    ctx.textAlign = "left"
    ctx.fillText("deck", deck.x + 8, deck.y - 8)
    ctx.fillText("post + lantern", lamp.x + 20, lamp.y + 4)
  }

  function fit() {
    if (!canvas || !ctx) return
    const width = canvas.clientWidth || W
    scale = Math.max(1, Math.min(1.5, (W / width) * 0.7))
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(((width * H) / W) * ratio))
    ctx.setTransform(canvas.width / W, 0, 0, canvas.height / H, 0, 0)
    draw(deckX, angle)
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
    draw(deckX, angle)
  })

  let last = 0
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      if (stepped !== "turning") return
      angle = (angle + dt * 0.8) % 6.28
    },
  )
</script>

<Demo label="A branch moves together">
  <canvas
    bind:this={canvas}
    aria-label="top down view of a deck carrying a post and a lantern"
    style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
  ></canvas>

  <div class="demo-controls">
    <Slider label="deck turn" min={0} max={6.28} step={0.01} bind:value={angle} format={fmt} />
    <Slider label="deck x" min={-6} max={6} step={0.5} bind:value={deckX} />
    <Choice label="game.stepped" options={["off", "turning"]} bind:value={stepped} />
  </div>

  <CodePanel {source} />
  <CodePanel source={readout} />
</Demo>
