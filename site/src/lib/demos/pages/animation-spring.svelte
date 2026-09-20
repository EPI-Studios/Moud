<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { whileVisible } from "../core/frames"
  import { clamp, pointIn, type Point } from "../core/pointer"
  import { fit, palette } from "../core/surface"

  type Bone = { tip: Point; v: Point; pivot: Point }

  const HEIGHT = 240
  const BONES = 3
  const LENGTH = 0.5
  const U = 70

  let stiffness = $state(40)
  let damping = $state(6)
  let maxAngle = $state(60)
  let canvas = $state<HTMLCanvasElement | null>(null)

  const bones: Bone[] = []
  const cart = { x: 0 }
  let held = false
  let lastPointer = 0
  let dir = 1
  let pause = 0
  let span = 3

  const source = $derived(
    'for _, name in { "tail", "tail2", "tail3" } do\n' +
      "    local bone = mesh:findFirstDescendant(name) :: Bone\n" +
      '    bone:add("JointSpring", {\n' +
      "        axis = vec3(0, 0, 1),\n" +
      `        length = ${LENGTH},\n` +
      `        stiffness = ${stiffness},\n` +
      `        damping = ${damping},\n` +
      `        maxAngle = ${maxAngle},\n` +
      "    })\n" +
      "end",
  )

  function wrapAngle(a: number) {
    let out = a
    while (out > Math.PI) out -= Math.PI * 2
    while (out < -Math.PI) out += Math.PI * 2
    return out
  }

  function limit(rest: Point, arm: Point, most: number) {
    const a = Math.atan2(rest.y, rest.x)
    const b = Math.atan2(arm.y, arm.x)
    const diff = wrapAngle(b - a)
    if (Math.abs(diff) <= most) return arm
    const angle = a + Math.sign(diff) * most
    const len = Math.hypot(arm.x, arm.y)
    return { x: Math.cos(angle) * len, y: Math.sin(angle) * len }
  }

  function simulate(dt: number) {
    let pivot = { x: cart.x - 0.55, y: 1.1 }
    let parentAngle = Math.PI
    const h = Math.min(dt, 0.1) / 4
    for (let i = 0; i < BONES; i++) {
      const rest = { x: Math.cos(parentAngle) * LENGTH, y: Math.sin(parentAngle) * LENGTH }
      let bone = bones[i]
      if (!bone) {
        bone = bones[i] = {
          tip: { x: pivot.x + rest.x, y: pivot.y + rest.y },
          v: { x: 0, y: 0 },
          pivot: { x: pivot.x, y: pivot.y },
        }
      }
      if (h > 0) {
        for (let n = 0; n < 4; n++) {
          const px = (pivot.x + rest.x - bone.tip.x) * stiffness - bone.v.x * damping
          const py = (pivot.y + rest.y - bone.tip.y) * stiffness - bone.v.y * damping - 9.8
          const was = { x: bone.tip.x, y: bone.tip.y }
          const vx = bone.v.x + px * h
          const vy = bone.v.y + py * h
          let arm = { x: was.x + vx * h - pivot.x, y: was.y + vy * h - pivot.y }
          const len = Math.hypot(arm.x, arm.y) || 1
          arm = { x: (arm.x / len) * LENGTH, y: (arm.y / len) * LENGTH }
          arm = limit(rest, arm, (maxAngle * Math.PI) / 180)
          const moved = { x: pivot.x + arm.x, y: pivot.y + arm.y }
          bone.v = { x: (moved.x - was.x) / h, y: (moved.y - was.y) / h }
          bone.tip = moved
        }
      }
      bone.pivot = { x: pivot.x, y: pivot.y }
      parentAngle = Math.atan2(bone.tip.y - pivot.y, bone.tip.x - pivot.x)
      pivot = { x: bone.tip.x, y: bone.tip.y }
    }
  }

  function draw() {
    const node = canvas
    if (!node) return
    const f = fit(node, HEIGHT)
    if (!f) return
    const c = palette()
    const ctx = f.ctx
    span = Math.max(1, (f.w / U - 4) / 2)
    const ox = f.w / 2
    const oy = HEIGHT - 30
    const sx = (x: number) => ox + x * U
    const sy = (y: number) => oy - y * U
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, f.w, HEIGHT)
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(0, oy)
    ctx.lineTo(f.w, oy)
    ctx.stroke()
    ctx.fillStyle = c.bg3
    ctx.strokeStyle = c.main
    ctx.lineWidth = 1.5
    ctx.fillRect(sx(cart.x - 0.55), sy(1.5), 1.1 * U, 0.8 * U)
    ctx.strokeRect(sx(cart.x - 0.55), sy(1.5), 1.1 * U, 0.8 * U)
    ctx.fillRect(sx(cart.x + 0.35), sy(1.9), 0.5 * U, 0.5 * U)
    ctx.strokeRect(sx(cart.x + 0.35), sy(1.9), 0.5 * U, 0.5 * U)
    for (const leg of [-0.4, 0.3]) {
      ctx.fillRect(sx(cart.x + leg), sy(0.7), 0.14 * U, 0.7 * U)
    }
    bones.forEach((bone, i) => {
      ctx.strokeStyle = i === 0 ? c.main : i === 1 ? c.accent : c.muted
      ctx.lineCap = "round"
      ctx.lineWidth = 12 - i * 3
      ctx.beginPath()
      ctx.moveTo(sx(bone.pivot.x), sy(bone.pivot.y))
      ctx.lineTo(sx(bone.tip.x), sy(bone.tip.y))
      ctx.stroke()
      ctx.lineCap = "butt"
      ctx.fillStyle = c.yellow
      ctx.beginPath()
      ctx.arc(sx(bone.pivot.x), sy(bone.pivot.y), 3, 0, Math.PI * 2)
      ctx.fill()
    })
  }

  simulate(0)

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(() => draw())
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    draw()
  })

  let previous = 0
  whileVisible(
    () => canvas,
    (now) => {
      const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0
      previous = now
      if (!held) {
        if (pause > 0) {
          pause -= dt
        } else {
          cart.x += dir * 3.2 * dt
          if (cart.x > span) {
            cart.x = span
            dir = -1
            pause = 1.2
          } else if (cart.x < -span) {
            cart.x = -span
            dir = 1
            pause = 1.2
          }
        }
      }
      simulate(dt)
      draw()
    },
  )
</script>

<Demo label="A tail on springs">
  <canvas
    bind:this={canvas}
    class="an-canvas an-drag"
    style="height: {HEIGHT}px"
    aria-label="a body running back and forth with a three bone tail trailing behind it"
    onpointerdown={(event) => {
      const node = canvas
      if (!node) return
      const point = pointIn(node, event, node.clientWidth, HEIGHT)
      if (Math.abs((point.x - node.clientWidth / 2) / U - cart.x) >= 1) return
      held = true
      lastPointer = point.x
      node.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => {
      const node = canvas
      if (!held || !node) return
      const point = pointIn(node, event, node.clientWidth, HEIGHT)
      cart.x = clamp(cart.x + (point.x - lastPointer) / U, -span, span)
      lastPointer = point.x
    }}
    onpointerup={() => {
      if (!held) return
      held = false
      pause = 1.5
    }}
    onpointercancel={() => {
      if (!held) return
      held = false
      pause = 1.5
    }}
  ></canvas>

  <div class="demo-controls">
    <Slider label="stiffness" min={5} max={120} step={5} bind:value={stiffness} />
    <Slider label="damping" min={0} max={20} bind:value={damping} />
    <Slider label="maxAngle" min={10} max={180} step={5} bind:value={maxAngle} />
  </div>

  <Note>
    Three bones, a spring on each, parents solved first. The body runs back and forth on its own; drag it
    to shake it yourself.
  </Note>
  <CodePanel {source} />
</Demo>
