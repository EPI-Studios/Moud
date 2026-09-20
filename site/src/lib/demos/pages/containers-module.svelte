<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Parent = { path: string; yes?: boolean; never?: boolean }

  const PARENTS: Record<string, Parent> = {
    "a LocalScript": { path: "hud", yes: true },
    ReplicatedStorage: { path: "game.world.ReplicatedStorage", yes: true },
    StarterGui: { path: "game.world.StarterGui", yes: true },
    StarterPlayerScripts: { path: "game.world.StarterPlayerScripts", yes: true },
    StarterCharacterScripts: { path: "game.world.StarterCharacterScripts", yes: true },
    ServerStorage: { path: "game.world.ServerStorage", never: true },
    ServerScriptService: { path: "game.world.ServerScriptService", never: true },
    "game.world": { path: "game.world" },
  }

  const SOURCES = ["none", "client/", "shared/", "server/"]

  let parent = $state("ReplicatedStorage")
  let from = $state("none")

  const holder = $derived(PARENTS[parent])

  const crosses = $derived(
    holder.never ? false : holder.yes ? true : from === "client/" || from === "shared/",
  )

  const why = $derived(
    holder.never
      ? "Inside ServerStorage or ServerScriptService it never crosses, whatever else is true."
      : holder.yes
        ? `Inside ${parent}, the module can be the clients'.`
        : crosses
          ? `Its source is a file under ${from}, so the module can be the clients'.`
          : "Anywhere else it stays on the server.",
  )

  const source = $derived.by(() => {
    let props = '\n    name = "prices",\n    code = "return { sword = 50 }",'
    if (from !== "none") props += `\n    source = "res://${from}prices.luau",`
    const lead = parent === "a LocalScript" ? "-- hud is a LocalScript\n" : ""
    return `${lead}${holder.path}:add("ModuleScript", {${props}\n})`
  })
</script>

<Demo label="Does a module's code reach clients">
  <div class="containers-result {crosses ? 'yes' : 'no'}">
    <span class="containers-result-value"
      >{crosses ? "code reaches clients" : "code stays on the server"}</span
    >
    <span class="containers-result-why">{why}</span>
  </div>

  <div class="demo-controls">
    <Choice label="parent" options={Object.keys(PARENTS)} bind:value={parent} />
    <Choice label="source under" options={SOURCES} bind:value={from} />
  </div>

  <CodePanel {source} />
</Demo>
