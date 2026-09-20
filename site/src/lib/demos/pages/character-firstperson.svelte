<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { palette } from "../core/surface"
  import { addPlayerFloor, aimPlayer, createPlayerView, type Object3d, type SkinViewer } from "../core/player"

  const W = 720
  const H = 240

  let mode = $state<"arm" | "hand" | "body" | "none">("arm")
  let hand = $state<"false" | "true">("false")
  let hasBody = $state<"yes" | "no">("yes")
  let t = $state(0)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let skin = $state<HTMLCanvasElement | null>(null)
  let viewer = $state<SkinViewer | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let floor: Object3d | null = null
  let previous = 0

  const seen = $derived.by(() => {
    if (hasBody === "no") return hand === "true" ? "arm" : "none"
    if (mode === "body") return "body"
    if (mode === "none") return hand === "true" ? "arm" : "none"
    return "arm"
  })

  const why = $derived.by(() => {
    if (hasBody === "no") {
      return hand === "true"
        ? "A player with no body still sees the arm, because the hand switch is on."
        : "A player with no body sees no arm."
    }
    if (mode === "body") return '"body" hides the arm even with the hand switch on, because the body is drawn instead.'
    if (mode === "none") {
      return hand === "true" ? 'With the hand switch on the arm always shows, for "none" too.' : '"none" hides the arm.'
    }
    return '"arm" and "hand" both draw the game\'s own first person arm, in the player\'s skin.'
  })

  const source = $derived(
    (hasBody === "yes" ? `body.appearance.firstPerson = "${mode}"\n\n` : "-- the player has no body\n\n") +
      `-- place.toml\n[features]\nhand = ${hand}`,
  )

  whileVisible(
    () => canvas,
    (now) => {
      const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0
      previous = now
      if (seen === "body") t += dt
      pose(seen)
    },
  )

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

  function pose(what: string) {
    if (!viewer) return
    const parts = viewer.playerObject.skin
    viewer.playerObject.position.set(0, 0, 0)
    viewer.playerObject.rotation.y = 0
    parts.head.visible = false
    parts.body.visible = what === "body"
    parts.leftLeg.visible = what === "body"
    parts.rightLeg.visible = what === "body"
    parts.leftArm.visible = what === "body"
    parts.rightArm.visible = what === "body" || what === "arm"
    parts.rightArm.rotation.set(what === "arm" ? -1.35 : 0, what === "arm" ? 0.3 : 0, what === "arm" ? 0.15 : 0)
    const swing = what === "body" ? Math.sin(t * 5) * 0.55 : 0
    parts.leftArm.rotation.set(what === "body" ? -swing * 0.7 : 0, 0, what === "body" ? 0.08 : 0)
    if (what === "body") parts.rightArm.rotation.set(swing * 0.7, 0, -0.08)
    parts.rightLeg.rotation.set(-swing, 0, 0)
    parts.leftLeg.rotation.set(swing, 0, 0)
    const eye = viewer.camera.position.clone()
    parts.head.getWorldPosition(eye)
    if (!floor) floor = addPlayerFloor(viewer)
    const hip = viewer.camera.position.clone()
    parts.body.getWorldPosition(hip)
    if (floor) floor.position.set(eye.x, hip.y - 18.1, eye.z)
    const y = eye.y + 4.5
    if (what === "arm") aimPlayer(viewer, [eye.x - 1, y + 1, eye.z - 5], [eye.x - 1, y - 4, eye.z + 20])
    else aimPlayer(viewer, [eye.x, y, eye.z - 0.5], [eye.x, y - 22, eye.z + 11])
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    pose(seen)
    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.fillStyle = c.muted
    ctx.textAlign = "left"
    ctx.fillText(
      seen === "arm" ? "the game's own arm" : seen === "body" ? "the whole body, from inside the head" : "nothing",
      16,
      24,
    )
  }

  $effect(() => {
    const node = skin
    if (!node) return
    const handle = createPlayerView(node, { fov: 70 })
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

<Demo label="What you see of your own body">
  <div class="player-stage">
    <canvas
      bind:this={canvas}
      style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
      aria-label="what the player sees when looking down"
    ></canvas>
    <div class="player-view">
      <canvas bind:this={skin}></canvas>
    </div>
  </div>

  <p class="demo-note">{why}</p>

  <div class="demo-controls">
    <Choice label="firstPerson" options={["arm", "hand", "body", "none"] as const} bind:value={mode} />
    <Choice label="hand switch" options={["false", "true"] as const} bind:value={hand} />
    <Choice label="has a body" options={["yes", "no"] as const} bind:value={hasBody} />
  </div>

  <CodePanel {source} />
</Demo>
