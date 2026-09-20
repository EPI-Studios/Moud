<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  const SOURCE =
    "-- server\ngame.stepped:connect(function()\n" +
    '    print("players", game.players:count())\n' +
    '    game.debug:watch("players", game.players:count())\n' +
    "end)"

  let count = $state(3)
  let log = $state("")
  let logBox = $state<HTMLDivElement | null>(null)

  let lines: string[] = []
  let tickAt = 0
  let last = 0

  whileVisible(
    () => logBox,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      tickAt += dt
      let changed = false
      while (tickAt >= 0.05) {
        tickAt -= 0.05
        lines.push("[server] players\t" + count)
        changed = true
      }
      if (lines.length > 9) lines = lines.slice(lines.length - 9)
      if (changed) log = lines.join("\n")
    },
  )
</script>

<Demo label="print against watch">
  <div class="dbg-split">
    <div class="dbg-pane">
      <span class="dbg-pane-name">logs/latest.log</span>
      <div class="dbg-log" bind:this={logBox}>{log}</div>
    </div>
    <div class="dbg-pane dbg-screen">
      <span class="dbg-pane-name">top right of the screen</span>
      <div class="dbg-watch">
        <div class="dbg-watch-row">
          <span>players (server)</span>
          <span class="dbg-watch-value">{count}</span>
        </div>
      </div>
    </div>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => (count += 1)}>
      a player joins
    </button>
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => (count = Math.max(0, count - 1))}
    >
      a player leaves
    </button>
  </div>

  <CodePanel source={SOURCE} />
</Demo>
