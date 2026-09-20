<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { fit, palette } from "../core/surface"

  const WIDTH_M = 24

  let inner = $state(20)
  let outer = $state(35)
  let range = $state(12)
  let from = $state({ x: 3, z: 6 })
  let to = $state({ x: 16, z: 6 })

  let plan = $state<HTMLCanvasElement | null>(null)
  let buffer = $state<HTMLCanvasElement | null>(null)

  let scale = 1
  let held = false
  let dragging: { x: number; z: number } = from

  function smoothstep(t: number) {
    return t * t * (3 - 2 * t)
  }

  function falloff(dist: number, reach: number) {
    if (dist > reach) return 0
    return 1 - smoothstep(Math.max(0, Math.min(1, dist / Math.max(reach, 1e-4))))
  }

  function fmt(value: number) {
    let text = value.toFixed(1)
    if (text.indexOf(".") !== -1) text = text.replace(/0+$/, "").replace(/\.$/, "")
    return text === "-0" ? "0" : text
  }

  const source = $derived(
    `local from = vec3(${fmt(from.x)}, 65, ${fmt(from.z)})\n` +
      `local to = vec3(${fmt(to.x)}, 65, ${fmt(to.z)})\n\n` +
      'world:add("SpotLight", {\n' +
      `    innerAngle = ${inner}, outerAngle = ${outer},\n` +
      `    range = ${range},\n` +
      "    cframe = cframe.lookAt(from, to),\n" +
      "})",
  )

  function draw() {
    if (!plan || !buffer) return
    const c = palette()
    const f = fit(plan, plan.clientHeight)
    if (!f) return
    const ctx = f.ctx
    scale = f.w / WIDTH_M
    const depth = f.h / scale
    const cols = Math.min(240, Math.round(f.w / 2))
    const rows = Math.round(cols * (f.h / f.w))
    buffer.width = cols
    buffer.height = rows
    const bctx = buffer.getContext("2d")!
    const image = bctx.createImageData(cols, rows)
    let dx = to.x - from.x
    let dz = to.z - from.z
    const len = Math.sqrt(dx * dx + dz * dz) || 1
    dx /= len
    dz /= len
    const cosIn = Math.cos((inner * Math.PI) / 180)
    const cosOut = Math.cos((Math.max(inner, outer) * Math.PI) / 180)
    for (let j = 0; j < rows; j++) {
      for (let i = 0; i < cols; i++) {
        const wx = ((i + 0.5) / cols) * WIDTH_M
        const wz = ((j + 0.5) / rows) * depth
        const lx = wx - from.x
        const lz = wz - from.z
        const dist = Math.sqrt(lx * lx + lz * lz)
        let a = falloff(dist, range)
        if (a > 0 && dist > 1e-4) {
          const cd = (lx * dx + lz * dz) / dist
          a *= Math.max(0, Math.min(1, (cd - cosOut) / Math.max(cosIn - cosOut, 1e-4)))
        }
        const checker = (Math.floor(wx) + Math.floor(wz)) & 1 ? 34 : 28
        const at = (j * cols + i) * 4
        image.data[at] = Math.min(255, checker + a * 235)
        image.data[at + 1] = Math.min(255, checker + a * 215)
        image.data[at + 2] = Math.min(255, checker + a * 170)
        image.data[at + 3] = 255
      }
    }
    bctx.putImageData(image, 0, 0)
    ctx.imageSmoothingEnabled = true
    ctx.drawImage(buffer, 0, 0, f.w, f.h)

    const ox = from.x * scale
    const oz = from.z * scale
    const aim = Math.atan2(dz, dx)
    const reach = range * scale
    const ray = (angle: number, color: string, dash: number[]) => {
      ctx.beginPath()
      ctx.setLineDash(dash)
      ctx.moveTo(ox, oz)
      ctx.lineTo(ox + Math.cos(aim + angle) * reach, oz + Math.sin(aim + angle) * reach)
      ctx.strokeStyle = color
      ctx.lineWidth = 1.25
      ctx.stroke()
      ctx.setLineDash([])
    }
    const innerRad = (inner * Math.PI) / 180
    const outerRad = (Math.max(inner, outer) * Math.PI) / 180
    ray(innerRad, c.main, [])
    ray(-innerRad, c.main, [])
    ray(outerRad, c.muted, [4, 4])
    ray(-outerRad, c.muted, [4, 4])

    ctx.beginPath()
    ctx.arc(ox, oz, reach, aim - outerRad, aim + outerRad)
    ctx.strokeStyle = c.light
    ctx.setLineDash([2, 4])
    ctx.stroke()
    ctx.setLineDash([])

    ctx.beginPath()
    ctx.moveTo(ox, oz)
    ctx.lineTo(to.x * scale, to.z * scale)
    ctx.strokeStyle = c.light
    ctx.stroke()

    ctx.beginPath()
    ctx.arc(to.x * scale, to.z * scale, 6, 0, Math.PI * 2)
    ctx.fillStyle = c.bg
    ctx.fill()
    ctx.lineWidth = 1.5
    ctx.strokeStyle = c.main
    ctx.stroke()

    ctx.beginPath()
    ctx.arc(ox, oz, 7, 0, Math.PI * 2)
    ctx.fillStyle = c.main
    ctx.fill()

    ctx.font = "11px " + c.mono
    ctx.fillStyle = c.text
    ctx.textAlign = "left"
    ctx.fillText("from", ox + 10, oz - 10)
    ctx.fillText("to", to.x * scale + 10, to.z * scale - 10)
  }

  function move(event: PointerEvent) {
    if (!plan) return
    const p = pointIn(plan, event, plan.clientWidth, plan.clientHeight)
    const depth = plan.clientHeight / scale
    dragging.x = clamp(p.x / scale, 0.3, WIDTH_M - 0.3)
    dragging.z = clamp(p.y / scale, 0.3, depth - 0.3)
    if (Math.hypot(to.x - from.x, to.z - from.z) < 0.5) {
      to.x = Math.min(WIDTH_M - 0.3, from.x + 1)
    }
  }

  function grab(event: PointerEvent) {
    if (!plan) return
    const p = pointIn(plan, event, plan.clientWidth, plan.clientHeight)
    const wx = p.x / scale
    const wz = p.y / scale
    dragging = Math.hypot(wx - from.x, wz - from.z) < Math.hypot(wx - to.x, wz - to.z) ? from : to
    plan.setPointerCapture(event.pointerId)
    held = true
    move(event)
  }

  $effect(() => {
    draw()
  })

  $effect(() => {
    const node = plan
    if (!node) return
    let last = 0
    const watcher = new ResizeObserver(() => {
      if (node.clientWidth === last) return
      last = node.clientWidth
      draw()
    })
    watcher.observe(node)
    return () => watcher.disconnect()
  })
</script>

<Demo label="SpotLight cone">
  <div class="rendering-wide">
    <canvas
      bind:this={plan}
      class="rendering-canvas rendering-drag rendering-plan"
      style="aspect-ratio: 1 / 0.44"
      aria-label="top down view of a spot light"
      onpointerdown={grab}
      onpointermove={(event) => held && move(event)}
      onpointerup={() => (held = false)}
      onpointercancel={() => (held = false)}
    ></canvas>
    <canvas bind:this={buffer} hidden></canvas>
  </div>

  <div class="demo-controls">
    <Slider label="innerAngle" min={1} max={80} step={1} bind:value={inner} />
    <Slider label="outerAngle" min={1} max={89} step={1} bind:value={outer} />
    <Slider label="range" min={3} max={24} step={1} bind:value={range} />
  </div>

  <Note>Seen from above with the light aimed level. Drag the light or the point it looks at.</Note>
  <CodePanel {source} />
</Demo>
