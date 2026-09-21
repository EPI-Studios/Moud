<script lang="ts">
  import { page } from "$app/state"
</script>

<svelte:head><title>{page.status} | Moud</title></svelte:head>

<main class="forum-shell">
  <div class="page-head">
    <div>
      <h1>{page.status === 404 ? "Nothing here" : "Something broke"}</h1>
      <p>{page.error?.message ?? "No further detail."}</p>
    </div>
  </div>

  {#if page.error?.id}
    <p class="composer-hint">Reference {page.error.id}</p>
  {/if}

  <p class="empty"><a href="/forum">Back to the forum</a></p>
</main>
