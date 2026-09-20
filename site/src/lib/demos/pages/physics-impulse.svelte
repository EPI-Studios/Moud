<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { createScene, type OverlayView, type Part, type Scene, type ViewRect } from "../core/mc2d"

  const GROUND = 64
  const IMPULSE = ["times getMass()", "on its own"] as const
  const GRAVITY = ["32", "32 / 6"] as const

  let canvas = $state<HTMLCanvasElement | null>(null)
  let size = $state(1)
  let density = $state(1)
  let impulse = $state<(typeof IMPULSE)[number]>("times getMass()")
  let gravity = $state<(typeof GRAVITY)[number]>("32")

  let scene: Scene | null = null
  let crate: Part | null = null
  let frame: ViewRect | null = null
  let flying = false
  let t = 0

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function numbers() {
    const mass = density * size * size * size
    const scale = impulse === "times getMass()" ? 1 : 1 / mass
    const g = gravity === "32" ? 32 : 32 / 6
    const vx = 6 * scale
    const vy = 4 * scale
    const air = (2 * vy) / g
    return { mass, g, vx, vy, air, far: vx * air, high: (vy * vy) / (2 * g) }
  }

  function position(n: ReturnType<typeof numbers>, at: number) {
    return {
      x: n.vx * at,
      y: GROUND + Math.max(0, n.vy * at - 0.5 * n.g * at * at) + size / 2,
    }
  }

  const note = $derived.by(() => {
    const n = numbers()
    const mass = num(n.mass, 4)
    const first = `getMass() is ${num(density)} × ${num(size)} × ${num(size)} × ${num(size)} = ${mass}. `
    const second =
      impulse === "times getMass()"
        ? "Multiplied by the mass, the push asks for the same 6 m/s out and 4 m/s up whatever the crate weighs. "
        : `Without the mass, the push changes the speed by 1 / ${mass} of (6, 4): ${num(n.vx, 1)} m/s out and ${num(n.vy, 1)} m/s up. `
    return `${first}${second}It rises ${num(n.high)} m and lands ${num(n.far, 1)} m away, before any sliding.`
  })

  const source = $derived.by(() => {
    const n = numbers()
    return (
      "-- server\n" +
      `game.gravity = ${gravity}\n` +
      `local crate = world:add("Part", { size = vec3(${num(size)}, ${num(size)}, ${num(size)}), density = ${num(density)}, anchored = false })\n` +
      `print(crate:getMass())   -- ${num(n.mass, 4)}\n` +
      (impulse === "times getMass()"
        ? "crate:applyImpulse(vec3(6, 4, 0) * crate:getMass())"
        : "crate:applyImpulse(vec3(6, 4, 0))")
    )
  })

  function overlay(ctx: CanvasRenderingContext2D, v: OverlayView) {
    const n = numbers()
    const gap = [0.5, 1, 2, 5, 10, 20, 50].filter((s) => s * v.unit >= 64)[0] || 100
    for (let m = 0; v.x(m, 0) < v.w; m += gap) {
      const x = v.x(m, 0)
      v.line(
        [
          [x, v.y(GROUND, 0)],
          [x, v.y(GROUND, 0) + 6],
        ],
        { width: 1.25, color: "rgba(255,255,255,0.85)", halo: false },
      )
      v.tag(`${num(m)} m`, x, v.y(GROUND, 0) + 17, { align: "center" })
    }

    const points: [number, number][] = []
    for (let i = 0; i <= 60; i++) {
      const p = position(n, (i / 60) * n.air)
      points.push([v.x(p.x, 0.5), v.y(p.y, 0.5)])
    }
    v.line(points, { dash: [3, 4], width: 1.25, color: "rgba(255,255,255,0.85)", halo: false })

    if (flying) return
    const at = position(n, 0)
    const speed = Math.sqrt(n.vx * n.vx + n.vy * n.vy)
    const reach = Math.min(70, 8 * speed)
    const x1 = v.x(at.x, 0.5)
    const y1 = v.y(at.y, 0.5)
    const x2 = x1 + (n.vx / speed) * reach
    const y2 = y1 - (n.vy / speed) * reach
    const a = Math.atan2(y2 - y1, x2 - x1)
    v.line(
      [
        [x1, y1],
        [x2, y2],
      ],
      { width: 2, color: v.colors.yellow },
    )
    v.line(
      [
        [x2 - Math.cos(a - 0.5) * 8, y2 - Math.sin(a - 0.5) * 8],
        [x2, y2],
        [x2 - Math.cos(a + 0.5) * 8, y2 - Math.sin(a + 0.5) * 8],
      ],
      { width: 2, color: v.colors.yellow },
    )
  }

  function draw() {
    if (!scene || !crate) return
    const at = position(numbers(), flying ? t : 0)
    crate.x = at.x
    crate.y = at.y
    crate.w = crate.h = crate.d = size
    scene.draw()
  }

  function reset() {
    flying = false
    t = 0
    scene?.stop()
    frame = null
    draw()
  }

  function push() {
    flying = true
    t = 0
    scene?.start()
  }

  $effect(() => {
    const node = canvas
    if (!node) return
    const made = createScene(node, {
      aspect: 0.42,
      minHeight: 190,
      maxHeight: 300,
      maxUnit: 64,
      time: 2400,
      view: (w, h) => {
        if (!frame || !flying || t === 0) {
          const n = numbers()
          const wide = Math.max(n.far + size, 2) + 3
          const tall = Math.max(n.high + size, 1) * 1.35 + 1.6
          const unit = Math.min(Math.min(w / wide, h / tall), 64)
          frame = {
            x0: -1.8,
            x1: -1.8 + w / unit,
            y0: GROUND - 0.9,
            y1: GROUND - 0.9 + h / unit,
          }
        }
        return frame
      },
      step: (dt) => {
        const n = numbers()
        t += dt
        if (t >= n.air) {
          t = n.air
          draw()
          return false
        }
        draw()
      },
      overlay,
    })
    for (let x = -20; x <= 180; x++) {
      made.set(x, 63, "grass_block")
      made.set(x, 62, "dirt")
      made.set(x, 61, "dirt")
      for (let y = 50; y <= 60; y++) made.set(x, y, "stone")
    }
    crate = made.part({ block: "oak_planks", stretch: true })
    scene = made
    return () => {
      made.destroy()
      scene = null
      crate = null
    }
  })

  $effect(() => {
    void size
    void density
    void impulse
    void gravity
    if (canvas) reset()
  })
</script>

<Demo label="Impulse and mass">
  <canvas bind:this={canvas} class="mc2d"></canvas>
  <Mc2dCredit />

  <div class="demo-controls">
    <Slider label="size" min={0.5} max={2} step={0.25} bind:value={size} />
    <Slider label="density" min={0.2} max={3} step={0.1} bind:value={density} />
    <Choice label="impulse" options={IMPULSE} bind:value={impulse} />
    <Choice label="game.gravity" options={GRAVITY} bind:value={gravity} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={push}>push the crate</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={reset}>put it back</button>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
