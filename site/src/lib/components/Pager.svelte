<script lang="ts">
  let { at, pages, base }: { at: number; pages: number; base: string } = $props()

  const numbers = $derived.by(() => {
    const out: number[] = []
    const from = Math.max(1, at - 2)
    const to = Math.min(pages, from + 4)
    for (let i = from; i <= to; i++) out.push(i)
    return out
  })
</script>

{#if pages > 1}
  <nav class="pages" aria-label="Pages">
    {#if at > 1}<a class="page-link" href="{base}&page={at - 1}">Previous</a>{/if}
    {#each numbers as number (number)}
      <a class="page-link" class:active={number === at} href="{base}&page={number}">{number}</a>
    {/each}
    {#if at < pages}<a class="page-link" href="{base}&page={at + 1}">Next</a>{/if}
  </nav>
{/if}
