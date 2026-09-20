<script lang="ts">
  import DocSidebar from "$lib/components/DocSidebar.svelte"
  import PageIcon from "$lib/components/PageIcon.svelte"
  import Icon from "$lib/components/Icon.svelte"

  let { data } = $props()
</script>

<svelte:head>
  <title>Moud — games in Minecraft, written in Luau</title>
  <meta
    name="description"
    content="Moud runs games written in Luau inside Minecraft. Guides, reference and a forum."
  />
</svelte:head>

<div class="shell">
  <aside class="doc-sidebar">
    <DocSidebar groups={data.groups} current="" />
  </aside>

  <main class="doc-main">
    <nav class="crumbs"><span>Home</span></nav>

    <article class="doc home-doc">
      <h1 id="moud">Moud</h1>
      <p>
        Moud runs games written in Luau inside Minecraft. A game is a place: a folder of Luau that
        builds a tree of instances, which the engine draws, collides and sends to every player. These
        pages cover that tree, every class in it, and how the server and each client share it.
      </p>

      <h2 id="first-place">Your first place</h2>
      <p>
        This is enough for a floor to stand on: the server builds the world and puts every player who
        joins on it.
      </p>
      {@html data.sample}
      <p><a href="/docs/getting-started">Getting started</a> walks through it line by line.</p>

      <h2 id="guides">Guides</h2>
      <div class="tiles">
        {#each data.tiles as tile (tile.stem)}
          <a class="tile" href="/docs/{tile.stem}">
            <span class="tile-art"><Icon name={tile.glyph} size="22px" /></span>
            <span class="tile-title">{tile.title}</span>
            <span class="tile-text">{tile.blurb}</span>
          </a>
        {/each}
      </div>

      <h2 id="every-page">Every page</h2>
      <div class="browse">
        {#each data.groups as group (group.name)}
          <div class="browse-col">
            <h3>{group.name}</h3>
            <ul>
              {#each group.pages as page (page.stem)}
                <li>
                  <a href="/docs/{page.slug}">
                    <PageIcon icon={page.icon} />
                    {page.label}
                  </a>
                </li>
              {/each}
            </ul>
          </div>
        {/each}
      </div>
    </article>

    <footer class="doc-footer">
      <span>&copy; 2026 EPI Studios. Moud is not affiliated with Mojang or Microsoft.</span>
      <a href="https://github.com/EPI-Studios/Moud">GitHub</a>
    </footer>
  </main>

  <aside class="doc-toc">
    <div class="doc-toc-title">On this page</div>
    <ul>
      <li><a href="#first-place">Your first place</a></li>
      <li><a href="#guides">Guides</a></li>
      <li><a href="#every-page">Every page</a></li>
    </ul>
  </aside>
</div>
