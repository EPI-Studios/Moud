<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Kind = "body" | "callback" | "run" | "suggest" | "url" | "copy"
  type Part = { text: string; tag?: Kind; source?: string; hover?: boolean }

  const PARTS: Part[] = [
    { text: "Welcome back. " },
    {
      text: "Meek",
      tag: "body",
      source: '<hover text="<b>Meek</b> is online"><body id=42>Meek</body></hover>',
      hover: true,
    },
    { text: " invites you: " },
    { text: "accept", tag: "callback", source: '<click callback="accept">accept</click>' },
    { text: " / " },
    { text: "decline", tag: "callback", source: '<click callback="decline">decline</click>' },
    { text: " | " },
    { text: "spawn", tag: "run", source: '<click run="/spawn">spawn</click>' },
    { text: " " },
    { text: "reply", tag: "suggest", source: '<click suggest="/msg Meek ">reply</click>' },
    { text: " " },
    { text: "site", tag: "url", source: '<click url="https://example.com">site</click>' },
    { text: " " },
    { text: "code", tag: "copy", source: '<click copy="ABC-123">code</click>' },
  ]

  const WHOLE = (() => {
    const chunks: string[] = []
    let chunk = ""
    for (const part of PARTS) {
      chunk += part.source ?? part.text
      if (part.tag) {
        chunks.push(chunk)
        chunk = ""
      }
    }
    return `game.chat:system(\n    '${chunks.join("'\n    .. '")}'\n)`
  })()

  let lines = $state<Line[]>([
    { id: 0, text: "click the underlined words; point at Meek for the tooltip", kind: "idle" },
  ])
  let next = 1
  let source = $state(WHOLE)
  let typed = $state("")
  let tip = $state(false)

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function act(part: Part) {
    if (part.tag === "run") {
      record("the player's game runs /spawn straight away", "in")
    } else if (part.tag === "suggest") {
      typed = "/msg Meek "
      record("the chat box now holds /msg Meek and waits for the player", "in")
    } else if (part.tag === "url") {
      record("the player is asked before https://example.com opens", "idle")
    } else if (part.tag === "copy") {
      record("ABC-123 is on the clipboard", "in")
    } else if (part.tag === "callback") {
      record(`game.chat.linkClicked fires with "${part.text}" and the message`, "in")
    } else if (part.tag === "body") {
      record("game.chat.bodyClicked fires with body 42 and the message", "in")
    }

    source =
      (part.source ?? "") +
      (part.tag === "callback"
        ? '\n\ngame.chat.linkClicked:connect(function(name, message)\n    if name == "accept" then end\nend)'
        : part.tag === "body"
          ? "\n\ngame.chat.bodyClicked:connect(function(body, message) end)"
          : "")
  }
</script>

<Demo label="Links and hover">
  <div class="demo-chat rt-links">{#each PARTS as part, i (i)}{#if part.tag}<span
          class="rt-link"
          role="button"
          tabindex="0"
          onclick={() => act(part)}
          onkeydown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault()
              act(part)
            }
          }}
          onmouseenter={() => part.hover && (tip = true)}
          onmouseleave={() => (tip = false)}
        >{part.text}{#if part.hover}<span class="rt-tip" hidden={!tip}><b>Meek</b> is online</span>{/if}</span>{:else}{part.text}{/if}{/each}</div>

  <div class="rt-chatbox" class:rt-chatbox-on={typed !== ""}>
    <span class="rt-chatbox-caret">&gt;</span><span class="rt-chatbox-text">{typed}</span>
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
