<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Place = "local" | "persist" | "store"

  const SOURCE =
    'local state = store("state")\n' +
    "local rounds = 0\n\n" +
    "local function roundEnded()\n" +
    "    rounds += 1\n" +
    "    game.persist.rounds = (game.persist.rounds or 0) + 1\n" +
    '    state:update("rounds", function(old) return (old or 0) + 1 end)\n' +
    "end"

  const CARDS: [Place, string][] = [
    ["local", "rounds"],
    ["persist", "game.persist.rounds"],
    ["store", 'state:get("rounds")'],
  ]

  let values = $state<Record<Place, number | null>>({ local: 0, persist: 0, store: 0 })
  let lost = $state<Place[]>([])
  let note = $state("End a few rounds, then save a file or stop the server.")

  function roundEnds() {
    values.local = (values.local ?? 0) + 1
    values.persist = (values.persist ?? 0) + 1
    values.store = (values.store ?? 0) + 1
    note = "All three count the round."
    lost = []
  }

  function hotReload() {
    values.local = 0
    note =
      "The place ran again from the top, so rounds started over at 0. game.persist was handed back and the store never left."
    lost = ["local"]
  }

  function stopServer() {
    values.local = 0
    values.persist = null
    note =
      "game.persist does not survive the server stopping. Only the store, written to the world folder, is still there."
    lost = ["local", "persist"]
  }
</script>

<Demo label="What survives">
  <div class="sv-keep">
    {#each CARDS as [place, name] (place)}
      <div class="sv-cell-card">
        <span class="sv-name">{name}</span>
        <span class="sv-big" class:sv-lost={lost.includes(place)}>{values[place] ?? "nil"}</span>
      </div>
    {/each}
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={roundEnds}>a round ends</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={hotReload}>save a file (hot reload)</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={stopServer}>
      stop the server, play again
    </button>
  </div>

  <Note>{note}</Note>
  <CodePanel source={SOURCE} />
</Demo>
