<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Row = { id: number; text: string; status: string; refused: boolean; prefix: boolean }

  const REASONS: Record<string, string> = {
    Muted: "You can't send messages in this channel.",
    TooLong: "That message is too long.",
    SlowMode: "Slow down, this channel has slow mode on.",
    Blocked: "Your message wasn't sent.",
  }

  let slowMode = $state(3)
  let maxLength = $state(40)
  let canSend = $state("true")
  let filter = $state("word filter")
  let draft = $state("anyone up for a round?")
  let rows = $state<Row[]>([
    { id: 0, text: "meek joined general", status: "System", refused: false, prefix: false },
  ])

  let next = 1
  let lastSent = -Infinity
  let fill = $state<HTMLDivElement | null>(null)

  const source = $derived.by(() => {
    let out =
      "-- server\n" +
      'local general = world:add("TextChannel", {\n' +
      '    name = "general", autoJoin = true,\n' +
      `    slowMode = ${slowMode}, maxLength = ${maxLength},\n` +
      "})\n"
    if (filter === "word filter") {
      out +=
        "\ngeneral.shouldSend = function(message)\n" +
        '    return not string.find(message.plain, "badword")\n' +
        "end\n"
    }
    if (canSend !== "true") out += "\nsource.canSend = false   -- your TextSource in general\n"
    return out.replace(/\n$/, "")
  })

  function refusal(text: string, now: number) {
    if (canSend !== "true") return "Muted"
    if (text.length > maxLength) return "TooLong"
    if (slowMode > 0 && now - lastSent < slowMode * 1000) return "SlowMode"
    if (filter === "word filter" && text.includes("badword")) return "Blocked"
    return null
  }

  function add(row: Omit<Row, "id">) {
    rows = [...rows, { id: next++, ...row }].slice(-5)
  }

  function fire() {
    if (!draft.trim()) return
    const now = performance.now()
    const refused = refusal(draft, now)
    if (refused) {
      add({ text: draft, status: refused, refused: true, prefix: false })
      return
    }
    lastSent = now
    add({ text: draft, status: "Success", refused: false, prefix: true })
    if (slowMode > 0 && fill) {
      fill.style.transition = "none"
      fill.style.width = "100%"
      void fill.offsetWidth
      fill.style.transition = `width ${slowMode}s linear`
      fill.style.width = "0%"
    }
  }
</script>

<Demo label="Sending a line">
  <div class="demo-field chat-field">
    <input
      type="text"
      class="demo-input"
      spellcheck="false"
      maxlength="120"
      aria-label="message to send"
      bind:value={draft}
      onkeydown={(event) => {
        if (event.key === "Enter") fire()
      }}
    />
    <button type="button" class="demo-pill demo-pill-wide" onclick={fire}>send</button>
  </div>

  <div class="chat-screen">
    <div class="chat-lines">
      {#each rows as row (row.id)}
        <div class="chat-line" class:refused={row.refused}>
          {#if row.prefix}<span class="chat-prefix">meek </span>{/if}
          <span class="chat-text">{row.refused ? REASONS[row.status] : row.text}</span>
          <span class="chat-status">{row.status}</span>
        </div>
      {/each}
    </div>
    <div class="chat-wait"><div bind:this={fill} class="chat-wait-fill"></div></div>
  </div>

  <Note>
    Send twice quickly, type past the limit, mute yourself or type badword. A refused line never
    reaches the channel; the sender sees why and the message's status names it.
  </Note>

  <div class="demo-controls">
    <Slider label="slowMode" min={0} max={10} step={1} bind:value={slowMode} />
    <Slider label="maxLength" min={8} max={80} step={1} bind:value={maxLength} />
    <Choice label="canSend" options={["true", "false"]} bind:value={canSend} />
    <Choice label="shouldSend" options={["word filter", "unset"]} bind:value={filter} />
  </div>

  <CodePanel {source} />
</Demo>
