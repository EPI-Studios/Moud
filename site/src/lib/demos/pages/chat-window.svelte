<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { ease } from "../core/easing"
  import { whileVisible } from "../core/frames"

  type Line = {
    id: number
    text: string
    born: number
    removed: number
    animation: string
    shown: string
    alpha: number
    dx: number
    dy: number
    scale: number
    hidden: boolean
  }

  const ANIMATIONS = ["none", "fade", "slideLeft", "slideRight", "slideUp", "slideDown", "pop", "typewriter"]
  const ANIM = 0.2
  const WORDS = [
    "Round starts in 10", "meek: anyone got a pickaxe?", "ana joined the game", "The gate is open",
    "bo: over here", "Wave 3 begins", "cyd found treasure", "dee: gg",
  ]

  let screen = $state<HTMLDivElement | null>(null)
  let visibleTime = $state(4)
  let fadeTime = $state(0.5)
  let enter = $state("slideLeft")
  let exit = $state("fade")
  let open = $state("closed")
  let lines = $state<Line[]>([])

  let clock = 0
  let count = 0
  let next = 0

  const source = $derived(
    "-- client\n" +
      'world:add("ChatWindow", {\n' +
      `    visibleTime = ${visibleTime}, fadeTime = ${fadeTime},\n` +
      `    enterAnimation = "${enter}", exitAnimation = "${exit}",\n` +
      '    animationTime = 0.2, easing = "quad",\n' +
      "})",
  )

  function push() {
    const text = WORDS[count % WORDS.length]
    count += 1
    lines.push({
      id: next++,
      text,
      born: clock,
      removed: -1,
      animation: enter,
      shown: text,
      alpha: 1,
      dx: 0,
      dy: 0,
      scale: 1,
      hidden: false,
    })
    while (lines.length > 8) lines.shift()
  }

  function remove() {
    for (let i = lines.length - 1; i >= 0; i--) {
      if (lines[i].removed < 0) {
        lines[i].removed = clock
        return
      }
    }
  }

  let last = 0
  whileVisible(
    () => screen,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      clock += dt
      for (let i = lines.length - 1; i >= 0; i--) {
        const line = lines[i]
        const age = clock - line.born
        let alpha = 1
        let hidden = false
        if (open === "closed" && visibleTime > 0 && line.removed < 0) {
          if (age > visibleTime + fadeTime) hidden = true
          else if (age > visibleTime) alpha = 1 - (age - visibleTime) / Math.max(0.0001, fadeTime)
        }
        const entering = Math.min(1, age / ANIM)
        const leaving = line.removed < 0 ? 0 : Math.min(1, (clock - line.removed) / ANIM)
        if (leaving >= 1) {
          lines.splice(i, 1)
          continue
        }
        const progress = ease("quad", "out", entering) * (1 - ease("quad", "in", leaving))
        const motion = leaving > 0 ? exit : line.animation
        let dx = 0
        let dy = 0
        let scale = 1
        let text = line.text
        if (motion === "fade") alpha *= progress
        else if (motion === "slideLeft") dx = -(1 - progress) * 100
        else if (motion === "slideRight") dx = (1 - progress) * 100
        else if (motion === "slideUp") dy = (1 - progress) * 100
        else if (motion === "slideDown") dy = -(1 - progress) * 100
        else if (motion === "typewriter") text = line.text.slice(0, Math.ceil(progress * line.text.length))
        else if (motion === "pop" && progress < 1) scale = 0.5 + 0.5 * progress
        line.hidden = hidden
        line.alpha = Math.max(0, alpha)
        line.dx = dx
        line.dy = dy
        line.scale = scale
        line.shown = text
      }
    },
  )

  push()
</script>

<Demo label="Lines that fade">
  <div bind:this={screen} class="chat-screen chat-window-screen" class:open={open === "open"}>
    <div class="chat-window-box">
      {#each lines as line (line.id)}
        <div
          class="chat-window-line"
          style:display={line.hidden ? "none" : null}
          style:opacity={line.alpha.toFixed(3)}
          style:transform="translate({line.dx.toFixed(1)}%, {line.dy.toFixed(1)}%) scale({line.scale.toFixed(3)})"
        >
          <span class="chat-window-text">{line.shown}</span>
        </div>
      {/each}
    </div>
    <div class="chat-open-bar">Say something</div>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={push}>game.chat:send</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={remove}>
      game.chat:delete the newest
    </button>
  </div>

  <Note>
    A line stays fully visible for visibleTime, then fades over fadeTime. With the chat open nothing
    fades. exitAnimation plays when a line is deleted; visibleTime 0 never fades.
  </Note>

  <div class="demo-controls">
    <Slider label="visibleTime" min={0} max={10} step={0.5} bind:value={visibleTime} />
    <Slider label="fadeTime" min={0} max={3} step={0.1} bind:value={fadeTime} />
    <div class="demo-choice">
      <span class="demo-slider-name">enterAnimation</span>
      <select class="demo-select" bind:value={enter}>
        {#each ANIMATIONS as name (name)}
          <option value={name}>{name}</option>
        {/each}
      </select>
    </div>
    <div class="demo-choice">
      <span class="demo-slider-name">exitAnimation</span>
      <select class="demo-select" bind:value={exit}>
        {#each ANIMATIONS as name (name)}
          <option value={name}>{name}</option>
        {/each}
      </select>
    </div>
    <Choice label="chat" options={["closed", "open"]} bind:value={open} />
  </div>

  <CodePanel {source} />
</Demo>
