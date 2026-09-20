<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const MARKS = Array.from({ length: 20 }, (_, i) => i)

  let lit = $state(0)
  let head = $state(0)
  let rate = $state(0)
  let stage = $state<HTMLDivElement | null>(null)

  const source = $derived(
    "game.stepped:connect(function(delta)\n" +
      "    -- delta is 0.050 seconds, twenty times a second\n" +
      "end)\n\n" +
      "game.renderStepped:connect(function(delta)\n" +
      "    -- delta is about " +
      (1 / Math.max(rate || 60, 1)).toFixed(3) +
      " seconds on this screen\n" +
      "end)",
  )

  let ticks = 0
  let frames = 0
  let countedAt = 0
  let tickedAt = 0

  whileVisible(
    () => stage,
    (now) => {
      if (!countedAt) {
        countedAt = now
        tickedAt = now
      }
      frames += 1
      if (now - tickedAt > 1000) tickedAt = now
      while (now - tickedAt >= 50) {
        tickedAt += 50
        ticks += 1
        lit = ticks % 20
      }
      head = ((now / 1000) % 1) * 100
      if (now - countedAt >= 1000) {
        rate = frames
        frames = 0
        countedAt = now
      }
    },
  )
</script>

<Demo label="The two clocks">
  <div class="demo-clocks" bind:this={stage}>
    <div class="clock-row">
      <span class="clock-name">game.stepped</span>
      <div class="clock-track clock-ticks">
        {#each MARKS as mark (mark)}
          <span class="clock-tick" class:on={mark === lit}></span>
        {/each}
      </div>
      <span class="clock-rate">20 /s</span>
    </div>
    <div class="clock-row">
      <span class="clock-name">game.renderStepped</span>
      <div class="clock-track">
        <span class="clock-head clock-head-client" style="left: {head.toFixed(2)}%"></span>
      </div>
      <span class="clock-rate">{rate} /s</span>
    </div>
  </div>

  <Note>
    The server steps 20 times a second whatever the frame rate is, so every player gets the same
    answer. The client draws every frame, which is why the camera belongs there.
  </Note>

  <CodePanel {source} />
</Demo>
