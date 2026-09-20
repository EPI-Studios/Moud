<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Row = { where: string; calls: number; ms: number; worst: number }

  const SOURCE =
    "task.every(5, function()\n" +
    "    for where, t in game.debug:profile() do\n" +
    "        print(where, t.milliseconds, t.calls, t.worst)\n" +
    "    end\n" +
    "end)"

  let cheap = $state(0.06)
  let slow = $state(6)

  const main = $derived<Row>({ where: "main", calls: 100, ms: cheap * 100, worst: cheap * 1.6 })
  const spawner = $derived<Row>({ where: "spawner", calls: 1, ms: slow, worst: slow })

  const rows = $derived(
    [main, spawner, { where: "TextChannel.shouldDeliver", calls: 12, ms: 0.6, worst: 0.09 }].sort(
      (a, b) => b.ms - a.ms,
    ),
  )

  const note = $derived(
    "Slowest first. main costs " +
      main.ms.toFixed(1) +
      " ms over " +
      main.calls +
      " small calls, and spawner " +
      spawner.ms.toFixed(1) +
      " ms in one call. worst is what tells them apart: " +
      "the first is work to trim everywhere, the second is one call to look at.",
  )
</script>

<Demo label="Reading a profile">
  <table class="demo-matrix dbg-profile">
    <tbody>
      <tr>
        <th>where</th>
        <th>milliseconds</th>
        <th>calls</th>
        <th>worst</th>
      </tr>
      {#each rows as row (row.where)}
        <tr>
          <td>{row.where}</td>
          <td>{row.ms.toFixed(2)}</td>
          <td>{row.calls}</td>
          <td class={row.worst > 2 ? "dbg-heavy" : ""}>{row.worst.toFixed(2)}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="demo-controls">
    <Slider label="main, per call" min={0.01} max={0.2} step={0.01} bind:value={cheap} />
    <Slider label="spawner's one call" min={0.5} max={20} step={0.5} bind:value={slow} />
  </div>

  <Note>{note}</Note>
  <CodePanel source={SOURCE} />
</Demo>
