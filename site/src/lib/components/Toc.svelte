<script lang="ts">
  import { onMount } from "svelte"
  import type { Heading } from "$lib/docs/render"

  let { headings }: { headings: Heading[] } = $props()

  let active = $state("")

  $effect(() => {
    if (!active) active = headings[0]?.id ?? ""
  })

  onMount(() => {
    const watcher = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) active = entry.target.id
        }
      },
      { rootMargin: "-80px 0px -70% 0px" },
    )

    for (const heading of [...headings]) {
      const node = document.getElementById(heading.id)
      if (node) watcher.observe(node)
    }

    return () => watcher.disconnect()
  })
</script>

<aside class="doc-toc">
  <div class="doc-toc-title">On this page</div>
  <ul>
    {#each headings as heading (heading.id)}
      <li><a href="#{heading.id}" class:active={active === heading.id}>{heading.name}</a></li>
    {/each}
  </ul>
</aside>
