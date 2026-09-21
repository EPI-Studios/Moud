<script lang="ts">
  import DocSidebar from "$lib/components/DocSidebar.svelte"
  import DemoHost from "$lib/components/DemoHost.svelte"
  import Toc from "$lib/components/Toc.svelte"
  import Icon from "$lib/components/Icon.svelte"
  import PageIcon from "$lib/components/PageIcon.svelte"
  import { page } from "$app/state"

  let { data } = $props()

  const parts = $derived(data.html.split("{{browse}}"))

  type Piece = { html: string; demo?: string }

  function pieces(html: string): Piece[] {
    const out: Piece[] = []
    const pattern = /\{\{demo:([a-z0-9-]+)\}\}/g
    let last = 0
    let match: RegExpExecArray | null
    while ((match = pattern.exec(html)) !== null) {
      out.push({ html: html.slice(last, match.index), demo: match[1] })
      last = match.index + match[0].length
    }
    out.push({ html: html.slice(last) })
    return out
  }
</script>

<svelte:head>
  <title>{data.title} | Moud Docs</title>
  <meta name="description" content={data.page.blurb || `${data.title} in the Moud documentation.`} />
  <meta property="og:title" content={data.title} />
  <meta property="og:description" content={data.page.blurb || `${data.title} in the Moud documentation.`} />
  <meta property="og:type" content="article" />
  <meta property="og:site_name" content="Moud" />
  <meta property="og:url" content={page.url.href} />
  <meta
    property="og:image"
    content="{page.url.origin}/docs/{data.page.slug ? data.page.slug + '/' : ''}og.png"
  />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="theme-color" content="#c6c6c6" />
</svelte:head>

<div class="shell">
  <aside class="doc-sidebar">
    <DocSidebar groups={data.groups} current={data.page.stem} />
  </aside>

  <main class="doc-main">
    <nav class="crumbs">
      <a href="/">Home</a>
      {#if data.page.stem === "README"}
        <span>Guides</span>
      {:else}
        <a href="/docs/">Guides</a>
        <span>{data.group}</span>
      {/if}
    </nav>

    <article class="doc">
      {#each pieces(parts[0]) as piece, index (index)}
        {@html piece.html}
        {#if piece.demo}<DemoHost name={piece.demo} />{/if}
      {/each}

      {#if parts.length > 1}
        <div class="tiles">
          {#each data.tiles as tile (tile.stem)}
            <a class="tile" href="/docs/{tile.stem}">
              <span class="tile-art"><Icon name={tile.glyph} size="22px" /></span>
              <span class="tile-title">{tile.title}</span>
              <span class="tile-text">{tile.blurb}</span>
            </a>
          {/each}
        </div>

        <div class="browse">
          {#each data.groups as group (group.name)}
            <div class="browse-col">
              <h3>{group.name}</h3>
              <ul>
                {#each group.pages as row (row.stem)}
                  <li>
                    <a href="/docs/{row.slug}">
                      <PageIcon icon={row.icon} />
                      {row.label}
                    </a>
                  </li>
                {/each}
              </ul>
            </div>
          {/each}
        </div>

        {#each pieces(parts[1]) as piece, index (index)}
          {@html piece.html}
          {#if piece.demo}<DemoHost name={piece.demo} />{/if}
        {/each}
      {/if}
    </article>

    <div class="doc-pager">
      {#if data.previous}
        <a class="prev" href="/docs/{data.previous.slug}">
          <span>Previous</span>{data.previous.label}
        </a>
      {:else}
        <span></span>
      {/if}
      {#if data.next}
        <a class="next" href="/docs/{data.next.slug}">
          <span>Next</span>{data.next.label}
        </a>
      {/if}
    </div>

    <footer class="doc-footer">
      <span>&copy; 2026 EPI Studios</span>
      <a href="https://github.com/EPI-Studios/Moud/tree/main/docs">Edit this page on GitHub</a>
    </footer>
  </main>

  {#if data.headings.length > 1}
    <Toc headings={data.headings} />
  {/if}
</div>
