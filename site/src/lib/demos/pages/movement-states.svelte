<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log, { type Line } from "../ui/Log.svelte"

  type State = "standing" | "running" | "jumping" | "falling" | "seated" | "dead"
  const SWITCHES = ["jumping", "seated", "dead"] as const
  type Switch = (typeof SWITCHES)[number]

  let stance = $state<State>("standing")
  let health = $state(20)
  let walking = false
  let enabled = $state<Record<Switch, boolean>>({ jumping: true, seated: true, dead: true })
  let shield = $state(false)
  let lastLine = $state("")
  let lines = $state<Line[]>([{ id: 0, text: "a player's body, standing", kind: "idle" }])
  let nextLine = 1
  let token = 0

  function num(value: number, places = 2) {
    const fixed = value.toFixed(places)
    return fixed.indexOf(".") === -1 ? fixed : fixed.replace(/0+$/, "").replace(/\.$/, "")
  }

  function record(message: string, kind?: Line["kind"]) {
    lines = [{ id: nextLine++, text: message, kind }, ...lines].slice(0, 7)
  }

  function grounded() {
    return stance === "standing" || stance === "running"
  }

  function setState(next: State) {
    const before = stance
    if (before === next) return
    stance = next
    if (before === "running") record("running(0)")
    if (before === "jumping") record("jumping(false)")
    if (before === "falling") record("freeFalling(false)", "in")
    if (before === "seated") record("seated(false)")
    record(`stateChanged, state is ${next}`)
    if (next === "running") record("running(6)")
    if (next === "jumping") record("jumping(true)")
    if (next === "falling") record("freeFalling(true)")
    if (next === "seated") record("seated(true)")
    if (next === "dead") record("died", "out")
  }

  const source = $derived.by(() => {
    const head: string[] = []
    for (const name of SWITCHES) {
      if (!enabled[name]) head.push(`h:setStateEnabled("${name}", false)`)
    }
    if (shield) head.push('local shield = body:add("ForceField")')
    return (
      "local h = body.humanoid\n" +
      (head.length ? `${head.join("\n")}\n` : "") +
      (lastLine ? `\n${lastLine}` : "")
    )
  })

  const healthText = $derived(
    `health ${health > 0 && health < 1 ? health.toFixed(2) : num(health, 0)} / 20`,
  )

  function jump() {
    if (stance === "dead") return
    if (stance === "seated") {
      record("jumping stands a sitting player up", "idle")
      setState("standing")
      lastLine = "-- the player pressed jump"
      return
    }
    if (!enabled.jumping) {
      record("nothing: jumping is off, so the player has no jump control", "out")
      lastLine = "-- the player pressed jump"
      return
    }
    if (!grounded()) return
    const mine = ++token
    setState("jumping")
    lastLine = "-- the player pressed jump"
    setTimeout(() => {
      if (mine !== token || stance !== "jumping") return
      setState("falling")
      setTimeout(() => {
        if (mine !== token || stance !== "falling") return
        setState(walking ? "running" : "standing")
      }, 450)
    }, 450)
  }

  function walk() {
    walking = true
    if (grounded()) setState("running")
    lastLine = "-- the player holds forward"
  }

  function stop() {
    walking = false
    if (stance === "running") setState("standing")
    lastLine = "-- the player lets go"
  }

  function sit() {
    if (stance === "dead" || !grounded()) return
    if (!enabled.seated) {
      record("nothing: seated is off, so the body cannot sit", "out")
    } else {
      walking = false
      setState("seated")
    }
    lastLine = "h.sit = true"
  }

  function damage() {
    lastLine = "h:takeDamage(5)"
    if (health <= 0) return
    if (shield) {
      record("nothing: a ForceField is inside the body", "out")
      return
    }
    health = Math.max(0, health - 5)
    record("damaged(5)")
    if (health <= 0 && !enabled.dead) health = 0.01
    record(`healthChanged, health is ${health < 1 ? health.toFixed(2) : health}`)
    if (health <= 0) {
      token++
      setState("dead")
    }
  }

  function respawn() {
    token++
    stance = "standing"
    health = 20
    walking = false
    enabled = { jumping: true, seated: true, dead: true }
    shield = false
    lines = []
    record("a new body: full health and every state on again", "in")
    lastLine = ""
  }

  function flip(name: Switch) {
    const now = !enabled[name]
    enabled[name] = now
    record(`setStateEnabled("${name}", ${now})`, "idle")
    if (name === "seated" && !now && stance === "seated") setState("standing")
    lastLine = `h:setStateEnabled("${name}", ${now})`
  }

  function flipShield() {
    shield = !shield
    record(shield ? "a ForceField went into the body" : "shield:destroy()", "idle")
    lastLine = shield ? "" : "shield:destroy()"
  }
</script>

<Demo label="State and events">
  <div class="move-status">
    <span class="move-state">{stance}</span>
    <div class="move-health">
      <div class="move-health-fill" class:low={health <= 5} style="width: {(health / 20) * 100}%">
      </div>
    </div>
    <span class="move-health-text">{healthText}</span>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={jump}>player jumps</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={walk}>player walks</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={stop}>player stops</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={sit}>h.sit = true</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={damage}>h:takeDamage(5)</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={respawn}>respawn</button>
  </div>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">states on</span>
      <div class="demo-choice-buttons">
        {#each SWITCHES as name (name)}
          <button
            type="button"
            class="demo-pill"
            aria-pressed={enabled[name]}
            onclick={() => flip(name)}>{name}</button
          >
        {/each}
      </div>
    </div>
    <div class="demo-choice">
      <span class="demo-slider-name">inside the body</span>
      <div class="demo-choice-buttons">
        <button type="button" class="demo-pill" aria-pressed={shield} onclick={flipShield}>
          ForceField
        </button>
      </div>
    </div>
  </div>

  <Log {lines} keep={7} class="move-log" />
  <CodePanel {source} />
</Demo>
