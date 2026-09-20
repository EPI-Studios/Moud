<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { fit, palette } from "../core/surface"

  type Pick = keyof typeof OPTIONS

  const OPTIONS = {
    first: 0,
    input: 100,
    camera: 200,
    "camera + 1": 201,
    character: 300,
    last: 2000,
  }

  const HEIGHT = 200
  const PICKS = Object.keys(OPTIONS) as Pick[]

  let canvas = $state<HTMLCanvasElement | null>(null)
  let pick = $state<Pick>("camera + 1")

  const priority = $derived(OPTIONS[pick])

  const steps = $derived.by(() => {
    const entries = [
      { at: 100, text: "bound at input, 100", kind: "" },
      { at: 200, text: "bound at camera, 200", kind: "" },
      { at: 200.5, text: "game.renderStepped fires", kind: "tw-event" },
      { at: 200.6, text: "the camera is placed", kind: "tw-event" },
      { at: 300, text: "bound at character, 300", kind: "" },
      { at: 99999, text: "the frame is drawn", kind: "tw-event" },
      {
        at: priority <= 200 ? priority - 0.01 : priority,
        text: `"lamp" at ${priority}`,
        kind: "tw-mine",
      },
    ]
    return entries.sort((a, b) => a.at - b.at)
  })

  const note = $derived(
    priority > 200
      ? "Above 200 the lamp runs after the camera is placed, so camera.cframe is this frame's and the lamp sits where it should."
      : "At 200 or below the lamp runs before the camera is placed, so camera.cframe is still last frame's and the lamp trails one frame behind (slowed down here to 8 frames a second so you can see it).",
  )

  const source = $derived.by(() => {
    const expr = pick === "camera + 1" ? "game.renderPriority.camera + 1" : `game.renderPriority.${pick}`
    return (
      `game:bindToRenderStep("lamp", ${expr}, function(dt)\n` +
      "    lamp.cframe = camera.cframe * cframe(0.4, -0.3, -1)\n" +
      "end)"
    )
  })

  let frame = -1
  let cams: { x: number; y: number; a: number }[] = []

  whileVisible(
    () => canvas,
    (now) => {
      const seconds = now / 1000
      const index = Math.floor(seconds * 8)
      if (index !== frame) {
        frame = index
        const angle = index * 0.28
        cams.push({ x: Math.cos(angle), y: Math.sin(angle) * 0.6, a: angle })
        if (cams.length > 2) cams.shift()
      }
      if (!cams.length || !canvas) return
      const view = fit(canvas, HEIGHT)
      if (!view) return
      const c = palette()
      const ctx = view.ctx
      ctx.clearRect(0, 0, view.w, view.h)

      const cx = view.w / 2
      const cy = view.h / 2
      const radius = Math.min(view.w * 0.34, 70)
      const current = cams[cams.length - 1]
      const used = priority > 200 || cams.length < 2 ? current : cams[0]
      const spot = (cam: { x: number; y: number; a: number }) => ({
        x: cx + cam.x * radius + Math.cos(cam.a + 1.6) * 40,
        y: cy + cam.y * radius + Math.sin(cam.a + 1.6) * 40,
      })

      ctx.strokeStyle = c.line2
      ctx.setLineDash([3, 4])
      ctx.beginPath()
      ctx.ellipse(cx, cy, radius, radius * 0.6, 0, 0, Math.PI * 2)
      ctx.stroke()
      ctx.setLineDash([])

      const want = spot(current)
      const got = spot(used)
      const camX = cx + current.x * radius
      const camY = cy + current.y * radius
      ctx.strokeStyle = c.line2
      ctx.beginPath()
      ctx.moveTo(camX, camY)
      ctx.lineTo(want.x, want.y)
      ctx.stroke()
      ctx.strokeStyle = c.light
      ctx.beginPath()
      ctx.arc(want.x, want.y, 7, 0, Math.PI * 2)
      ctx.stroke()
      ctx.fillStyle = priority > 200 ? c.green : c.yellow
      ctx.beginPath()
      ctx.arc(got.x, got.y, 5, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = c.main
      ctx.fillRect(camX - 6, camY - 5, 12, 10)
      ctx.font = `11px ${c.mono}`
      ctx.fillStyle = c.muted
      ctx.fillText("camera", camX + 9, camY + 4)
      ctx.fillText("lamp", got.x + 8, got.y + 14)
    },
  )
</script>

<Demo label="Render priority">
  <div class="tw-render">
    <ol class="tw-steps">
      {#each steps as step (step.text)}
        <li class={step.kind}>{step.text}</li>
      {/each}
    </ol>
    <canvas bind:this={canvas} class="tw-view"></canvas>
  </div>

  <div class="demo-controls">
    <Choice label="priority" options={PICKS} bind:value={pick} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
