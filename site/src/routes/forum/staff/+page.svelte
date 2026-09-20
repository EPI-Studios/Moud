<script lang="ts">
  import { excerpt } from "$lib/render"
  import { when } from "$lib/time"

  let { data } = $props()
</script>

<svelte:head><title>Staff | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>Staff</span>
  </nav>

  <div class="page-head">
    <div>
      <h1>Staff</h1>
      <p>Reports waiting, posts held back, and what has been done lately.</p>
    </div>
  </div>

  <h2 class="profile-heading">Reports ({data.reports.length})</h2>
  {#if data.reports.length === 0}
    <p class="empty">Nothing reported.</p>
  {:else}
    <div class="queue">
      {#each data.reports as report (report.id)}
        <article class="queue-row">
          <div>
            <a class="topic-title" href="/forum/t/{report.topicSlug}">{report.topicTitle}</a>
            <p class="topic-meta">
              {report.authorHandle} · {report.reason}{report.note ? ` · ${report.note}` : ""} ·
              {when(report.createdAt)}
            </p>
            <p class="queue-body">{excerpt(report.body, 240)}</p>
          </div>
          <div class="queue-tools">
            <form method="POST" action="?/resolve">
              <input type="hidden" name="id" value={report.id} />
              <input type="hidden" name="outcome" value="closed" />
              <button class="button button-quiet" type="submit">Leave it</button>
            </form>
            <form method="POST" action="?/resolve">
              <input type="hidden" name="id" value={report.id} />
              <input type="hidden" name="outcome" value="remove" />
              <button class="button" type="submit">Remove post</button>
            </form>
          </div>
        </article>
      {/each}
    </div>
  {/if}

  <h2 class="profile-heading">Held for review ({data.held.length})</h2>
  {#if data.held.length === 0}
    <p class="empty">Nothing held.</p>
  {:else}
    <div class="queue">
      {#each data.held as post (post.id)}
        <article class="queue-row">
          <div>
            <a class="topic-title" href="/forum/t/{post.topicSlug}">{post.topicTitle}</a>
            <p class="topic-meta">{post.authorHandle} · {when(post.createdAt)}</p>
            <p class="queue-body">{excerpt(post.body, 240)}</p>
          </div>
          <div class="queue-tools">
            <form method="POST" action="?/release">
              <input type="hidden" name="postId" value={post.id} />
              <button class="button" type="submit">Publish it</button>
            </form>
          </div>
        </article>
      {/each}
    </div>
  {/if}

  <h2 class="profile-heading">Ban or mute</h2>
  <form class="composer punish" method="POST" action="?/punish">
    <div class="punish-row">
      <input class="field" name="handle" placeholder="handle" required />
      <select class="field" name="what">
        <option value="mute">Mute</option>
        <option value="ban">Ban</option>
      </select>
      <input class="field" name="days" type="number" min="0" value="1" />
      <input class="field" name="reason" placeholder="reason, shown to them" />
      <button class="button button-primary" type="submit">Apply</button>
    </div>
    <span class="composer-hint">Zero days lifts it.</span>
  </form>

  <h2 class="profile-heading">Recent staff actions</h2>
  <ul class="mod-log">
    {#each data.log as row (row.id)}
      <li>
        <b>{row.staffHandle}</b>
        {row.action}
        <span class="post-when">{when(row.createdAt)}</span>
      </li>
    {:else}
      <li class="post-when">Nothing yet.</li>
    {/each}
  </ul>
</main>
