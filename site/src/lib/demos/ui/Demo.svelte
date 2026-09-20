<script lang="ts">
  import { getContext, type Snippet } from "svelte"
  import { DEMO_NAME } from "../context"

  let { label, name = "", children }: { label: string; name?: string; children: Snippet } = $props()

  const fromHost = getContext<(() => string) | undefined>(DEMO_NAME)
  const id = $derived(name || fromHost?.() || "")
</script>

<div class="demo" data-demo={id || undefined}>
  <span class="demo-label">{label}</span>
  <div class="demo-body">
    {@render children()}
  </div>
</div>
