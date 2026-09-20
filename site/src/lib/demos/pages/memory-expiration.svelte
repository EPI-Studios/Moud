<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"

  type Entry = { joined: number; until: number; life: number }

  const KEYS: [string, number][] = [
    ["meek", 10],
    ["ana", 20],
    ["bo", 30],
  ]

  const started = performance.now()

  let rows = $state<HTMLDivElement | null>(null)
  let now = $state(0)
  let entries = $state<Record<string, Entry>>({
    meek: { joined: 0, until: 10, life: 10 },
    ana: { joined: 0, until: 20, life: 20 },
  })

  const bars = $derived(
    KEYS.map(([key]) => {
      const entry = entries[key]
      const live = entry !== undefined && entry.until > now
      return {
        key,
        live,
        fraction: live ? (entry.until - now) / entry.life : 0,
        left: live ? `${(entry.until - now).toFixed(1)} s` : entry ? "expired" : "never set",
      }
    }),
  )

  const source = $derived.by(() => {
    const lines = bars.map(({ key, live }) => {
      const entry = entries[key]
      const value = live ? `{ joined = ${entry.joined.toFixed(1)} }` : "nil"
      return `waiting:get("${key}")${" ".repeat(5 - key.length)}   -- ${value}`
    })
    const alive = bars.filter((bar) => bar.live).map((bar) => bar.key).sort()
    lines.push(
      `waiting:list()         -- { ${alive.map((key) => `"${key}"`).join(", ")}${alive.length ? " }" : "}"}`,
    )
    return `-- clock() is ${now.toFixed(0)}\n${lines.join("\n")}`
  })

  function set(key: string, life: number) {
    const at = (performance.now() - started) / 1000
    entries[key] = { joined: at, until: at + life, life }
  }

  whileVisible(
    () => rows,
    () => {
      now = (performance.now() - started) / 1000
    },
  )
</script>

<Demo label="Everything expires">
  <div class="mm-actions">
    {#each KEYS as [key, life] (key)}
      <button type="button" class="demo-pill mm-button" onclick={() => set(key, life)}>
        waiting:set("{key}", ..., {life})
      </button>
    {/each}
  </div>

  <div class="mm-rows" bind:this={rows}>
    {#each bars as bar (bar.key)}
      <div class="mm-row" class:mm-gone={!bar.live}>
        <span class="mm-key">{bar.key}</span>
        <div class="mm-bar"><div class="mm-fill" style="width: {(bar.fraction * 100).toFixed(1)}%"></div></div>
        <span class="mm-left">{bar.left}</span>
      </div>
    {/each}
  </div>

  <Note>
    Writing a key again starts its expiration over. Once it runs out, get gives nil and list leaves the key out.
  </Note>
  <CodePanel {source} />
</Demo>
