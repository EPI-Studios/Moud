<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { fitLegacy, palette} from "../core/surface"
  import { whileVisible } from "../core/frames"

  const HEIGHT = 200

  const SOURCE = `-- server
local looking = world:add("UnreliableRemote", { name = "looking", accepts = "vec3" })
looking.onServer:connect(function(body, direction) end)

-- client, every tick
looking:fireServer(direction)`

  let canvas = $state<HTMLCanvasElement | null>(null)
  let loss = $state(0.3)

  let clock = 0
  let tickAt = 0
  let reliable = 0
  let unreliable = 0
  let lost = 0
  let sent = 0

  function aim(t: number) {
    return Math.sin(t * 1.3) * 1.1 + Math.sin(t * 3.1) * 0.35
  }

  function draw() {
    if (!canvas) return
    const fit = fitLegacy(canvas, HEIGHT)
    if (!fit) return
    const c = palette()
    const { ctx, width } = fit

    ctx.fillStyle = c.bg
    ctx.fillRect(0, 0, width, HEIGHT)
    const cx = width / 2
    const cy = HEIGHT - 24
    const r = Math.min(HEIGHT - 50, width / 2 - 30)
    ctx.strokeStyle = c.line2
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.arc(cx, cy, r, Math.PI, 0)
    ctx.stroke()

    const dot = (angle: number, radius: number, color: string, size: number) => {
      const x = cx + Math.sin(angle) * radius
      const y = cy - Math.cos(angle) * radius
      ctx.strokeStyle = color
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.moveTo(cx, cy)
      ctx.lineTo(x, y)
      ctx.stroke()
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.arc(x, y, size, 0, Math.PI * 2)
      ctx.fill()
    }
    dot(reliable, r * 0.62, c.blue, 5)
    dot(unreliable, r * 0.8, c.yellow, 5)
    dot(aim(clock), r, c.main, 6)

    ctx.font = `11.5px ${c.mono}`
    ctx.textBaseline = "middle"
    ctx.textAlign = "left"
    ctx.fillStyle = c.blue
    ctx.fillText("Remote", 10, 16)
    ctx.fillStyle = c.yellow
    ctx.fillText(`UnreliableRemote  lost ${lost} of ${sent}`, 10, 34)
    ctx.fillStyle = c.main
    ctx.fillText("client", 10, 52)
  }

  let last: number | null = null
  whileVisible(
    () => canvas,
    (now) => {
      const dt = last === null ? 0 : Math.min(0.1, (now - last) / 1000)
      last = now
      clock += dt
      tickAt += dt
      while (tickAt >= 0.05) {
        tickAt -= 0.05
        const value = aim(clock - tickAt)
        reliable = value
        sent += 1
        if (Math.random() < loss) lost += 1
        else unreliable = value
      }
      draw()
    },
  )

  let counted = untrack(() => loss)
  $effect(() => {
    if (loss === counted) return
    counted = loss
    lost = 0
    sent = 0
  })
</script>

<Demo label="Remote against UnreliableRemote">
  <canvas bind:this={canvas} class="tk-canvas" style="height: {HEIGHT}px"></canvas>

  <div class="demo-controls">
    <Slider label="messages lost" min={0} max={0.8} step={0.05} bind:value={loss} />
  </div>

  <Note>
    The client sends where it is looking every tick. The white dot is where it really looks; the
    others are what the server last heard.
  </Note>

  <CodePanel source={SOURCE} />
</Demo>
