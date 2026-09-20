<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  const HEAD = "-- lamp is a Part inside the Model cart"

  let handlers = $state([
    { key: "changed", code: "lamp.changed:connect(function(property) end)", count: 0, flashes: 0 },
    { key: "size", code: 'lamp:getPropertyChangedSignal("size"):connect(...)', count: 0, flashes: 0 },
    {
      key: "worldCframe",
      code: 'lamp:getPropertyChangedSignal("worldCframe"):connect(...)',
      count: 0,
      flashes: 0,
    },
  ])

  let source = $state(HEAD)
  let lines = $state<Line[]>([
    { id: 0, text: "each button runs one line against the lamp" },
  ])
  let nextLine = 1

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 5)
  }

  function fire(key: string, text: string) {
    const handler = handlers.find((entry) => entry.key === key)
    if (!handler) return
    handler.count += 1
    handler.flashes += 1
    record(text, "in")
  }

  const ACTIONS: { code: string; run: () => void }[] = [
    {
      code: "lamp.color = color(1, 0.8, 0.3)",
      run: () => fire("changed", 'changed fired with "color"'),
    },
    {
      code: "lamp.size = vec3(1, 2, 1)",
      run: () => {
        fire("changed", 'changed fired with "size"')
        fire("size", "the size signal fired, with nothing")
      },
    },
    {
      code: "cart.cframe = cframe(4, 65, 0)",
      run: () =>
        fire("worldCframe", "worldCframe fired: an ancestor moved, nobody wrote to the lamp"),
    },
    {
      code: 'lamp:getPropertyChangedSignal("sise")',
      run: () =>
        record('error: "sise" is not a name a Part has, caught the moment the line runs', "out"),
    },
  ]

  function perform(action: (typeof ACTIONS)[number]) {
    source = `${HEAD}\n${action.code}`
    action.run()
  }
</script>

<Demo label="Signals">
  <div class="instances-cards">
    {#each handlers as handler (handler.key)}
      {#key handler.flashes}
        <div class="instances-card" class:flash={handler.flashes > 0}>
          <code class="instances-card-code">{handler.code}</code>
          <span class="instances-card-count"
            >fired {handler.count} {handler.count === 1 ? "time" : "times"}</span
          >
        </div>
      {/key}
    {/each}
  </div>

  <div class="demo-controls demo-row">
    {#each ACTIONS as action (action.code)}
      <button type="button" class="demo-pill demo-pill-wide" onclick={() => perform(action)}>
        {action.code}
      </button>
    {/each}
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
