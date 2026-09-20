<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type SortKey = number | string | undefined
  type Item = { key: string; sortKey?: SortKey }
  type Bound = { key?: string; sortKey?: SortKey }

  const DIRECTIONS = ["ascending", "descending"] as const

  function rank(sortKey: SortKey) {
    if (typeof sortKey === "number") return 0
    if (typeof sortKey === "string") return 1
    return 2
  }

  function sameRank(a: SortKey, b: SortKey) {
    if (typeof a === "number" && typeof b === "number") return a < b ? -1 : a > b ? 1 : 0
    if (typeof a === "string" && typeof b === "string") return a < b ? -1 : a > b ? 1 : 0
    return 0
  }

  function compare(a: Item, b: Item) {
    let r = rank(a.sortKey) - rank(b.sortKey)
    if (r === 0) r = sameRank(a.sortKey, b.sortKey)
    if (r !== 0) return r
    return a.key < b.key ? -1 : a.key > b.key ? 1 : 0
  }

  function against(item: Item, bound: Bound, lower: boolean) {
    let r = rank(item.sortKey) - rank(bound.sortKey)
    if (r === 0) r = sameRank(item.sortKey, bound.sortKey)
    if (r !== 0) return r
    if (bound.key === undefined) return lower ? -1 : 1
    return item.key < bound.key ? -1 : item.key > bound.key ? 1 : 0
  }

  function show(value: SortKey) {
    if (value === undefined) return "nil"
    if (typeof value === "string") return `"${value}"`
    return String(value)
  }

  function boundText(bound: Bound) {
    const parts: string[] = []
    if (bound.key !== undefined) parts.push(`key = ${show(bound.key)}`)
    if (bound.sortKey !== undefined) parts.push(`sortKey = ${show(bound.sortKey)}`)
    return `{ ${parts.join(", ")} }`
  }

  const items: Item[] = [
    { key: "ana", sortKey: 42 },
    { key: "bo", sortKey: 7 },
    { key: "gus", sortKey: 42 },
    { key: "al", sortKey: -3 },
    { key: "Cy", sortKey: "gold" },
    { key: "dee", sortKey: "bronze" },
    { key: "eve" },
    { key: "Finn" },
  ].sort(compare)

  let direction = $state<(typeof DIRECTIONS)[number]>("ascending")
  let count = $state(3)
  let lower = $state<Bound | null>(null)
  let upper = $state<Bound | null>(null)

  const page = $derived.by(() => {
    const order = direction === "ascending" ? items : items.slice().reverse()
    const result: Item[] = []
    for (let i = 0; i < order.length && result.length < count; i++) {
      const item = order[i]
      if (lower && against(item, lower, true) <= 0) continue
      if (upper && against(item, upper, false) >= 0) continue
      result.push(item)
    }
    return result
  })

  const source = $derived.by(() => {
    let args = `"${direction}", ${count}`
    if (lower) args += `, ${boundText(lower)}`
    if (upper) args += `, nil, ${boundText(upper)}`
    return (
      `local page = scores:getRange(${args})\n` +
      (page.length
        ? page
            .map((item, i) => `-- ${i + 1}  ${item.key}${item.sortKey === undefined ? "" : `  sortKey ${show(item.sortKey)}`}`)
            .join("\n")
        : "-- an empty page: nothing is left past the bound")
    )
  })

  const note = $derived(
    direction === "descending"
      ? "Descending is the same order backwards. Bounds still count in ascending order, so the last row of a descending page is the upper bound of the next."
      : "Numbers first, then strings, then keys with no sort key. Capitals come before small letters, so Finn is before eve.",
  )

  function firstPage() {
    lower = null
    upper = null
  }

  function nextPage() {
    const last = page[page.length - 1]
    if (!last) return
    const bound: Bound = { key: last.key, sortKey: last.sortKey }
    if (direction === "ascending") lower = bound
    else upper = bound
  }

  $effect(() => {
    direction
    firstPage()
  })
</script>

<Demo label="Pages of a sorted map">
  <ul class="demo-tree mm-sorted">
    {#each items as item (item.key)}
      {@const at = page.indexOf(item)}
      <li class="demo-tree-row mm-sorted-row" class:hit={at !== -1}>
        <span class="mm-place">{at === -1 ? "" : at + 1}</span>
        <span class="mm-sk">{item.sortKey === undefined ? "no sort key" : `sortKey ${show(item.sortKey)}`}</span>
        <span>{item.key}</span>
      </li>
    {/each}
  </ul>

  <div class="demo-controls">
    <Choice label="direction" options={DIRECTIONS} bind:value={direction} />
    <Slider label="count" min={1} max={4} step={1} bind:value={count} />
    <div class="demo-choice">
      <span class="demo-slider-name">pages</span>
      <div class="demo-choice-buttons">
        <button type="button" class="demo-pill mm-button" onclick={firstPage}>first page</button>
        <button type="button" class="demo-pill mm-button" onclick={nextPage} disabled={page.length === 0}>
          next page
        </button>
      </div>
    </div>
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
