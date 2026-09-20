<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  const NAMES = ["floor", "statue"]
  const SOURCE =
    "local joins = 0\n\n" +
    "game.players.joined:connect(function(player)\n" +
    "    joins += 1\n" +
    "    game.persist.joins = (game.persist.joins or 0) + 1\n" +
    "end)"

  let joins = $state(0)
  let persist = $state(0)
  let generation = $state(1)
  let ids = $state<Record<string, number>>({ floor: 12, statue: 13 })
  let flashes = $state(0)
  let lines = $state<Line[]>([{ id: 0, text: "add a few joins, then save", kind: "idle" }])

  let next = 14
  let nextLine = 1

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 4)
  }

  function join() {
    joins += 1
    persist += 1
    record("joined: joins is " + joins + ", game.persist.joins is " + persist, "in")
  }

  function save() {
    generation += 1
    joins = 0
    ids = { floor: next++, statue: next++ }
    flashes += 1
    record(
      "reload: every instance destroyed and made again, every variable cleared; game.persist handed back",
      "out",
    )
  }
</script>

<Demo label="What a reload keeps">
  <div class="gs-reload">
    {#key flashes}
      <div class="gs-panel">
        <div class="gs-panel-title">game.world, run {generation}</div>
        {#each NAMES as name (name)}
          <div class="gs-row" class:gs-flash={flashes > 0}>
            <img class="engine-icon" src="/art/icons/MeshInstance3D.png" alt="" width="16" height="16" />
            <span>{name}</span>
            <span class="gs-id">instance #{ids[name]}</span>
          </div>
        {/each}
      </div>
      <div class="gs-panel">
        <div class="gs-panel-title">values</div>
        <div class="gs-row" class:gs-flash={flashes > 0}>
          <span class="gs-var">joins</span>
          <span class="gs-id">{joins}</span>
        </div>
        <div class="gs-row">
          <span class="gs-var">game.persist.joins</span>
          <span class="gs-id">{persist || "nil"}</span>
        </div>
      </div>
    {/key}
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={join}>a player joins</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={save}>save a file</button>
  </div>

  <Log {lines} />
  <CodePanel source={SOURCE} />
</Demo>
