<script lang="ts">
  import "../styles/site.css"
  import "../styles/demos.css"
  import "../app.css"
  import { page } from "$app/state"
  import Search from "$lib/components/Search.svelte"
  import Icon from "$lib/components/Icon.svelte"
  import { displayName, faceUrl } from "$lib/user"

  let { children } = $props()

  const user = $derived(page.data.session?.user)
  const tab = $derived(page.url.pathname.startsWith("/forum") ? "forum" : "docs")

  let navOpen = $state(false)

  $effect(() => {
    page.url.pathname
    navOpen = false
  })

  $effect(() => {
    document.body.classList.toggle("nav-open", navOpen)
  })
</script>

<svelte:head>
  <link rel="icon" href="/logo.png" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="" />
  <link
    href="https://fonts.googleapis.com/css2?family=Gabarito:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap"
    rel="stylesheet"
  />
</svelte:head>

<header class="topbar">
  <button type="button" class="menu-button" aria-label="Menu" onclick={() => (navOpen = !navOpen)}>
    <Icon name="list" size="20px" />
  </button>
  <a href="/" class="brand"><img src="/logo.png" alt="" />Moud</a>
  <nav class="tabs">
    <a class="tab" href="/docs">Guides</a>
    <a class="tab" href="/docs/reference">Reference</a>
    <a class="tab" class:active={tab === "forum"} href="/forum">Forum</a>
  </nav>
  <div class="topbar-end">
    {#if user?.role === "staff"}
      <a class="button button-quiet" href="/forum/staff">Staff</a>
    {/if}
    <Search />
    {#if user}
      <a class="button button-quiet" href="/forum/u/{user.handle}">
        <img class="head head-sm" src={faceUrl(user, 48)} alt="" />
        {displayName(user)}
      </a>
      <form method="POST" action="/forum/signout">
        <button class="button button-quiet" type="submit" aria-label="Sign out">
          <Icon name="sign-out" size="15px" />
        </button>
      </form>
    {:else}
      <a class="button" href="/forum/signin">
        <Icon name="discord-logo" size="15px" />
        Sign in
      </a>
    {/if}
  </div>
</header>

{@render children()}
