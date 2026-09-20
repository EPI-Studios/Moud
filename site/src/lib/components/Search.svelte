<script lang="ts">
  import { goto } from "$app/navigation"
  import { page } from "$app/state"
  import Icon from "./Icon.svelte"
  import type { SearchEntry } from "$lib/docs/pages"

  let open = $state(false)
  let query = $state("")
  let selected = $state(0)
  let entries = $state<SearchEntry[]>([])
  let field = $state<HTMLInputElement | null>(null)
  let loading = $state(false)

  const forum = $derived(page.url.pathname.startsWith("/forum"))
  const what = $derived(forum ? "the forum" : "the docs")

  async function show() {
    open = true
    if (!forum && entries.length === 0) {
      const answer = await fetch("/docs/search.json")
      entries = await answer.json()
    }
    field?.focus()
  }

  async function askForum(text: string) {
    if (!forum) return
    if (text.trim().length < 2) {
      entries = []
      return
    }
    loading = true
    const answer = await fetch(`/forum/search.json?q=${encodeURIComponent(text)}`)
    entries = await answer.json()
    loading = false
  }

  let timer: ReturnType<typeof setTimeout>
  $effect(() => {
    const text = query
    if (!forum) return
    clearTimeout(timer)
    timer = setTimeout(() => askForum(text), 180)
  })

  function hide() {
    open = false
    query = ""
    selected = 0
  }

  function score(entry: SearchEntry, words: string[]) {
    const heading = entry.heading.toLowerCase()
    const page = entry.page.toLowerCase()
    const text = entry.text.toLowerCase()
    let total = 0

    for (const word of words) {
      let hit = 0
      if (heading === word) hit += 40
      if (heading.startsWith(word)) hit += 20
      if (heading.includes(word)) hit += 12
      if (page.includes(word)) hit += 6
      const count = text.split(word).length - 1
      if (count) hit += Math.min(count, 5) * 2
      if (!hit) return 0
      total += hit
    }
    return total
  }

  const words = $derived(query.toLowerCase().trim().split(/\s+/).filter(Boolean))

  const results = $derived.by(() => {
    if (words.length === 0) return []
    if (forum) return entries
    return entries
      .map((entry) => ({ entry, rank: score(entry, words) }))
      .filter((row) => row.rank > 0)
      .sort((a, b) => b.rank - a.rank)
      .slice(0, 12)
      .map((row) => row.entry)
  })

  function snippet(text: string) {
    const lower = text.toLowerCase()
    let at = -1
    for (const word of words) {
      at = lower.indexOf(word)
      if (at >= 0) break
    }
    if (at < 0) return text.slice(0, 140)
    const start = Math.max(0, at - 50)
    return (start > 0 ? "…" : "") + text.slice(start, start + 150) + (start + 150 < text.length ? "…" : "")
  }

  function marked(text: string) {
    let out = text.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]!)
    for (const word of words) {
      if (word.length < 2) continue
      const safe = word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
      out = out.replace(new RegExp(`(${safe})`, "gi"), "<mark>$1</mark>")
    }
    return out
  }

  function onKey(event: KeyboardEvent) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault()
      open ? hide() : show()
      return
    }
    if (!open) return
    if (event.key === "Escape") hide()
    if (event.key === "ArrowDown") {
      event.preventDefault()
      selected = Math.min(selected + 1, results.length - 1)
    }
    if (event.key === "ArrowUp") {
      event.preventDefault()
      selected = Math.max(selected - 1, 0)
    }
    if (event.key === "Enter" && results[selected]) {
      event.preventDefault()
      goto(results[selected].url)
      hide()
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<button type="button" class="search-button" onclick={show}>
  <Icon name="magnifying-glass" size="15px" />
  <span>Search {what}</span>
  <kbd>Ctrl K</kbd>
</button>

{#if open}
  <div
    class="search"
    role="presentation"
    onclick={(event) => event.target === event.currentTarget && hide()}
  >
    <div class="search-box" role="dialog" aria-label="Search the docs">
      <div class="search-field">
        <Icon name="magnifying-glass" size="16px" />
        <input
          bind:this={field}
          bind:value={query}
          type="search"
          placeholder="Search {what}"
          autocomplete="off"
          spellcheck="false"
        />
      </div>
      <div class="search-results">
        {#if words.length === 0}
          <div class="search-empty">
            {forum ? "Search every topic and reply." : "Search every page of the docs."}
          </div>
        {:else if loading && results.length === 0}
          <div class="search-empty">Looking…</div>
        {:else if results.length === 0}
          <div class="search-empty">Nothing matches.</div>
        {:else}
          {#each results as result, index (result.url + index)}
            <a
              class="search-result"
              class:selected={index === selected}
              href={result.url}
              onclick={hide}
              onmouseenter={() => (selected = index)}
            >
              <span class="search-where">
                {result.page}{#if result.heading}&nbsp;›&nbsp;{@html marked(result.heading)}{/if}
              </span>
              <span class="search-text">{@html marked(snippet(result.text))}</span>
            </a>
          {/each}
        {/if}
      </div>
      <div class="search-keys">
        <span><kbd>↑</kbd><kbd>↓</kbd> to move</span>
        <span><kbd>Enter</kbd> to open</span>
        <span><kbd>Esc</kbd> to close</span>
      </div>
    </div>
  </div>
{/if}
