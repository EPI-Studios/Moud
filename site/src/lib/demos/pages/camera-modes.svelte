<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type PlayerEntity, type Point2, type Scene } from "../core/mc2d"
  import { clamp, pointIn, type Point } from "../core/pointer"

  type Mode = "firstPerson" | "thirdPerson" | "scriptable"
  type Shot = { from: Point; dir: Point }

  const GROUND = 64
  const EYE = 1.62

  let mode = $state<Mode>("thirdPerson")
  let distance = $state(6)
  let offset = $state(0.2)
  let fov = $state(0)
  let aim = $state<Point>({ x: 9.44, y: 68.44 })
  let eye = $state<Point>({ x: -7.78, y: 70.11 })

  let canvas = $state<HTMLCanvasElement | null>(null)
  let scene = $state<Scene | null>(null)
  let steve = $state<PlayerEntity | null>(null)
  let dragging = false
  let shown: { shot: Shot; at: Point; aim: Point; fov: number; mode: Mode } | null = null

  const shot = $derived(camera())
  const fovShown = $derived(fov === 0 ? 70 : fov)

  const source = $derived.by(() => {
    const fovLine = `camera.fov = ${fov}${fov === 0 ? "   -- the player's own, drawn here as 70" : ""}`
    if (mode === "scriptable") {
      return (
        'camera.mode = "scriptable"\n' +
        `camera.cframe = cframe.lookAt(vec3(${num(shot.from.x, 1)}, ${num(shot.from.y, 1)}, 0), vec3(0, ${num(GROUND + EYE)}, 0))\n` +
        fovLine
      )
    }
    return (
      `camera.mode = "${mode}"\n` +
      (mode === "thirdPerson" ? `camera.distance = ${num(distance, 1)}\n` : "") +
      `camera.offset = vec3(0, ${num(offset, 1)}, 0)\n` +
      fovLine
    )
  })

  const note = $derived(
    mode === "scriptable"
      ? "Nothing places the camera but you: it stays where cframe says, looking at the player's eye. Aiming does not move it."
      : mode === "thirdPerson"
        ? `Aiming stays the player's. The camera sits ${num(distance, 1)} m back along the look, then offset shifts it.`
        : "The camera is at the player's eye, shifted by offset. distance does nothing in this mode.",
  )

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function eyeAt(): Point {
    return { x: 0, y: GROUND + EYE }
  }

  function unit(x: number, y: number): Point {
    const length = Math.hypot(x, y) || 1
    return { x: x / length, y: y / length }
  }

  function camera(): Shot {
    const e = eyeAt()
    if (mode === "scriptable") {
      return { from: { x: eye.x, y: eye.y }, dir: unit(e.x - eye.x, e.y - eye.y) }
    }
    const dir = unit(aim.x - e.x, aim.y - e.y)
    const from = { x: e.x, y: e.y + offset }
    if (mode === "thirdPerson") {
      from.x -= dir.x * distance
      from.y -= dir.y * distance
    }
    return { from, dir }
  }

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const now = shown
    if (!now) return
    const fx = v.x(now.shot.from.x, 0.5)
    const fy = v.y(now.shot.from.y, 0.5)
    const half = (now.fov / 2) * (Math.PI / 180)
    const angle = Math.atan2(-now.shot.dir.y, now.shot.dir.x)
    const reach = 2000
    const left: Point2 = [fx + Math.cos(angle - half) * reach, fy + Math.sin(angle - half) * reach]
    const right: Point2 = [fx + Math.cos(angle + half) * reach, fy + Math.sin(angle + half) * reach]
    ctx.fillStyle = "rgba(255,255,255,0.16)"
    ctx.beginPath()
    ctx.moveTo(fx, fy)
    ctx.lineTo(left[0], left[1])
    ctx.lineTo(right[0], right[1])
    ctx.closePath()
    ctx.fill()
    v.line([left, [fx, fy], right], { width: 1, color: "rgba(255,255,255,0.7)", halo: false })
    const ex = v.x(now.at.x, 0.5)
    const ey = v.y(now.at.y, 0.5)
    const tx = now.mode === "scriptable" ? fx : v.x(now.aim.x, 0.5)
    const ty = now.mode === "scriptable" ? fy : v.y(now.aim.y, 0.5)
    v.line(
      [
        [ex, ey],
        [tx, ty],
      ],
      { dash: [3, 4], width: 1.25, color: "rgba(255,255,255,0.9)", halo: false },
    )
    if (now.mode !== "scriptable") {
      ctx.fillStyle = "rgba(20,20,20,0.55)"
      ctx.strokeStyle = "#ffffff"
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.arc(tx, ty, 6, 0, Math.PI * 2)
      ctx.fill()
      ctx.stroke()
    }
    ctx.save()
    ctx.translate(fx, fy)
    ctx.rotate(angle)
    ctx.fillStyle = v.colors.accent
    ctx.strokeStyle = "rgba(0,0,0,0.6)"
    ctx.lineWidth = 1
    ctx.beginPath()
    if (ctx.roundRect) ctx.roundRect(-9, -6, 13, 12, 2)
    else ctx.rect(-9, -6, 13, 12)
    ctx.moveTo(4, -4)
    ctx.lineTo(11, -7)
    ctx.lineTo(11, 7)
    ctx.lineTo(4, 4)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
    ctx.restore()
    v.tag(now.mode === "scriptable" ? "drag the camera" : "drag to aim", 8, 16)
  }

  function move(event: PointerEvent) {
    const node = canvas
    const view = scene?.view
    if (!node || !view) return
    const box = node.getBoundingClientRect()
    const point = pointIn(node, event, box.width, box.height)
    const px = clamp(point.x, 6, box.width - 6)
    const py = clamp(point.y, 6, view.y(GROUND, 0.5) - 4)
    const p = { x: view.worldX(px, 0.5), y: view.worldY(py, 0.5) }
    const e = eyeAt()
    if (mode === "scriptable") eye = p
    else if (Math.hypot(p.x - e.x, p.y - e.y) * view.unit > 12) aim = p
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const active = createScene(node, {
      aspect: 0.46,
      minHeight: 220,
      maxHeight: 360,
      time: 3000,
      view: { x0: -13.9, x1: 10.6, y0: 62.3, y1: 75.1 },
      overlay,
    })
    for (let x = -40; x <= 40; x++) {
      active.set(x, 63, "grass_block")
      active.set(x, 62, "dirt")
      active.set(x, 61, "dirt")
      for (let y = 52; y <= 60; y++) active.set(x, y, "stone")
    }
    active.fill(-13, 66, -9, 67, "oak_leaves")
    active.fill(-12, 68, -10, 69, "oak_leaves")
    active.fill(-11, 64, -11, 67, "oak_log")
    steve = active.player({ x: 0, y: GROUND, facing: 1 })
    scene = active
    return () => {
      scene = null
      steve = null
      active.destroy()
    }
  })

  $effect(() => {
    const active = scene
    const body = steve
    if (!active || !body) return
    const at = eyeAt()
    if (mode !== "scriptable") {
      const dx = aim.x - at.x
      body.facing = dx < 0 ? -1 : 1
      body.pitch = clamp(-Math.atan2(aim.y - at.y, Math.abs(dx)), -1.4, 1.4)
    }
    shown = { shot, at, aim: { ...aim }, fov: fovShown, mode }
    active.draw()
  })
</script>

<Demo label="Camera modes">
  <canvas
    bind:this={canvas}
    class="mc2d mc2d-drag"
    aria-label="side view of the player and the camera"
    onpointerdown={(event) => {
      dragging = true
      canvas?.setPointerCapture(event.pointerId)
      move(event)
    }}
    onpointermove={(event) => dragging && move(event)}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
  ></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    <Choice
      label="camera.mode"
      options={["firstPerson", "thirdPerson", "scriptable"] as const}
      bind:value={mode}
    />
    <Slider label="distance" min={0} max={10} step={0.5} bind:value={distance} />
    <Slider label="offset y" min={-1} max={2} step={0.1} bind:value={offset} />
    <Slider label="fov" min={0} max={110} step={5} bind:value={fov} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
