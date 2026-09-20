<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log, { type Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"

  type Name = "A" | "B"
  type Body = { name: Name; x: number; y: number; dead: boolean; color: string }

  const W = 440
  const H = 260
  const SCALE = 8
  const CRATE = { x: 220, y: 130 }
  const MODES = ["automatic", "setNetworkOwner(a)", "setNetworkOwner(nil)"] as const
  const TEXT = "fill:var(--text-muted);font-family:var(--font-mono)"

  let players = $state<Body[]>([
    { name: "A", x: 70, y: 130, dead: false, color: "var(--blue)" },
    { name: "B", x: 370, y: 60, dead: false, color: "var(--green)" },
  ])
  let mode = $state<(typeof MODES)[number]>("automatic")
  let owner = $state<Name | null>(null)
  let lines = $state<Line[]>([{ id: 0, text: "drag A or B across the rings", kind: "idle" }])
  let node = $state<SVGSVGElement | null>(null)
  let dragging: Body | null = null
  let nextLine = 1

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function body(name: Name) {
    return players.find((p) => p.name === name) as Body
  }

  function distance(name: Name) {
    const p = body(name)
    return Math.hypot(p.x - CRATE.x, p.y - CRATE.y) / SCALE
  }

  function record(message: string, kind: Line["kind"]) {
    lines = [{ id: nextLine++, text: message, kind }, ...lines].slice(0, 4)
  }

  function decide(): { owner: Name | null; why: string } {
    if (mode === "setNetworkOwner(a)")
      return { owner: "A", why: "a script chose A, however far A walks" }
    if (mode === "setNetworkOwner(nil)")
      return { owner: null, why: "a script kept it on the server" }
    if (owner && body(owner).dead)
      return { owner, why: `${owner} keeps it: still connected, waiting to respawn` }
    if (owner && distance(owner) <= 14) return { owner, why: `${owner} keeps it: within 14 m` }
    let best: Name | null = null
    for (const p of players) {
      if (p.dead || distance(p.name) > 10) continue
      if (!best || distance(p.name) < distance(best)) best = p.name
    }
    if (best) return { owner: best, why: `${best} takes it: the nearest living body within 10 m` }
    return { owner: null, why: "the server takes it: nobody within 10 m" }
  }

  function tick() {
    const next = decide()
    if (next.owner === owner) return
    owner = next.owner
    record(next.why, next.owner ? "in" : "out")
  }

  const source = $derived.by(() => {
    const head =
      mode === "setNetworkOwner(a)"
        ? "crate:setNetworkOwner(a)"
        : mode === "setNetworkOwner(nil)"
          ? "crate:setNetworkOwner(nil)"
          : "crate:setNetworkOwnershipAuto()"
    return (
      `${head}\n` +
      `print(crate:isNetworkOwnershipAuto())   -- ${mode === "automatic"}\n` +
      `print(crate.networkOwner)               -- ${owner ? `${owner}'s id` : '"", the server'}\n` +
      `\n-- A at ${num(distance("A"), 1)} m, B at ${num(distance("B"), 1)} m`
    )
  })

  const fill = $derived(owner ? body(owner).color : "var(--bg-4)")
</script>

<Demo label="Who simulates the crate">
  <Stage>
    <svg
      bind:this={node}
      viewBox="0 0 {W} {H}"
      class="demo-plan demo-plan-wide"
      role="img"
      aria-label="top down view of a crate and two players"
      onpointerdown={(event) => {
        if (!node) return
        const p = pointIn(node, event, W, H)
        let best: { body: Body; d: number } | null = null
        for (const player of players) {
          const d = Math.hypot(player.x - p.x, player.y - p.y)
          if (d < 40 && (!best || d < best.d)) best = { body: player, d }
        }
        if (!best) return
        dragging = best.body
        node.setPointerCapture(event.pointerId)
      }}
      onpointermove={(event) => {
        if (!dragging || !node) return
        const p = pointIn(node, event, W, H)
        dragging.x = clamp(p.x, 12, W - 12)
        dragging.y = clamp(p.y, 12, H - 12)
        tick()
      }}
      onpointerup={() => (dragging = null)}
      onpointercancel={() => (dragging = null)}
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      <circle
        cx={CRATE.x}
        cy={CRATE.y}
        r={14 * SCALE}
        fill="none"
        stroke-dasharray="2 5"
        style="stroke:var(--text-light)"
      />
      <circle
        cx={CRATE.x}
        cy={CRATE.y}
        r={10 * SCALE}
        fill="none"
        stroke-dasharray="5 4"
        style="stroke:var(--text-muted)"
      />
      <text
        x={CRATE.x}
        y={CRATE.y - 10 * SCALE + 14}
        font-size="10.5"
        text-anchor="middle"
        style={TEXT}>10 m</text
      >
      <text
        x={CRATE.x}
        y={CRATE.y - 14 * SCALE + 14}
        font-size="10.5"
        text-anchor="middle"
        style={TEXT}>14 m</text
      >
      <rect
        x={CRATE.x - 9}
        y={CRATE.y - 9}
        width="18"
        height="18"
        stroke-width="1.5"
        style="stroke:var(--text-main);fill:{fill}"
      />
      {#each players as player (player.name)}
        <g>
          <line
            x1={player.x}
            y1={player.y}
            x2={CRATE.x}
            y2={CRATE.y}
            stroke-dasharray="3 3"
            style="stroke:var(--text-light)"
            opacity={owner === player.name ? 1 : 0}
          />
          <circle
            cx={player.x}
            cy={player.y}
            r="11"
            stroke-width="1.5"
            style={player.dead
              ? "fill:var(--bg-3);stroke:var(--text-light)"
              : `fill:${player.color};stroke:var(--text-main)`}
          />
          <text
            x={player.x}
            y={player.y + 4}
            font-size="11"
            text-anchor="middle"
            style="font-family:var(--font-mono);font-weight:700;fill:{player.dead
              ? 'var(--text-light)'
              : '#111'}">{player.name}</text
          >
        </g>
      {/each}
    </svg>
  </Stage>

  <div class="demo-controls">
    <Choice
      label="owner"
      options={MODES}
      bind:value={
        () => mode,
        (next) => {
          mode = next
          tick()
        }
      }
    />
    <div class="demo-choice">
      <span class="demo-slider-name">bodies</span>
      <div class="demo-choice-buttons">
        {#each players as player (player.name)}
          <button
            type="button"
            class="demo-pill"
            aria-pressed={player.dead}
            onclick={() => {
              player.dead = !player.dead
              tick()
            }}
          >
            {player.name} is dead
          </button>
        {/each}
      </div>
    </div>
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
