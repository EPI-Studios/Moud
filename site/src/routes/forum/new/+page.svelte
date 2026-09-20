<script lang="ts">
  let { data, form } = $props()
</script>

<svelte:head><title>New topic | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>New topic</span>
  </nav>

  <div class="page-head">
    <div>
      <h1>New topic</h1>
      <p>One question or one thing to show, with enough detail to answer it.</p>
    </div>
  </div>

  {#if form?.message}<p class="form-error">{form.message}</p>{/if}

  <form class="composer" method="POST">
    <label for="categoryId">Category</label>
    <select class="field" id="categoryId" name="categoryId" value={data.chosen || data.categories[0]?.id}>
      {#each data.categories as category (category.id)}
        <option value={category.id}>{category.name}</option>
      {/each}
    </select>

    <label for="title">Title</label>
    <input class="field" id="title" name="title" maxlength="140" placeholder="What goes wrong, in one line" required />

    <label for="body">Post</label>
    <textarea class="field" id="body" name="body" placeholder="Markdown. Luau goes in a ```luau fence. An image or YouTube link on its own line embeds." required></textarea>

    <div class="composer-foot">
      <span class="composer-hint">Say which side the code runs on, server or client.</span>
      <button class="button button-primary" type="submit">Post topic</button>
    </div>
  </form>
</main>
