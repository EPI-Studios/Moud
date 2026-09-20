<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import type { Line } from "../ui/Log.svelte"

  const SOURCE = `window.closing:connect(function()
    window:preventClose()
    saveMenu.enabled = true               -- "quit without saving?"
end)`

  let closed = $state(false)
  let asking = $state(false)
  let fill = $state(0)
  let label = $state("")
  let lines = $state<Line[]>([{ id: 0, text: "click the window's x", kind: undefined }])
  let desk: HTMLDivElement | null = $state(null)

  let heldAt: number | null = null
  let next = 1

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  whileVisible(
    () => desk,
    () => {
      if (heldAt === null || closed) {
        fill = 0
        label = ""
        return
      }
      const passed = (performance.now() - heldAt) / 1000
      if (passed >= 10) {
        heldAt = null
        fill = 0
        label = ""
        record("10 seconds passed: the next close can be held again", "idle")
        return
      }
      fill = (1 - passed / 10) * 100
      label = "a second close within " + (10 - passed).toFixed(1) + " s always closes"
    },
  )

  function attempt() {
    if (closed) return
    if (heldAt !== null) {
      closed = true
      record("closed again within 10 seconds: the window closes, closing does not hold it", "out")
      return
    }
    heldAt = performance.now()
    asking = true
    record("closing fired, preventClose() held the window open", "in")
  }

  function again() {
    closed = false
    heldAt = null
    asking = false
    lines = [{ id: next++, text: "click the window's x", kind: undefined }]
  }
</script>

<Demo label="Holding the window open">
  <div class="window-desk window-desk-short" bind:this={desk}>
    <div class="window-win" style:visibility={closed ? "hidden" : "visible"}>
      <div class="window-bar">
        <span class="window-title">Crystal Rush</span>
        <button type="button" class="window-x" aria-label="close" onclick={attempt}>x</button>
      </div>
      <div class="window-inside">
        <div class="window-dialog" style:display={asking ? "flex" : "none"}>quit without saving?</div>
      </div>
    </div>
    <div class="window-gone" style:display={closed ? "flex" : "none"}>the game closed</div>
  </div>

  <div class="window-power">
    <div class="window-power-fill" style="width: {fill}%"></div>
    <span class="window-power-label">{label}</span>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={attempt}>
      press the close button
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={again}>start again</button>
  </div>

  <Log {lines} keep={5} />
  <CodePanel source={SOURCE} />
</Demo>
