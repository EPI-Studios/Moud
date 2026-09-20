<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const W = 440
  const H = 260
  const PX = 12
  const SPOTS: [number, number][] = [
    [-2.2, -1.9],
    [2.2, -1.9],
    [-2.2, 1.9],
    [2.2, 1.9],
  ]
  const NAMES = ["front left", "front right", "back left", "back right"]
  const LEVELS = ["-1", "0", "1"] as const

  let throttle = $state<(typeof LEVELS)[number]>("1")
  let steer = $state<(typeof LEVELS)[number]>("0")
  let speed = $state(14)
  let turn = $state(7)
  let car = $state({ x: W / 2, y: H / 2, heading: 0 })
  let trail = $state<[number, number][]>([])
  let node = $state<SVGSVGElement | null>(null)

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  const spins = $derived.by(() => {
    const forward = Number(throttle) * speed
    const swing = Number(steer) * turn
    return { forward, turn: swing, left: forward + swing, right: forward - swing }
  })

  const source = $derived(
    `-- seat.throttle is ${throttle}, seat.steer is ${steer}\n` +
      `local SPEED = ${speed}\n` +
      `local TURN = ${turn}\n` +
      `local forward = seat.throttle * SPEED   -- ${spins.forward}\n` +
      `local turn = seat.steer * TURN          -- ${spins.turn}\n` +
      "local spin = if wheel.left then forward + turn else forward - turn\n" +
      `wheel.hinge.angularVelocity = -spin     -- left ${num(-spins.left, 1)}, right ${num(-spins.right, 1)}`,
  )

  let elapsed = 0
  let last = 0
  whileVisible(
    () => node,
    (now) => {
      const dt = last ? Math.min(0.05, (now - last) / 1000) : 0
      last = now
      const left = spins.left * 0.8
      const right = spins.right * 0.8
      const along = (left + right) / 2
      const yaw = (left - right) / 4.4
      car.heading += yaw * dt
      car.x += Math.sin(car.heading) * along * dt * PX
      car.y -= Math.cos(car.heading) * along * dt * PX
      let wrapped = false
      if (car.x < -30) {
        car.x += W + 60
        wrapped = true
      }
      if (car.x > W + 30) {
        car.x -= W + 60
        wrapped = true
      }
      if (car.y < -30) {
        car.y += H + 60
        wrapped = true
      }
      if (car.y > H + 30) {
        car.y -= H + 60
        wrapped = true
      }
      if (wrapped) trail = []
      elapsed += dt
      if (elapsed > 0.08) {
        elapsed = 0
        trail.push([car.x, car.y])
        if (trail.length > 40) trail.shift()
      }
    },
  )
</script>

<Demo label="Tank steering">
  <Stage>
    <svg
      bind:this={node}
      viewBox="0 0 {W} {H}"
      class="move-view"
      role="img"
      aria-label="top down view of the car driving"
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      <g>
        {#each trail as spot, i (i)}
          <circle
            cx={spot[0]}
            cy={spot[1]}
            r="1.4"
            opacity={((i + 1) / trail.length) * 0.6}
            style="fill:var(--text-muted)"
          />
        {/each}
      </g>
      <g
        transform="translate({car.x.toFixed(1)},{car.y.toFixed(1)}) rotate({(
          (car.heading * 180) /
          Math.PI
        ).toFixed(2)})"
      >
        <rect
          x={-1.8 * PX}
          y={-2.8 * PX}
          width={3.6 * PX}
          height={5.6 * PX}
          rx="3"
          stroke-width="1.5"
          style="fill:var(--red);fill-opacity:0.55;stroke:var(--text-main)"
        />
        <rect
          x={-0.7 * PX}
          y={0.1 * PX}
          width={1.4 * PX}
          height={1.4 * PX}
          style="fill:var(--bg-3);stroke:var(--text-main)"
        />
        <path d="M0,{-2.8 * PX - 6} l-5,7 h10 z" style="fill:var(--text-main)" />
        {#each SPOTS as spot (spot)}
          <rect
            x={spot[0] * PX - 0.3 * PX}
            y={spot[1] * PX - 0.8 * PX}
            width={0.6 * PX}
            height={1.6 * PX}
            style="fill:#111;stroke:var(--text-muted)"
          />
        {/each}
      </g>
    </svg>
  </Stage>

  <table class="demo-matrix move-wheels">
    <tbody>
      <tr>
        <th>wheel</th>
        <th>spot</th>
        <th>angularVelocity</th>
      </tr>
      {#each SPOTS as spot, i (spot)}
        <tr>
          <th>{NAMES[i]}</th>
          <td>vec3({spot[0]}, 0, {spot[1]})</td>
          <td>{num(-(spot[0] < 0 ? spins.left : spins.right), 1)}</td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="demo-controls">
    <Choice label="throttle" options={LEVELS} bind:value={throttle} />
    <Choice label="steer" options={LEVELS} bind:value={steer} />
    <Slider label="SPEED" min={0} max={20} step={1} bind:value={speed} />
    <Slider label="TURN" min={0} max={14} step={1} bind:value={turn} />
  </div>

  <Note>
    Each wheel is 1.6 across, so one radian a second rolls it 0.8 m a second. Faster left wheels
    swing the car right. The demo leaves wheel slip out.
  </Note>
  <CodePanel {source} />
</Demo>
