<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { onDestroy, untrack } from "svelte"

  type Mode = (typeof MODES)[number]
  type Step = [string, string, number]

  const MODES = ["get, then set", "update"] as const

  const PLANS: Record<Mode, Step[]> = {
    "get, then set": [
      ["players:get(id)  -- coins 10", "", 10],
      ["", "players:get(id)  -- coins 10", 10],
      ["data.coins += 5\nplayers:set(id, data)", "", 15],
      ["", "data.coins += 5\nplayers:set(id, data)", 15],
    ],
    update: [
      ["players:update(id, fn)\n-- reads 10, writes 15", "", 15],
      ["", "players:update(id, fn)\n-- reads 15, writes 20", 20],
    ],
  }

  let mode = $state<Mode>("get, then set")
  let shown = $state(PLANS["get, then set"].length)

  let timers: ReturnType<typeof setTimeout>[] = []

  const plan = $derived(PLANS[mode])

  const source = $derived(
    mode === "update"
      ? "local new = players:update(id, function(old)\n" +
          "    old = old or { coins = 0 }\n" +
          "    old.coins += 5\n" +
          "    return old\n" +
          "end)"
      : "local data = players:get(id) or { coins = 0 }\n" + "data.coins += 5\n" + "players:set(id, data)",
  )

  const note = $derived.by(() => {
    if (shown < plan.length) return "Both scripts add 5 coins to the same save at the same moment."
    return plan[plan.length - 1][2] === 20
      ? "20. update did each read and write together, so B read the 15 that A wrote and both changes count."
      : "15, not 20. B read the old 10 before A wrote, then wrote its own 15 over A's. One change is lost."
  })

  function stop() {
    for (const timer of timers) clearTimeout(timer)
    timers = []
  }

  function run() {
    stop()
    shown = 0
    for (let n = 1; n <= plan.length; n++) {
      timers.push(setTimeout(() => (shown = n), n * 650))
    }
  }

  let noted = untrack(() => mode)

  $effect(() => {
    if (mode === noted) return
    noted = mode
    stop()
    shown = 0
  })

  onDestroy(stop)
</script>

<Demo label="Two changes at once">
  <div class="demo-controls sv-top">
    <Choice label="each script does" options={MODES} bind:value={mode} />
    <div class="demo-choice">
      <span class="demo-slider-name"></span>
      <div class="demo-choice-buttons">
        <button type="button" class="demo-pill demo-pill-wide" onclick={run}>run both at once</button>
      </div>
    </div>
  </div>

  <table class="demo-matrix sv-steps">
    <tbody>
      <tr>
        <th></th>
        <th>script A adds 5</th>
        <th>script B adds 5</th>
        <th>stored coins</th>
      </tr>
      <tr>
        <th>before</th>
        <td></td>
        <td></td>
        <td class="sv-num">10</td>
      </tr>
      {#each plan as step, i (i)}
        <tr class:sv-hidden={i >= shown}>
          <th>step {i + 1}</th>
          <td class="sv-cell">
            {#each step[0].split("\n") as line, n (n)}{#if n}<br />{/if}{line}{/each}
          </td>
          <td class="sv-cell">
            {#each step[1].split("\n") as line, n (n)}{#if n}<br />{/if}{line}{/each}
          </td>
          <td class="sv-num">{step[2]}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
