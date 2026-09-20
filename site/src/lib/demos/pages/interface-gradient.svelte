<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Slider from "../ui/Slider.svelte"
  import Stage from "../ui/Stage.svelte"

  type Rgb = [number, number, number]

  const PAIRS: Record<string, [Rgb, Rgb]> = {
    violet: [
      [0.3, 0.2, 0.6],
      [0.05, 0.05, 0.15],
    ],
    sunset: [
      [1, 0.55, 0.2],
      [0.55, 0.1, 0.35],
    ],
    sea: [
      [0.2, 0.75, 0.8],
      [0.05, 0.2, 0.45],
    ],
  }

  const BACKS: Record<string, Rgb> = {
    white: [1, 1, 1],
    grey: [0.5, 0.5, 0.5],
    red: [1, 0.35, 0.35],
  }

  const W = 240
  const H = 144

  let pair = $state("violet")
  let back = $state("white")
  let rotation = $state(90)
  let offset = $state(0)
  let card = $state<HTMLCanvasElement | null>(null)

  const from = $derived(PAIRS[pair][0])
  const to = $derived(PAIRS[pair][1])
  const tint = $derived(BACKS[back])
  const ox = $derived(Math.round(offset * 100) / 100)

  const legend = $derived<[string, Rgb][]>([
    ["startColor", from],
    ["endColor", to],
    ["backgroundColor", tint],
  ])

  const note = $derived(
    back === "white"
      ? "The frame is white, so the gradient's colours come out as written."
      : `The gradient multiplies backgroundColor, so on a ${back} frame every colour is ` +
          (back === "grey" ? "half as bright." : "pulled toward red.") +
          " Keep the background white to see them as written.",
  )

  const source = $derived(
    `local card = hud:add("Frame", {\n    size = udim2(0, 200, 0, 120), backgroundColor = ${fmt(tint)},\n})\n` +
      'card:add("UIGradient", {\n' +
      `    startColor = ${fmt(from)}, endColor = ${fmt(to)},\n` +
      `    rotation = ${rotation}${ox ? `, offset = vec3(${ox}, 0, 0)` : ""},\n` +
      "})",
  )

  function fmt(colour: Rgb) {
    return `color(${colour.join(", ")})`
  }

  function css(colour: Rgb) {
    return `rgb(${colour.map((value) => Math.round(value * 255)).join(",")})`
  }

  $effect(() => {
    const ctx = card?.getContext("2d")
    if (!ctx) return
    const image = ctx.createImageData(W, H)
    const angle = (rotation * Math.PI) / 180
    const dx = Math.cos(angle)
    const dy = Math.sin(angle)
    const reach = (Math.abs(dx) + Math.abs(dy)) / 2
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const u = (x + 0.5) / W - 0.5 - ox
        const v = (y + 0.5) / H - 0.5
        const t = Math.max(0, Math.min(1, (u * dx + v * dy) / (2 * reach) + 0.5))
        const at = (y * W + x) * 4
        for (let k = 0; k < 3; k++) {
          image.data[at + k] = Math.round((from[k] + (to[k] - from[k]) * t) * tint[k] * 255)
        }
        image.data[at + 3] = 255
      }
    }
    ctx.putImageData(image, 0, 0)
  })
</script>

<Demo label="UIGradient">
  <Stage>
    <canvas bind:this={card} class="ui-card" width={W} height={H}></canvas>
    <div class="ui-legend">
      {#each legend as [name, colour] (name)}
        <div class="ui-legend-row">
          <span class="ui-chip" style="background: {css(colour)}"></span>
          <span>{name}</span>
        </div>
      {/each}
    </div>
  </Stage>

  <div class="demo-controls">
    <Choice label="colours" options={Object.keys(PAIRS)} bind:value={pair} />
    <Choice label="backgroundColor" options={Object.keys(BACKS)} bind:value={back} />
    <Slider label="rotation" min={0} max={360} step={15} bind:value={rotation} />
    <Slider label="offset x" min={-0.5} max={0.5} step={0.05} bind:value={offset} format={() => String(ox)} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
