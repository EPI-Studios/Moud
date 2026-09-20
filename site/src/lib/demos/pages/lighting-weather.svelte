<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const MIX: Record<string, number[]> = {
    clear: [0, 0, 0, 0, 0],
    rain: [1, 0, 0, 0.15, 1],
    snow: [0, 1, 0, 0.25, 0.8],
    storm: [1, 0, 1, 0.3, 1],
    fog: [0, 0, 0, 1, 0.3],
  }
  const INGREDIENTS = ["Rain", "Snow", "Storm", "Fog", "Overcast"]

  let kind = $state("rain")
  let intensity = $state(0.5)
  let committed = $state(0.5)
  let transition = $state(5)

  let current = $state(MIX.rain.map((v) => v * 0.5))
  let target = $state(MIX.rain.map((v) => v * 0.5))
  let elapsed = $state(0)
  let span = $state(0)
  let from = untrack(() => current.slice())

  let bars = $state<HTMLDivElement | null>(null)

  function smoothstep(t: number) {
    return t * t * (3 - 2 * t)
  }

  const settled = $derived(span <= 0 || elapsed >= span)

  const progress = $derived(
    settled
      ? "Settled. The marks are where the mix is heading; the bars are where it is."
      : `Easing: ${elapsed.toFixed(1)} of ${span} seconds, slow at both ends.`,
  )

  const source = $derived(
    `weather.transition = ${transition}\n` +
      `weather.kind = "${kind}"\n` +
      `weather.intensity = ${committed}`,
  )

  function easeTo(goal: number[]) {
    if (goal.every((v, i) => Math.abs(v - target[i]) < 1e-9)) return
    from = current.slice()
    target = goal
    elapsed = 0
    span = transition
    if (span <= 0) current = goal.slice()
  }

  $effect(() => {
    const goal = MIX[kind].map((v) => v * committed)
    untrack(() => easeTo(goal))
  })

  let last = 0

  whileVisible(
    () => bars,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      if (settled) return
      elapsed += dt
      const t = Math.min(1, elapsed / span)
      const e = smoothstep(t)
      current = target.map((v, i) => from[i] + (v - from[i]) * e)
    },
  )
</script>

<Demo label="Weather mix">
  <div class="lighting-bars" bind:this={bars}>
    {#each INGREDIENTS as name, i (name)}
      <div class="lighting-bar-row">
        <span class="lighting-bar-name">{name}</span>
        <div class="lighting-bar-track">
          <div class="lighting-bar-fill" style="width: {(current[i] * 100).toFixed(2)}%"></div>
          <div class="lighting-bar-mark" style="left: {(target[i] * 100).toFixed(2)}%"></div>
        </div>
        <span class="lighting-bar-value">{current[i].toFixed(2)}</span>
      </div>
    {/each}
  </div>

  <Note>{progress}</Note>

  <div class="demo-controls">
    <Choice label="kind" options={["clear", "rain", "snow", "storm", "fog"]} bind:value={kind} />
    <div onchange={() => (committed = intensity)}>
      <Slider label="intensity" min={0} max={1} step={0.05} bind:value={intensity} />
    </div>
    <Slider label="transition" min={0} max={20} step={1} bind:value={transition} />
    <Note>
      Change the kind while the bars are still moving: the new ease starts from where they had got to.
      Changing transition on its own restarts nothing.
    </Note>
  </div>

  <CodePanel {source} />
</Demo>
