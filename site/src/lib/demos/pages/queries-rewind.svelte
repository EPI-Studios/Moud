<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Log from "../ui/Log.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"

  type Ghost = { id: number; x: number; y: number; label: string; hit: boolean }

  const W = 440
  const H = 200
  const PX = 20
  const AIM = 220
  const WIDTH = 1
  const LANES = [
    { y: 34, label: "THE PLAYER'S SCREEN" },
    { y: 128, label: "THE SERVER, NOW" },
  ]

  let view = $state<SVGSVGElement | null>(null)
  let rtt = $state(0.2)
  let drawDelay = $state(0.1)
  let t = $state(0)
  let ghosts = $state<Ghost[]>([])
  let lines = $state<Line[]>([
    { id: 0, text: "shoot when the target on the player's screen crosses the crosshair", kind: "idle" },
  ])

  let next = 1
  let ghostId = 0
  let shot: number | null = null

  function num(value: number, places = 1) {
    const fixed = value.toFixed(places)
    return fixed.includes(".") ? fixed.replace(/0+$/, "").replace(/\.$/, "") : fixed
  }

  function position(time: number) {
    return AIM + 120 * Math.sin((time * 2 * Math.PI) / 3.6)
  }

  const behind = $derived(rtt / 2 + drawDelay)

  const source = $derived(
    "shoot.onServer:connect(function(body, from, direction)\n" +
      `    local seen = game.history:viewTime(body)   -- now - ${num(rtt, 2)} - ${num(drawDelay, 2)}\n` +
      "    local hit = game.history:rewind(seen, function()\n" +
      "        return game.world:raycast(from, direction, 100, { exclude = { body } })\n" +
      "    end)\n" +
      "end)",
  )

  function near(x: number) {
    return Math.abs(x - AIM) <= (WIDTH * PX) / 2
  }

  function shoot() {
    const at = t
    const arrive = at + rtt / 2
    const viewTime = arrive - rtt - drawDelay
    const saw = position(at - behind)
    const now = position(arrive)
    const back = position(viewTime)

    ghosts = [
      { id: ghostId++, x: now, y: 138, label: "not rewound", hit: near(now) },
      { id: ghostId++, x: back, y: 138, label: "rewound", hit: near(back) },
    ]
    shot = 1.8

    const missed = near(now) ? "hit" : `miss, ${num(Math.abs(now - AIM) / PX)} m off`
    lines = [
      {
        id: next++,
        text:
          `the player saw it ${num(Math.abs(saw - AIM) / PX)} m off the crosshair. Not rewound: ${missed}` +
          `. Rewound ${num(rtt + drawDelay, 2)} s: ${near(back) ? "hit" : "miss"}`,
        kind: near(back) ? "in" : "out",
      },
      ...lines,
    ]
  }

  let last: number | null = null
  whileVisible(
    () => view,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.05, (now - last) / 1000)
      last = now
      t += dt
      if (shot !== null) {
        shot -= dt
        if (shot <= 0) {
          shot = null
          ghosts = []
        }
      }
    },
  )
</script>

<Demo label="Rewinding a shot">
  <Stage>
    <svg
      bind:this={view}
      class="qry-view"
      viewBox="0 0 {W} {H}"
      role="img"
      aria-label="the target as the player sees it and as the server has it"
      onpointerdown={shoot}
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      {#each LANES as lane (lane.y)}
        <rect
          x="10"
          y={lane.y}
          width={W - 20}
          height="50"
          rx="4"
          style="fill:var(--bg-2);stroke:var(--line-2)"
        />
        <text x="14" y={lane.y - 6} font-size="10.5" style="fill:var(--text-muted);font-family:var(--font-mono)"
          >{lane.label}</text
        >
      {/each}
      <line x1={AIM} y1="30" x2={AIM} y2="88" stroke-width="1.5" style="stroke:var(--accent)" />
      <circle cx={AIM} cy="59" r="6" fill="none" stroke-width="1.5" style="stroke:var(--accent)" />
      <rect
        x={position(t - behind) - (WIDTH * PX) / 2}
        y="44"
        width={WIDTH * PX}
        height="30"
        rx="3"
        style="fill:var(--blue)"
      />
      <rect
        x={position(t) - (WIDTH * PX) / 2}
        y="138"
        width={WIDTH * PX}
        height="30"
        rx="3"
        style="fill:var(--blue)"
      />
      <g>
        {#each ghosts as ghost (ghost.id)}
          <rect
            x={ghost.x - (WIDTH * PX) / 2}
            y={ghost.y}
            width={WIDTH * PX}
            height="30"
            rx="3"
            stroke-dasharray="3 3"
            fill="none"
            stroke-width="1.5"
            style="stroke:{ghost.hit ? 'var(--green)' : 'var(--red)'}"
          />
          <text
            x={ghost.x}
            y={ghost.y + 44}
            text-anchor="middle"
            font-size="9.5"
            style="fill:{ghost.hit ? 'var(--green)' : 'var(--red)'};font-family:var(--font-mono)"
            >{ghost.label}</text
          >
        {/each}
      </g>
    </svg>
  </Stage>

  <div class="demo-controls">
    <Slider label="round trip" min={0} max={0.4} step={0.02} bind:value={rtt} />
    <Slider label="draw delay" min={0} max={0.2} step={0.02} bind:value={drawDelay} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={shoot}>shoot</button>
  </div>

  <Log {lines} />

  <Note>
    The player's screen runs half a round trip plus the draw delay behind the server, and the shot
    takes another half round trip to arrive. The server keeps one second of positions, so viewTime
    has that far to reach back.
  </Note>

  <CodePanel {source} />
</Demo>
