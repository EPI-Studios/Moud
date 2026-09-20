<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  const INVENTORY = "minecraft:iron_pickaxe"

  let kind = $state("player")
  let rightItem = $state("minecraft:diamond_sword")
  let override = $state("")

  const shown = $derived.by(() => {
    if (override !== "") return override === "minecraft:air" ? "an empty hand" : override
    if (kind === "player") return INVENTORY
    return rightItem === "" ? "an empty hand" : rightItem
  })

  const why = $derived.by(() => {
    if (override !== "") return "rightItemOverride is set, so it wins over everything else."
    if (kind === "player") return "A player's body shows what is in their own inventory. rightItem is ignored."
    return "A body the place owns draws its rightItem."
  })

  const source = $derived.by(() => {
    const name = kind === "player" ? "body" : "npc"
    const lines = []
    if (kind === "player") lines.push(`-- the player is holding ${INVENTORY}`)
    lines.push(`${name}.rightItem = "${rightItem}"`)
    lines.push(`${name}.rightItemOverride = "${override}"`)
    return lines.join("\n")
  })

  const quoted = (option: string) => (option === "" ? '""' : option)
</script>

<Demo label="What the right hand draws">
  <div class="rendering-held">
    <div class="rendering-held-hand">
      <span class="rendering-held-label">right hand draws</span>
      <span class="rendering-held-name">{shown}</span>
      <span class="rendering-held-why">{why}</span>
    </div>
  </div>

  <div class="demo-controls">
    <Choice label="body" options={["player", "npc"]} bind:value={kind} />
    <Choice
      label="rightItem"
      options={["minecraft:diamond_sword", "minecraft:torch", ""]}
      labelOf={quoted}
      bind:value={rightItem}
    />
    <Choice
      label="rightItemOverride"
      options={["", "minecraft:blaze_rod", "minecraft:air"]}
      labelOf={quoted}
      bind:value={override}
    />
  </div>

  <CodePanel {source} />
</Demo>
