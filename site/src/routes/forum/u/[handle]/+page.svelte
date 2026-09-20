<script lang="ts">
  import Badge from "$lib/components/Badge.svelte"
  import Icon from "$lib/components/Icon.svelte"
  import { fullUrl, displayName, isLinked } from "$lib/user"
  import { when } from "$lib/time"
  import { excerpt } from "$lib/render"

  let { data } = $props()

  const me = $derived(data.session?.user.handle === data.person.handle)
</script>

<svelte:head><title>{displayName(data.person)} | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>{displayName(data.person)}</span>
  </nav>

  <section class="profile">
    <img class="profile-render" src={fullUrl(data.person, 288)} alt="" />
    <div class="profile-who">
      <h1>
        {displayName(data.person)}
        {#if data.person.role === "staff"}<Badge kind="staff" label="Staff" large />{/if}
      </h1>
      {#if isLinked(data.person)}
        <p class="profile-line">Minecraft account linked</p>
      {:else}
        <p class="profile-line">No Minecraft account linked yet</p>
      {/if}
      <p class="profile-line">
        Joined {when(data.person.createdAt)} · {data.person.topicCount} topics ·
        {data.person.postCount} posts
      </p>
      {#if data.person.bio}<p class="profile-bio">{data.person.bio}</p>{/if}
    </div>
  </section>

  {#if data.badges.length}
    <div class="profile-badges">
      {#each data.badges as badge (badge.id)}
        <span class="profile-badge tone-{badge.tone}" title={badge.blurb}>
          <Icon name={badge.icon} size="15px" />
          {badge.name}
        </span>
      {/each}
    </div>
  {/if}

  <h2 class="profile-heading">Topics</h2>
  {#if data.topics.length === 0}
    <p class="empty">No topics yet.</p>
  {:else}
    <div class="topic-list">
      {#each data.topics as topic (topic.id)}
        <a class="topic topic-flat" href="/forum/t/{topic.slug}">
          <span>
            <span class="topic-title">{topic.title}</span>
            <span class="topic-meta">{topic.category} · {when(topic.lastPostAt)}</span>
          </span>
          <span class="replies">{topic.replyCount} <span>replies</span></span>
        </a>
      {/each}
    </div>
  {/if}

  <h2 class="profile-heading">Replies</h2>
  {#if data.replies.length === 0}
    <p class="empty">No replies yet.</p>
  {:else}
    <div class="topic-list">
      {#each data.replies as reply (reply.id)}
        <a class="topic topic-flat" href="/forum/t/{reply.slug}">
          <span>
            <span class="topic-title">{reply.title}</span>
            <span class="topic-meta">{excerpt(reply.body, 120)}</span>
          </span>
          <span class="replies">{when(reply.createdAt)}</span>
        </a>
      {/each}
    </div>
  {/if}
</main>
