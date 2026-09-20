<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  const GROUPS = ["players", "walls", "ghosts"] as const
  type Group = (typeof GROUPS)[number]

  let ignores = $state<Record<Group, Group[]>>({
    players: ["players"],
    walls: [],
    ghosts: ["walls", "players"],
  })

  function passes(a: Group, b: Group) {
    return ignores[a].includes(b) || ignores[b].includes(a)
  }

  function flip(group: Group, other: Group) {
    const at = ignores[group].indexOf(other)
    if (at === -1) ignores[group].push(other)
    else ignores[group].splice(at, 1)
  }

  const source = $derived(
    GROUPS.map(
      (g) => `world:add("CollisionGroup", { name = "${g}", ignores = "${ignores[g].join(", ")}" })`,
    ).join("\n") + '\n\nwall.collisionGroup = "walls"\nbody.collisionGroup = "ghosts"',
  )
</script>

<Demo label="Collision groups">
  <table class="demo-matrix">
    <tbody>
      <tr>
        <th></th>
        {#each GROUPS as group (group)}
          <th>{group}</th>
        {/each}
      </tr>
      {#each GROUPS as a (a)}
        <tr>
          <th>{a}</th>
          {#each GROUPS as b (b)}
            {#if passes(a, b)}
              <td class="through">passes through</td>
            {:else}
              <td class="solid">collides</td>
            {/if}
          {/each}
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="demo-controls">
    {#each GROUPS as group (group)}
      <div class="demo-choice">
        <span class="demo-slider-name">{group} ignores</span>
        <div class="demo-choice-buttons">
          {#each GROUPS as other (other)}
            <button
              type="button"
              class="demo-pill"
              aria-pressed={ignores[group].includes(other)}
              onclick={() => flip(group, other)}
            >
              {other}
            </button>
          {/each}
        </div>
      </div>
    {/each}
  </div>

  <CodePanel {source} />
</Demo>
