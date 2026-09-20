<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Slider from "../ui/Slider.svelte"

  const PAD = 6
  const WIDTH = 236

  let cell = $state(40)
  let gap = $state(4)
  let slots = $state(40)
  let corner = $state<"topLeft" | "topRight" | "bottomLeft" | "bottomRight">("topLeft")
  let max = $state(0)
  let frame = $state<HTMLDivElement | null>(null)

  const fit = $derived(Math.max(1, Math.floor((WIDTH - PAD * 2 + gap) / (cell + gap))))
  const across = $derived(max > 0 ? Math.min(max, fit) : fit)
  const rows = $derived(Math.ceil(slots / across))
  const blockW = $derived(across * cell + (across - 1) * gap)
  const blockH = $derived(rows * cell + Math.max(0, rows - 1) * gap)
  const canvasH = $derived(Math.max(180, blockH + PAD * 2))
  const bottom = $derived(corner.startsWith("bottom"))

  const placed = $derived(
    Array.from({ length: slots }, (_, index) => {
      let row = Math.floor(index / across)
      let column = index % across
      if (corner === "topRight" || corner === "bottomRight") column = across - 1 - column
      if (corner === "bottomLeft" || corner === "bottomRight") row = rows - 1 - row
      return {
        index,
        x: PAD + column * (cell + gap),
        y: bottom ? canvasH - PAD - blockH + row * (cell + gap) : PAD + row * (cell + gap),
      }
    }),
  )

  const facts = $derived([
    ["across", `${across}${max > 0 && max < fit ? " (capped)" : ""}`],
    ["rows", String(rows)],
    ["block", `${blockW} x ${blockH}`],
    ["canvas", `236 x ${canvasH}`],
    ["scrolls", canvasH > 180 ? "yes" : "no"],
  ])

  const source = $derived.by(() => {
    const layout = `cellSize = udim2(0, ${cell}, 0, ${cell}), cellPadding = udim2(0, ${gap}, 0, ${gap})`
    const extra: string[] = []
    if (corner !== "topLeft") extra.push(`startCorner = "${corner}"`)
    if (max > 0) extra.push(`fillDirectionMaxCells = ${max}`)
    return (
      'local bag = hud:add("ScrollingFrame", {\n' +
      "    size = udim2(0, 236, 0, 180),\n" +
      '    canvasSize = udim2(1, 0, 0, 0), automaticCanvasSize = "y",\n' +
      "})\n" +
      `bag:add("UIGridLayout", {\n    ${layout}${extra.length ? `,\n    ${extra.join(", ")}` : ""},\n})\n` +
      'bag:add("UIPadding", {\n' +
      "    paddingTop = udim(0, 6), paddingBottom = udim(0, 6),\n" +
      "    paddingLeft = udim(0, 6), paddingRight = udim(0, 6),\n})\n" +
      `for slot = 1, ${slots} do\n` +
      '    bag:add("ImageButton", { name = "slot" .. slot, layoutOrder = slot })\n' +
      "end"
    )
  })

  $effect(() => {
    if (frame) frame.scrollTop = bottom ? canvasH : 0
  })
</script>

<Demo label="Grid layout">
  <div class="demo-stage ui-grid-stage">
    <div class="ui-scroll" bind:this={frame}>
      <div class="ui-canvas" style="height: {canvasH}px">
        {#each placed as slot (slot.index)}
          <div class="ui-slot" style="left: {slot.x}px; top: {slot.y}px; width: {cell}px; height: {cell}px">
            {slot.index + 1}
          </div>
        {/each}
      </div>
    </div>
    <ul class="ui-facts">
      {#each facts as [name, value] (name)}
        <li>
          <span>{name}</span>
          <b>{value}</b>
        </li>
      {/each}
    </ul>
  </div>

  <div class="demo-controls">
    <Slider label="slots" min={1} max={60} bind:value={slots} />
    <Slider label="cellSize" min={20} max={72} step={2} bind:value={cell} />
    <Slider label="cellPadding" min={0} max={12} bind:value={gap} />
    <Slider label="maxCells" min={0} max={8} bind:value={max} />
    <Choice
      label="startCorner"
      options={["topLeft", "topRight", "bottomLeft", "bottomRight"] as const}
      bind:value={corner}
    />
  </div>

  <CodePanel {source} />
</Demo>
