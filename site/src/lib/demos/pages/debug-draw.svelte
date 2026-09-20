<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { fit, palette } from "../core/surface"

  type Pose = { x: number; y: number; angle: number }

  const HEIGHT = 230
  const MODES = ["every step, no time", "every step, 2 seconds", "once, 2 seconds"] as const

  let mode = $state<(typeof MODES)[number]>(MODES[0])
  let canvas = $state<HTMLCanvasElement | null>(null)

  let clock = 0
  let tickAt = 0
  let last = 0
  let shapes: { at: Pose; until: number }[] = []

  const source = $derived(
    mode === MODES[0]
      ? "game.stepped:connect(function()\n    game.debug:drawBox(part.worldCframe, part.size)\nend)"
      : mode === MODES[1]
        ? "game.stepped:connect(function()\n    game.debug:drawBox(part.worldCframe, part.size, nil, 2)\nend)"
        : "game.debug:drawBox(part.worldCframe, part.size, nil, 2)",
  )

  const note = $derived(
    mode === MODES[0]
      ? "With no time, each box is up for one frame. Drawing it again every step gives a box that follows the part."
      : mode === MODES[1]
        ? "Every box stays for 2 seconds, and a new one is drawn twenty times a second, so they pile up into a trail."
        : "Drawn once, the box stays where the part was for 2 seconds and then goes, while the part moves on.",
  )

  function partAt(t: number): Pose {
    return { x: Math.cos(t * 0.9) * 0.62, y: Math.sin(t * 1.8) * 0.5, angle: t * 0.9 + Math.PI / 2 }
  }

  function draw() {
    const node = canvas
    if (!node) return
    const view = fit(node, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, view.w, HEIGHT)
    const cx = view.w / 2
    const cy = HEIGHT / 2
    const sx = Math.min(view.w / 2 - 40, 260)
    const sy = HEIGHT / 2 - 34
    const box = (p: Pose, color: string, alpha: number, fill?: string) => {
      ctx.save()
      ctx.translate(cx + p.x * sx, cy + p.y * sy)
      ctx.rotate(p.angle)
      ctx.globalAlpha = alpha
      if (fill) {
        ctx.fillStyle = fill
        ctx.fillRect(-22, -13, 44, 26)
      }
      ctx.strokeStyle = color
      ctx.lineWidth = 1.5
      ctx.strokeRect(-22, -13, 44, 26)
      ctx.restore()
    }
    const part = partAt(clock)
    box(part, c.line2, 1, c.bg4)
    for (const shape of shapes) box(shape.at, c.green, Math.max(0.15, (shape.until - clock) / 2))
    let up = shapes.length
    if (mode === MODES[0]) {
      box(part, c.green, 1)
      up += 1
    }
    ctx.font = "11.5px " + c.mono
    ctx.textBaseline = "middle"
    ctx.fillStyle = c.muted
    ctx.fillText("shapes up: " + up, 10, 16)
  }

  $effect(() => {
    if (canvas) draw()
  })

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      clock += dt
      tickAt += dt
      shapes = shapes.filter((shape) => shape.until > clock)
      while (tickAt >= 0.05) {
        tickAt -= 0.05
        if (mode === MODES[1]) shapes.push({ at: partAt(clock - tickAt), until: clock - tickAt + 2 })
      }
      draw()
    },
  )
</script>

<Demo label="How long a shape stays up">
  <canvas
    bind:this={canvas}
    class="dbg-canvas"
    style="height: {HEIGHT}px"
    aria-label="a box drawn on a moving part"
  ></canvas>

  <div class="demo-controls">
    <Choice label="drawn" options={MODES} bind:value={mode} />
    <div class="demo-choice">
      <span class="demo-slider-name"></span>
      <div class="demo-choice-buttons">
        <button
          type="button"
          class="demo-pill demo-pill-wide"
          onclick={() => shapes.push({ at: partAt(clock), until: clock + 2 })}
        >
          drawBox once
        </button>
        <button type="button" class="demo-pill demo-pill-wide" onclick={() => (shapes = [])}>
          game.debug:clear()
        </button>
      </div>
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
