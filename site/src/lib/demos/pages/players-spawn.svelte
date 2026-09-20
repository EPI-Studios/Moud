<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { clamp, pointIn } from "../core/pointer"
  import { palette } from "../core/surface"

  type Team = "red" | "blue"
  type Pad = {
    name: string
    x: number
    y: number
    neutral: boolean
    team: Team | null
    touch: boolean
    enabled: boolean
  }

  const W = 720
  const H = 330

  const START = `-- server/main.luau
game.world:add("SpawnLocation", {
    name = "joinRed",
    neutral = false,
    teamColor = red.teamColor,
    color = red.teamColor,
    allowTeamChangeOnTouch = true,
})`

  let pads = $state<Pad[]>([
    { name: "lobby", x: 360, y: 90, neutral: true, team: null, touch: false, enabled: true },
    { name: "joinRed", x: 250, y: 190, neutral: false, team: "red", touch: true, enabled: true },
    { name: "joinBlue", x: 470, y: 190, neutral: false, team: "blue", touch: true, enabled: true },
    { name: "redBase", x: 80, y: 260, neutral: false, team: "red", touch: false, enabled: true },
    { name: "blueBase", x: 640, y: 260, neutral: false, team: "blue", touch: false, enabled: true },
  ])
  let me = $state({ x: 360, y: 90, placed: true })
  let team = $state<Team | null>(null)
  let picked = $state<"none" | Team>("none")
  let lines = $state<Line[]>([{ id: 0, text: "drag the player onto a team pad, or spawn" }])
  let source = $state(START)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let box = $state({ w: 0, h: 0, scale: 1 })
  let dragging = $state(false)
  let next = 1

  const rand = seeded(11)

  function seeded(start: number) {
    let seed = start
    return () => {
      seed = (seed * 16807) % 2147483647
      return seed / 2147483647
    }
  }

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function fits(pad: Pad) {
    return pad.enabled && (pad.neutral || (team !== null && pad.team === team))
  }

  function candidates() {
    const fitting = pads.filter(fits)
    if (fitting.length) return { list: fitting, how: "fits" }
    const enabled = pads.filter((pad) => pad.enabled)
    if (enabled.length) return { list: enabled, how: "any" }
    return { list: [], how: "none" }
  }

  function onPad(pad: Pad) {
    return Math.abs(me.x - pad.x) < 30 && Math.abs(me.y - pad.y) < 30
  }

  function setTeam(to: Team | null, why?: string) {
    if (to === team) return
    const old = team
    team = to
    picked = to ?? "none"
    if (old) record(`playerRemoved fired on ${old}${why ? ` (${why})` : ""}`, "out")
    if (to) record(`playerAdded fired on ${to}${why ? ` (${why})` : ""}`, "in")
  }

  function move(event: PointerEvent) {
    if (!canvas) return
    const point = pointIn(canvas, event, W, H)
    me = { ...me, x: clamp(point.x, 10, W - 10), y: clamp(point.y, 10, H - 10) }
    for (const pad of pads) {
      if (pad.touch && !pad.neutral && pad.enabled && onPad(pad) && team !== pad.team) {
        setTeam(pad.team, `stood on ${pad.name}`)
        source = `-- the engine, every tick, for a body standing on ${pad.name}\n-- player.team = ${pad.team}`
      }
    }
  }

  function spawn() {
    const pick = candidates()
    if (!pick.list.length) {
      record("no enabled SpawnLocation: the body arrives at (0.5, 70, 0.5)")
      me = { ...me, placed: false }
    } else {
      const pad = pick.list[Math.floor(rand() * pick.list.length)]
      me = { x: pad.x, y: pad.y, placed: true }
      record(`spawned on ${pad.name}, picked at random from ${pick.list.length}`, "in")
    }
    source = "player:spawn()   -- on a SpawnLocation"
  }

  function fit() {
    const node = canvas
    if (!node) return
    const width = node.clientWidth || W
    const ratio = window.devicePixelRatio || 1
    box = {
      w: Math.max(1, Math.round(width * ratio)),
      h: Math.max(1, Math.round(((width * H) / W) * ratio)),
      scale: Math.max(1, Math.min(1.5, (W / width) * 0.7)),
    }
  }

  function draw(ctx: CanvasRenderingContext2D) {
    const c = palette()
    const shade = { red: c.red, blue: c.blue }
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, W, H)
    const pick = candidates()
    ctx.font = `${Math.round(12 * box.scale)}px ${c.mono}`
    ctx.textAlign = "center"
    for (const pad of pads) {
      const colour = pad.team ? shade[pad.team] : c.main
      if (pick.list.includes(pad)) {
        ctx.strokeStyle = c.accent
        ctx.setLineDash([4, 4])
        ctx.strokeRect(pad.x - 38.5, pad.y - 38.5, 77, 77)
        ctx.setLineDash([])
      }
      ctx.globalAlpha = pad.enabled ? 1 : 0.3
      ctx.fillStyle = colour
      ctx.globalAlpha *= 0.25
      ctx.fillRect(pad.x - 30, pad.y - 30, 60, 60)
      ctx.globalAlpha = pad.enabled ? 1 : 0.3
      ctx.strokeStyle = colour
      ctx.lineWidth = 1.5
      ctx.strokeRect(pad.x - 30, pad.y - 30, 60, 60)
      ctx.lineWidth = 1
      ctx.fillStyle = c.text
      ctx.fillText(pad.name, pad.x, pad.y + 50)
      if (pad.touch) {
        ctx.fillStyle = c.muted
        ctx.fillText("changes team", pad.x, pad.y + 64)
      }
      ctx.globalAlpha = 1
    }
    if (me.placed) {
      ctx.beginPath()
      ctx.arc(me.x, me.y, 10, 0, Math.PI * 2)
      ctx.fillStyle = team ? shade[team] : c.main
      ctx.fill()
      ctx.strokeStyle = c.bg
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.lineWidth = 1
    }
    ctx.textAlign = "left"
    ctx.fillStyle = c.muted
    ctx.fillText(`player.team = ${team ?? "nil"}`, 14, 22)
    ctx.fillText(
      pick.how === "fits"
        ? "a spawn picks one of the ringed pads"
        : pick.how === "any"
          ? "none fits: any enabled pad will do"
          : "none enabled: (0.5, 70, 0.5)",
      14,
      40,
    )
  }

  $effect(() => {
    const wanted = picked === "none" ? null : picked
    if (wanted === team) return
    setTeam(wanted)
    source = `-- server\nplayer.team = ${wanted ?? "nil"}\n-- the body stays where it is until the next spawn`
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(fit)
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  $effect(() => {
    const node = canvas
    const ctx = node?.getContext("2d")
    if (!node || !ctx || !box.w) return
    node.width = box.w
    node.height = box.h
    ctx.setTransform(box.w / W, 0, 0, box.h / H, 0, 0)
    draw(ctx)
  })
</script>

<Demo label="Spawn locations and teams">
  <canvas
    bind:this={canvas}
    class="demo-plan demo-plan-wide"
    style="aspect-ratio: {W} / {H}"
    aria-label="top down view of spawn locations, a lobby and team pads"
    onpointerdown={(event) => {
      if (!canvas) return
      const point = pointIn(canvas, event, W, H)
      const dx = point.x - me.x
      const dy = point.y - me.y
      if (dx * dx + dy * dy >= 26 * 26) return
      dragging = true
      canvas.setPointerCapture(event.pointerId)
      event.preventDefault()
    }}
    onpointermove={(event) => dragging && move(event)}
    onpointerup={() => (dragging = false)}
    onpointercancel={() => (dragging = false)}
  ></canvas>

  <div class="demo-controls">
    <Choice label="player.team" options={["none", "red", "blue"] as const} bind:value={picked} />
  </div>

  <div class="demo-controls demo-row">
    <span class="demo-slider-name players-inline">enabled</span>
    {#each pads as pad (pad.name)}
      <button
        type="button"
        class="demo-pill"
        aria-pressed={pad.enabled}
        onclick={() => {
          pad.enabled = !pad.enabled
          source = `game.world.${pad.name}.enabled = ${pad.enabled}`
        }}
      >
        {pad.name}
      </button>
    {/each}
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={spawn}>player:spawn()</button>
  </div>

  <Log {lines} />
  <CodePanel {source} />
</Demo>
