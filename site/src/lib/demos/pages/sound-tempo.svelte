<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { whileVisible } from "../core/frames"

  type Hit = { id: number; asked: number; at: number }

  let bpm = $state(100)
  let per = $state(4)

  let track = $state<HTMLDivElement | null>(null)
  let clock = $state(0)
  let hits = $state<Hit[]>([])
  let flashBeat = $state(0)
  let flashBar = $state(0)

  let lastBeat = -1
  let last = 0
  let next = 0

  const beatLength = $derived(60 / bpm)
  const barLength = $derived(beatLength * per)
  const span = $derived(barLength * 2)
  const start = $derived(Math.floor(clock / span) * span)
  const cells = $derived(Array.from({ length: per * 2 }, (_, i) => i))

  const marks = $derived(
    hits.map((hit) => {
      const from = Math.max(start, hit.asked)
      return {
        id: hit.id,
        at: hit.at,
        done: clock >= hit.at,
        left: ((from - start) / span) * 100,
        width: ((Math.min(hit.at, start + span) - from) / span) * 100,
        dot: ((hit.at - start) / span) * 100,
        shown: hit.at < start + span,
      }
    }),
  )

  const source = $derived(
    `-- client\naudio.tempo(${bpm}, ${per})\n` +
      "audio.beat:connect(function(n) end)\n" +
      "audio.bar:connect(function(n) end)\n" +
      'audio.stinger("res://sounds/hit.ogg", "bar")',
  )

  function stinger(kind: "beat" | "bar") {
    const step = kind === "bar" ? barLength : beatLength
    hits = [...hits, { id: next++, asked: clock, at: Math.ceil((clock + 0.0001) / step) * step }]
  }

  $effect(() => {
    void bpm
    void per
    clock = 0
    lastBeat = -1
    hits = []
  })

  whileVisible(
    () => track,
    (now) => {
      const dt = last ? Math.min(0.1, (now - last) / 1000) : 0
      last = now
      clock += dt

      const beat = Math.floor(clock / beatLength)
      if (beat !== lastBeat) {
        lastBeat = beat
        flashBeat = 1
        if (beat % per === 0) flashBar = 1
      }
      flashBeat = Math.max(0, flashBeat - dt * 5)
      flashBar = Math.max(0, flashBar - dt * 3)

      const floor = Math.floor(clock / span) * span
      hits = hits.filter((hit) => hit.at >= floor || clock < hit.at)
    },
  )
</script>

<Demo label="Tempo and stingers">
  <div class="sound-track" bind:this={track}>
    <div class="sound-beats">
      {#each cells as i (i)}
        <span class="sound-beat" class:sound-beat-bar={i % per === 0}>
          {i % per === 0 ? `bar ${Math.floor(i / per) + 1}` : ""}
        </span>
      {/each}
    </div>
    <div class="sound-head" style="left: {(((clock - start) / span) * 100).toFixed(2)}%"></div>
    <div class="sound-marks">
      {#each marks as mark (mark.id)}
        <span class="sound-wait" style="left: {mark.left.toFixed(2)}%; width: {mark.width.toFixed(2)}%"></span>
        {#if mark.shown}
          <span class="sound-hit" class:done={mark.done} style="left: {mark.dot.toFixed(2)}%"></span>
        {/if}
      {/each}
    </div>
  </div>

  <div class="sound-lamps">
    <span class="sound-lamp" style="opacity: {(0.3 + 0.7 * flashBeat).toFixed(2)}">beat</span>
    <span class="sound-lamp" style="opacity: {(0.3 + 0.7 * flashBar).toFixed(2)}">bar</span>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => stinger("beat")}>
      stinger on "beat"
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => stinger("bar")}>
      stinger on "bar"
    </button>
  </div>

  <Note>
    Press a stinger at any moment. It waits, the thin line, until the next beat or bar starts, and
    only then plays.
  </Note>

  <div class="demo-controls">
    <Slider label="bpm" min={60} max={180} step={5} bind:value={bpm} />
    <Slider label="beats per bar" min={2} max={7} step={1} bind:value={per} />
  </div>

  <CodePanel {source} />
</Demo>
