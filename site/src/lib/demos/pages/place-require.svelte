<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Side = "server" | "client"

  const FILES: Record<string, string> = {
    "@shared/palette": "shared/palette.luau",
    "res://shared/palette.luau": "shared/palette.luau",
    "@server/economy": "server/economy.luau",
    "@client/hud": "client/hud.luau",
  }

  const PATHS: Record<Side, string[]> = {
    server: ["@shared/palette", "res://shared/palette.luau", "@server/economy"],
    client: [
      "@shared/palette",
      "res://shared/palette.luau",
      "@client/hud",
      "@server/economy",
    ],
  }

  const SIDES: Side[] = ["server", "client"]

  let side = $state<Side>("server")
  let path = $state("@shared/palette")
  let loaded = $state<Record<Side, Record<string, number>>>({ server: {}, client: {} })
  let lines = $state<Line[]>([{ id: 0, text: "both forms of the palette path name the same file" }])
  let nextLine = 1

  const options = $derived(PATHS[side])
  const source = $derived(`-- ${side}/main.luau\nlocal module = require("${path}")`)

  $effect(() => {
    if (!options.includes(path)) path = options[0]
  })

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 5)
  }

  function run() {
    const file = FILES[path]
    if (side === "client" && file.startsWith("server/")) {
      record("error: a client script may not require server/", "out")
      return
    }
    const here = loaded[side]
    if (!here[file]) {
      here[file] = 1
      record(`${file} runs on the ${side} and returns its value`, "in")
    } else {
      here[file] += 1
      record(`${file} is already loaded on the ${side}: the same value comes back`)
    }
  }

  function save() {
    loaded = { server: {}, client: {} }
    record("the place reloads, and every module runs again when it is next required")
  }
</script>

<Demo label="require">
  <div class="place-sides">
    {#each SIDES as name (name)}
      <div class="place-side">
        <span class="place-side-name">{name}</span>
        <ul class="place-side-list">
          {#each Object.entries(loaded[name]) as [file, calls] (file)}
            <li class="place-side-item">
              <span>{file}</span>
              <span class="place-side-count"
                >ran once, {calls} {calls === 1 ? "caller" : "callers"}</span
              >
            </li>
          {:else}
            <li class="place-side-empty">nothing loaded yet</li>
          {/each}
        </ul>
      </div>
    {/each}
  </div>

  <div class="demo-controls">
    <Choice label="script on the" options={SIDES} bind:value={side} />
    <Choice label="path" {options} bind:value={path} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={run}>run require</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={save}>save a module</button>
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
