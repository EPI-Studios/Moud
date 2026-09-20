<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"

  type Props = Record<string, string | number | boolean>
  type Spec = { klass: string; name: string; props?: Props; tags?: string[]; children?: Spec[] }
  type Node = {
    klass: string
    name: string
    props?: Props
    tags?: string[]
    parent: Node | null
    depth: number
  }
  type Test = { key: string; op: string; value: string }
  type Step = { klass: string; name: string | null; tag: string | null; test: Test | null }

  const PARENTS: Record<string, string> = {
    MeshPart: "Part",
    Part: "Spatial",
    Character: "Spatial",
    Model: "Spatial",
    PointLight: "Light",
    SpotLight: "Light",
    Light: "Spatial",
    Spatial: "Instance",
    Folder: "Instance",
  }

  const TREE: Spec = {
    klass: "World",
    name: "world",
    children: [
      {
        klass: "Folder",
        name: "enemies",
        children: [
          { klass: "Character", name: "guard" },
          { klass: "Character", name: "archer", tags: ["boss"] },
        ],
      },
      {
        klass: "Model",
        name: "hut",
        children: [
          { klass: "Part", name: "roof", props: { anchored: true } },
          { klass: "Part", name: "door", props: { anchored: false }, tags: ["lava"] },
          { klass: "PointLight", name: "lamp", props: { brightness: 4 } },
        ],
      },
      { klass: "Part", name: "floor", props: { anchored: true } },
      { klass: "Character", name: "shopkeeper" },
    ],
  }

  function isA(node: Node, name: string) {
    if (name === "*") return true
    let at: string | undefined = node.klass
    while (at) {
      if (at === name) return true
      at = PARENTS[at]
    }
    return false
  }

  function parseStep(text: string): Step {
    const step: Step = { klass: "*", name: null, tag: null, test: null }
    let rest = text
    const attribute = rest.match(/\[([a-zA-Z]+)(!=|>=|<=|=|>|<)([^\]]*)\]/)
    if (attribute) {
      step.test = { key: attribute[1], op: attribute[2], value: attribute[3] }
      rest = rest.replace(attribute[0], "")
    }
    const name = rest.match(/#([A-Za-z0-9_]+)/)
    if (name) {
      step.name = name[1]
      rest = rest.replace(name[0], "")
    }
    const tag = rest.match(/\.([A-Za-z0-9_]+)/)
    if (tag) {
      step.tag = tag[1]
      rest = rest.replace(tag[0], "")
    }
    if (rest.trim()) step.klass = rest.trim()
    return step
  }

  function stepMatches(node: Node, step: Step) {
    if (!isA(node, step.klass)) return false
    if (step.name && node.name !== step.name) return false
    if (step.tag && !(node.tags ?? []).includes(step.tag)) return false
    if (!step.test) return true
    const actual = (node.props ?? {})[step.test.key]
    if (actual === undefined) return false
    const wanted = step.test.value
    if (step.test.op === "=") return String(actual) === wanted
    if (step.test.op === "!=") return String(actual) !== wanted
    const number = parseFloat(wanted)
    if (isNaN(number) || typeof actual !== "number") return false
    if (step.test.op === ">") return actual > number
    if (step.test.op === "<") return actual < number
    if (step.test.op === ">=") return actual >= number
    return actual <= number
  }

  function parseSelector(text: string) {
    const steps: { step: Step; joiner: string }[] = []
    let joiner = " "
    for (const token of text
      .trim()
      .replace(/\s*>\s*/g, " > ")
      .split(/\s+/)
      .filter(Boolean)) {
      if (token === ">") {
        joiner = ">"
        continue
      }
      steps.push({ step: parseStep(token), joiner })
      joiner = " "
    }
    return steps
  }

  function selects(node: Node, steps: { step: Step; joiner: string }[]) {
    if (!steps.length || !stepMatches(node, steps[steps.length - 1].step)) return false
    let at = node.parent
    for (let i = steps.length - 2; i >= 0; i--) {
      if (steps[i + 1].joiner === ">") {
        if (!at || !stepMatches(at, steps[i].step)) return false
        at = at.parent
        continue
      }
      while (at && !stepMatches(at, steps[i].step)) at = at.parent
      if (!at) return false
      at = at.parent
    }
    return true
  }

  function flatten(spec: Spec, parent: Node | null, depth: number, out: Node[]) {
    const node: Node = {
      klass: spec.klass,
      name: spec.name,
      props: spec.props,
      tags: spec.tags,
      parent,
      depth,
    }
    out.push(node)
    for (const child of spec.children ?? []) flatten(child, node, depth + 1, out)
    return out
  }

  function labelOf(node: Node) {
    let label = node.klass + (node.name ? ` "${node.name}"` : "")
    if (node.tags) label += ` .${node.tags.join(" .")}`
    for (const key of Object.keys(node.props ?? {})) label += ` [${key}=${(node.props ?? {})[key]}]`
    return label
  }

  const nodes = flatten(TREE, null, 0, [])

  let selector = $state("Model Part[anchored=false]")
  const steps = $derived(parseSelector(selector))
  const hits = $derived(nodes.map((node) => node.parent !== null && selects(node, steps)))
  const count = $derived(hits.filter(Boolean).length)
</script>

<Demo label="Selectors">
  <div class="demo-field">
    <span class="demo-field-prefix">world:query(</span>
    <input
      class="demo-input"
      type="text"
      spellcheck="false"
      aria-label="selector"
      bind:value={selector}
    />
    <span class="demo-field-prefix">)</span>
  </div>

  <ul class="demo-tree">
    {#each nodes as node, index (index)}
      <li class="demo-tree-row" class:hit={hits[index]} style="padding-left: {node.depth * 18}px">
        {labelOf(node)}
      </li>
    {/each}
  </ul>

  <Note
    >{count === 0
      ? "No instance matches."
      : count === 1
        ? "1 instance matches."
        : `${count} instances match.`}</Note
  >
</Demo>
