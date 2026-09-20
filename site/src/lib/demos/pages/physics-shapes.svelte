<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Point = [number, number]
  type Outline = { circle: number; points?: undefined } | { circle?: undefined; points: Point[] }
  type Panel = { cx: number; hu: number; hy: number; outline: Outline; left: string; right: string }

  const SHAPES = ["block", "ball", "cylinder", "wedge", "cornerWedge"] as const
  type Shape = (typeof SHAPES)[number]

  const W = 440
  const H = 230
  const CY = 112
  const TEXT = "fill:var(--text-muted);font-family:var(--font-mono)"

  let shape = $state<Shape>("wedge")
  let sizeX = $state(2)
  let sizeY = $state(1.5)
  let sizeZ = $state(3)

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function rect(hu: number, hy: number): Outline {
    return {
      points: [
        [-hu, -hy],
        [hu, -hy],
        [hu, hy],
        [-hu, hy],
      ],
    }
  }

  const unit = $derived(Math.min(40, 150 / Math.max(sizeX, sizeY, sizeZ)))
  const smallest = $derived(Math.min(sizeX, sizeY, sizeZ))
  const round = $derived(Math.min(sizeY, sizeZ))

  const panels = $derived.by<Panel[]>(() => {
    const hy = sizeY / 2
    const acrossX = sizeX / 2
    const acrossZ = sizeZ / 2

    let x: Outline
    if (shape === "ball") x = { circle: smallest / 2 }
    else if (shape === "cylinder") x = rect(acrossX, round / 2)
    else if (shape === "cornerWedge")
      x = {
        points: [
          [-acrossX, -hy],
          [acrossX, -hy],
          [acrossX, hy],
        ],
      }
    else x = rect(acrossX, hy)

    let z: Outline
    if (shape === "ball") z = { circle: smallest / 2 }
    else if (shape === "cylinder") z = { circle: round / 2 }
    else if (shape === "wedge")
      z = {
        points: [
          [-acrossZ, -hy],
          [acrossZ, -hy],
          [acrossZ, hy],
        ],
      }
    else if (shape === "cornerWedge")
      z = {
        points: [
          [-acrossZ, -hy],
          [acrossZ, -hy],
          [-acrossZ, hy],
        ],
      }
    else z = rect(acrossZ, hy)

    return [
      { cx: 110, hu: acrossX, hy, outline: x, left: "-X", right: "+X" },
      { cx: 330, hu: acrossZ, hy, outline: z, left: "front -Z", right: "back +Z" },
    ]
  })

  const note = $derived.by(() => {
    if (shape === "block") return "The whole box, as size gives it."
    if (shape === "ball")
      return (
        `A ball ${num(smallest)} m across, as wide as the smallest side.` +
        (sizeX === sizeY && sizeY === sizeZ
          ? " It fills the part."
          : " The rest of the dashed box is empty.")
      )
    if (shape === "cylinder")
      return (
        `Lies along X, ${num(round)} m round, the smaller of Y and Z.` +
        (sizeY === sizeZ ? "" : " The rest of the dashed box is empty.")
      )
    if (shape === "wedge")
      return "Tall at the back, +Z, sloping down to the bottom front edge, so a body walks up it from in front."
    return "The high corner is at +X, +Y, -Z, and the slope falls away from it."
  })

  const source = $derived(
    `world:add("Part", {
    shape = "${shape}",
    size = vec3(${num(sizeX)}, ${num(sizeY)}, ${num(sizeZ)}),
    anchored = true,
})`,
  )

  function outlinePoints(points: Point[], cx: number) {
    return points
      .map((p) => `${(cx + p[0] * unit).toFixed(1)},${(CY - p[1] * unit).toFixed(1)}`)
      .join(" ")
  }
</script>

<Demo label="Shapes">
  <Stage>
    <svg
      viewBox="0 0 {W} {H}"
      class="phys-view"
      role="img"
      aria-label="a part drawn across X and across Z"
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      <text x="110" y="22" font-size="10.5" text-anchor="middle" letter-spacing="1.4" style={TEXT}>
        ACROSS X
      </text>
      <text x="330" y="22" font-size="10.5" text-anchor="middle" letter-spacing="1.4" style={TEXT}>
        ACROSS Z
      </text>
      {#each panels as panel (panel.cx)}
        <rect
          x={panel.cx - panel.hu * unit}
          y={CY - panel.hy * unit}
          width={panel.hu * 2 * unit}
          height={panel.hy * 2 * unit}
          fill="none"
          stroke-dasharray="4 4"
          style="stroke:var(--text-light)"
        />
        {#if panel.outline.circle !== undefined}
          <circle
            cx={panel.cx}
            cy={CY}
            r={panel.outline.circle * unit}
            stroke-width="1.5"
            style="fill:var(--bg-4);stroke:var(--text-main)"
          />
        {:else}
          <polygon
            points={outlinePoints(panel.outline.points, panel.cx)}
            stroke-width="1.5"
            style="fill:var(--bg-4);stroke:var(--text-main)"
          />
        {/if}
        <text
          x={panel.cx - panel.hu * unit}
          y={CY + panel.hy * unit + 16}
          font-size="10.5"
          text-anchor="start"
          style={TEXT}>{panel.left}</text
        >
        <text
          x={panel.cx + panel.hu * unit}
          y={CY + panel.hy * unit + 16}
          font-size="10.5"
          text-anchor="end"
          style={TEXT}>{panel.right}</text
        >
        <text
          x={panel.cx - panel.hu * unit - 8}
          y={CY + 4}
          font-size="10.5"
          text-anchor="end"
          style={TEXT}>Y</text
        >
      {/each}
    </svg>
  </Stage>

  <div class="demo-controls">
    <Choice label="shape" options={SHAPES} bind:value={shape} />
    <Slider label="size x" min={0.5} max={4} step={0.5} bind:value={sizeX} />
    <Slider label="size y" min={0.5} max={4} step={0.5} bind:value={sizeY} />
    <Slider label="size z" min={0.5} max={4} step={0.5} bind:value={sizeZ} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
