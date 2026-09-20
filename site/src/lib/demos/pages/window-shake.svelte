<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const DESK_W = 1920
  const DESK_H = 1080
  const BASE = { x: 560, y: 300 }
  const SIZE = { w: 800, h: 450 }

  let strength = $state(24)
  let time = $state(0.6)
  let x = $state(BASE.x)
  let y = $state(BASE.y)
  let power = $state(0)
  let desk: HTMLDivElement | null = $state(null)

  let running = false
  let left = 0
  let last = 0

  const source = $derived(
    "shake(" + strength + ", " + time + ")\n\n" +
      "-- each frame, while left > 0:\n" +
      "local power = strength * (left / time)\n" +
      "window:moveTo(baseX + math.random(-power, power), baseY + math.random(-power, power))",
  )

  whileVisible(
    () => desk,
    (now) => {
      if (!running) {
        last = 0
        return
      }
      const dt = last ? (now - last) / 1000 : 0
      last = now
      left -= dt
      if (left <= 0) {
        x = BASE.x
        y = BASE.y
        power = 0
        running = false
        return
      }
      power = strength * (left / time)
      const reach = Math.floor(power)
      x = BASE.x + Math.floor(Math.random() * (2 * reach + 1)) - reach
      y = BASE.y + Math.floor(Math.random() * (2 * reach + 1)) - reach
    },
  )

  function shake() {
    if (running) return
    running = true
    left = time
    last = 0
  }
</script>

<Demo label="Shaking the window">
  <div class="window-desk" bind:this={desk}>
    <div
      class="window-win"
      style="left: {(x / DESK_W) * 100}%; top: {(y / DESK_H) * 100}%; width: {(SIZE.w / DESK_W) *
        100}%; height: {(SIZE.h / DESK_H) * 100}%"
    >
      <div class="window-bar">
        <span class="window-title">Crystal Rush</span>
        <button type="button" class="window-x" aria-label="close">x</button>
      </div>
      <div class="window-inside">
        <span class="window-hint">window.x, window.y</span>
      </div>
    </div>
  </div>

  <div class="window-power">
    <div class="window-power-fill" style="width: {(power / 60) * 100}%"></div>
    <span class="window-power-label">power</span>
  </div>

  <p class="demo-note window-readout">window.x = {x}, window.y = {y}</p>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={shake}>shake</button>
  </div>

  <div class="demo-controls">
    <Slider label="strength" min={4} max={60} bind:value={strength} />
    <Slider label="time" min={0.2} max={2} step={0.1} bind:value={time} />
  </div>

  <CodePanel {source} />
</Demo>
