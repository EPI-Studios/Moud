<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Slider from "../ui/Slider.svelte"
  import { whileVisible } from "../core/frames"
  import { palette } from "../core/surface"
  import { aimPlayer, createPlayerView, type SkinViewer } from "../core/player"

  const W = 720
  const H = 250
  const PX = 6
  const HIP = 150

  let feeding = $state<"on" | "off">("on")
  let rate = $state(8)
  let speed = $state(1)
  let clock = $state(0)
  let distance = $state(0)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let skin = $state<HTMLCanvasElement | null>(null)
  let viewer = $state<SkinViewer | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let previous = 0

  const source = $derived(
    feeding === "on"
      ? `game.stepped:connect(function(dt)\n    clock += dt\n    mannequin.moveDistance = clock * ${rate}\n    mannequin.moveSpeed = ${fmt(speed)}\nend)`
      : `-- the handler stopped adding: moveDistance holds at ${distance.toFixed(1)}`,
  )

  whileVisible(
    () => canvas,
    (now) => {
      const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0
      previous = now
      if (feeding !== "on") return
      clock += dt
      distance = clock * rate
    },
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

  function pose() {
    if (!viewer) return
    const swing = Math.cos(distance * 0.6662) * 1.4 * Math.min(1, speed)
    const arm = Math.cos(distance * 0.6662 + Math.PI) * Math.min(1, speed)
    const parts = viewer.playerObject.skin
    parts.rightLeg.rotation.x = swing
    parts.leftLeg.rotation.x = -swing
    parts.rightArm.rotation.x = arm
    parts.leftArm.rotation.x = -arm
    viewer.playerObject.position.set(0, 0, 0)
    viewer.playerObject.rotation.y = Math.PI / 2
    aimPlayer(viewer, [0, 3.8, 63.7], [0, 3.8, 0])
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    const ground = HIP + 12 * PX
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(0, ground + 0.5)
    ctx.lineTo(W, ground + 0.5)
    ctx.stroke()
    const shift = (distance * 24) % 48
    ctx.fillStyle = c.bg4
    for (let x = -shift; x < W; x += 48) ctx.fillRect(x, ground + 4, 24, 3)

    pose()
    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.textAlign = "left"
    ctx.fillStyle = c.muted
    ctx.fillText(`moveDistance  ${distance.toFixed(1)}`, 16, 24)
    ctx.fillText(`moveSpeed     ${fmt(speed)}`, 16, 42)
    ctx.fillStyle = feeding === "on" ? c.green : c.yellow
    ctx.fillText(
      feeding === "on" ? "adding ground every tick" : "nothing added: the legs stay where they are",
      16,
      60,
    )
  }

  $effect(() => {
    feeding
    rate
    untrack(() => {
      if (rate > 0) clock = distance / rate
    })
  })

  $effect(() => {
    const node = skin
    if (!node) return
    const handle = createPlayerView(node, { fov: 40 })
    handle.ready.then(
      (ready) => (viewer = ready),
      () => (viewer = null),
    )
    return () => handle.destroy()
  })

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

<Demo label="Walking on distance">
  <div class="player-stage">
    <canvas
      bind:this={canvas}
      style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
      aria-label="a character seen from the side, walking on the distance fed to it"
    ></canvas>
    <div class="player-view">
      <canvas bind:this={skin}></canvas>
    </div>
  </div>

  <div class="demo-controls">
    <Choice label="feed it ground" options={["on", "off"] as const} bind:value={feeding} />
    <Slider label="clock *" min={1} max={16} bind:value={rate} />
    <Slider label="moveSpeed" min={0} max={1} step={0.05} bind:value={speed} format={fmt} />
  </div>

  <CodePanel {source} />
</Demo>
