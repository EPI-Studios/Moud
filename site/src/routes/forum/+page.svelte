<script lang="ts">
  import Icon from "$lib/components/Icon.svelte"
  import { displayName, bustUrl } from "$lib/user"
  import { when } from "$lib/time"

  let { data } = $props()
</script>

<svelte:head><title>Forum | Moud</title></svelte:head>

<main class="forum-shell">
  <div class="page-head">
    <div>
      <h1>Forum</h1>
      <p>Ask about the engine, show a place you built, report what breaks.</p>
    </div>
    <a class="button button-primary" href="/forum/new">New topic</a>
  </div>

  <div class="card-list">
    {#each data.categories as category (category.id)}
      <a class="category" href="/forum/c/{category.id}">
        <span class="category-icon"><Icon name={category.icon} /></span>
        <span>
          <h2 class="category-name">{category.name}</h2>
          <p>{category.blurb}</p>
        </span>
        <span class="counts">
          <span><b>{category.topics}</b>topics</span>
          <span><b>{category.replies}</b>replies</span>
        </span>
      </a>
    {/each}
  </div>

  {#if data.latest.length}
    <div class="page-head" style="margin-top: 40px">
      <h1 style="font-size: 20px">Latest</h1>
    </div>
    <div class="topic-list">
      {#each data.latest as topic (topic.id)}
        <a class="topic" href="/forum/t/{topic.slug}">
          <img class="bust" src={bustUrl(topic, 64)} alt="" />
          <span>
            <span class="topic-title">{topic.title}</span>
            <span class="topic-meta">
              {topic.categoryName} · {displayName(topic)} · {when(topic.lastPostAt)}
            </span>
          </span>
          <span class="replies">{topic.replyCount} <span>replies</span></span>
        </a>
      {/each}
    </div>
  {/if}
</main>
