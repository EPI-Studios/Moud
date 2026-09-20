<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Returns = "nothing" | '"sink"' | '"pass"'
  type Binding = {
    name: string
    priority: number
    keys: string[]
    returns: Returns
    bound: boolean
    order: number
    call: "bindAction" | "bindActionAtPriority"
  }

  const RETURNS: Returns[] = ["nothing", '"sink"', '"pass"']
  const MINECRAFT: Record<string, string> = {
    e: "opens the inventory",
    space: "jumps",
    leftshift: "sneaks",
  }

  let bindings = $state<Binding[]>([
    { name: "dash", priority: 0, keys: ["leftshift"], returns: "nothing", bound: true, order: 1, call: "bindAction" },
    { name: "menu", priority: 0, keys: ["e"], returns: '"sink"', bound: true, order: 2, call: "bindAction" },
    { name: "countJumps", priority: 0, keys: ["space"], returns: '"pass"', bound: true, order: 3, call: "bindAction" },
    {
      name: "cutscene",
      priority: 100,
      keys: ["e", "space", "leftshift"],
      returns: "nothing",
      bound: false,
      order: 4,
      call: "bindActionAtPriority",
    },
  ])
  let chat = $state(false)
  let lines = $state<Line[]>([{ id: 0, text: "press a key to see who hears it", kind: "idle" }])
  let nextLine = 1

  const ordered = $derived(
    bindings
      .filter((binding) => binding.bound)
      .sort((a, b) => (a.priority !== b.priority ? b.priority - a.priority : b.order - a.order)),
  )

  const source = $derived.by(() => {
    const rows = bindings
      .filter((binding) => binding.bound)
      .map((binding) => {
        const fn = `function(name, state)${binding.returns === "nothing" ? " end" : ` return ${binding.returns} end`}`
        const keys = binding.keys.map(quote).join(", ")
        return binding.call === "bindAction"
          ? `input:bindAction(${quote(binding.name)}, ${fn}, ${keys})`
          : `input:bindActionAtPriority(${quote(binding.name)}, ${fn}, ${binding.priority}, ${keys})`
      })
    rows.push("")
    rows.push(`input:getBoundActions()   -- { ${ordered.map((binding) => quote(binding.name)).join(", ")} }`)
    return rows.join("\n")
  })

  function quote(value: string) {
    return '"' + value.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"'
  }

  function press(key: string) {
    const out: Line[] = []
    const say = (text: string, kind: Line["kind"] = "idle") => out.push({ id: nextLine++, text, kind })
    if (chat) {
      say(`gameProcessed is true: the chat had ${key} first, so no binding hears it`, "out")
      say("the chat types it")
      lines = out
      return
    }
    let sunk = false
    for (const binding of ordered) {
      if (sunk || !binding.keys.includes(key)) continue
      const sinks = binding.returns !== '"pass"'
      say(
        `${binding.name} hears begin and returns ${binding.returns}${sinks ? ": the key stops here" : ": the next one hears it"}`,
        sinks ? "out" : "in",
      )
      if (sinks) sunk = true
    }
    if (sunk) say(`Minecraft never sees this press of ${key}`)
    else say(`Minecraft sees ${key} and ${MINECRAFT[key]}`, "in")
    lines = out
  }

  function toggle(binding: Binding) {
    binding.bound = !binding.bound
    if (binding.bound) binding.order = Math.max(...bindings.map((other) => other.order)) + 1
  }
</script>

<Demo label="Bound actions">
  <div class="cam-bindings">
    {#each bindings as binding (binding.name)}
      <div class="cam-binding" class:off={!binding.bound}>
        <button
          type="button"
          class="demo-pill"
          aria-pressed={binding.bound}
          onclick={() => toggle(binding)}
        >
          {binding.bound ? "bound" : "unbound"}
        </button>
        <span class="cam-binding-name">{binding.name}</span>
        <span class="cam-binding-meta">priority {binding.priority} · {binding.keys.join(", ")}</span>
        <select class="demo-select" bind:value={binding.returns}>
          {#each RETURNS as option (option)}
            <option value={option}>returns {option}</option>
          {/each}
        </select>
      </div>
    {/each}
  </div>

  <div class="demo-controls demo-row">
    {#each ["e", "space", "leftshift"] as key (key)}
      <button type="button" class="demo-pill demo-pill-wide" onclick={() => press(key)}>press {key}</button>
    {/each}
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      aria-pressed={chat}
      onclick={() => (chat = !chat)}
    >
      chat open
    </button>
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
