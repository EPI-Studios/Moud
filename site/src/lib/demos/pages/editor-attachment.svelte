<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"

  type Face = { name: string; axis: number; sign: number; label: string }
  type Point = [number, number]

  const W = 440
  const H = 260
  const SIZE = [2.4, 1.2, 1.6]
  const UNIT = 62
  const PINK = "#ef6fb5"

  const FACES: Face[] = [
    { name: "+X", axis: 0, sign: 1, label: "+X" },
    { name: "-X", axis: 0, sign: -1, label: "-X" },
    { name: "+Y", axis: 1, sign: 1, label: "+Y, top" },
    { name: "-Y", axis: 1, sign: -1, label: "-Y, bottom" },
    { name: "+Z", axis: 2, sign: 1, label: "+Z, back" },
    { name: "-Z", axis: 2, sign: -1, label: "-Z, front" },
  ]

  const RIGHT = [-1 / Math.SQRT2, 0, -1 / Math.SQRT2]
  const UP = [-1 / Math.sqrt(6), 2 / Math.sqrt(6), 1 / Math.sqrt(6)]
  const VIEW = [1, 1, -1]
  const HALF = SIZE.map((s) => s / 2)

  function project(p: number[]): Point {
    const x = p[0] * RIGHT[0] + p[1] * RIGHT[1] + p[2] * RIGHT[2]
    const y = p[0] * UP[0] + p[1] * UP[1] + p[2] * UP[2]
    return [W / 2 + x * UNIT, H / 2 + 14 - y * UNIT]
  }

  function vec(axis: number, sign: number) {
    const v = [0, 0, 0]
    v[axis] = sign
    return v
  }

  function nameOf(v: number[]) {
    const axis = v[0] ? 0 : v[1] ? 1 : 2
    return (v[axis] > 0 ? "+" : "-") + "XYZ".charAt(axis)
  }

  function corners(face: Face) {
    const a = (face.axis + 1) % 3
    const b = (face.axis + 2) % 3
    return [
      [-1, -1],
      [1, -1],
      [1, 1],
      [-1, 1],
    ].map((c) => {
      const p = [0, 0, 0]
      p[face.axis] = face.sign * HALF[face.axis]
      p[a] = c[0] * HALF[a]
      p[b] = c[1] * HALF[b]
      return p
    })
  }

  function facing(face: Face) {
    const v = vec(face.axis, face.sign)
    return v[0] * VIEW[0] + v[1] * VIEW[1] + v[2] * VIEW[2] > 0
  }

  function axesOf(face: Face) {
    return { x: vec(face.axis, face.sign), y: face.axis === 1 ? [0, 0, 1] : [0, 1, 0] }
  }

  function arrow(at: number[], dir: number[], length: number) {
    return project([at[0] + dir[0] * length, at[1] + dir[1] * length, at[2] + dir[2] * length])
  }

  let picked = $state("+X")

  const face = $derived(FACES.filter((f) => f.name === picked)[0])
  const hidden = $derived(!facing(face))
  const ax = $derived(axesOf(face))
  const at = $derived(vec(face.axis, face.sign).map((v, i) => v * HALF[i]))
  const dot = $derived(project(at))
  const xTip = $derived(arrow(at, ax.x, 1.1))
  const yTip = $derived(arrow(at, ax.y, 0.55))

  const edges = $derived.by(() => {
    const back = [-HALF[0], -HALF[1], HALF[2]]
    const a = project(back)
    return [
      [HALF[0], -HALF[1], HALF[2]],
      [-HALF[0], HALF[1], HALF[2]],
      [-HALF[0], -HALF[1], -HALF[2]],
    ].map((end) => ({ a, b: project(end) }))
  })

  const shown = $derived(
    FACES.filter(facing).map((f) => ({
      face: f,
      points: corners(f)
        .map(project)
        .map((p) => p[0].toFixed(1) + "," + p[1].toFixed(1))
        .join(" "),
      centre: project(vec(f.axis, f.sign).map((v, i) => v * HALF[i])),
    })),
  )
</script>

<Demo label="Where a click puts the axes">
  <div class="demo-stage">
    <svg
      class="ed-view"
      viewBox="0 0 {W} {H}"
      role="img"
      aria-label="a part with an attachment on the clicked face"
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      {#each edges as edge, index (index)}
        <line
          x1={edge.a[0]}
          y1={edge.a[1]}
          x2={edge.b[0]}
          y2={edge.b[1]}
          stroke-dasharray="3 4"
          style="stroke:var(--text-light)"
        />
      {/each}
      {#each shown as side (side.face.name)}
        <polygon
          class="ed-face"
          role="button"
          tabindex="0"
          aria-label="pick the {side.face.name} face"
          onkeydown={(event) => event.key === "Enter" && (picked = side.face.name)}
          points={side.points}
          stroke-width="1.5"
          style="stroke:var(--text-main);fill:{picked === side.face.name ? 'var(--bg-4)' : 'var(--bg-3)'}"
          onclick={() => (picked = side.face.name)}
        />
        <text
          x={side.centre[0]}
          y={side.centre[1] + (side.face.axis === 1 ? -18 : 40)}
          font-size="10.5"
          text-anchor="middle"
          style="fill:var(--text-muted);font-family:var(--font-mono);pointer-events:none"
        >
          {side.face.label}
        </text>
      {/each}
      <circle cx={dot[0]} cy={dot[1]} r="4" fill={PINK} opacity={hidden ? 0.6 : 1} />
      <line
        x1={dot[0]}
        y1={dot[1]}
        x2={xTip[0]}
        y2={xTip[1]}
        stroke-width="2.2"
        opacity={hidden ? 0.6 : 1}
        stroke-dasharray={hidden ? "4 3" : "none"}
        stroke={PINK}
      />
      <circle cx={xTip[0]} cy={xTip[1]} r="3.2" opacity={hidden ? 0.6 : 1} fill={PINK} />
      <line
        x1={dot[0]}
        y1={dot[1]}
        x2={yTip[0]}
        y2={yTip[1]}
        stroke-width="1.2"
        opacity={hidden ? 0.35 : 0.55}
        stroke-dasharray={hidden ? "4 3" : "none"}
        stroke={PINK}
      />
      <circle cx={yTip[0]} cy={yTip[1]} r="2.2" opacity={hidden ? 0.35 : 0.55} fill={PINK} />
      <text x={xTip[0] + 6} y={xTip[1] + 4} font-size="10.5" style="fill:{PINK};font-family:var(--font-mono)"
        >X</text
      >
      <text
        x={yTip[0] + 6}
        y={yTip[1] + 4}
        font-size="10.5"
        style="fill:{PINK};font-family:var(--font-mono);opacity:0.7">Y</text
      >
    </svg>
  </div>

  <div class="demo-controls">
    <Choice label="click on" options={FACES.map((f) => f.name)} bind:value={picked} />
  </div>

  <table class="demo-matrix ed-axes">
    <tbody>
      <tr>
        <th>clicked face</th>
        <th>X, rightVector</th>
        <th>Y</th>
      </tr>
      <tr>
        <td>{face.label}</td>
        <td>{nameOf(ax.x)}, straight out of the face</td>
        <td>{nameOf(ax.y)}{face.axis === 1 ? ", the part's back" : ", the part's own up"}</td>
      </tr>
    </tbody>
  </table>

  <p class="demo-note">
    {hidden ? "That face is on the far side, so its axes are drawn dashed. " : ""}The editor draws X
    long and Y short and faint. A hinge or slider made from this click turns or slides along X.
  </p>
</Demo>
