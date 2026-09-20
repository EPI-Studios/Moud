<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp, pointIn } from "../core/pointer"

  type Person = { name: string; tags: string[]; x: number; y: number; sender?: boolean }
  type Check = {
    code: string
    ring?: number
    sight?: boolean
    test: (person: Person, distance: number, seen: boolean) => [boolean, string]
  }

  const W = 400
  const H = 240
  const SCALE = 4
  const WALL = { x: 236, y: 30, w: 10, h: 150 }
  const COLUMNS = [40, 80, 120, 160, 200, 240, 280, 320, 360]
  const ROWS = [40, 80, 120, 160, 200]

  const CHECKS: Record<string, Check> = {
    "within(30)": {
      code: "general.shouldDeliver = game.chat.within(30)",
      ring: 30,
      test: (person, d) => (d <= 30 ? [true, "within 30 m"] : [false, "further than 30 m"]),
    },
    "audible(30)": {
      code: "general.shouldDeliver = game.chat.audible(30)",
      ring: 30,
      sight: true,
      test: (person, d, seen) => {
        if (d > 30) return [false, "further than 30 m"]
        return seen
          ? [true, "within 30 m, nothing in between"]
          : [false, "within 30 m, but the wall is in between"]
      },
    },
    'sameTag("red")': {
      code: 'team.shouldDeliver = game.chat.sameTag("red")',
      test: (person) =>
        person.tags.includes("red") ? [true, "body has the red tag"] : [false, "no red tag"],
    },
    'all(within(80), sameTag("alive"))': {
      code: 'general.shouldDeliver = game.chat.all(game.chat.within(80), game.chat.sameTag("alive"))',
      ring: 80,
      test: (person, d) => {
        if (d > 80) return [false, "further than 80 m"]
        return person.tags.includes("alive")
          ? [true, "within 80 m and alive"]
          : [false, "within 80 m, but no alive tag"]
      },
    },
    'any(sameTag("staff"), within(20))': {
      code: 'general.shouldDeliver = game.chat.any(game.chat.sameTag("staff"), game.chat.within(20))',
      ring: 20,
      test: (person, d) => {
        if (person.tags.includes("staff")) return [true, "has the staff tag"]
        return d <= 20 ? [true, "within 20 m"] : [false, "not staff and further than 20 m"]
      },
    },
  }
  const NAMES = Object.keys(CHECKS)

  let plan = $state<SVGSVGElement | null>(null)
  let picked = $state(NAMES[0])
  let people = $state<Person[]>([
    { name: "you", tags: ["red", "alive"], x: 120, y: 120, sender: true },
    { name: "ana", tags: ["red", "alive"], x: 190, y: 70 },
    { name: "bo", tags: ["alive"], x: 70, y: 190 },
    { name: "cyd", tags: ["red"], x: 300, y: 110 },
    { name: "dee", tags: ["staff"], x: 360, y: 200 },
    { name: "eli", tags: ["alive"], x: 330, y: 40 },
  ])

  function blocked(a: Person, b: Person) {
    const x0 = WALL.x
    const x1 = WALL.x + WALL.w
    const y0 = WALL.y
    const y1 = WALL.y + WALL.h
    const dx = b.x - a.x
    const dy = b.y - a.y
    let t0 = 0
    let t1 = 1
    const p = [-dx, dx, -dy, dy]
    const q = [a.x - x0, x1 - a.x, a.y - y0, y1 - a.y]
    for (let i = 0; i < 4; i++) {
      if (p[i] === 0) {
        if (q[i] < 0) return false
      } else {
        const r = q[i] / p[i]
        if (p[i] < 0) {
          if (r > t1) return false
          if (r > t0) t0 = r
        } else {
          if (r < t0) return false
          if (r < t1) t1 = r
        }
      }
    }
    return true
  }

  const check = $derived(CHECKS[picked])
  const sender = $derived(people[0])

  const rows = $derived(
    people.map((person) => {
      const distance = Math.hypot(person.x - sender.x, person.y - sender.y) / SCALE
      const seen = !blocked(sender, person)
      const [pass, why] = check.test(person, distance, seen)
      return { person, distance, seen, pass, why }
    }),
  )
  const reached = $derived(rows.filter((row) => row.pass).length)
  const sight = $derived(
    check.sight ? rows.filter((row) => !row.person.sender && row.distance <= 30) : [],
  )
  const source = $derived(
    `-- server\n${check.code}\n\n-- ${reached} of ${people.length} members get the line`,
  )

  let dragging: Person | null = null

  function at(event: PointerEvent) {
    const point = pointIn(plan!, event, W, H)
    return { x: clamp(point.x, 10, W - 10), y: clamp(point.y, 10, H - 36) }
  }
</script>

<Demo label="Who hears it">
  <Stage>
    <svg
      bind:this={plan}
      class="demo-plan demo-plan-wide chat-plan"
      viewBox="0 0 {W} {H}"
      role="img"
      aria-label="top down view of players around a sender"
      onpointerdown={(event) => {
        const point = at(event)
        let best: Person | null = null
        let nearest = 30
        for (const person of people) {
          const d = Math.hypot(person.x - point.x, person.y - point.y)
          if (d < nearest) {
            nearest = d
            best = person
          }
        }
        dragging = best ?? people[0]
        plan?.setPointerCapture(event.pointerId)
        dragging.x = point.x
        dragging.y = point.y
      }}
      onpointermove={(event) => {
        if (!dragging) return
        const point = at(event)
        dragging.x = point.x
        dragging.y = point.y
      }}
      onpointerup={() => (dragging = null)}
      onpointercancel={() => (dragging = null)}
    >
      <rect width={W} height={H} style="fill:var(--bg)" />
      <g style="stroke:var(--line)">
        {#each COLUMNS as x (x)}
          <line x1={x} y1={0} x2={x} y2={H} />
        {/each}
        {#each ROWS as y (y)}
          <line x1={0} y1={y} x2={W} y2={y} />
        {/each}
      </g>
      <rect
        x={WALL.x}
        y={WALL.y}
        width={WALL.w}
        height={WALL.h}
        style="fill:var(--bg-4);stroke:var(--text-muted)"
      />
      <text
        x={WALL.x + WALL.w / 2}
        y={WALL.y - 8}
        text-anchor="middle"
        font-size="10"
        letter-spacing="1.2"
        style="fill:var(--text-light)">WALL</text
      >
      <circle
        cx={sender.x}
        cy={sender.y}
        r={check.ring ? check.ring * SCALE : 0}
        stroke-dasharray="5 4"
        style="fill:rgba(198,198,198,0.05);stroke:var(--text-muted)"
      />
      <g>
        {#each sight as row (row.person.name)}
          <line
            x1={sender.x}
            y1={sender.y}
            x2={row.person.x}
            y2={row.person.y}
            stroke-width="1.2"
            stroke-dasharray={row.seen ? "" : "3 3"}
            style="stroke:{row.seen ? 'var(--text-muted)' : 'var(--red)'}"
          />
        {/each}
      </g>
      {#each rows as row (row.person.name)}
        <g>
          <circle
            cx={row.person.x}
            cy={row.person.y}
            r={row.person.sender ? 8 : 7}
            stroke-width="2"
            style={row.pass
              ? "fill:var(--text-main);stroke:var(--text-main)"
              : "fill:var(--bg);stroke:var(--text-light)"}
          />
          <text
            x={row.person.x}
            y={row.person.y + 21}
            text-anchor="middle"
            font-size="11"
            style="fill:var(--text)">{row.person.name}</text
          >
          <text
            x={row.person.x}
            y={row.person.y + 32}
            text-anchor="middle"
            font-size="9"
            style="fill:var(--text-light)">{row.person.tags.map((tag) => `.${tag}`).join(" ")}</text
          >
        </g>
      {/each}
    </svg>
  </Stage>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">shouldDeliver</span>
      <select class="demo-select chat-select" bind:value={picked}>
        {#each NAMES as name (name)}
          <option value={name}>game.chat.{name}</option>
        {/each}
      </select>
    </div>
  </div>

  <Note>
    Drag anyone. A grid square is 10 metres. The sender is a member too, so a check can keep a
    player's own line from them.
  </Note>

  <ul class="demo-log chat-reasons">
    {#each rows as row (row.person.name)}
      <li class="demo-log-row {row.pass ? 'in' : 'out'}">
        {row.person.name}{row.person.sender ? " (sender)" : ""}, {Math.round(row.distance)} m: {row.pass
          ? "gets it, "
          : "held back, "}{row.why}
      </li>
    {/each}
  </ul>

  <CodePanel {source} />
</Demo>
