<script lang="ts">
  let target = $state<string | null>(null)

  const host = $derived.by(() => {
    if (!target) return ""
    try {
      return new URL(target).host
    } catch {
      return target
    }
  })

  function onClick(event: MouseEvent) {
    const link = (event.target as HTMLElement | null)?.closest?.("a[data-external]")
    if (!(link instanceof HTMLAnchorElement)) return
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    target = link.href
  }

  function onKey(event: KeyboardEvent) {
    if (event.key === "Escape") target = null
  }
</script>

<svelte:document onclick={onClick} onkeydown={onKey} />

{#if target}
  <div class="leaving" role="dialog" aria-modal="true" aria-label="Leaving the site">
    <div class="leaving-box">
      <h2>You are leaving Moud</h2>
      <p>
        This link goes to <b>{host}</b>, which nobody here controls. Open it only if you trust
        whoever posted it.
      </p>
      <p class="leaving-url">{target}</p>
      <div class="leaving-buttons">
        <button class="button" type="button" onclick={() => (target = null)}>Stay here</button>
        <a
          class="button button-primary"
          href={target}
          target="_blank"
          rel="noopener noreferrer nofollow ugc"
          onclick={() => (target = null)}
        >
          Open {host}
        </a>
      </div>
    </div>
  </div>
{/if}
