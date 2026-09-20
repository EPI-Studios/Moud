<script lang="ts">
  import { untrack } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"

  type Job = {
    map: string
    priority: number
    seq: number
    hiddenUntil: number | null
    readId: string | null
  }

  const MAPS = ["desert", "castle", "forest", "harbour", "mines"]
  const started = performance.now()

  function clock() {
    return (performance.now() - started) / 1000
  }

  let shelf = $state<HTMLDivElement | null>(null)
  let timeout = $state(10)
  let now = $state(0)
  let items = $state<Job[]>([])
  let waiting = $state(false)
  let lastRead = $state<string | null>(null)
  let body = $state("")
  let lines = $state<Line[]>([])

  let seq = 0
  let reads = 0
  let next = 0

  const source = $derived(`local jobs = memory:queue("jobs", ${timeout})\n\n${body}`)
  const order = $derived([...items].sort((a, b) => b.priority - a.priority || a.seq - b.seq))
  const hiddenCount = $derived(items.filter((item) => item.hiddenUntil !== null).length)
  const sizes = $derived(
    `jobs:getSize()  ${items.length}     jobs:getSize(true)  ${items.length - hiddenCount}` +
      (waiting ? "     a reader is waiting" : ""),
  )

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function deliver() {
    if (!waiting) return
    const ready = items
      .filter((item) => item.hiddenUntil === null)
      .sort((a, b) => b.priority - a.priority || a.seq - b.seq)
    if (!ready.length) return
    const item = ready[0]
    reads += 1
    item.hiddenUntil = clock() + timeout
    item.readId = `read${reads}`
    lastRead = item.readId
    waiting = false
    record(
      `readAsync(1) gave { map = "${item.map}" } with id ${item.readId}, hidden for ${timeout} s`,
      "in",
    )
  }

  function refresh() {
    const t = clock()
    for (const item of items) {
      if (item.hiddenUntil !== null && item.hiddenUntil <= t) {
        item.hiddenUntil = null
        item.readId = null
        record(`"${item.map}" was never removed, so it shows up again`, "out")
      }
    }
    deliver()
  }

  function add(priority: number) {
    const map = MAPS[seq % MAPS.length]
    seq += 1
    items.push({ map, priority, seq, hiddenUntil: null, readId: null })
    body = priority
      ? `jobs:addAsync({ map = "${map}" }, 600, ${priority})    -- higher priority, read first`
      : `jobs:addAsync({ map = "${map}" }, 600)`
    record(`added "${map}" at priority ${priority}`)
  }

  function read() {
    body = "local values, id = jobs:readAsync(1)\nstartMatch(values[1])\njobs:removeAsync(id)"
    waiting = true
    const before = reads
    deliver()
    if (reads === before) record("nothing can be read, so readAsync waits for a value", "idle")
  }

  function remove() {
    const id = lastRead
    const gone = items.filter((item) => item.readId === id && item.hiddenUntil !== null)
    items = items.filter((item) => !gone.includes(item))
    body = `jobs:removeAsync(${id})`
    record(
      gone.length
        ? `removed "${gone[0].map}" for good`
        : `${id} removes nothing: its value showed up again, and only a new read can take it`,
      gone.length ? "in" : "out",
    )
    lastRead = null
  }

  add(0)
  add(10)
  lines = []
  record("castle is priority 10, so it is read before desert", "idle")

  let noted = untrack(() => timeout)

  $effect(() => {
    if (timeout === noted) return
    noted = timeout
    body = `-- the next read hides its value for ${timeout} seconds`
  })

  whileVisible(
    () => shelf,
    () => {
      now = clock()
      refresh()
    },
  )
</script>

<Demo label="A queue">
  <div class="mm-actions">
    <button type="button" class="demo-pill mm-button" onclick={() => add(0)}>
      jobs:addAsync(value, 600)
    </button>
    <button type="button" class="demo-pill mm-button" onclick={() => add(10)}>
      jobs:addAsync(value, 600, 10)
    </button>
    <button type="button" class="demo-pill mm-button" onclick={read}>jobs:readAsync(1)</button>
    <button type="button" class="demo-pill mm-button" onclick={remove} disabled={lastRead === null}>
      jobs:removeAsync({lastRead ?? "id"})
    </button>
  </div>

  <div class="mm-queue" bind:this={shelf}>
    {#if !items.length}
      <span class="mm-empty">the queue is empty</span>
    {/if}
    {#each order as item (item.seq)}
      <div class="mm-item" class:mm-hidden={item.hiddenUntil !== null}>
        <span class="mm-map">{item.map}</span>
        <span class="mm-meta">priority {item.priority}</span>
        <span class="mm-meta">
          {item.hiddenUntil !== null
            ? `hidden by ${item.readId}, ${Math.max(0, item.hiddenUntil - now).toFixed(1)} s`
            : "can be read"}
        </span>
      </div>
    {/each}
  </div>

  <div class="mm-sizes">{sizes}</div>

  <div class="demo-controls">
    <Slider label="invisibility" min={3} max={30} step={1} bind:value={timeout} />
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
