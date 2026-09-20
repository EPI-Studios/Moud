<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"

  const CHAT_COLORS: Record<string, string> = {
    black: "#000000", darkblue: "#0000aa", darkgreen: "#00aa00", darkaqua: "#00aaaa",
    darkred: "#aa0000", darkpurple: "#aa00aa", gold: "#ffaa00", gray: "#aaaaaa",
    darkgray: "#555555", blue: "#5555ff", green: "#55ff55", aqua: "#55ffff",
    red: "#ff5555", purple: "#ff55ff", yellow: "#ffff55", white: "#ffffff",
  }

  const TAGS: Record<string, (value: string) => string | null> = {
    b: () => 'style="font-weight:700"',
    i: () => 'style="font-style:italic"',
    u: () => 'style="text-decoration:underline"',
    s: () => 'style="text-decoration:line-through"',
    uc: () => 'style="text-transform:uppercase"',
    uppercase: () => 'style="text-transform:uppercase"',
    sc: () => 'style="font-variant:small-caps"',
    smallcaps: () => 'style="font-variant:small-caps"',
    rainbow: () => 'class="rt-rainbow"',
    wave: () => 'class="rt-wave"',
    obf: () => 'class="rt-obf"',
    color: (value) => (value ? `style="color:${escapeHtml(CHAT_COLORS[value.toLowerCase()] ?? value)}"` : null),
    size: (value) => (value ? `style="font-size:${parseFloat(value) || 1}em"` : null),
    alpha: (value) => (value ? `style="opacity:${isNaN(parseFloat(value)) ? 1 : parseFloat(value)}"` : null),
    transparency: (value) => (value ? `style="opacity:${1 - (parseFloat(value) || 0)}"` : null),
  }

  const LETTERS = "abcdefghijklmnopqrstuvwxyz0123456789"

  let text = $state('<color=gold><b>meek</b></color> found <rainbow>treasure</rainbow> <3')
  let screen = $state<HTMLDivElement | null>(null)

  function escapeHtml(source: string) {
    return source
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
  }

  function richText(source: string) {
    let html = ""
    const open: string[] = []
    let at = 0
    while (at < source.length) {
      const start = source.indexOf("<", at)
      const end = start === -1 ? -1 : source.indexOf(">", start)
      if (end === -1) {
        html += escapeHtml(source.slice(at))
        break
      }
      html += escapeHtml(source.slice(at, start))
      const raw = source.slice(start + 1, end)
      const name = raw.split("=")[0].toLowerCase()
      const value = raw.slice(name.length + 1)
      const attrs = TAGS[name] ? TAGS[name](value) : null
      if (raw === "br" || raw === "br/") {
        html += "<br>"
      } else if (raw.charAt(0) === "/" && open.lastIndexOf(raw.slice(1)) !== -1) {
        while (open.length) {
          html += "</span>"
          if (open.pop() === raw.slice(1)) break
        }
      } else if (attrs) {
        html += `<span ${attrs}>`
        open.push(name)
      } else {
        html += escapeHtml(`<${raw}>`)
      }
      at = end + 1
    }
    return html + open.map(() => "</span>").join("")
  }

  const rendered = $derived(richText(text))

  $effect(() => {
    const timer = setInterval(() => {
      for (const node of screen?.querySelectorAll(".rt-obf") ?? []) {
        node.textContent = (node.textContent ?? "").replace(/\S/g, () =>
          LETTERS.charAt(Math.floor(Math.random() * LETTERS.length)),
        )
      }
    }, 90)
    return () => clearInterval(timer)
  })
</script>

<Demo label="Rich text">
  <div class="demo-field">
    <span class="demo-field-prefix">game.chat:system(</span>
    <input class="demo-input" type="text" spellcheck="false" aria-label="rich text" bind:value={text} />
    <span class="demo-field-prefix">)</span>
  </div>

  <div class="demo-chat" bind:this={screen}>{@html rendered}</div>

  <Note>Tags the engine does not know are drawn as text, so a player typing &lt;3 sees &lt;3.</Note>
</Demo>
