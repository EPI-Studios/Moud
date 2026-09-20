<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { EASING_NAMES, ease, type Direction, type EasingName } from "../core/easing"

  const DIRECTIONS: Direction[] = ["in", "out", "inOut"]

  let track = $state<HTMLDivElement | null>(null)
  let easing = $state<EasingName>("quad")
  let direction = $state<Direction>("out")
  let time = $state(1)

  let dot = $state({ x: 10, y: 130 })
  let blockLeft = $state("calc(6px + 0.00% - 0.00px)")
  let started: number | null = null

  const curve = $derived.by(() => {
    const points: string[] = []
    for (let i = 0; i <= 60; i++) {
      const t = i / 60
      points.push(`${(10 + t * 200).toFixed(1)},${(130 - ease(easing, direction, t) * 120).toFixed(1)}`)
    }
    return `M${points.join(" L")}`
  })

  const source = $derived(
    "lantern:tween({ cframe = cframe(6, 64, 0) }, {\n" +
      `    time = ${time},\n` +
      `    easing = "${easing}",\n` +
      `    direction = "${direction}",\n` +
      "})",
  )

  $effect(() => {
    easing
    direction
    time
    started = null
  })

  whileVisible(
    () => track,
    (now) => {
      if (started === null) started = now
      const t = Math.min((((now - started) / 1000 / time) % 1.6), 1)
      const v = ease(easing, direction, t)
      dot = { x: 10 + t * 200, y: 130 - v * 120 }
      blockLeft = `calc(6px + ${(v * 100).toFixed(2)}% - ${(v * 44).toFixed(2)}px)`
    },
  )
</script>

<Demo label="Easing">
  <Stage>
    <svg class="demo-curve" viewBox="0 0 220 140" role="img" aria-label="easing curve">
      <rect x="10" y="10" width="200" height="120" fill="none" style="stroke: var(--line)" />
      <line x1="10" y1="130" x2="210" y2="130" style="stroke: var(--line)" />
      <path d={curve} fill="none" stroke-width="1.75" style="stroke: var(--text-main)" />
      <circle cx={dot.x} cy={dot.y} r="3.5" style="fill: var(--accent)" />
    </svg>
    <div class="demo-track" bind:this={track}>
      <div class="demo-block" style="left: {blockLeft}"></div>
    </div>
  </Stage>

  <div class="demo-controls">
    <Choice label="direction" options={DIRECTIONS} bind:value={direction} />
    <div class="demo-choice">
      <span class="demo-slider-name">easing</span>
      <select class="demo-select" bind:value={easing}>
        {#each EASING_NAMES as name (name)}
          <option value={name}>{name}</option>
        {/each}
      </select>
    </div>
    <Slider label="time" min={0.2} max={3} step={0.1} bind:value={time} />
  </div>

  <CodePanel {source} />
</Demo>
