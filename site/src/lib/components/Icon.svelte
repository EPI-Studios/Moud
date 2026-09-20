<script lang="ts">
  import { iconSvg } from "$lib/icons"

  let { name, size = "1em" }: { name: string; size?: string } = $props()
</script>

{@html iconSvg(name, size)}
