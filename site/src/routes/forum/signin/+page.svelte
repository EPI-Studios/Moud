<script lang="ts">
  import { signIn } from "@auth/sveltekit/client"

  let { data } = $props()
</script>

<svelte:head><title>Sign in | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <div class="signin">
    <h1>Sign in to post</h1>
    <p>Reading needs no account. Posting does.</p>

    {#if data.discordReady}
      <button class="button button-primary" type="button" onclick={() => signIn("discord", { redirectTo: "/forum" })}>
        Continue with Discord
      </button>
    {:else}
      <p class="composer-hint">Discord sign-in is off until AUTH_DISCORD_ID and AUTH_DISCORD_SECRET are set.</p>
    {/if}

    {#if data.dev}
      <div class="signin-dev">
        <span class="composer-hint">Local only</span>
        <a class="button" href="/forum/dev?as=staff">Sign in as dev staff</a>
        <a class="button" href="/forum/dev?as=member">Sign in as dev member</a>
      </div>
    {/if}
  </div>
</main>
