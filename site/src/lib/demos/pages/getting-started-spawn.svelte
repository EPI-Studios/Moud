<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type Part, type PlayerEntity } from "../core/mc2d"

  let anchored = $state<"true" | "false">("true")
  let spawnY = $state(66)
  let walkSpeed = $state(6)
  let canvas = $state<HTMLCanvasElement | null>(null)

  let floor: Part | null = null
  let steve: PlayerEntity | null = null
  const sim = { t: 0, bodyY: 66, bodyV: 0, floorY: 64, floorV: 0, x: 0, dir: 1, grounded: false }

  const source = $derived(
    'world:add("Part", {\n' +
      '    name = "floor",\n' +
      "    size = vec3(40, 1, 40),\n" +
      "    cframe = cframe(0, 64, 0),\n" +
      "    anchored = " +
      anchored +
      ",\n" +
      "})\n\n" +
      "game.players.joined:connect(function(player)\n" +
      "    player:spawn(vec3(0, " +
      spawnY +
      ", 0))\n" +
      "    player.character.humanoid.walkSpeed = " +
      walkSpeed +
      "\n" +
      "end)",
  )

  const note = $derived(
    anchored === "true"
      ? "The floor's centre is at y 64 and it is 1 m thick, so its top is at 64.5. The body lands on it and walks " +
          walkSpeed +
          " metres a second."
      : "anchored = false hands the floor to physics, and nothing holds it up: it falls, and the player falls with it.",
  )

  function respawn() {
    sim.t = 0
    sim.bodyY = spawnY
    sim.bodyV = 0
    sim.floorY = 64
    sim.floorV = 0
    sim.x = 0
    sim.dir = 1
    sim.grounded = false
  }

  function tick(dt: number) {
    const held = anchored === "true"
    sim.t += dt
    const g = 20
    if (!held) {
      sim.floorV -= g * dt
      sim.floorY += sim.floorV * dt
    }
    const top = sim.floorY + 0.5
    if (!sim.grounded || !held) {
      sim.bodyV -= g * dt
      sim.bodyY += sim.bodyV * dt
      if (sim.bodyY <= top && sim.floorY > 40) {
        sim.bodyY = top
        sim.bodyV = held ? 0 : sim.floorV
        sim.grounded = held
      }
    }
    if (sim.grounded) {
      sim.x += sim.dir * walkSpeed * dt
      if (sim.x > 12) {
        sim.x = 12
        sim.dir = -1
      }
      if (sim.x < -12) {
        sim.x = -12
        sim.dir = 1
      }
    }
    if (sim.t > (held ? 9 : 3.5)) respawn()
    if (floor) floor.y = sim.floorY
    if (steve) {
      steve.x = sim.x
      steve.y = sim.bodyY
      steve.facing = sim.dir
      steve.walk(dt, sim.grounded ? walkSpeed : 0)
    }
  }

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const narrow = v.w < 560
    for (const y of [64, 66]) {
      v.line(
        [
          [0, v.y(y, 0.5)],
          [v.w, v.y(y, 0.5)],
        ],
        { dash: [4, 5], width: 1, color: "rgba(255,255,255,0.8)", halo: false },
      )
      v.tag("y " + y, 6, v.y(y, 0.5) - 11)
    }
    const fy = v.y(sim.floorY - 0.5, 0)
    if (fy < v.h - 10) {
      const left = Math.max(v.x(-20, 0), 8)
      v.tag(narrow ? "floor, 40 m wide" : "floor, 40 m", left, fy + 13)
    }
    const sx = v.x(0, 0.5)
    const sy = v.y(spawnY, 0.5)
    v.line(
      [
        [sx - 7, sy],
        [sx + 7, sy],
      ],
      { color: v.colors.yellow, width: 2 },
    )
    v.line(
      [
        [sx, sy - 7],
        [sx, sy + 7],
      ],
      { color: v.colors.yellow, width: 2 },
    )
    v.tag("spawn, y " + spawnY, sx, sy - 17, { color: v.colors.yellow, align: "center" })
  }

  $effect(() => {
    anchored
    spawnY
    untrack(respawn)
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    const scene = createScene(node, {
      height: 250,
      time: 2600,
      view: (w) => (w < 560 ? { x0: -13.5, x1: 13.5, y0: 61.6, y1: 74.6 } : { x0: -22, x1: 22, y0: 61.6, y1: 74.6 }),
      step: tick,
      overlay,
    })
    floor = scene.part({ block: "smooth_stone", x: 0, y: 64, w: 40, h: 1, d: 1 })
    steve = scene.player({ x: 0, y: untrack(() => spawnY), facing: 1 })
    scene.start()
    return () => {
      floor = null
      steve = null
      scene.destroy()
    }
  })
</script>

<Demo label="The floor and the spawn">
  <canvas bind:this={canvas} class="mc2d" aria-label="a body spawning above a floor and walking on it"></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    <Choice label="anchored" options={["true", "false"] as const} bind:value={anchored} />
    <Slider label="spawn y" min={65} max={72} step={0.5} bind:value={spawnY} />
    <Slider label="walkSpeed" min={0} max={16} step={1} bind:value={walkSpeed} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
