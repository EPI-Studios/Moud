<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Column = { name: string; auto: boolean | null; players: string[] }

  const NAMES = ["Meek", "Alex", "Sam", "Kai", "Noor", "Lin", "Ada", "Iris", "Tom", "Zoe", "Eli", "Mia"]

  let teams = $state<Column[]>([
    { name: "red", auto: true, players: [] },
    { name: "blue", auto: true, players: [] },
    { name: "spectators", auto: false, players: [] },
  ])
  let none = $state<string[]>([])
  let lines = $state<Line[]>([{ id: 0, text: "players join one at a time" }])
  let joined = 0
  let next = 1

  const rand = seeded(5)

  const columns = $derived<Column[]>([...teams, { name: "no team", auto: null, players: none }])
  const source = $derived(
    `-- the StringValue the recount writes\ncount.value = "red ${teams[0].players.length}  blue ${teams[1].players.length}"`,
  )

  function seeded(start: number) {
    let seed = start
    return () => {
      seed = (seed * 16807) % 2147483647
      return seed / 2147483647
    }
  }

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function join() {
    const name = NAMES[joined % NAMES.length] + (joined >= NAMES.length ? String(Math.floor(joined / NAMES.length) + 1) : "")
    joined += 1
    const open = teams.filter((team) => team.auto)
    if (!open.length) {
      none.push(name)
      record(`${name} joined: no autoAssignable team, so no team`)
      return
    }
    const fewest = Math.min(...open.map((team) => team.players.length))
    const tied = open.filter((team) => team.players.length === fewest)
    const team = tied[Math.floor(rand() * tied.length)]
    team.players.push(name)
    record(
      `${name} joined ${team.name}${
        tied.length > 1 ? `, picked at random from ${tied.length} tied teams` : ", the one with fewest players"
      }; playerAdded fired`,
      "in",
    )
  }

  function leave() {
    const all: { player: string; team: Column | null }[] = []
    for (const team of teams) for (const player of team.players) all.push({ player, team })
    for (const player of none) all.push({ player, team: null })
    if (!all.length) return
    const order = (entry: { player: string }) =>
      NAMES.indexOf(entry.player.replace(/\d+$/, "")) + 100 * (parseInt(entry.player.replace(/^\D+/, ""), 10) || 0)
    all.sort((a, b) => order(a) - order(b))
    const gone = all[0]
    if (gone.team) {
      gone.team.players.splice(gone.team.players.indexOf(gone.player), 1)
      record(`${gone.player} left ${gone.team.name}; playerRemoved fired`, "out")
    } else {
      none.splice(none.indexOf(gone.player), 1)
      record(`${gone.player} left; they were on no team`, "out")
    }
  }
</script>

<Demo label="Auto assigning teams">
  <div class="players-teams">
    {#each columns as column (column.name)}
      <div class="players-team players-team-{column.name.replace(' ', '-')}">
        <div class="players-team-head">
          <span class="players-team-name">{column.name}</span>
          {#if column.auto !== null}
            <span class="players-team-auto">{column.auto ? "autoAssignable" : "not autoAssignable"}</span>
          {/if}
        </div>
        <div class="players-chips">
          {#each column.players as player (player)}
            <span class="players-chip">{player}</span>
          {/each}
        </div>
      </div>
    {/each}
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={join}>a player joins</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={leave}>the first player leaves</button>
  </div>

  <div class="demo-controls demo-row">
    <span class="demo-slider-name players-inline">autoAssignable</span>
    {#each teams as team (team.name)}
      <button
        type="button"
        class="demo-pill"
        aria-pressed={team.auto}
        onclick={() => {
          team.auto = !team.auto
          record(`${team.name}.autoAssignable = ${team.auto}`)
        }}
      >
        {team.name}
      </button>
    {/each}
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
