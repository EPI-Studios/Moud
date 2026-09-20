<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log, { type Line } from "../ui/Log.svelte"

  const EDITOR: [string, string][] = [
    ["Ctrl+N", "New Scene..."],
    ["Ctrl+O", "Open Scene..."],
    ["Ctrl+S", "Save Scene"],
    ["Ctrl+Shift+S", "Save Scene As..."],
    ["Ctrl+E", "Export..."],
    ["Ctrl+Z", "Undo"],
    ["Ctrl+Y", "Redo"],
    ["Ctrl+Shift+Z", "Redo"],
    ["Ctrl+C", "Copy"],
    ["Ctrl+V", "Paste"],
    ["Ctrl+D", "Duplicate"],
    ["F2", "Rename"],
    ["Ctrl+G", "Group"],
    ["Ctrl+U", "Ungroup"],
    ["Ctrl+R", "Rotate 90° around Y"],
    ["Ctrl+T", "Rotate 90° around X"],
    ["Ctrl+L", "Lock"],
    ["Ctrl+Shift+L", "Unlock"],
    ["Ctrl+W", "Weld Selected"],
    ["Ctrl+A", "Select All"],
    ["Ctrl+Shift+A", "Animation Editor"],
  ]

  const PRESETS = [
    "Ctrl+Shift+G",
    "alt+1",
    "F5",
    "Ctrl+D",
    "Ctrl+Space",
    "Cmd+G",
    "control+shift+p",
    "F13",
    "",
  ]

  let name = $state("Align")
  let shortcut = $state("Ctrl+Shift+G")

  function parse(text: string) {
    let ctrl = false
    let shift = false
    let alt = false
    const parts = text.split("+")
    for (let n = 0; n < parts.length - 1; n++) {
      const mod = parts[n].trim().toLowerCase()
      if (mod === "ctrl" || mod === "control") ctrl = true
      else if (mod === "shift") shift = true
      else if (mod === "alt") alt = true
      else return null
    }
    const key = parts[parts.length - 1].trim().toUpperCase()
    if (!/^([A-Z0-9]|F([1-9]|1[0-2]))$/.test(key)) return null
    return {
      label: (ctrl ? "Ctrl+" : "") + (shift ? "Shift+" : "") + (alt ? "Alt+" : "") + key,
    }
  }

  function taken(label: string) {
    const found = EDITOR.find((entry) => entry[0] === label)
    return found ? found[1] : null
  }

  const result = $derived.by(() => {
    const lines: Line[] = []
    const say = (text: string, kind?: Line["kind"]) =>
      lines.push({ id: lines.length, text, kind })
    let shown = ""
    if (!name) {
      say("error: a command needs a name", "out")
    } else if (!shortcut.trim()) {
      say("no shortcut: the command is only in the menu", "idle")
    } else {
      const parsed = parse(shortcut)
      const clash = parsed ? taken(parsed.label) : null
      if (!parsed) {
        say(`grid: '${shortcut}' is not a shortcut, use something like Ctrl+Shift+G or F5`, "out")
      } else if (clash) {
        say(
          `grid: ${parsed.label} is the editor's ${clash} shortcut, so '${name}' gets no shortcut`,
          "out",
        )
        say(`pressing it does ${clash} as before`, "idle")
      } else {
        shown = parsed.label
        say(`fires on ${parsed.label}, and only with exactly those modifiers held`, "in")
      }
    }
    return { lines, shown }
  })

  const source = $derived(
    `plugin:command("${name}", ${shortcut ? `"${shortcut}", ` : ""}align)`,
  )
</script>

<Demo label="Shortcuts">
  <div class="demo-field">
    <span class="demo-field-prefix">name</span>
    <input
      type="text"
      class="demo-input"
      spellcheck="false"
      aria-label="command name"
      bind:value={name}
    />
  </div>
  <div class="demo-field">
    <span class="demo-field-prefix">shortcut</span>
    <input
      type="text"
      class="demo-input"
      spellcheck="false"
      aria-label="shortcut"
      bind:value={shortcut}
    />
  </div>

  <div class="pl-menu">
    <span class="pl-menu-title">Plugins</span>
    {#if name}
      <div class="pl-menu-item">
        <span>{name}</span>
        <span class="pl-menu-key">{result.shown}</span>
      </div>
    {/if}
  </div>

  <Log lines={result.lines} keep={2} />

  <div class="demo-controls demo-row">
    {#each PRESETS as preset (preset)}
      <button type="button" class="demo-pill pl-mono" onclick={() => (shortcut = preset)}>
        {preset || "none"}
      </button>
    {/each}
  </div>

  <CodePanel {source} />
</Demo>
