<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"
  import { palette, type Palette } from "../core/surface"

  const SIZE = 200

  const HOMES: Record<string, { run: boolean; path: string }> = {
    "game.world": { run: true, path: "game.world" },
    "arena (Model)": { run: true, path: "game.world.arena" },
    ServerStorage: { run: false, path: "game.world.ServerStorage" },
    ReplicatedStorage: { run: false, path: "game.world.ReplicatedStorage" },
    StarterPack: { run: false, path: "game.world.StarterPack" },
    StarterCharacterScripts: { run: false, path: "game.world.StarterCharacterScripts" },
  }

  const OWNED = [
    { icon: "Signal", text: "game.stepped connection" },
    { icon: "Loop", text: "task.every thread" },
    { icon: "Animation", text: "tween on the platform" },
  ]

  let canvas = $state<HTMLCanvasElement | null>(null)
  let ctx: CanvasRenderingContext2D | null = null
  let colors: Palette | null = null
  let angle = 0

  let home = $state("arena (Model)")
  let enabled = $state("true")
  let source = $state(
    'arena:add("Script", { name = "spinner", source = "res://server/spinner.luau" })',
  )
  let lines = $state<Line[]>([{ id: 0, text: "move the script or switch it off" }])
  let nextLine = 1

  const running = $derived(enabled === "true" && HOMES[home].run)

  let lastHome = untrack(() => home)
  let lastEnabled = untrack(() => enabled)
  let wasRunning = true

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text, kind }, ...lines].slice(0, 4)
  }

  function draw(spinning: boolean) {
    const c = colors
    if (!ctx || !c) return
    ctx.clearRect(0, 0, SIZE, SIZE)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, SIZE, SIZE)
    ctx.save()
    ctx.translate(100, 100)
    ctx.rotate(angle)
    ctx.fillStyle = spinning ? c.bg4 : c.bg3
    ctx.fillRect(-60, -14, 120, 28)
    ctx.strokeStyle = spinning ? c.main : c.light
    ctx.lineWidth = 1.5
    ctx.strokeRect(-60, -14, 120, 28)
    ctx.restore()
    ctx.beginPath()
    ctx.arc(100, 100, 3, 0, Math.PI * 2)
    ctx.fillStyle = c.muted
    ctx.fill()
  }

  function fit() {
    if (!canvas || !ctx) return
    const width = canvas.clientWidth || SIZE
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(width * ratio))
    ctx.setTransform(canvas.width / SIZE, 0, 0, canvas.height / SIZE, 0, 0)
    draw(running)
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    ctx = node.getContext("2d")
    colors = palette()
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    draw(running)
  })

  $effect(() => {
    const at = home
    const on = enabled
    if (at === lastHome && on === lastEnabled) return
    const line = at !== lastHome ? `spinner.parent = ${HOMES[at].path}` : `spinner.enabled = ${on}`
    lastHome = at
    lastEnabled = on
    const now = on === "true" && HOMES[at].run
    if (now && !wasRunning) record("spinner runs", "in")
    else if (!now && wasRunning) {
      record("spinner stops, with everything it connected or scheduled", "out")
    } else if (now) record("spinner keeps running where it is now")
    else record("spinner is still only kept")
    wasRunning = now
    source = line
  })

  let last = 0
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      if (!running) return
      angle += dt * 1.6
      draw(running)
    },
  )
</script>

<Demo label="Scripts as instances">
  <div class="place-script-stage">
    <canvas
      bind:this={canvas}
      class="place-spin"
      aria-label="a part the spinner script turns"
      style="display: block; width: 100%; height: auto; border: 1px solid var(--line-2); border-radius: 6px; aspect-ratio: 1 / 1"
    ></canvas>
    <div class="place-script-side">
      <div class="place-status">
        <img class="engine-icon" src="/art/icons/Script.png" alt="" width="16" height="16" />
        <span class="place-status-text">spinner {running ? "is running" : "is not running"}</span>
      </div>
      <ul class="demo-tree">
        {#each OWNED as entry (entry.icon)}
          <li class="demo-tree-row" class:hit={running}>
            <img class="engine-icon" src="/art/icons/{entry.icon}.png" alt="" width="14" height="14" />
            {entry.text}{running ? "" : "  (stopped)"}
          </li>
        {/each}
      </ul>
    </div>
  </div>

  <div class="demo-controls">
    <Choice label="parent" options={Object.keys(HOMES)} bind:value={home} />
    <Choice label="enabled" options={["true", "false"]} bind:value={enabled} />
  </div>

  <Log {lines} keep={4} />
  <CodePanel {source} />
</Demo>
