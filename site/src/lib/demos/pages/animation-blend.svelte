<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type PlayerEntity, type Point2, type Scene } from "../core/mc2d"
  import { num } from "../core/surface"

  type Track = {
    name: string
    priority: number
    angle: number
    color: string
    additive?: boolean
  }

  const HEIGHT = 250
  const TRACKS: Track[] = [
    { name: "idle", priority: 1, angle: 15, color: "blue" },
    { name: "point", priority: 1, angle: 90, color: "green" },
    { name: "attack", priority: 3, angle: 160, color: "red" },
    { name: "nod", priority: 0, angle: 25, color: "purple", additive: true },
  ]

  let weights = $state([1, 0, 0, 0])
  let printed = $state(0)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let clock = 0
  let walkAngle = 0
  let lastWrite = ""

  const note = $derived.by(() => {
    const p1 = weights[0] + weights[1]
    let text: string
    if (weights[2] >= 1) text = "attack is at priority 3 with full weight, so it hides everything below it."
    else if (weights[2] > 0)
      text = "attack covers what is under it by its weight, " + weights[2] + ", and leaves the rest to priority 1."
    else if (p1 === 0) text = "No normal track moves the arm, so it keeps the engine's walk."
    else if (p1 < 1)
      text = "Priority 1 adds up to " + num(p1, 1) + ", so it covers the walk by that much and the walk shows through the rest."
    else if (weights[0] > 0 && weights[1] > 0) text = "idle and point share priority 1, so they share the arm by weight."
    else text = "Priority 1 is at full weight, so the walk underneath is hidden."
    if (weights[3] > 0) text += " nod is additive: it adds its turn on top, after everything else."
    return text
  })

  const source = $derived(
    [
      'local idle = body.animator:loadAnimation(world:find("idle"))',
      'local point = body.animator:loadAnimation(world:find("point"))',
      'local attack = body.animator:loadAnimation(world:find("attack"))',
      'local nod = body.animator:loadAnimation(world:find("nod"))',
      "idle.priority = 1",
      "point.priority = 1",
      "attack.priority = 3",
      'nod.blend = "additive"',
      "",
      ...TRACKS.map((track, i) => `${track.name}:adjustWeight(${weights[i]})`),
      "",
      `-- the arm ends up ${printed} degrees forward`,
    ].join("\n"),
  )

  function blend(walk: number) {
    let out = walk
    const levels = new Map<number, Track[]>()
    TRACKS.forEach((track, i) => {
      if (track.additive || weights[i] === 0) return
      const level = levels.get(track.priority) ?? []
      level.push(track)
      levels.set(track.priority, level)
    })
    for (const priority of [...levels.keys()].sort((a, b) => a - b)) {
      let level = 0
      let total = 0
      for (const track of levels.get(priority) ?? []) {
        const weight = weights[TRACKS.indexOf(track)]
        level = total === 0 ? track.angle : level + (track.angle - level) * (weight / (total + weight))
        total += weight
      }
      out = out + (level - out) * Math.min(1, total)
    }
    TRACKS.forEach((track, i) => {
      if (track.additive) out += track.angle * Math.min(1, weights[i])
    })
    return out
  }

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const colors: Record<string, string> = v.colors
    const pivot = { x: 0, y: 64 + (22 * 0.9375) / 16, d: 0.5 - (5 * 0.9375) / 16 }
    const sx = v.x(pivot.x, pivot.d)
    const sy = v.y(pivot.y, pivot.d)
    const length = 0.95 * v.unit
    const ghost = (angle: number, color: string, label: string) => {
      const a = (angle * Math.PI) / 180
      const from: Point2 = [sx, sy]
      const to: Point2 = [sx + Math.sin(a) * length, sy + Math.cos(a) * length]
      v.line([from, to], { dash: [4, 4], width: 1.5, color })
      v.tag(label, sx + Math.sin(a) * (length + 20), sy + Math.cos(a) * (length + 14), {
        align: "center",
        color,
      })
    }
    ghost(walkAngle, "#e8e8e8", "walk")
    TRACKS.forEach((track, i) => {
      if (track.additive || weights[i] === 0) return
      ghost(track.angle, colors[track.color], track.name)
    })
    ctx.fillStyle = "#ffffff"
    ctx.strokeStyle = "rgba(0,0,0,0.5)"
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.arc(sx, sy, 3.5, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
    v.tag("rightArm, seen from the side", 8, 16)
  }

  function pose(steve: PlayerEntity) {
    const walk = Math.sin(clock * 4) * 30
    const result = blend(walk)
    walkAngle = walk
    steve.limbs.rightArm = (-result * Math.PI) / 180
    steve.limbs.leftArm = (walk * Math.PI) / 180
    steve.limbs.rightLeg = (walk * Math.PI) / 180
    steve.limbs.leftLeg = (-walk * Math.PI) / 180
    const key = weights.join() + "|" + Math.round(result / 5) * 5
    if (key === lastWrite) return
    lastWrite = key
    printed = Math.round(result)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    let steve: PlayerEntity | null = null
    const scene: Scene = createScene(node, {
      height: HEIGHT,
      time: 2200,
      view: { x0: -2.4, x1: 2.4, y0: 63.55, y1: 66.75 },
      overlay,
      step: (dt) => {
        clock += dt
        if (steve) pose(steve)
      },
    })
    for (let gx = -20; gx <= 20; gx++) {
      scene.set(gx, 63, "grass_block")
      scene.set(gx, 62, "dirt")
    }
    steve = scene.player({ x: 0, y: 64, facing: 1 })
    untrack(() => steve && pose(steve))
    scene.start()
    return () => scene.destroy()
  })
</script>

<Demo label="Priority and weight on one joint">
  <canvas bind:this={canvas} class="an-canvas mc2d" aria-label="a character seen from the side, one arm blended from several tracks"
  ></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    {#each TRACKS as track, i (track.name)}
      <Slider
        label={track.additive ? "nod (additive)" : `${track.name} (priority ${track.priority})`}
        min={0}
        max={1}
        step={0.1}
        bind:value={weights[i]}
      />
    {/each}
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
