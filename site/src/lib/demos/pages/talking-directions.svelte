<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { fitLegacy, palette} from "../core/surface"
  import { whileVisible } from "../core/frames"

  type Where = "server" | "a" | "b"
  type Packet = { from: Where; to: Where; t: number; color: "white" | "blue"; done: (() => void) | null }

  const HEIGHT = 190

  let canvas = $state<HTMLCanvasElement | null>(null)
  let spawned = $state("yes")
  let lines = $state<Line[]>([
    { id: 0, text: "the sender is handed over by the engine, never passed by the client", kind: "idle" },
  ])
  let next = 1
  let packets: Packet[] = []

  function push(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function send(from: Where, to: Where, color: Packet["color"], done: () => void) {
    packets.push({ from, to, t: 0, color, done })
  }

  function places(width: number) {
    return {
      server: { x: width / 2, y: 40, name: "server" },
      a: { x: width * 0.22, y: 150, name: "client of meek" },
      b: { x: width * 0.78, y: 150, name: "client of ana" },
    }
  }

  function draw() {
    if (!canvas) return
    const fit = fitLegacy(canvas, HEIGHT)
    if (!fit) return
    const c = palette()
    const { ctx, width } = fit
    const p = places(width)

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, width, HEIGHT)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1.5
    ctx.setLineDash([4, 4])
    for (const end of [p.a, p.b]) {
      ctx.beginPath()
      ctx.moveTo(p.server.x, p.server.y)
      ctx.lineTo(end.x, end.y)
      ctx.stroke()
    }
    ctx.setLineDash([])
    ctx.font = `12px ${c.mono}`
    ctx.textAlign = "center"
    ctx.textBaseline = "middle"
    for (const place of [p.server, p.a, p.b]) {
      const box = ctx.measureText(place.name).width + 24
      ctx.fillStyle = c.bg3
      ctx.strokeStyle = c.line2
      ctx.fillRect(place.x - box / 2, place.y - 15, box, 30)
      ctx.strokeRect(place.x - box / 2, place.y - 15, box, 30)
      ctx.fillStyle = c.main
      ctx.fillText(place.name, place.x, place.y)
    }
    for (const packet of packets) {
      const from = p[packet.from]
      const to = p[packet.to]
      const t = Math.min(1, packet.t)
      ctx.fillStyle = packet.color === "blue" ? c.blue : c.accent
      ctx.beginPath()
      ctx.arc(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, 6, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  let last: number | null = null
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      for (const packet of packets) {
        packet.t += dt * 1.6
        if (packet.t >= 1 && packet.done) {
          packet.done()
          packet.done = null
        }
      }
      packets = packets.filter((packet) => packet.t < 1.15)
      draw()
    },
  )

  let told = untrack(() => spawned)
  $effect(() => {
    if (spawned === told) return
    told = spawned
    push(
      spawned === "yes"
        ? "meek spawned: he has a body again"
        : "meek is dead, or has not spawned yet: no body",
      "idle",
    )
  })
</script>

<Demo label="Who hears what">
  <canvas bind:this={canvas} class="tk-canvas" style="height: {HEIGHT}px"></canvas>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() =>
        send("a", "server", "white", () => {
          push(`server  onServer(${spawned === "yes" ? "meek's body" : "nil"}, "jump")`, spawned === "yes" ? "in" : "out")
          push('server  onServerPlayer(Player meek, "jump")', "in")
        })}
    >
      {'meek: remote:fireServer("jump")'}
    </button>
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        if (spawned !== "yes") {
          push("meek has no body right now, so there is no body to hand fireClient", "out")
          return
        }
        send("server", "a", "blue", () => push('client of meek  onClient("hi")', "in"))
      }}
    >
      {'server: remote:fireClient(body, "hi")'}
    </button>
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        send("server", "a", "blue", () => push('client of meek  onClient("round")', "in"))
        send("server", "b", "blue", () => push('client of ana  onClient("round")', "in"))
      }}
    >
      {'server: remote:fireAllClients("round")'}
    </button>
  </div>

  <div class="demo-controls">
    <Choice label="meek has spawned" options={["yes", "no"]} bind:value={spawned} />
  </div>

  <Log {lines} keep={5} />
</Demo>
