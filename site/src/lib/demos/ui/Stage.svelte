<script lang="ts">
  import type { Snippet } from "svelte"

  let {
    children,
    wide = false,
    class: extra = "",
  }: { children: Snippet; wide?: boolean; class?: string } = $props()
</script>

<div class="demo-stage {extra}" class:demo-stage-wide={wide}>
  {@render children()}
</div>
