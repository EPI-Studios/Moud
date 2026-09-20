<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const TAGS = ["wave", "bounce", "shake", "pulse", "fade", "spin"] as const
  type Tag = (typeof TAGS)[number]

  const TEXT = "treasure ahead"
  const chars = TEXT.split("")

  let on = $state<Record<Tag, boolean>>({
    wave: true,
    bounce: false,
    shake: false,
    pulse: false,
    fade: false,
    spin: false,
  })
  let strength = $state(1)
  let speed = $state(1)

  let screen = $state<HTMLDivElement | null>(null)
  let motion = $state(chars.map(() => ({ transform: "", opacity: "1" })))

  const source = $derived.by(() => {
    const active = TAGS.filter((tag) => on[tag])
    const args = (strength !== 1 ? ` strength=${strength}` : "") + (speed !== 1 ? ` speed=${speed}` : "")
    const open = active.map((tag) => `<${tag}${args}>`).join("")
    const close = [...active].reverse().map((tag) => `</${tag}>`).join("")
    return (
      `game.chat:system("${open}${TEXT}${close}")` +
      (active.length > 1 ? "\n-- they stack: every tag moves the same letters" : "") +
      (active.length ? "" : "\n-- no moving tag, so the letters stand still")
    )
  })

  function hash(a: number, b: number) {
    const h = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return h - Math.floor(h)
  }

  whileVisible(
    () => screen,
    (now) => {
      if (!screen) return
      const time = now / 1000
      const px = parseFloat(getComputedStyle(screen).fontSize) || 20
      const unit = px / 9
      const reach = strength * unit

      motion = chars.map((_, i) => {
        let dx = 0
        let dy = 0
        let alpha = 1
        let spin = 0
        if (on.wave) dy += Math.sin(time * speed * 5 + i * 0.6) * reach
        if (on.bounce) dy -= Math.abs(Math.sin(time * speed * 4 + i * 0.35)) * reach * 2
        if (on.shake) {
          const tick = Math.floor(time * 30 * speed)
          dx += (hash(i, tick) - 0.5) * reach
          dy += (hash(i + 7919, tick) - 0.5) * reach
        }
        if (on.pulse) alpha *= 0.55 + 0.45 * Math.sin(time * speed * 4)
        if (on.fade) alpha *= 0.5 + 0.5 * Math.sin(time * speed * 3 + i * 0.4)
        if (on.spin) spin += time * speed * 3
        return {
          transform: `translate(${dx.toFixed(2)}px,${dy.toFixed(2)}px) rotate(${spin.toFixed(3)}rad)`,
          opacity: Math.max(0, alpha).toFixed(3),
        }
      })
    },
  )
</script>

<Demo label="Moving text">
  <div class="demo-chat rt-moving" bind:this={screen}>{#each chars as ch, i (i)}<span class="rt-letter" style="transform: {motion[i].transform}; opacity: {motion[i].opacity}">{ch === " " ? " " : ch}</span>{/each}</div>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">tags</span>
      <div class="demo-choice-buttons">
        {#each TAGS as tag (tag)}
          <button
            type="button"
            class="demo-pill"
            aria-pressed={on[tag]}
            onclick={() => (on[tag] = !on[tag])}
          >
            {tag}
          </button>
        {/each}
      </div>
    </div>
    <Slider label="strength" min={0} max={4} step={0.5} bind:value={strength} />
    <Slider label="speed" min={0} max={3} step={0.25} bind:value={speed} />
  </div>

  <CodePanel {source} />
</Demo>
