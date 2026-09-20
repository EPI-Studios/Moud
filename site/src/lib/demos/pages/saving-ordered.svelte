<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  const ASCENDING = ["false", "true"] as const

  let scores = $state<Record<string, number>>({ meek: 12, ana: 17, bo: 12, kit: 4, zed: 9 })
  let ascending = $state<(typeof ASCENDING)[number]>("false")
  let limit = $state(3)
  let last = $state('scores:increment("meek")')

  const names = Object.keys(scores)

  const rows = $derived.by(() => {
    const up = ascending === "true"
    return names
      .slice()
      .sort((a, b) => {
        if (scores[a] !== scores[b]) return up ? scores[a] - scores[b] : scores[b] - scores[a]
        return a < b ? -1 : a > b ? 1 : 0
      })
      .slice(0, limit)
  })

  const source = $derived(
    `${last}\n\n` +
      `for place, row in scores:getSorted(${ascending}, ${limit}) do\n` +
      "    print(place, row.key, row.value)\n" +
      "end",
  )

  const note = $derived.by(() => {
    const ties: Record<number, string[]> = {}
    for (const key of names) (ties[scores[key]] ??= []).push(key)
    const tied = Object.values(ties)
      .filter((group) => group.length > 1)
      .map((group) => group.slice().sort().join(" and "))
    return tied.length
      ? `${tied.join(", ")} are tied, so they come back in key order.`
      : "Press a name to add 1 to its score. The board is sorted by the store, not by your script."
  })

  function bump(key: string) {
    scores[key] += 1
    last = `scores:increment("${key}")   -- ${scores[key]}, and hands it back`
  }
</script>

<Demo label="A leaderboard">
  <div class="sv-players">
    {#each names as key (key)}
      <button type="button" class="demo-pill sv-player" onclick={() => bump(key)}>
        <span>{key} +1 </span>
        <span class="sv-score">{scores[key]}</span>
      </button>
    {/each}
  </div>

  <table class="demo-matrix sv-board">
    <tbody>
      <tr>
        <th>place</th>
        <th>row.key</th>
        <th>row.value</th>
      </tr>
      {#each rows as key, i (key)}
        <tr>
          <th>{i + 1}</th>
          <td>{key}</td>
          <td>{scores[key]}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="demo-controls">
    <Choice label="ascending" options={ASCENDING} bind:value={ascending} />
    <Slider label="limit" min={1} max={5} step={1} bind:value={limit} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
