<script lang="ts">
  import { highlightLuau } from "$lib/render"

  let { source, class: extra = "" }: { source: string; class?: string } = $props()
</script>

<pre class="demo-code {extra}">{@html highlightLuau(source)}</pre>
