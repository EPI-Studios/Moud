<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import Slider from "../ui/Slider.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { palette } from "../core/surface"
  import type { Palette } from "../core/surface"

  type Limb = { pivot: [number, number]; box: [number, number, number, number] }

  const LIMBS: Record<string, Limb> = {
    head: { pivot: [0, 0], box: [-4, -8, 8, 8] },
    torso: { pivot: [0, 0], box: [-4, 0, 8, 12] },
    rightArm: { pivot: [-5, 2], box: [-3, -2, 4, 12] },
    leftArm: { pivot: [5, 2], box: [-1, -2, 4, 12] },
    rightLeg: { pivot: [-1.9, 12], box: [-2, 0, 4, 12] },
    leftLeg: { pivot: [1.9, 12], box: [-2, 0, 4, 12] },
  }

  const NAMES = ["head", "rightArm", "leftArm", "rightLeg", "leftLeg"]
  const ORDER = ["rightLeg", "leftLeg", "torso", "rightArm", "leftArm", "head"]

  const W = 720
  const H = 360
  const PX = 8
  const CX = 360
  const NECK = 158

  let joints = $state<Record<string, { angle: number; scale: number }>>(
    Object.fromEntries(NAMES.map((name) => [name, { angle: 0, scale: 1 }])),
  )
  let pick = $state("rightArm")
  let stray = $state<{ name: string; angle: number } | null>(null)
  let lines = $state<Line[]>([{ id: 0, text: "pick a joint and turn it; the dot is where it holds its limb" }])
  let source = $state("-- every joint at rest")
  let canvas = $state<HTMLCanvasElement | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let next = 1

  function fmt(value: number) {
    const rounded = Math.round(value * 100) / 100
    return String(Object.is(rounded, -0) ? 0 : rounded)
  }

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function jointCode() {
    const written: string[] = []
    for (const name of NAMES) {
      const joint = joints[name]
      if (Math.abs(joint.angle) > 0.001) {
        written.push(`character.joints.${name}.transform = cframe.angles(0, 0, ${fmt(joint.angle)})`)
      }
      if (Math.abs(joint.scale - 1) > 0.001) {
        written.push(
          `character.joints.${name}.scale = vec3(${fmt(joint.scale)}, ${fmt(joint.scale)}, ${fmt(joint.scale)})`,
        )
      }
    }
    return written.length ? written.join("\n") : "-- every joint at rest"
  }

  function writeLimb() {
    const name = pick
    const angle = joints[name].angle + 1.2
    stray = { name, angle }
    source = `-- wrong: writing the output\ncharacter.${name}.cframe = cframe.angles(0, 0, ${fmt(angle)})`
    record(`wrote character.${name}.cframe`, "out")
    setTimeout(() => {
      stray = null
      record(`next tick: the engine composed ${name} from its joint again`)
    }, 700)
  }

  function reset() {
    for (const name of NAMES) joints[name] = { angle: 0, scale: 1 }
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

  function drawLimb(
    ctx: CanvasRenderingContext2D,
    c: Palette,
    name: string,
    angle: number,
    scale: number,
    override: boolean,
  ) {
    const limb = LIMBS[name]
    ctx.save()
    ctx.translate(CX + limb.pivot[0] * PX, NECK + limb.pivot[1] * PX)
    ctx.rotate(angle)
    ctx.scale(scale, scale)
    const [bx, by, bw, bh] = limb.box
    ctx.fillStyle = name === "torso" ? c.bg3 : c.bg4
    ctx.fillRect(bx * PX, by * PX, bw * PX, bh * PX)
    ctx.lineWidth = 1.5 / scale
    ctx.strokeStyle = override ? c.red : name === pick ? c.main : c.light
    if (override) ctx.setLineDash([5 / scale, 4 / scale])
    ctx.strokeRect(bx * PX, by * PX, bw * PX, bh * PX)
    ctx.setLineDash([])
    ctx.restore()
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = c.line2
    ctx.beginPath()
    ctx.moveTo(0, NECK + 24 * PX + 0.5)
    ctx.lineTo(W, NECK + 24 * PX + 0.5)
    ctx.stroke()

    for (const name of ORDER) {
      const joint = joints[name] ?? { angle: 0, scale: 1 }
      if (stray && stray.name === name) drawLimb(ctx, c, name, stray.angle, joint.scale, true)
      else drawLimb(ctx, c, name, joint.angle, joint.scale, false)
    }

    const pivot = LIMBS[pick].pivot
    ctx.beginPath()
    ctx.arc(CX + pivot[0] * PX, NECK + pivot[1] * PX, 4, 0, Math.PI * 2)
    ctx.fillStyle = c.accent
    ctx.fill()
    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.fillStyle = c.muted
    ctx.textAlign = "left"
    ctx.fillText(`joints.${pick}`, CX + pivot[0] * PX + (pivot[0] < 0 ? -150 : 12), NECK + pivot[1] * PX - 8)
    if (stray) {
      ctx.fillStyle = c.red
      ctx.textAlign = "center"
      ctx.fillText("the limb's cframe, written by hand: gone on the next tick", CX, 24)
    }
  }

  $effect(() => {
    source = jointCode()
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

<Demo label="Posing with joints">
  <canvas
    bind:this={canvas}
    style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: {W} / {H}"
    aria-label="a character seen from the front, posed at its joints"
  ></canvas>

  <div class="demo-controls">
    <Choice label="joint" options={NAMES} bind:value={pick} />
    <Slider label="transform" min={-3.14} max={3.14} step={0.01} bind:value={joints[pick].angle} format={fmt} />
    <Slider label="scale" min={0.5} max={2.2} step={0.05} bind:value={joints[pick].scale} format={fmt} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={writeLimb}>write the limb instead</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={reset}>reset</button>
  </div>

  <Log {lines} keep={3} />
  <CodePanel {source} />
</Demo>
