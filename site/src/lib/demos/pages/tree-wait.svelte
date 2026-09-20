<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"

  const SPAN = 8
  const TICKS = Array.from({ length: SPAN + 1 }, (_, second) => second)

  let first = $state(2)
  let second = $state(6.5)
  let timeout = $state(5)

  const times = $derived([first, second].sort((a, b) => a - b))
  const earliest = $derived(times[0])
  const found = $derived(earliest <= timeout)
  const waitAt = $derived(found ? earliest : timeout)

  function x(t: number) {
    return 96 + (t / SPAN) * 300
  }

  const note = $derived(
    found
      ? `${
          earliest === 0
            ? "A door is already there, so waitForChild hands it back at once."
            : `A door lands at ${earliest}s, inside the ${timeout} seconds, so waitForChild hands it back then.`
        } It returns once. onChild runs for every door, the one there now and the later one.`
      : `No door arrives within ${timeout} seconds, so waitForChild gives up and returns nil. ` +
          "onChild never gives up: it still runs for each door when it turns up.",
  )

  const source = $derived(
    `task.spawn(function()
    local door = world:waitForChild("door", ${timeout})   -- ${
      found ? `the door, after ${earliest}s` : `nil, after ${timeout}s`
    }
end)
world:onChild("door", function(door) end)   -- runs at ${times[0]}s and at ${times[1]}s`,
  )
</script>

<Demo label="Waiting and watching">
  <div class="tree-wait-stage">
    <svg class="tree-wait-svg" viewBox="0 0 410 150" role="img" aria-label="timeline">
      <line x1="96" y1="128" x2="396" y2="128" style="stroke: var(--line-2)" />
      {#each TICKS as tick (tick)}
        <line x1={x(tick)} y1="124" x2={x(tick)} y2="132" style="stroke: var(--line-2)" />
        <text x={x(tick)} y="146" text-anchor="middle" font-size="9" style="fill: var(--text-light)"
          >{tick}s</text
        >
      {/each}
      <text x="0" y="31" font-size="10.5" style="fill: var(--text-muted)">door added</text>
      <text x="0" y="68" font-size="10.5" style="fill: var(--text-muted)">waitForChild</text>
      <text x="0" y="106" font-size="10.5" style="fill: var(--text-muted)">onChild runs</text>
      {#each times as time, index (index)}
        <rect x={x(time) - 5} y="20" width="10" height="14" rx="2" style="fill: var(--text-main)" />
        <circle cx={x(time)} cy="102" r="6" style="fill: var(--green)" />
      {/each}
      <line
        x1={x(0)}
        y1="64"
        x2={x(waitAt)}
        y2="64"
        stroke-width="4"
        stroke-linecap="round"
        style="stroke: var(--bg-4)"
      />
      <line
        x1={x(timeout)}
        y1="52"
        x2={x(timeout)}
        y2="76"
        stroke-dasharray="3 3"
        style="stroke: var(--text-light)"
      />
      <circle cx={x(waitAt)} cy="64" r="6" style="fill: {found ? 'var(--green)' : 'var(--yellow)'}" />
      <text
        x={x(waitAt)}
        y="50"
        text-anchor="middle"
        font-size="9.5"
        style="fill: {found ? 'var(--green)' : 'var(--yellow)'}">{found ? "door" : "nil"}</text
      >
    </svg>
  </div>

  <div class="demo-controls">
    <Slider label="a door at" min={0} max={8} step={0.5} bind:value={first} />
    <Slider label="another at" min={0} max={8} step={0.5} bind:value={second} />
    <Slider label="timeout" min={1} max={8} step={0.5} bind:value={timeout} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
