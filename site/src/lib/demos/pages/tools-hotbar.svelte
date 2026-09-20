<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import PageIcon from "$lib/components/PageIcon.svelte"

  type Where = "body" | "backpack" | "world"
  type Tool = { name: string; item: string; handle: boolean; drop: boolean; where: Where; slot: number }

  const CATALOGUE = [
    { name: "sword", item: "minecraft:iron_sword", handle: true, drop: true },
    { name: "torch", item: "minecraft:torch", handle: true, drop: false },
    { name: "map", item: "minecraft:filled_map", handle: false, drop: true },
    { name: "bow", item: "minecraft:bow", handle: true, drop: true },
  ]

  const SLOTS = [0, 1, 2, 3, 4, 5, 6, 7, 8]

  let tools = $state<Tool[]>(
    CATALOGUE.slice(0, 2).map((base, slot) => ({ ...base, where: "backpack" as Where, slot })),
  )
  let selected = $state(0)
  let added = $state(2)
  let chosen = $state("sword")
  let lines = $state<Line[]>([{ id: 0, text: "click a slot to select it, or add more tools" }])
  let source = $state("-- server")
  let next = 1

  const carried = $derived(tools.filter((tool) => tool.where !== "world"))

  const rows = $derived.by(() => {
    const held = heldTool()
    const list: { key: string; depth: number; icon: string; text: string; lit: boolean }[] = [
      { key: "alex", depth: 0, icon: "CharacterBody3D", text: "alex", lit: false },
    ]
    if (held) list.push({ key: "held", depth: 1, icon: "ToolSelect", text: `${describe(held)}   held`, lit: true })
    list.push({ key: "backpack", depth: 1, icon: "Folder", text: "backpack", lit: false })
    for (const tool of tools.filter((tool) => tool.where === "backpack")) {
      list.push({ key: `bag-${tool.name}`, depth: 2, icon: "ToolSelect", text: `${describe(tool)}   carried`, lit: false })
    }
    list.push({ key: "world", depth: 0, icon: "Node3D", text: "world", lit: false })
    for (const tool of tools.filter((tool) => tool.where === "world")) {
      list.push({ key: `world-${tool.name}`, depth: 1, icon: "ToolSelect", text: `${tool.name}   lying in the world`, lit: false })
    }
    return list
  })

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function heldTool() {
    return tools.filter((tool) => tool.where === "body")[0] ?? null
  }

  function inSlot(slot: number) {
    return tools.filter((tool) => tool.where !== "world" && tool.slot === slot)[0] ?? null
  }

  function freeSlot() {
    for (let slot = 0; slot < 9; slot++) if (!inSlot(slot)) return slot
    return -1
  }

  function unequip(tool: Tool, to: Where) {
    tool.where = to
    record(`unequipped fired on ${tool.name}`, "out")
  }

  function equip(tool: Tool) {
    const now = heldTool()
    if (now === tool) return
    if (now) unequip(now, "backpack")
    tool.where = "body"
    record(`equipped fired on ${tool.name}`, "in")
  }

  function describe(tool: Tool) {
    return tool.name + (tool.handle ? "" : "  (no Handle)") + (tool.drop ? "" : "  canBeDropped = false")
  }

  function press(slot: number) {
    selected = slot
    const there = inSlot(slot)
    if (there) {
      equip(there)
      source = `-- the player pressed ${slot + 1}: the slot's tool is equipped`
    } else {
      const now = heldTool()
      if (now) unequip(now, "backpack")
      source = `-- the player pressed ${slot + 1}: an empty slot unequips`
    }
  }

  function add() {
    const slot = freeSlot()
    if (slot === -1) return
    const base = CATALOGUE[added % CATALOGUE.length]
    const name = base.name + (added >= CATALOGUE.length ? String(Math.floor(added / CATALOGUE.length) + 1) : "")
    added += 1
    tools.push({ ...base, name, where: "backpack", slot })
    record(`${name} takes slot ${slot + 1}`)
    source =
      `body.backpack:add("Tool", { name = "${name}", item = "${base.item}" })` +
      (base.handle ? "" : "\n-- no Handle") +
      (base.drop ? "" : "\n-- canBeDropped = false")
  }

  function drop() {
    const held = heldTool()
    if (!held) {
      record("nothing is held: the key does nothing")
      return
    }
    if (!held.handle) {
      record(`${held.name} has no Handle: it is never dropped`)
      return
    }
    if (!held.drop) {
      record(`${held.name} has canBeDropped off: it stays in the hand`)
      return
    }
    unequip(held, "world")
    record(`${held.name} left its slot; its Handle lies 2 metres in front`)
    source = "-- the drop key, with canBeDropped on and a Handle"
  }

  function equipByName() {
    const tool = tools.filter((tool) => tool.name === chosen)[0]
    if (!tool) return
    equip(tool)
    selected = tool.slot
    source = `local tool = body.backpack:find("${tool.name}") :: Tool\nbody.humanoid:equipTool(tool)   -- the selection moves to its slot`
  }

  function unequipAll() {
    const held = heldTool()
    if (held) unequip(held, "backpack")
    else record("nothing is held: unequipTools does nothing")
    source = "body.humanoid:unequipTools()   -- the selection stays where it is"
  }

  $effect(() => {
    if (!carried.some((tool) => tool.name === chosen)) chosen = carried[0]?.name ?? ""
  })
</script>

<Demo label="Carried, held and the hotbar">
  <div class="tools-bar">
    {#each SLOTS as slot (slot)}
      {@const tool = inSlot(slot)}
      <button
        type="button"
        class="tools-slot"
        class:selected={slot === selected}
        class:holding={tool?.where === "body"}
        onclick={() => press(slot)}
      >
        <span class="tools-slot-key">{slot + 1}</span>
        <span class="tools-slot-name">{tool ? tool.name : ""}</span>
      </button>
    {/each}
  </div>

  <ul class="demo-tree tools-tree">
    {#each rows as row (row.key)}
      <li class="demo-tree-row tools-row" class:hit={row.lit} style="padding-left: {12 + row.depth * 18}px">
        <PageIcon icon="e:{row.icon}" />
        <span>{row.text}</span>
      </li>
    {/each}
  </ul>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={add}>backpack:add("Tool")</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={drop}>press the drop key</button>
  </div>

  <div class="demo-controls demo-row">
    <select class="demo-select" aria-label="tool" bind:value={chosen}>
      {#each carried as tool (tool.name)}
        <option value={tool.name}>{tool.name}</option>
      {/each}
    </select>
    <button type="button" class="demo-pill demo-pill-wide" onclick={equipByName}>humanoid:equipTool(tool)</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={unequipAll}>humanoid:unequipTools()</button>
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
