<script lang="ts">
  import Badge from "$lib/components/Badge.svelte"
  import Pager from "$lib/components/Pager.svelte"
  import { displayName, bustUrl } from "$lib/user"
  import { when } from "$lib/time"

  let { data } = $props()
</script>

<svelte:head><title>{data.category.name} | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>{data.category.name}</span>
  </nav>

  <div class="page-head">
    <div>
      <h1>{data.category.name}</h1>
      <p>{data.category.blurb}</p>
    </div>
    <a class="button button-primary" href="/forum/new?category={data.category.id}">New topic</a>
  </div>

  <div class="sorts">
    {#each Object.entries(data.sorts) as [key, label] (key)}
      <a
        class="sort"
        class:active={data.sort === key}
        href="/forum/c/{data.category.id}?sort={key}"
      >
        {label}
      </a>
    {/each}
    <span class="sorts-count">{data.total} {data.total === 1 ? "topic" : "topics"}</span>
  </div>

  {#if data.topics.length === 0}
    <p class="empty">No topics here yet. Be the first to post one.</p>
  {:else}
    <div class="topic-list">
      {#each data.topics as topic (topic.id)}
        <a class="topic" class:unread={topic.unread} href="/forum/t/{topic.slug}">
          <img class="bust" src={bustUrl(topic, 64)} alt="" />
          <span>
            <span class="topic-title">
              {#if topic.pinned}<Badge kind="pin" label="Pinned" />{/if}
              {#if topic.locked}<Badge kind="lock" label="Locked" />{/if}
              {#if topic.solvedPostId}<Badge kind="solved" label="Solved" />{/if}
              {topic.title}
            </span>
            <span class="topic-meta">{displayName(topic)} · {when(topic.lastPostAt)}</span>
          </span>
          <span class="replies">{topic.replyCount} <span>replies</span></span>
        </a>
      {/each}
    </div>

    <Pager at={data.at} pages={data.pages} base="/forum/c/{data.category.id}?sort={data.sort}" />
  {/if}
</main>
