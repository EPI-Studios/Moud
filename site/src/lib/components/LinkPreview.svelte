<script lang="ts">
  let {
    card,
  }: {
    card: {
      url: string
      title: string | null
      blurb: string | null
      image: string | null
      site: string | null
      author: string | null
      accent: string | null
      large: boolean
    }
  } = $props()

  const host = $derived.by(() => {
    try {
      return new URL(card.url).host.replace(/^www\./, "")
    } catch {
      return card.url
    }
  })
</script>

<div class="embed" style={card.accent ? `--embed-accent: ${card.accent}` : ""}>
  <div class="embed-body">
    <span class="embed-site">{card.site || host}</span>
    <a class="embed-title" href={card.url} data-external rel="noopener noreferrer nofollow ugc">
      {card.title || card.url}
    </a>
    {#if card.blurb}<p class="embed-blurb">{card.blurb}</p>{/if}
    {#if card.author}<span class="embed-author">{card.author}</span>{/if}

    {#if card.image && card.large}
      <a class="embed-shot" href={card.url} data-external rel="noopener noreferrer nofollow ugc">
        <img src={card.image} alt="" loading="lazy" />
      </a>
    {/if}
  </div>

  {#if card.image && !card.large}
    <a class="embed-thumb" href={card.url} data-external rel="noopener noreferrer nofollow ugc">
      <img src={card.image} alt="" loading="lazy" />
    </a>
  {/if}
</div>
