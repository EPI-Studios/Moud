<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"

  type Block = "P" | "G" | "S" | "." | "air" | "grass" | "flower"

  const CELL = 26
  const COPY: Block[][] = [
    ["P", "P", "S", "P", "P"],
    ["P", ".", ".", ".", "P"],
    ["P", ".", ".", ".", "G"],
    ["P", "P", "P", "P", "P"],
  ]
  const SX = 5
  const SZ = 4
  const GROUND = 9
  const FLOWERS = ["3,3", "4,4", "5,3", "1,6", "6,6", "3,4", "7,2"]
  const ORIGIN = [2, 2]
  const FACING = ["north", "east", "south", "west"]

  let turns = $state(0)
  let skipAir = $state(true)

  const world = $derived.by(() => {
    const cells = new Map<string, { block: Block; facing: string }>()
    for (let x = 0; x < GROUND; x++) {
      for (let z = 0; z < GROUND; z++) {
        cells.set(x + "," + z, {
          block: FLOWERS.includes(x + "," + z) ? "flower" : "grass",
          facing: "north",
        })
      }
    }
    const turned = ((turns % 4) + 4) % 4
    COPY.forEach((row, zz) => {
      row.forEach((block, xx) => {
        const air = block === "."
        if (air && skipAir) return
        let rx: number
        let rz: number
        if (turned === 1) {
          rx = SZ - 1 - zz
          rz = xx
        } else if (turned === 2) {
          rx = SX - 1 - xx
          rz = SZ - 1 - zz
        } else if (turned === 3) {
          rx = zz
          rz = SX - 1 - xx
        } else {
          rx = xx
          rz = zz
        }
        cells.set(ORIGIN[0] + rx + "," + (ORIGIN[1] + rz), {
          block: air ? "air" : block,
          facing: FACING[turned],
        })
      })
    })
    const out: { x: number; z: number; block: Block; facing: string }[] = []
    for (let x = 0; x < GROUND; x++) {
      for (let z = 0; z < GROUND; z++) {
        const cell = cells.get(x + "," + z)
        out.push({ x, z, block: cell ? cell.block : "grass", facing: cell ? cell.facing : "north" })
      }
    }
    return out
  })

  const source = $derived(
    "local house = b:copy(vec3(0, 65, 0), vec3(4, 65, 3))\n" +
      "b:paste(house, vec3(" + (20 + ORIGIN[0]) + ", 65, " + ORIGIN[1] + "), " + turns + ", " + skipAir + ")\n" +
      "-- the stair faces " + FACING[((turns % 4) + 4) % 4] +
      (skipAir
        ? ", and the poppies inside are kept"
        : ", and the air in the copy cleared the poppies it landed on"),
  )
</script>

{#snippet cell(x: number, z: number, block: Block, facing: string)}
  {@const cx = x * CELL}
  {@const cy = z * CELL}
  {#if block === "P" || block === "G"}
    <rect
      x={cx + 1}
      y={cy + 1}
      width={CELL - 2}
      height={CELL - 2}
      style={block === "P"
        ? "fill:#8a6a3f;stroke:#5e4526"
        : "fill:rgba(160,210,230,0.18);stroke:#9fd0e2"}
    />
    <text
      x={cx + CELL / 2}
      y={cy + CELL / 2 + 4}
      font-size="10"
      text-anchor="middle"
      style="fill:#f0f0f0;font-family:var(--font-mono)">{block}</text
    >
  {:else if block === "S"}
    <rect x={cx + 1} y={cy + 1} width={CELL - 2} height={CELL - 2} style="fill:#8a6a3f;stroke:#5e4526" />
    <path
      d="M0,-8 L6,4 L0,1 L-6,4 z"
      transform="translate({cx + CELL / 2},{cy + CELL / 2}) rotate({FACING.indexOf(facing) * 90})"
      style="fill:#f0f0f0"
    />
  {:else if block === "air"}
    <rect x={cx + 1} y={cy + 1} width={CELL - 2} height={CELL - 2} style="fill:var(--bg);stroke:var(--line-2)" />
  {:else if block === "grass"}
    <rect x={cx + 1} y={cy + 1} width={CELL - 2} height={CELL - 2} style="fill:#34502c" />
  {:else if block === "flower"}
    <rect x={cx + 1} y={cy + 1} width={CELL - 2} height={CELL - 2} style="fill:#34502c" />
    <circle cx={cx + CELL / 2} cy={cy + CELL / 2} r="4" style="fill:var(--red)" />
  {:else}
    <rect x={cx + 1} y={cy + 1} width={CELL - 2} height={CELL - 2} style="fill:var(--bg-3)" />
  {/if}
{/snippet}

<Demo label="Paste, turned">
  <div class="demo-stage blk-paste">
    <div class="blk-panel">
      <span class="blk-small">the copy</span>
      <svg
        class="blk-mini"
        viewBox="0 0 {SX * CELL} {SZ * CELL}"
        role="img"
        aria-label="the copied blocks"
      >
        {#each COPY as row, z (z)}
          {#each row as block, x (x)}
            {@render cell(x, z, block, "north")}
          {/each}
        {/each}
      </svg>
    </div>
    <div class="blk-panel">
      <span class="blk-small">the ground after paste</span>
      <svg
        class="blk-mini blk-mini-big"
        viewBox="0 0 {GROUND * CELL} {GROUND * CELL}"
        role="img"
        aria-label="the ground after pasting"
      >
        {#each world as spot (spot.x + "," + spot.z)}
          {@render cell(spot.x, spot.z, spot.block, spot.facing)}
        {/each}
        <rect x={ORIGIN[0] * CELL} y={ORIGIN[1] * CELL} width="3" height="3" style="fill:var(--accent)" />
      </svg>
    </div>
  </div>

  <Note>
    Seen from above, north up. P is oak planks, G glass, the arrow a stair and the direction it
    faces, a dot a poppy on the grass. Empty cells in the copy are air.
  </Note>

  <div class="demo-controls">
    <Choice label="rotation" options={[0, 1, 2, 3]} bind:value={turns} />
    <Choice label="skipAir" options={[true, false]} bind:value={skipAir} />
  </div>

  <CodePanel {source} />
</Demo>
