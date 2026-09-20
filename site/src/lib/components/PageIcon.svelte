<script lang="ts">
  import Icon from "./Icon.svelte"

  let { icon, size = "16px" }: { icon: string; size?: string } = $props()

  const kind = $derived(icon.slice(0, 1))
  const name = $derived(icon.slice(2))
</script>

{#if kind === "e"}
  <img class="engine-icon" src="/art/icons/{name}.png" alt="" width="16" height="16" />
{:else}
  <Icon name={name} {size} />
{/if}
