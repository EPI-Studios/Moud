<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"

  type Rule = { sent: boolean; runs: boolean; inWorld: boolean; also: string }
  type Row = { depth: number; klass: string; name: string; lit: boolean }

  const ICONS: Record<string, string> = {
    World: "Node3D",
    Folder: "Folder",
    Part: "MeshInstance3D",
    Script: "Script",
    LocalScript: "Script",
    ModuleScript: "Script",
    Character: "CharacterBody3D",
    ServerStorage: "Folder",
    ServerScriptService: "Script",
    ReplicatedStorage: "Folder",
    StarterPack: "Folder",
    StarterCharacterScripts: "Script",
    StarterGui: "CanvasLayer",
    StarterPlayerScripts: "Script",
    Backpack: "Folder",
  }

  const RULES: Record<string, Rule> = {
    "no container": {
      sent: true,
      runs: true,
      inWorld: true,
      also: "game.world with no container on the way: everything happens",
    },
    ServerStorage: { sent: false, runs: false, inWorld: false, also: "" },
    ServerScriptService: { sent: false, runs: true, inWorld: false, also: "only Scripts run here" },
    ReplicatedStorage: { sent: true, runs: false, inWorld: false, also: "" },
    StarterPack: {
      sent: true,
      runs: false,
      inWorld: false,
      also: "its tools are copied into every new body's backpack",
    },
    StarterCharacterScripts: {
      sent: true,
      runs: false,
      inWorld: false,
      also: "its children are copied into every new body",
    },
    StarterGui: { sent: true, runs: true, inWorld: true, also: "an ordinary container" },
    StarterPlayerScripts: { sent: true, runs: true, inWorld: true, also: "an ordinary container" },
    Backpack: { sent: true, runs: true, inWorld: false, also: "every body has one" },
  }

  let container = $state("ServerStorage")
  let levels = $state(1)

  const rule = $derived(RULES[container])

  const plan = $derived.by(() => {
    const rows: Row[] = []
    let depth = 0
    let path = "game.world"
    rows.push({ depth: depth++, klass: "World", name: "world", lit: false })
    if (container === "Backpack") {
      rows.push({ depth: depth++, klass: "Character", name: "alex", lit: false })
      rows.push({ depth: depth++, klass: "Backpack", name: "backpack", lit: false })
      path = "body.backpack"
    } else if (container !== "no container") {
      rows.push({ depth: depth++, klass: container, name: container, lit: false })
      path = `game.world.${container}`
    }
    for (let i = 1; i < levels; i++) {
      rows.push({ depth: depth++, klass: "Folder", name: `level${i + 1}`, lit: false })
      path += `.level${i + 1}`
    }
    rows.push({ depth, klass: "Part", name: "coin", lit: true })
    rows.push({ depth: depth + 1, klass: "Script", name: "collect", lit: true })
    return { rows, path }
  })

  const cells = $derived([
    {
      title: "Sent to players",
      yes: rule.sent,
      detail: rule.sent ? "a client's mirror holds the coin" : "no client can find, require or read it",
    },
    {
      title: "Scripts inside run",
      yes: rule.runs,
      detail: rule.runs ? "collect runs" : "collect is only kept",
    },
    {
      title: "Parts inside are in the world",
      yes: rule.inWorld,
      detail: rule.inWorld ? "drawn, collides, touched" : "not drawn, no collision, no touch",
    },
  ])

  const note = $derived(
    (levels > 1 ? `The coin is ${levels} levels down, and the rule still holds. ` : "") +
      (rule.also ? `Also: ${rule.also}.` : ""),
  )

  const source = $derived(
    `local coin = ${plan.path}.coin
coin.color = color(1, 0.8, 0.2)       -- works wherever it sits
local copy = coin:clone(game.world)   -- the copy is in the world`,
  )
</script>

<Demo label="Where it sits">
  <div class="demo-controls containers-top">
    <Choice label="container" options={Object.keys(RULES)} bind:value={container} />
    <Slider label="levels down" min={1} max={10} step={1} bind:value={levels} />
  </div>

  <div class="containers-stage">
    <ul class="demo-tree">
      {#each plan.rows as row, index (index)}
        <li
          class="demo-tree-row containers-row"
          class:hit={row.lit}
          style="padding-left: {12 + row.depth * 18}px"
        >
          <img
            class="engine-icon"
            src="/art/icons/{ICONS[row.klass] ?? 'Node3D'}.png"
            alt=""
            width="14"
            height="14"
          />
          <span>{row.name}</span>
          <span class="containers-class">{row.klass}</span>
        </li>
      {/each}
    </ul>
    <div class="containers-cells">
      {#each cells as cell (cell.title)}
        <div class="containers-cell {cell.yes ? 'yes' : 'no'}">
          <span class="containers-cell-title">{cell.title}</span>
          <span class="containers-cell-value">{cell.yes ? "yes" : "no"}</span>
          <span class="containers-cell-detail">{cell.detail}</span>
        </div>
      {/each}
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
