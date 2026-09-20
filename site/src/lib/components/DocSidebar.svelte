<script lang="ts">
  import Icon from "./Icon.svelte"
  import PageIcon from "./PageIcon.svelte"
  import type { Group } from "$lib/docs/pages"

  let { groups, current }: { groups: Group[]; current: string } = $props()
</script>

{#each groups as group (group.name)}
  <details class="nav-group" open={group.pages.some((page) => page.stem === current)}>
    <summary><Icon name="caret-down" size="13px" />{group.name}</summary>
    <ul>
      {#each group.pages as page (page.stem)}
        <li>
          <a href="/docs/{page.slug}" class:active={page.stem === current}>
            <PageIcon icon={page.icon} />
            <span>{page.label}</span>
          </a>
        </li>
      {/each}
    </ul>
  </details>
{/each}
