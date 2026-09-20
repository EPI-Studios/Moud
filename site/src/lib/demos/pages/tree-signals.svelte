<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

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

  const SOURCE = `map.descendantAdded:connect(function(instance) end)
map.descendantRemoving:connect(function(instance) end)
lid.ancestryChanged:connect(function(child, parent) end)`

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

  function walk(node: Node, visit: (node: Node) => void) {
    visit(node)
    for (const child of node.children.slice()) walk(child, visit)
  }

  function find(node: Node, name: string) {
    return node.children.find((child) => child.name === name) ?? null
  }

  let world: Node
  let map: Node
  let lid: Node
  let heard: [string, string, string][] = []

  let rows = $state<Row[]>([])
  let lines = $state<Line[]>([])
  let nextLine = 0

  function logRow(text: string, kind: string, limit = 12) {
    lines = [...lines, { id: nextLine++, text, kind }].slice(-limit)
  }

  function draw(fresh: Node[]) {
    const out: Row[] = []
    walkDepth(world, 0, (node, depth) => {
      out.push({
        id: node.id,
        depth,
        klass: node.klass,
        name: node.name,
        mark: fresh.includes(node) ? "hit" : node === map || node === lid ? "tree-self" : "",
      })
    })
    rows = out
  }

  function walkDepth(node: Node, depth: number, visit: (node: Node, depth: number) => void) {
    visit(node, depth)
    for (const child of node.children) walkDepth(child, depth + 1, visit)
  }

  function alive(node: Node) {
    for (let at: Node | null = node; at; at = at.parent) if (at === world) return true
    return false
  }

  function removing(node: Node) {
    for (let up = node.parent; up; up = up.parent) {
      if (up === map) heard.push(["map.descendantRemoving", node.name, "out"])
    }
  }

  function added(node: Node) {
    for (let up = node.parent; up; up = up.parent) {
      if (up === map) heard.push(["map.descendantAdded", node.name, "in"])
    }
  }

  function ancestry(node: Node, moved: Node) {
    if (node === lid) {
      heard.push([
        "lid.ancestryChanged",
        `${moved.name}, ${moved.parent ? moved.parent.name : "nil"}`,
        moved.parent ? "idle" : "out",
      ])
    }
    for (const child of node.children.slice()) ancestry(child, moved)
  }

  function destroy(node: Node) {
    removing(node)
    for (let i = node.children.length - 1; i >= 0; i--) destroy(node.children[i])
    const parent = node.parent
    if (parent) {
      const index = parent.children.indexOf(node)
      if (index !== -1) parent.children.splice(index, 1)
    }
    node.parent = null
    if (node === lid) heard.push(["lid.ancestryChanged", `${lid.name}, nil`, "out"])
  }

  function move(node: Node, parent: Node) {
    walk(node, removing)
    const from = node.parent
    if (from) from.children.splice(from.children.indexOf(node), 1)
    node.parent = parent
    parent.children.push(node)
    ancestry(node, node)
    walk(node, added)
  }

  function named(name: string) {
    let found: Node | null = null
    walk(world, (n) => {
      if (!found && n.name === name) found = n
    })
    return found as Node | null
  }

  const ACTIONS: { code: string; run: () => Node[] | string }[] = [
    {
      code: 'room:add("Part", { name = "stool" })',
      run: () => {
        const room = named("room")
        if (!room) return "room has been destroyed"
        const stool: Node = {
          id: nextId++,
          klass: "Part",
          name: "stool",
          tags: [],
          parent: room,
          children: [],
        }
        room.children.push(stool)
        added(stool)
        return [stool]
      },
    },
    {
      code: "crate.parent = room",
      run: () => {
        const room = named("room")
        const crate = named("crate")
        if (!room) return "room has been destroyed"
        if (!crate || !alive(crate)) return "crate has been destroyed"
        if (crate.parent === room) return "crate is already in room, nothing fires"
        move(crate, room)
        return [crate, lid]
      },
    },
    {
      code: "crate.parent = game.world",
      run: () => {
        const crate = named("crate")
        if (!crate) return "crate has been destroyed"
        if (crate.parent === world) return "crate is already in the world, nothing fires"
        move(crate, world)
        return [crate, lid]
      },
    },
    {
      code: "room:destroy()",
      run: () => {
        const room = named("room")
        if (!room) return "room has been destroyed"
        destroy(room)
        return []
      },
    },
  ]

  function reset() {
    world = make([
      "World",
      "World",
      [
        ["Folder", "map", [["Folder", "room", [["Part", "chair"]]]]],
        ["Folder", "crate", [["Part", "lid"]]],
      ],
    ])
    map = find(world, "map") as Node
    lid = find(find(world, "crate") as Node, "lid") as Node
    lines = []
    logRow("listening on map and on lid", "idle")
    draw([])
  }

  function perform(action: (typeof ACTIONS)[number]) {
    heard = []
    const fresh = action.run()
    lines = []
    logRow(action.code, "tree-cmd")
    if (typeof fresh === "string") {
      logRow(fresh, "out")
      return
    }
    if (!heard.length) logRow("nothing fires on map or lid", "idle")
    for (const [signal, argument, kind] of heard) logRow(`${signal}(${argument})`, kind, 20)
    draw(fresh)
  }

  reset()
</script>

<Demo label="Hierarchy signals">
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
    {#each ACTIONS as action (action.code)}
      <button type="button" class="demo-pill demo-pill-wide tree-action" onclick={() => perform(action)}>
        {action.code}
      </button>
    {/each}
    <button type="button" class="demo-pill demo-pill-wide" onclick={reset}>reset</button>
  </div>

  <ul class="demo-log tree-log">
    {#each lines as line (line.id)}
      <li class="demo-log-row {line.kind}">{line.text}</li>
    {/each}
  </ul>

  <CodePanel source={SOURCE} />
</Demo>
