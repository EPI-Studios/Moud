<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Side = "server" | "a" | "b"

  const SIDES: { key: Side; name: string }[] = [
    { key: "server", name: "server" },
    { key: "a", name: "client A" },
    { key: "b", name: "client B" },
  ]
  const COLORS = ["#d65a56", "#608cce", "#7ebb66", "#e8730c"]

  let swatches = $state<Record<Side, string | null>>({ server: null, a: null, b: null })
  let note = $state("The server owns the tree. Each client keeps a mirror of it.")
  let source = $state("-- write the property from one side or the other")
  let turn = 0

  function fromServer() {
    const color = COLORS[turn++ % COLORS.length]
    swatches = { ...swatches, server: color }
    setTimeout(() => (swatches = { ...swatches, a: color, b: color }), 280)
    note =
      "The server wrote the property. The engine sent the change to every client and applied it there."
    source = "-- server/main.luau\nfloor.color = color(0.8, 0.3, 0.3)"
  }

  function fromClient() {
    swatches = { ...swatches, a: COLORS[turn++ % COLORS.length] }
    note =
      "Client A changed its own mirror. The server never hears about it and client B draws the old colour."
    source = "-- client/main.luau\nfloor.color = color(0.3, 0.5, 0.8)   -- only this client sees it"
  }
</script>

<Demo label="Replication">
  <div class="demo-stage demo-wires">
    <div class="wire-row">
      {#each SIDES as side (side.key)}
        <div class="wire-node">
          <span class="wire-name">{side.name}</span>
          <span class="wire-swatch" style:background={swatches[side.key]}></span>
        </div>
      {/each}
    </div>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={fromServer}>
      server writes floor.color
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={fromClient}>
      client A writes floor.color
    </button>
  </div>

  <p class="demo-note">{note}</p>

  <CodePanel {source} />
</Demo>
