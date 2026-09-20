<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"
  import { fit, num, palette } from "../core/surface"

  type Ending = "loop" | "once" | "hold"

  const HEIGHT = 86
  const LENGTH = 1.2
  const MARKERS = [0.3, 0.9]

  let ending = $state<Ending>("once")
  let speed = $state(1)
  let track = $state({ playing: false, time: 0, fade: 0, ended: false })
  let lines = $state<Line[]>([])
  let canvas = $state<HTMLCanvasElement | null>(null)
  let nextLine = 0

  const source = $derived(
    `-- walk.anim has "loop": "${ending}" and two markers named step\n` +
      `track:play(0.1, 1, ${speed})\n\n` +
      "track.keyframeReached:connect(function(name) print(name) end)\n" +
      "track.didLoop:connect(function() end)\n" +
      "track.ended:connect(function() end)\n" +
      "track.stopped:connect(function() end)",
  )

  const readout = $derived(
    `playing ${track.playing}    timePosition ${num(track.time)}    weight x fade ${num(track.fade)}`,
  )

  function say(text: string, kind?: Line["kind"]) {
    lines.unshift({ id: nextLine++, text, kind })
    if (lines.length > 5) lines.length = 5
  }

  function stop(reason?: string) {
    if (!track.playing) return
    track.playing = false
    say("stopped" + (reason ? `  (${reason})` : ""), "out")
  }

  function play() {
    if (track.playing) {
      say("play() on a playing track keeps its place", "idle")
      return
    }
    track.playing = true
    track.ended = false
    track.time = speed < 0 ? LENGTH : 0
    say(`play()  from ${speed < 0 ? "the end" : "the start"}`, "idle")
  }

  function step(dt: number) {
    if (track.playing) track.fade = Math.min(1, track.fade + dt / 0.1)
    else track.fade = Math.max(0, track.fade - dt / 0.1)
    if (!track.playing) return
    const before = track.time
    track.time += dt * speed
    if (speed > 0) {
      for (const marker of MARKERS) {
        if (before < marker && track.time >= marker) say("keyframeReached  step", "in")
      }
    }
    if (track.time >= LENGTH) {
      if (ending === "loop") {
        track.time -= LENGTH
        say("didLoop")
        for (const marker of MARKERS) {
          if (track.time >= marker) say("keyframeReached  step", "in")
        }
      } else if (ending === "once") {
        track.time = LENGTH
        say("ended")
        stop("the server set playing to false")
      } else {
        track.time = LENGTH
        if (!track.ended) {
          track.ended = true
          say("ended, and it stays playing on the last frame")
        }
      }
    } else if (track.time <= 0 && speed < 0) {
      if (ending === "loop") {
        track.time += LENGTH
        say("didLoop")
      } else {
        track.time = 0
      }
    }
  }

  function draw() {
    const node = canvas
    if (!node) return
    const f = fit(node, HEIGHT)
    if (!f) return
    const c = palette()
    const ctx = f.ctx
    const left = 16
    const right = f.w - 16
    const x = (t: number) => left + (right - left) * (t / LENGTH)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, f.w, HEIGHT)
    ctx.fillStyle = c.bg3
    ctx.fillRect(left, 34, right - left, 22)
    ctx.fillStyle = c.bg4
    ctx.fillRect(left, 34, x(track.time) - left, 22)
    ctx.font = `11px ${c.mono}`
    ctx.textAlign = "center"
    ctx.textBaseline = "middle"
    for (const marker of MARKERS) {
      ctx.fillStyle = c.green
      ctx.save()
      ctx.translate(x(marker), 26)
      ctx.rotate(Math.PI / 4)
      ctx.fillRect(-4, -4, 8, 8)
      ctx.restore()
      ctx.fillText("step", x(marker), 12)
    }
    ctx.fillStyle = c.light
    ctx.fillText("0", left + 6, 70)
    ctx.fillText(`${LENGTH} s`, right - 14, 70)
    ctx.strokeStyle = track.playing ? c.main : c.light
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(x(track.time), 28)
    ctx.lineTo(x(track.time), 62)
    ctx.stroke()
  }

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
      step(dt)
      draw()
    },
  )
</script>

<Demo label="How a track ends">
  <canvas
    bind:this={canvas}
    class="an-canvas"
    style="height: {HEIGHT}px"
    aria-label="a track's playhead running between its markers"
  ></canvas>

  <div class="an-readout">{readout}</div>

  <div class="demo-controls">
    <Choice label="loop" options={["loop", "once", "hold"] as const} bind:value={ending} />
    <Slider label="speed" min={-2} max={2} step={0.25} bind:value={speed} />
    <div class="demo-choice">
      <span class="demo-slider-name"></span>
      <div class="demo-choice-buttons">
        <button type="button" class="demo-pill demo-pill-wide" onclick={play}>track:play()</button>
        <button type="button" class="demo-pill demo-pill-wide" onclick={() => stop()}>track:stop()</button>
      </div>
    </div>
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
