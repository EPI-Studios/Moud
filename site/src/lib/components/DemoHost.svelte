<script lang="ts">
  import type { Component } from "svelte"
  import { setContext } from "svelte"
  import { loadDemo } from "$lib/demos"
  import { DEMO_NAME } from "$lib/demos/context"

  let { name }: { name: string } = $props()

  let view = $state<Component | null>(null)

  setContext(DEMO_NAME, () => name)

  $effect(() => {
    let live = true
    loadDemo(name).then((found) => {
      if (live) view = found
    })
    return () => {
      live = false
    }
  })
</script>

{#if view}
  {@const View = view}
  <View />
{/if}
