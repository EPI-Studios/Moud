<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Log from "../ui/Log.svelte"
  import Note from "../ui/Note.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { fitLegacy, palette} from "../core/surface"
  import { whileVisible } from "../core/frames"

  const BEHAVIOURS = ["presses yes", "presses nothing", "leaves", "never answers"]
  const NAMES = ["meek", "ana", "bo", "kit"]
  const SPAN = 12
  const HEIGHT = 44 + NAMES.length * 30

  let canvas = $state<HTMLCanvasElement | null>(null)
  let does = $state(["presses yes", "presses nothing", "leaves", "never answers"])
  let lines = $state<Line[]>([])
  let next = 0

  let clock = 0
  let running = true

  function outcome(name: string, behaviour: string) {
    if (behaviour === "presses yes") return { at: 3, kind: "yes", text: `${name} said true, player:spawn()` }
    if (behaviour === "presses nothing") {
      return { at: 8, kind: "no", text: `${name} said false: wait(8) gave nil, nobody spawns` }
    }
    if (behaviour === "leaves") {
      return { at: 5, kind: "error", text: `${name} did not answer: ready got no answer, the player left` }
    }
    return {
      at: 10,
      kind: "error",
      text: `${name} did not answer: ready got no answer from the client within 10 seconds`,
    }
  }

  function draw() {
    if (!canvas) return
    const fit = fitLegacy(canvas, HEIGHT)
    if (!fit) return
    const c = palette()
    const { ctx, width } = fit

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, width, HEIGHT)
    const left = 56
    const right = width - 26
    const x = (t: number) => left + (right - left) * (t / SPAN)

    ctx.font = `11px ${c.mono}`
    ctx.textBaseline = "middle"
    ctx.textAlign = "center"
    ctx.fillStyle = c.light
    for (let s = 0; s <= SPAN; s += 2) {
      ctx.fillText(`${s} s`, x(s), 12)
      ctx.fillStyle = c.line
      ctx.fillRect(x(s), 22, 1, HEIGHT - 26)
      ctx.fillStyle = c.light
    }
    for (const at of [8, 10]) {
      ctx.strokeStyle = c.line2
      ctx.setLineDash([3, 3])
      ctx.beginPath()
      ctx.moveTo(x(at), 22)
      ctx.lineTo(x(at), HEIGHT - 4)
      ctx.stroke()
      ctx.setLineDash([])
    }

    ctx.textAlign = "left"
    NAMES.forEach((name, i) => {
      const y = 38 + i * 30
      const end = outcome(name, does[i])
      ctx.fillStyle = c.muted
      ctx.fillText(name, 8, y)
      if (clock < 0) return
      const shown = Math.min(clock, end.at)
      ctx.fillStyle = c.bg4
      ctx.fillRect(x(0), y - 8, x(shown) - x(0), 16)
      if (clock >= end.at) {
        ctx.fillStyle = end.kind === "yes" ? c.green : end.kind === "no" ? c.yellow : c.red
        ctx.beginPath()
        ctx.arc(x(end.at), y, 6, 0, Math.PI * 2)
        ctx.fill()
      }
    })
  }

  let last: number | null = null
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      if (!running) return
      const before = clock
      clock += dt * 2
      NAMES.forEach((name, i) => {
        const end = outcome(name, does[i])
        if (before < end.at && clock >= end.at) {
          lines = [{ id: next++, text: end.text, kind: end.kind === "yes" ? "in" : "out" }, ...lines]
        }
      })
      if (clock >= SPAN) running = false
      draw()
    },
  )

  let picked = does.join("|")
  $effect(() => {
    const now = does.join("|")
    if (now !== picked) {
      picked = now
      clock = -1
      running = false
      lines = []
    }
    draw()
  })
</script>

<svelte:window onresize={draw} />

<Demo label="Asking every player">
  <div class="tk-ready">
    {#each NAMES as name, i (name)}
      <Choice label={name} options={BEHAVIOURS} bind:value={does[i]} />
    {/each}
  </div>

  <canvas bind:this={canvas} class="tk-canvas" style="height: {HEIGHT}px"></canvas>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        clock = 0
        running = true
        lines = []
      }}
    >
      ask everyone (twice as fast)
    </button>
  </div>

  <Log {lines} />

  <Note>
    ready has timeout = 10. Each player is asked from their own task.spawn, so a slow one holds up
    nobody else.
  </Note>
</Demo>
