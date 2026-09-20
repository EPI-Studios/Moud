<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Player = { name: string; kills: number; deaths: number; stats: boolean }

  let players = $state<Player[]>([
    { name: "Meek", kills: 3, deaths: 1, stats: true },
    { name: "Alex", kills: 5, deaths: 4, stats: true },
    { name: "Sam", kills: 1, deaths: 0, stats: true },
    { name: "Kai", kills: 0, deaths: 0, stats: false },
  ])
  let first = $state<"Kills" | "Deaths">("Kills")
  let ratio = $state<"off" | "on">("off")
  let pick = $state("Meek")

  const order = $derived<("Kills" | "Deaths")[]>(first === "Kills" ? ["Kills", "Deaths"] : ["Deaths", "Kills"])

  const ranked = $derived(
    [...players].sort((a, b) => {
      if (a.stats !== b.stats) return a.stats ? -1 : 1
      if (!a.stats) return 0
      return values(b)[0].value - values(a)[0].value
    }),
  )

  const source = $derived(
    `game.players.joined:connect(function(player)\n    local stats = player.leaderstats :: Leaderstats\n${order
      .map((key) => `    stats:add("NumberValue", { name = "${key}" })`)
      .join("\n")}${ratio === "on" ? '\n    stats:add("NumberValue", { name = "Ratio" })' : ""}\nend)`,
  )

  function show(value: number) {
    return value === Math.floor(value) ? String(value) : value.toFixed(2)
  }

  function values(player: Player) {
    if (!player.stats) return []
    const list: { key: string; value: number }[] = order.map((key) => ({
      key,
      value: key === "Kills" ? player.kills : player.deaths,
    }))
    if (ratio === "on") list.push({ key: "Ratio", value: player.deaths ? player.kills / player.deaths : player.kills })
    return list
  }

  function current() {
    return players.filter((player) => player.name === pick)[0]
  }
</script>

<Demo label="The Tab list">
  <div class="players-tab">
    <div class="players-tab-row players-tab-head">
      <span>ranked by {first}, highest first</span>
    </div>
    {#each ranked as player (player.name)}
      <div class="players-tab-row">
        <span class="players-tab-name">{player.name}</span>
        <span class="players-tab-values">
          {#each values(player) as entry, index (entry.key)}
            <span class={index === 0 ? "players-tab-rank" : ""}>{entry.key} {show(entry.value)}</span>
          {:else}
            <span class="players-tab-empty">no NumberValue yet</span>
          {/each}
        </span>
      </div>
    {/each}
  </div>

  <div class="demo-controls">
    <Choice label="added first" options={["Kills", "Deaths"] as const} bind:value={first} />
    <Choice label="a Ratio value" options={["off", "on"] as const} bind:value={ratio} />
    <Choice label="player" options={players.map((player) => player.name)} bind:value={pick} />
  </div>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        const player = current()
        player.stats = true
        player.kills += 1
      }}
    >
      kills.value += 1
    </button>
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        const player = current()
        player.stats = true
        player.deaths += 1
      }}
    >
      deaths.value += 1
    </button>
  </div>

  <CodePanel {source} />
</Demo>
