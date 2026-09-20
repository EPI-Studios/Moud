<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type PlayerEntity, type Point2, type Scene } from "../core/mc2d"

  let fov = $state(80)
  let distance = $state(6)
  let sneaking = $state(false)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let scene = $state<Scene | null>(null)
  let steve: PlayerEntity | null = null

  const source = $derived(
    "camera.fov = " +
      fov +
      "\n\n" +
      "game.renderStepped:connect(function()\n" +
      '    camera.mode = input:down("sneak") and "thirdPerson" or "firstPerson"\n' +
      "    camera.distance = " +
      distance +
      "\n" +
      "end)\n" +
      '-- camera.mode is "' +
      (sneaking ? "thirdPerson" : "firstPerson") +
      '" this frame',
  )

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const shown = sneaking ? 1 : 0
    const headY = 64 + (sneaking ? 1.5 : 1.8) - 0.15
    const tilt = 0.35
    const camX = -Math.cos(tilt) * distance * shown
    const camY = headY + Math.sin(tilt) * distance * shown
    const cx = v.x(camX, 0.5)
    const cy = v.y(camY, 0.5)
    const half = (fov / 2) * (Math.PI / 180)
    const reach = v.w * 1.5
    const aim = shown ? tilt : 0
    const p1: Point2 = [cx + Math.cos(aim - half) * reach, cy + Math.sin(aim - half) * reach]
    const p2: Point2 = [cx + Math.cos(aim + half) * reach, cy + Math.sin(aim + half) * reach]
    ctx.fillStyle = "rgba(255,255,255,0.16)"
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(p1[0], p1[1])
    ctx.lineTo(p2[0], p2[1])
    ctx.closePath()
    ctx.fill()
    v.line([p1, [cx, cy], p2], { width: 1, color: "rgba(255,255,255,0.8)", halo: false })
    if (shown) {
      v.line(
        [
          [cx, cy],
          [v.x(0, 0.5), v.y(headY, 0.5)],
        ],
        { dash: [3, 4], color: v.colors.yellow, width: 1.25 },
      )
    }
    ctx.fillStyle = v.colors.yellow
    ctx.strokeStyle = "rgba(0,0,0,0.45)"
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.arc(cx, cy, 5, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
    v.tag(sneaking ? "thirdPerson, " + distance + " m back" : "firstPerson, at the eyes", 8, 16)
    v.tag("fov " + fov + "°", 8, 38)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const active = createScene(node, {
      height: 230,
      time: 3200,
      view: (w) => (w < 560 ? { x0: -9, x1: 5, y0: 62.4, y1: 70.4 } : { x0: -13, x1: 9, y0: 62.4, y1: 70.4 }),
      overlay,
    })
    for (let x = -30; x <= 30; x++) {
      active.set(x, 63, "grass_block")
      active.set(x, 62, "dirt")
      active.set(x, 61, "dirt")
      for (let y = 55; y <= 60; y++) active.set(x, y, "stone")
    }
    active.fill(4, 66, 8, 67, "oak_leaves")
    active.fill(5, 68, 7, 68, "oak_leaves")
    active.fill(6, 64, 6, 66, "oak_log")
    steve = active.player({ x: 0, y: 64, facing: 1 })
    scene = active
    return () => {
      steve = null
      scene = null
      active.destroy()
    }
  })

  $effect(() => {
    const active = scene
    if (!active) return
    if (steve) steve.crouch = sneaking
    fov
    distance
    active.draw()
  })
</script>

<Demo label="Sneak to switch">
  <canvas bind:this={canvas} class="mc2d" aria-label="the camera at the eyes or behind the player"></canvas>
  <Mc2dCredit />

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      aria-pressed={sneaking}
      onpointerdown={(event) => {
        event.preventDefault()
        sneaking = true
      }}
      onpointerup={() => (sneaking = false)}
      onpointerleave={() => (sneaking = false)}
      onpointercancel={() => (sneaking = false)}
      onkeydown={(event) => {
        if (event.key === " " || event.key === "Enter") {
          event.preventDefault()
          sneaking = true
        }
      }}
      onkeyup={() => (sneaking = false)}
    >
      hold to sneak
    </button>
  </div>

  <div class="demo-controls">
    <Slider label="fov" min={30} max={110} step={5} bind:value={fov} />
    <Slider label="distance" min={2} max={12} step={1} bind:value={distance} />
  </div>

  <CodePanel {source} />
</Demo>
