<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Log from "../ui/Log.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Item = { id: string; klass: "Part" | "Model"; depth: number; locked?: boolean }
  type Weld = { name: string; inside: string; part0: string; part1: string; step: number }

  const ICONS: Record<string, string> = {
    Part: "MeshInstance3D",
    Model: "PackedScene",
    WeldConstraint: "RemoteTransform3D",
  }

  const ITEMS: Item[] = [
    { id: "cart", klass: "Part", depth: 0 },
    { id: "seat", klass: "Part", depth: 0 },
    { id: "wheel", klass: "Part", depth: 0 },
    { id: "lamp", klass: "Part", depth: 0, locked: true },
    { id: "crates", klass: "Model", depth: 0 },
    { id: "crateA", klass: "Part", depth: 1 },
    { id: "crateB", klass: "Part", depth: 1 },
  ]

  const FIRST: Weld = {
    name: "WeldConstraint",
    inside: "cart",
    part0: "cart",
    part1: "seat",
    step: 0,
  }

  let welds = $state<Weld[]>([{ ...FIRST }])
  let selection = $state<string[]>([])
  let history = $state<string[]>([])
  let lines = $state<Line[]>([{ id: 0, text: "cart and seat are already welded", kind: "idle" }])
  let next = 1

  const source = $derived.by(() => {
    const made = welds.filter((w) => w.step > 0)
    return made.length
      ? made
          .map((w) => w.inside + ':add("WeldConstraint", { part0 = ' + w.part0 + ", part1 = " + w.part1 + " })")
          .join("\n")
      : "-- click parts in the Explorer, then press Ctrl+W"
  })

  function record(message: string, kind: Line["kind"]) {
    lines = [{ id: next++, text: message, kind }, ...lines]
  }

  function pick(id: string) {
    const at = selection.indexOf(id)
    if (at === -1) selection = [...selection, id]
    else selection = selection.filter((other) => other !== id)
  }

  function welded(a: string, b: string) {
    return welds.some((w) => (w.part0 === a && w.part1 === b) || (w.part0 === b && w.part1 === a))
  }

  function nameFor(inside: string) {
    const count = welds.filter((w) => w.inside === inside).length
    return count === 0 ? "WeldConstraint" : "WeldConstraint" + (count + 1)
  }

  function weld() {
    const parts = selection.filter((id) => ITEMS.filter((i) => i.id === id)[0].klass === "Part")
    const skipped = selection.length - parts.length
    if (parts.length < 2) {
      record("refused: fewer than two parts selected" + (skipped ? ", and a model is not looked into" : ""), "out")
      return
    }
    const first = parts[0]
    const step = history.length + 1
    let made = 0
    let already = 0
    for (const other of parts.slice(1)) {
      if (welded(first, other)) {
        already++
        continue
      }
      welds = [...welds, { name: nameFor(first), inside: first, part0: first, part1: other, step }]
      made++
    }
    if (!made) {
      record("nothing made: every pair is already welded", "out")
      return
    }
    const label = made === 1 ? "Weld" : "Weld " + made
    history = [...history, label]
    record(
      label + ": " + made + " weld" + (made === 1 ? "" : "s") + " inside " + first +
        (already ? ", " + already + " pair already welded" : "") +
        (skipped ? ", the model was skipped" : ""),
      "in",
    )
  }

  function undo() {
    if (!history.length) {
      record("nothing to undo", "idle")
      return
    }
    const step = history.length
    const label = history[step - 1]
    history = history.slice(0, -1)
    welds = welds.filter((w) => w.step !== step)
    record("undo " + label, "out")
  }

  function startOver() {
    welds = [{ ...FIRST }]
    selection = []
    history = []
    lines = [{ id: next++, text: "cart and seat are already welded", kind: "idle" }]
  }
</script>

<Demo label="Weld Selected">
  <ul class="demo-tree ed-tree" role="listbox" aria-label="parts">
    {#each ITEMS as item (item.id)}
      {@const at = selection.indexOf(item.id)}
      <li
        class="demo-tree-row ed-row"
        class:hit={at !== -1}
        style="padding-left: {12 + item.depth * 18}px"
        role="option"
        aria-selected={at !== -1}
        tabindex="0"
        onkeydown={(event) => event.key === "Enter" && pick(item.id)}
        onclick={() => pick(item.id)}
      >
        <img class="engine-icon" src="/art/icons/{ICONS[item.klass]}.png" alt="" width="16" height="16" />
        <span class="ed-row-name">{item.id}</span>
        {#if item.locked}
          <span class="ed-lock">
            <img class="engine-icon" src="/art/icons/Lock.png" alt="" width="12" height="12" />
          </span>
        {/if}
        {#if at !== -1}<span class="ed-order">{at + 1}</span>{/if}
      </li>
      {#each welds.filter((w) => w.inside === item.id) as made, index (index)}
        <li
          class="demo-tree-row ed-row ed-weld"
          class:ed-new={made.step === history.length && made.step > 0}
          style="padding-left: {12 + (item.depth + 1) * 18}px"
        >
          <img class="engine-icon" src="/art/icons/{ICONS.WeldConstraint}.png" alt="" width="16" height="16" />
          <span class="ed-row-name">{made.name}</span>
          <span class="ed-row-meta">{made.part0} + {made.part1}</span>
        </li>
      {/each}
    {/each}
  </ul>

  <Note>
    Clicking a row adds it to the selection, as Ctrl or Shift click does. The number is the order it
    was picked in.
  </Note>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={weld}>Ctrl+W</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={undo}>Undo</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => (selection = [])}>
      clear selection
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={startOver}>start over</button>
  </div>

  <Log {lines} keep={3} />
  <CodePanel {source} />
</Demo>
