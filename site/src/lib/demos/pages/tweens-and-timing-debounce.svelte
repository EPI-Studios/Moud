<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { fit, palette } from "../core/surface"

  const HEIGHT = 150
  const SPAN = 5

  let canvas = $state<HTMLCanvasElement | null>(null)
  let quiet = $state(0.5)
  let gap = $state(0.2)
  let holding = $state<number | null>(null)

  let calls: number[] = []
  let saves: number[] = []
  let shots: number[] = []
  let pending: number | null = null
  let lastShot = -Infinity
  let clock = 0

  const source = $derived(
    `local save = task.debounce(function() end, ${quiet})  -- runs once things are quiet for ${quiet} s\n` +
      `local shoot = task.throttle(function() end, ${gap}) -- at most once every ${gap} s\n\n` +
      "save()\nshoot()",
  )

  function call() {
    calls.push(clock)
    pending = clock + quiet
    if (clock - lastShot >= gap) {
      lastShot = clock
      shots.push(clock)
    }
  }

  function draw() {
    if (!canvas) return
    const view = fit(canvas, HEIGHT)
    if (!view) return
    const c = palette()
    const ctx = view.ctx
    const left = 104
    const right = view.w - 12
    const from = clock - SPAN
    const x = (t: number) => left + ((t - from) / SPAN) * (right - left)

    ctx.clearRect(0, 0, view.w, view.h)
    ctx.font = `11px ${c.mono}`
    const lanes: [string, number[], string, number][] = [
      ["calls", calls, c.muted, 30],
      ["save runs", saves, c.green, 72],
      ["shoot runs", shots, c.blue, 114],
    ]
    for (const [name, list, color, y] of lanes) {
      ctx.fillStyle = c.muted
      ctx.fillText(name, 8, y + 4)
      ctx.strokeStyle = c.line2
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(left, y)
      ctx.lineTo(right, y)
      ctx.stroke()
      ctx.fillStyle = color
      for (const t of list) {
        const px = x(t)
        if (px < left - 2) continue
        if (list === calls) ctx.fillRect(px - 1, y - 8, 2, 16)
        else {
          ctx.beginPath()
          ctx.arc(px, y, 5, 0, Math.PI * 2)
          ctx.fill()
        }
      }
    }
    if (pending !== null) {
      ctx.strokeStyle = c.green
      ctx.globalAlpha = 0.5
      ctx.setLineDash([3, 3])
      ctx.beginPath()
      ctx.moveTo(x(clock), 72)
      ctx.lineTo(x(pending), 72)
      ctx.stroke()
      ctx.setLineDash([])
      ctx.globalAlpha = 1
      ctx.fillStyle = c.muted
      ctx.fillText("waiting for quiet", Math.min(x(clock) + 4, right - 110), 60)
    }
    ctx.fillStyle = c.light
    if (!calls.length) ctx.fillText("press a button below", left + 12, 52)
    ctx.fillText("now", right - 22, 142)
    ctx.fillText("-5 s", left, 142)
  }

  let last: number | null = null

  whileVisible(
    () => canvas,
    (now) => {
      const seconds = now / 1000
      const dt = last === null ? 0 : Math.min(0.1, seconds - last)
      last = seconds
      clock += dt
      if (holding !== null) {
        while (holding + 0.05 <= clock) {
          holding += 0.05
          const keep = clock
          clock = holding
          call()
          clock = keep
        }
      }
      if (pending !== null && clock >= pending) {
        saves.push(pending)
        pending = null
      }
      const from = clock - SPAN
      for (const list of [calls, saves, shots]) {
        while (list.length && list[0] < from - 1) list.shift()
      }
      draw()
    },
  )
</script>

<svelte:window onresize={draw} />

<Demo label="debounce and throttle">
  <canvas bind:this={canvas} class="tw-lanes"></canvas>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide tw-mono" onclick={call}>call both once</button>
    <button
      type="button"
      class="demo-pill demo-pill-wide tw-mono"
      aria-pressed={holding !== null}
      onpointerdown={(event) => {
        event.preventDefault()
        holding = clock
        call()
      }}
      onpointerup={() => (holding = null)}
      onpointerleave={() => (holding = null)}
      onpointercancel={() => (holding = null)}
    >
      hold to call 20 times a second
    </button>
  </div>

  <div class="demo-controls">
    <Slider label="debounce" min={0.1} max={1.5} step={0.1} bind:value={quiet} />
    <Slider label="throttle" min={0.05} max={1} step={0.05} bind:value={gap} />
  </div>

  <CodePanel {source} />
</Demo>
