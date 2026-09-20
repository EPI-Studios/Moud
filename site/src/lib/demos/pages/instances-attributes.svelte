<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  const HANDLER = `door:getAttributeChangedSignal("locked"):connect(function()
    local locked = door:getAttribute("locked")
    door.color = if locked then color(0.8, 0.2, 0.2) else color(0.3, 0.8, 0.3)
end)`

  let attrs = $state<Record<string, boolean | number>>({ locked: true })
  let source = $state(HANDLER)
  let lines = $state<Line[]>([{ id: 0, text: "only the handler on the attribute writes the colour" }])
  let nextLine = 1

  const locked = $derived(attrs.locked === true)
  const table = $derived.by(() => {
    const keys = Object.keys(attrs)
    const body = keys.length
      ? `{ ${keys.map((key) => `${key} = ${attrs[key]}`).join(", ")} }`
      : "{}"
    return `door:getAttributes()\n${body}`
  })

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 5)
  }

  function set(name: string, value: boolean | number | null) {
    const old = name in attrs ? attrs[name] : null
    if (old === value) {
      record("the value is the one it already holds: nothing fires")
      return
    }
    if (value === null) delete attrs[name]
    else attrs[name] = value
    record(`attributeChanged fired with "${name}"`, "in")
    if (name === "locked") {
      record('getAttributeChangedSignal("locked") fired, and its handler recoloured the door', "in")
    }
  }

  const ACTIONS: { label: string; code: string; run: () => void }[] = [
    {
      label: "prompt triggered",
      code: 'door:setAttribute("locked", not door:getAttribute("locked"))',
      run: () => set("locked", !locked),
    },
    {
      label: "another script",
      code: 'door:setAttribute("locked", true)',
      run: () => set("locked", true),
    },
    { label: "worth 20", code: 'door:setAttribute("worth", 20)', run: () => set("worth", 20) },
    {
      label: "remove worth",
      code: 'door:setAttribute("worth", nil)',
      run: () => set("worth", null),
    },
    {
      label: "a table",
      code: 'door:setAttribute("keys", { "gold" })',
      run: () => record("error: an attribute cannot hold a table", "out"),
    },
  ]

  function perform(action: (typeof ACTIONS)[number]) {
    source = action.code
    action.run()
  }
</script>

<Demo label="Attributes">
  <div class="instances-attr-stage">
    <div class="instances-door" style="background: {locked ? 'var(--red)' : 'var(--green)'}">
      {locked ? "locked" : "open"}
    </div>
    <CodePanel source={table} class="instances-attr-table" />
  </div>

  <div class="demo-controls demo-row">
    {#each ACTIONS as action (action.label)}
      <button type="button" class="demo-pill demo-pill-wide" onclick={() => perform(action)}>
        {action.label}
      </button>
    {/each}
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
