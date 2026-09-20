<script lang="ts">
  import Demo from "../ui/Demo.svelte"

  const BLOCKS = [
    "minecraft:oak_stairs[facing=north,half=bottom]",
    "minecraft:oak_stairs[facing=north,half=bottom]",
    "minecraft:oak_stairs[facing=east,half=bottom]",
    "minecraft:oak_stairs[facing=north,half=top]",
    "minecraft:stone",
    "minecraft:oak_planks",
    "minecraft:stone",
  ]

  const PICKS = [
    "minecraft:oak_stairs",
    "minecraft:oak_stairs[facing=north,half=bottom]",
    "minecraft:stone",
  ]

  let wanted = $state("minecraft:oak_stairs")

  function hits(name: string) {
    const query = wanted.trim()
    return query !== "" && (query === name.split("[")[0] || query === name)
  }

  const found = $derived(BLOCKS.filter(hits).length)
</script>

<Demo label="Matching a name">
  <div class="demo-field">
    <span class="demo-field-prefix">b:count("</span>
    <input
      type="text"
      class="demo-input"
      spellcheck="false"
      aria-label="block name"
      bind:value={wanted}
    />
    <span class="demo-field-prefix">", from, to)</span>
  </div>

  <ul class="demo-tree blk-list">
    {#each BLOCKS as name, index (index)}
      <li class="demo-tree-row" class:hit={hits(name)}>{name}</li>
    {/each}
  </ul>

  <p class="demo-note">count returns {found}.</p>

  <div class="demo-controls demo-row blk-picks">
    {#each PICKS as pick (pick)}
      <button type="button" class="demo-pill" onclick={() => (wanted = pick)}>{pick}</button>
    {/each}
  </div>
</Demo>
