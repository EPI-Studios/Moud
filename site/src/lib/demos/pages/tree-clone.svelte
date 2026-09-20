<script lang="ts">
  import Demo from "../ui/Demo.svelte"

  type Node = {
    id: number
    klass: string
    name: string
    tags: string[]
    parent: Node | null
    children: Node[]
  }
  type Spec = [string, string, Spec[]?]
  type Row = { id: number; depth: number; klass: string; name: string; mark: string }
  type Line = { id: number; text: string; kind: string }

  const PARENTS: Record<string, string> = {
    Folder: "Instance",
    Spatial: "Instance",
    Part: "Spatial",
    Limb: "Part",
    Model: "Spatial",
    Character: "Spatial",
    Light: "Spatial",
    PointLight: "Light",
    World: "Instance",
  }

  const ICONS: Record<string, string> = {
    Folder: "Folder",
    Model: "PackedScene",
    Part: "MeshInstance3D",
    Limb: "Mesh",
    Character: "CharacterBody3D",
    Light: "OmniLight3D",
    Spatial: "Node3D",
  }

  function iconOf(klass: string) {
    let at: string | undefined = klass
    while (at) {
      if (ICONS[at]) return ICONS[at]
      at = PARENTS[at]
    }
    return "Node3D"
  }

  let nextId = 0

  function make(spec: Spec, parent: Node | null = null): Node {
    const node: Node = {
      id: nextId++,
      klass: spec[0],
      name: spec[1],
      tags: [],
      parent,
      children: [],
    }
    for (const child of spec[2] ?? []) node.children.push(make(child, node))
    return node
  }

  function walk(node: Node, depth: number, visit: (node: Node, depth: number) => void) {
    visit(node, depth)
    for (const child of node.children) walk(child, depth + 1, visit)
  }

  function find(node: Node, name: string) {
    return node.children.find((child) => child.name === name) as Node
  }

  let world: Node
  let car: Node
  let garage: Node

  let rows = $state<Row[]>([])
  let lines = $state<Line[]>([])
  let nextLine = 0

  function logRow(text: string, kind: string) {
    lines = [...lines, { id: nextLine++, text, kind }].slice(-12)
  }

  function draw(fresh: Node[]) {
    const out: Row[] = []
    walk(world, 0, (node, depth) => {
      out.push({
        id: node.id,
        depth,
        klass: node.klass,
        name: node.name,
        mark: fresh.includes(node) ? "hit" : node === car ? "tree-self" : "",
      })
    })
    rows = out
  }

  function copy(node: Node, parent: Node | null): Node {
    const made: Node = {
      id: nextId++,
      klass: node.klass,
      name: node.name,
      tags: node.tags.slice(),
      parent,
      children: [],
    }
    for (const child of node.children) made.children.push(copy(child, made))
    return made
  }

  function clone(into: Node | null, text: string) {
    lines = []
    logRow(text, "tree-cmd")
    const parent = into ?? (car.parent as Node)
    if (!parent.parent && parent !== world) {
      logRow(`error: cannot clone into ${parent.name}, it has been destroyed`, "out")
      return
    }
    const made = copy(car, parent)
    parent.children.push(made)
    logRow(`${parent === world ? "world" : parent.name}.childAdded(car)`, "in")
    const fresh: Node[] = []
    walk(made, 0, (node) => {
      fresh.push(node)
      logRow(`world.descendantAdded(${node.name})`, "in")
    })
    logRow("nothing fired while the copy was built", "idle")
    draw(fresh)
  }

  function wreck() {
    lines = []
    logRow("garage:destroy()", "tree-cmd")
    if (!garage.parent) {
      logRow("garage is already destroyed; the variable still holds it", "idle")
      return
    }
    garage.parent.children.splice(garage.parent.children.indexOf(garage), 1)
    garage.parent = null
    logRow("the variable garage now holds a destroyed instance", "out")
    draw([])
  }

  function reset() {
    world = make([
      "World",
      "World",
      [
        ["Model", "car", [["Part", "body"], ["Part", "wheel"]]],
        ["Folder", "garage"],
      ],
    ])
    car = find(world, "car")
    garage = find(world, "garage")
    lines = []
    logRow("world.descendantAdded and every childAdded are connected", "idle")
    draw([])
  }

  reset()
</script>

<Demo label="Copies">
  <ul class="demo-tree tree-list">
    {#each rows as row (row.id)}
      <li class="demo-tree-row tree-row {row.mark}" style="padding-left: {12 + row.depth * 18}px">
        <img class="engine-icon" src="/art/icons/{iconOf(row.klass)}.png" alt="" width="16" height="16" />
        <span class="tree-name">{row.name}</span>
        {#if row.depth > 0}
          <span class="tree-class">{row.klass}</span>
        {/if}
      </li>
    {/each}
  </ul>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide tree-action"
      onclick={() => clone(null, "local second = car:clone()")}
    >
      local second = car:clone()
    </button>
    <button
      type="button"
      class="demo-pill demo-pill-wide tree-action"
      onclick={() => clone(garage, "local third = car:clone(garage)")}
    >
      local third = car:clone(garage)
    </button>
    <button type="button" class="demo-pill demo-pill-wide tree-action" onclick={wreck}>
      garage:destroy()
    </button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={reset}>reset</button>
  </div>

  <ul class="demo-log tree-log">
    {#each lines as line (line.id)}
      <li class="demo-log-row {line.kind}">{line.text}</li>
    {/each}
  </ul>
</Demo>
