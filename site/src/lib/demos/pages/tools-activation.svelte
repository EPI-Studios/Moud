<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  let enabled = $state<"true" | "false">("true")
  let manual = $state<"false" | "true">("false")
  let where = $state<"held" | "in the backpack">("held")
  let screen = $state<"closed" | "open">("closed")
  let lines = $state<Line[]>([{ id: 0, text: "try a click and a scripted activate with each setting" }])
  let next = 1

  const source = $derived(
    `tool.enabled = ${enabled === "true"}\ntool.manualActivationOnly = ${manual === "true"}\n\ntool.activated:connect(function(tool) end)`,
  )

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function click() {
    const why =
      where !== "held"
        ? "the tool is not held"
        : screen === "open"
          ? "a Minecraft screen is open"
          : enabled === "false"
            ? "enabled is off"
            : manual === "true"
              ? "manualActivationOnly is on"
              : null
    if (why) record(`click: nothing fires, ${why}`, "out")
    else record("click: activated fires, on the server and in the holder's LocalScripts", "in")
  }

  function activate() {
    const why = where !== "held" ? "the tool is not held" : enabled === "false" ? "enabled is off" : null
    if (why) record(`tool:activate(): nothing fires, ${why}`, "out")
    else record("tool:activate(): activated fires, on the side that called it", "in")
  }
</script>

<Demo label="What fires activated">
  <div class="demo-controls tools-top">
    <Choice label="enabled" options={["true", "false"] as const} bind:value={enabled} />
    <Choice label="manualActivationOnly" options={["false", "true"] as const} bind:value={manual} />
    <Choice label="the tool is" options={["held", "in the backpack"] as const} bind:value={where} />
    <Choice label="chat or inventory" options={["closed", "open"] as const} bind:value={screen} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={click}>the holder clicks</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={activate}>tool:activate()</button>
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
