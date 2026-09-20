<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Entry = { id: number; name: string; changes: string[] }

  let history = $state<Entry[]>([])
  let note = $state("Every call a plugin makes is one entry. Press a few, then undo.")
  let source = $state("plugin:recording(name: string, changes: () -> ())")
  let held = $state(false)
  let nextEntry = 1
  let timer: ReturnType<typeof setInterval> | null = null
  let frames = 0
  let slider: Entry | null = null

  function push(name: string, changes: string[]) {
    history.push({ id: nextEntry++, name, changes })
  }

  function undo() {
    const entry = history.pop()
    if (!entry) return
    note = `Undo takes back "${entry.name}" whole: ${entry.changes.join(", ")}.`
  }

  const BEFORE_DRAG: { label: string; code: string; run: () => string }[] = [
    {
      label: "Align to grid",
      code: `plugin:command("Align to grid", "Ctrl+Shift+G", function()
    plugin:recording("Align to grid", function()
        for _, chosen in plugin.selection:get() do
            local part = chosen :: Part
            part.position = snap(part.position)
        end
    end)
end)`,
      run: () => {
        push("Align to grid", ["crate moved", "barrel moved", "lamp moved"])
        return "Three parts moved in one call, inside a recording, so they are one entry with the recording's name."
      },
    },
    {
      label: "Anchor",
      code: `plugin:command("Anchor", function()
    for _, chosen in plugin.selection:get() do
        (chosen :: Part).anchored = true
    end
end)`,
      run: () => {
        push("grid", ["crate.anchored", "barrel.anchored"])
        return "No recording, so the call's changes are still one entry, named after the plugin."
      },
    },
    {
      label: "Stamp block",
      code: `plugin.viewportClicked:connect(function(mouse)
    plugin:recording("Stamp block", function()
        game.world:add("Part", { name = "block", anchored = true })
    end)
end)`,
      run: () => {
        push("Stamp block", ["block added"])
        return "Each click is its own call, so each stamp is its own entry."
      },
    },
  ]

  const AFTER_DRAG: { label: string; code: string; run: () => string }[] = [
    {
      label: "Rename all, which errors",
      code: `local ok, why = pcall(function()
    plugin:recording("Rename all", function()
        for _, chosen in plugin.selection:get() do
            chosen.name = ""
        end
    end)
end)
print(\`stopped: {why}\`)   -- stopped: a name can not be empty`,
      run: () =>
        'The very first rename to "" errors and pcall catches it, so nothing changed. Changes made before an error are not undone: had some landed first, they would stay as one entry called Rename all.',
    },
    {
      label: "two plugins, one frame",
      code: "-- grid and paint both change the scene\n-- from game.renderStepped in the same frame",
      run: () => {
        push("grid", ["crate moved"])
        push("paint", ["crate.color"])
        return "Two plugins never share an entry: each one's changes from the frame are an entry of their own, named after it."
      },
    },
  ]

  function act(action: { code: string; run: () => string }) {
    note = action.run()
    source = action.code
  }

  function grab(event: PointerEvent) {
    event.preventDefault()
    frames = 0
    held = true
    history.push({ id: nextEntry++, name: "grid", changes: ["crate.transparency"] })
    slider = history[history.length - 1]
    source = `plugin:panel("Look"):onDraw(function(ui)
    crate.transparency = ui:slider("Transparency", crate.transparency, 0, 1)
end)`
    timer = setInterval(() => {
      frames += 1
      if (slider) slider.changes = [`crate.transparency, set ${frames} times`]
    }, 50)
  }

  function release() {
    if (timer === null) return
    clearInterval(timer)
    timer = null
    held = false
    note = `${frames} frames each set crate.transparency while the button was held, and they merged into the one entry.`
    slider = null
  }

  $effect(() => () => {
    if (timer !== null) clearInterval(timer)
  })

  const shown = $derived(history.slice().reverse())
</script>

<Demo label="Undo entries">
  <div class="pl-undo">
    <div class="pl-actions">
      {#each BEFORE_DRAG as action (action.label)}
        <button
          type="button"
          class="demo-pill demo-pill-wide pl-mono"
          onclick={() => act(action)}>{action.label}</button
        >
      {/each}
      <button
        type="button"
        class="demo-pill demo-pill-wide pl-mono"
        aria-pressed={held}
        onpointerdown={grab}
        onpointerup={release}
        onpointerleave={release}
        onpointercancel={release}
      >
        hold: drag a transparency slider
      </button>
      {#each AFTER_DRAG as action (action.label)}
        <button
          type="button"
          class="demo-pill demo-pill-wide pl-mono"
          onclick={() => act(action)}>{action.label}</button
        >
      {/each}
    </div>

    <div class="pl-history">
      <div class="pl-history-title">Edit &gt; Undo history</div>
      <ol class="pl-history-list">
        {#if !shown.length}
          <li class="pl-empty">nothing to undo</li>
        {/if}
        {#each shown as entry, index (entry.id)}
          <li class={index === 0 ? "pl-top" : ""}>
            <span>{entry.name}</span>
            <span class="pl-changes">{entry.changes.join(", ")}</span>
          </li>
        {/each}
      </ol>
      <button
        type="button"
        class="demo-pill demo-pill-wide"
        disabled={!history.length}
        onclick={undo}>Ctrl+Z</button
      >
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
