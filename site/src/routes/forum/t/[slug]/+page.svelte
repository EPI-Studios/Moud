<script lang="ts">
  import Badge from "$lib/components/Badge.svelte"
  import TextBadge from "$lib/components/TextBadge.svelte"
  import ExternalLinks from "$lib/components/ExternalLinks.svelte"
  import Composer from "$lib/components/Composer.svelte"
  import LinkPreview from "$lib/components/LinkPreview.svelte"
  import { lonelyLinks } from "$lib/preview-links"
  import { excerpt } from "$lib/render"
  import { page } from "$app/state"
  import { bustUrl, displayName } from "$lib/user"
  import { when } from "$lib/time"

  let { data } = $props()

  let editing = $state<string | null>(null)
  let reporting = $state<string | null>(null)
  let reply = $state("")
  let composer = $state<ReturnType<typeof Composer> | null>(null)

  const staff = $derived(data.session?.user.role === "staff")
  const asked = $derived(data.session?.user.id === data.topic.authorId)

  function mine(authorId: string) {
    return data.session?.user.id === authorId
  }

  function quote(post: { handle: string; minecraftName: string | null; body: string }) {
    const who = displayName(post)
    const text = post.body
      .split("\n")
      .map((line) => `> ${line}`)
      .join("\n")
    composer?.insert(`@${who} said:\n${text}`)
  }
</script>

<svelte:head>
  <title>{data.topic.title} | Moud Forum</title>
  <meta name="description" content={excerpt(data.posts[0]?.body ?? "", 180)} />
  <meta property="og:title" content={data.topic.title} />
  <meta property="og:description" content={excerpt(data.posts[0]?.body ?? "", 180)} />
  <meta property="og:type" content="article" />
  <meta property="og:site_name" content="Moud Forum" />
  <meta property="og:url" content={page.url.href} />
  <meta property="og:image" content="{page.url.origin}/forum/t/{data.topic.slug}/og.png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="article:published_time" content={new Date(data.topic.createdAt).toISOString()} />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="theme-color" content="#c6c6c6" />
</svelte:head>

<ExternalLinks />

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    {#if data.category}<a href="/forum/c/{data.category.id}">{data.category.name}</a>{/if}
  </nav>

  <div class="page-head">
    <div>
      <h1>
        {#if data.topic.pinned}<Badge kind="pin" label="Pinned" large />{/if}
        {#if data.topic.locked}<Badge kind="lock" label="Locked" large />{/if}
        {#if data.topic.solvedPostId}<Badge kind="solved" label="Solved" large />{/if}
        {data.topic.title}
      </h1>
      <p>
        {data.posts.length}
        {data.posts.length === 1 ? "post" : "posts"} · {data.topic.viewCount} views
      </p>
    </div>
    {#if staff}
      <div class="head-tools">
        <form method="POST" action="?/moderate">
          <input type="hidden" name="what" value="pin" />
          <button class="button button-quiet" type="submit">
            {data.topic.pinned ? "Unpin" : "Pin"}
          </button>
        </form>
        <form method="POST" action="?/moderate">
          <input type="hidden" name="what" value="lock" />
          <button class="button button-quiet" type="submit">
            {data.topic.locked ? "Unlock" : "Lock"}
          </button>
        </form>
      </div>
    {/if}
  </div>

  {#each data.posts as post (post.id)}
    <article class="post" class:post-answer={data.topic.solvedPostId === post.id}>
      <img class="bust" src={bustUrl(post)} alt="" />
      <div>
        <div class="post-head">
          <a class="post-author" href="/forum/u/{post.handle}">{displayName(post)}</a>
          {#if post.role === "staff"}<Badge kind="staff" label="Staff" />{/if}
          {#if post.authorId === data.topic.authorId}
            <TextBadge text="OP" label="Started this topic" />
          {/if}
          {#if data.topic.solvedPostId === post.id}
            <Badge kind="solved" label="Marked as the answer" />
          {/if}
          <span class="post-when">
            {when(post.createdAt)}{post.editedAt ? " · edited" : ""}
          </span>
        </div>

        {#if post.hidden}
          <p class="post-gone">Held for review. Staff will look at it shortly.</p>
        {/if}

        {#if post.deletedAt}
          <p class="post-gone">This post was deleted.</p>
        {:else if editing === post.id}
          <Composer
            name="body"
            value={post.body}
            label="Edit post"
            placeholder="Markdown. Luau in a ```luau fence."
            hint="Editing shows an edited mark on the post."
            submit="Save"
            action="?/edit"
            fields={{ postId: post.id }}
            small
          />
          <button class="post-tool" type="button" onclick={() => (editing = null)}>Cancel</button>
        {:else}
          <div class="post-body">{@html post.html}</div>

          {#each lonelyLinks(post.html) as url (url)}
            {#if data.previews[url]}
              <LinkPreview card={data.previews[url]} />
            {/if}
          {/each}

          {#if data.session?.user}
            <div class="post-tools">
              {#if !data.topic.locked}
                <button class="post-tool" type="button" onclick={() => quote(post)}>Quote</button>
              {/if}
              {#if mine(post.authorId) || staff}
                <button class="post-tool" type="button" onclick={() => (editing = post.id)}>
                  Edit
                </button>
                <form method="POST" action="?/remove">
                  <input type="hidden" name="postId" value={post.id} />
                  <button class="post-tool" type="submit">Delete</button>
                </form>
              {/if}
              {#if !mine(post.authorId)}
                <button class="post-tool" type="button" onclick={() => (reporting = post.id)}>
                  Report
                </button>
              {/if}
              {#if (asked || staff) && post.authorId !== data.topic.authorId}
                <form method="POST" action="?/answer">
                  <input type="hidden" name="postId" value={post.id} />
                  <button class="post-tool answer" type="submit">
                    {data.topic.solvedPostId === post.id ? "Unmark answer" : "Mark as answer"}
                  </button>
                </form>
              {/if}
            </div>
          {/if}
        {/if}

        {#if reporting === post.id}
          <form class="report-form" method="POST" action="?/report">
            <input type="hidden" name="postId" value={post.id} />
            <select class="field" name="reason">
              <option value="spam">Spam</option>
              <option value="abuse">Abusive</option>
              <option value="off-topic">Off topic</option>
              <option value="other">Something else</option>
            </select>
            <input class="field" name="note" placeholder="anything staff should know" />
            <button class="button" type="submit">Send report</button>
            <button class="post-tool" type="button" onclick={() => (reporting = null)}>Cancel</button>
          </form>
        {/if}
      </div>
    </article>
  {/each}

  {#if data.topic.locked}
    <p class="empty">This topic is locked. Nobody can reply to it.</p>
  {:else if data.session?.user}
    <Composer
      bind:this={composer}
      bind:value={reply}
      label="Reply"
      placeholder="Markdown. Luau in a ```luau fence. An image or YouTube link on its own line embeds."
      hint="Paste the code and the error. @name mentions somebody."
      submit="Post reply"
      action="?/reply"
    />
  {:else}
    <p class="empty"><a href="/forum/signin">Sign in</a> to reply.</p>
  {/if}
</main>
