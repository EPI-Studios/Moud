<script lang="ts">
  import type { Snippet } from "svelte"

  let { children, class: extra = "" }: { children: Snippet; class?: string } = $props()
</script>

<p class="demo-note {extra}">{@render children()}</p>
