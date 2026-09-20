<script lang="ts">
  export type Line = { id: number; text: string; kind?: "in" | "out" | "idle" }

  let {
    lines,
    keep = 4,
    class: extra = "",
  }: { lines: Line[]; keep?: number; class?: string } = $props()
</script>

<ul class="demo-log {extra}">
  {#each lines.slice(0, keep) as line (line.id)}
    <li class="demo-log-row {line.kind ?? ''}">{line.text}</li>
  {/each}
</ul>
