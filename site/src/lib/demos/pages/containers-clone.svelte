<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { whileVisible } from "../core/frames"
  import { palette, type Palette } from "../core/surface"

  const W = 720
  const H = 330
  const AREA = { x: 210, y: 10, w: 500, h: 310 }

  const SETUP = `local storage = game.world:add("ServerStorage")
local coin = storage:add("Part", { name = "coin", anchored = true, collides = false })
coin:add("Script", { name = "collect", source = "res://server/coin.luau" })`

  const CLONE_INTO_WORLD = `local copy = coin:clone(game.world) :: Part
copy.position = vec3(random.range(-10, 10), 66, random.range(-10, 10))`

  const EVERY = `task.every(3, function()
    local copy = coin:clone(game.world) :: Part
    copy.position = vec3(random.range(-10, 10), 66, random.range(-10, 10))
end)`

  let canvas = $state<HTMLCanvasElement | null>(null)
  let ctx: CanvasRenderingContext2D | null = null
  let colors: Palette | null = null
  let scale = 1
  let dragging = false

  let coins: { x: number; y: number }[] = []
  let inert = 0
  let player = { x: 460, y: 170 }
  let timer = 0
  let spin = 0
  let seed = 7

  let every = $state(false)
  let source = $state(SETUP)
  let lines = $state<Line[]>([{ id: 0, text: "clone a coin, then drag the body onto it" }])
  let nextLine = 1

  function rand() {
    seed = (seed * 16807) % 2147483647
    return seed / 2147483647
  }

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 4)
  }

  function cloneToWorld() {
    coins.push({
      x: AREA.x + 30 + rand() * (AREA.w - 60),
      y: AREA.y + 30 + rand() * (AREA.h - 60),
    })
    source = CLONE_INTO_WORLD
    record("a copy lands in the world, and its own collect script starts", "in")
    draw()
  }

  function cloneBeside() {
    inert += 1
    source = "local copy = coin:clone()   -- beside the original, still inside the storage"
    record("a copy lands beside the template, inside the storage, as inert as it")
    draw()
  }

  function loop() {
    every = !every
    timer = 0
    source = every ? EVERY : "-- the loop is off"
  }

  function touch() {
    for (let i = coins.length - 1; i >= 0; i--) {
      const dx = coins[i].x - player.x
      const dy = coins[i].y - player.y
      if (dx * dx + dy * dy < 22 * 22) {
        coins.splice(i, 1)
        record("touched fired on that copy: its collect script destroyed it", "out")
      }
    }
  }

  function drawCoin(x: number, y: number, alive: boolean) {
    const c = colors
    if (!ctx || !c) return
    const squash = alive ? Math.abs(Math.cos(spin + x * 0.05)) : 1
    ctx.beginPath()
    ctx.ellipse(x, y, Math.max(2, 9 * squash), 9, 0, 0, Math.PI * 2)
    ctx.fillStyle = alive ? c.yellow : c.bg3
    ctx.fill()
    ctx.strokeStyle = alive ? c.yellow : c.light
    ctx.setLineDash(alive ? [] : [2, 2])
    ctx.stroke()
    ctx.setLineDash([])
  }

  function draw() {
    const c = colors
    if (!ctx || !c) return
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1
    ctx.setLineDash([4, 4])
    ctx.strokeRect(10.5, 10.5, 180, 309)
    ctx.setLineDash([])
    ctx.strokeRect(AREA.x + 0.5, AREA.y + 0.5, AREA.w, AREA.h)
    ctx.font = `${Math.round(12 * scale)}px ${c.mono}`
    ctx.textAlign = "left"
    ctx.fillStyle = c.muted
    ctx.fillText("ServerStorage", 22, 32)
    ctx.fillText("game.world", AREA.x + 12, AREA.y + 22)
    ctx.fillStyle = c.light
    ctx.fillText("not sent, not drawn,", 22, 290)
    ctx.fillText("scripts only kept", 22, 306)

    drawCoin(40, 66, false)
    ctx.fillStyle = c.text
    ctx.fillText("coin", 58, 70)
    ctx.fillStyle = c.light
    ctx.fillText("template", 100, 70)
    for (let i = 0; i < inert; i++) {
      const column = i % 6
      const line = Math.floor(i / 6)
      if (line > 4) break
      drawCoin(40 + column * 24, 110 + line * 24, false)
    }
    if (inert > 0) {
      ctx.fillStyle = c.muted
      ctx.fillText(
        `${inert}${inert === 1 ? " inert copy" : " inert copies"}`,
        22,
        110 + Math.min(4, Math.floor((inert - 1) / 6)) * 24 + 32,
      )
    }

    for (const made of coins) drawCoin(made.x, made.y, true)

    ctx.beginPath()
    ctx.arc(player.x, player.y, 11, 0, Math.PI * 2)
    ctx.fillStyle = c.main
    ctx.fill()
    ctx.textAlign = "center"
    ctx.fillStyle = c.muted
    ctx.fillText("body", player.x, player.y - 18)
  }

  function fit() {
    if (!canvas || !ctx) return
    const width = canvas.clientWidth || W
    scale = Math.max(1, Math.min(1.5, (W / width) * 0.7))
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(((width * H) / W) * ratio))
    ctx.setTransform(canvas.width / W, 0, 0, canvas.height / H, 0, 0)
    draw()
  }

  function grab(event: PointerEvent) {
    if (!canvas) return
    const point = pointIn(canvas, event, W, H)
    const dx = point.x - player.x
    const dy = point.y - player.y
    if (dx * dx + dy * dy >= 30 * 30) return
    dragging = true
    canvas.setPointerCapture(event.pointerId)
    event.preventDefault()
  }

  function move(event: PointerEvent) {
    if (!dragging || !canvas) return
    const point = pointIn(canvas, event, W, H)
    player = {
      x: clamp(point.x, AREA.x + 12, AREA.x + AREA.w - 12),
      y: clamp(point.y, AREA.y + 12, AREA.y + AREA.h - 12),
    }
    touch()
    draw()
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

  let last = 0
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      spin += dt * 3
      if (every) {
        timer += dt
        if (timer >= 3) {
          timer -= 3
          cloneToWorld()
        }
      }
      draw()
    },
  )
</script>

<Demo label="Cloning out of ServerStorage">
  <canvas
    bind:this={canvas}
    class="demo-plan demo-plan-wide"
    aria-label="a storage panel beside the world, with coins cloned into both"
    style="aspect-ratio: {W} / {H}"
    onpointerdown={grab}
    onpointermove={move}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
  ></canvas>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={cloneToWorld}>
      coin:clone(game.world)
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={cloneBeside}>
      coin:clone()
    </button>
    <button type="button" class="demo-pill" aria-pressed={every} onclick={loop}>
      task.every(3, ...)
    </button>
  </div>

  <Log {lines} keep={4} />
  <CodePanel {source} />
</Demo>
