<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"

  type Node = { klass: string; name: string; tags: string[]; parent: Node | null; children: Node[] }
  type Spec = [string, string, Spec[]?, string[]?]
  type Result = Node | Node[] | boolean | string | null

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

  function isA(node: Node, klass: string) {
    let at: string | undefined = node.klass
    while (at) {
      if (at === klass) return true
      at = PARENTS[at]
    }
    return false
  }

  function make(spec: Spec, parent: Node | null = null): Node {
    const node: Node = { klass: spec[0], name: spec[1], tags: spec[3] ?? [], parent, children: [] }
    for (const child of spec[2] ?? []) node.children.push(make(child, node))
    return node
  }

  function walk(node: Node, visit: (node: Node) => void) {
    visit(node)
    for (const child of node.children) walk(child, visit)
  }

  function below(node: Node) {
    const out: Node[] = []
    for (const child of node.children) walk(child, (n) => out.push(n))
    return out
  }

  function find(node: Node, name: string) {
    return node.children.find((child) => child.name === name) ?? null
  }

  function fullName(node: Node) {
    const parts: string[] = []
    for (let at: Node | null = node; at; at = at.parent) parts.unshift(at.name)
    return parts.join(".")
  }

  function isAncestor(a: Node, b: Node) {
    for (let at = b.parent; at; at = at.parent) if (at === a) return true
    return false
  }

  const world = make([
    "World",
    "World",
    [
      [
        "Folder",
        "map",
        [
          ["Folder", "room", [["Part", "chair"], ["PointLight", "lamp"]]],
          ["Model", "statue", [["Part", "base", [], ["lava"]]]],
        ],
      ],
      [
        "Folder",
        "enemies",
        [
          ["Character", "guard", [["Limb", "arm"]]],
          ["Character", "archer", [["Limb", "leg"]]],
        ],
      ],
    ],
  ])

  function at(path: string) {
    let node: Node | null = world
    for (const name of path.split(".")) node = node && find(node, name)
    return node as Node
  }

  const map = at("map")
  const room = at("map.room")
  const chair = at("map.room.chair")
  const arm = at("enemies.guard.arm")

  type Call = {
    label: string
    self: Node
    other?: Node
    code: string
    run: () => Result
    note: string
  }

  const CALLS: Call[] = [
    {
      label: "find",
      self: world,
      code: 'world:find("statue")',
      run: () => find(world, "statue"),
      note: "statue is inside map, not a direct child of the world, so find gives nil.",
    },
    {
      label: "findFirstDescendant",
      self: world,
      code: 'world:findFirstDescendant("statue")',
      run: () => below(world).filter((n) => n.name === "statue")[0] ?? null,
      note: "It keeps going down through map without you naming it.",
    },
    {
      label: "descendants",
      self: world,
      code: 'world:descendants("Part")',
      run: () => below(world).filter((n) => isA(n, "Part")),
      note: "A class matches its subclasses, so the limbs come back with the parts.",
    },
    {
      label: "childrenOfClass",
      self: room,
      code: 'room:childrenOfClass("Light")',
      run: () => room.children.filter((n) => isA(n, "Light")),
      note: "One level down only. The lamp is a PointLight, which is a Light.",
    },
    {
      label: "firstAncestorOfClass",
      self: arm,
      code: 'arm:firstAncestorOfClass("Character")',
      run: () => {
        for (let a = arm.parent; a; a = a.parent) if (isA(a, "Character")) return a
        return null
      },
      note: "From a limb upwards to the body it belongs to, the way a touch handler finds who stepped on something.",
    },
    {
      label: "firstAncestor",
      self: arm,
      code: 'arm:firstAncestor("enemies")',
      run: () => {
        for (let a = arm.parent; a; a = a.parent) if (a.name === "enemies") return a
        return null
      },
      note: "Upwards again, this time by name.",
    },
    {
      label: "byTag",
      self: world,
      code: 'world:byTag("lava")',
      run: () => below(world).filter((n) => n.tags.includes("lava")),
      note: "Every tagged instance below the one you call it on.",
    },
    {
      label: "firstChildOfClass",
      self: map,
      code: 'map:firstChildOfClass("Part")',
      run: () => map.children.filter((n) => isA(n, "Part"))[0] ?? null,
      note: "map holds a folder and a model, no part, so this is nil.",
    },
    {
      label: "firstChildOfClass, true",
      self: map,
      code: 'map:firstChildOfClass("Part", true)',
      run: () => below(map).filter((n) => isA(n, "Part"))[0] ?? null,
      note: "With true it searches every level below instead of only the children.",
    },
    {
      label: "isDescendantOf",
      self: chair,
      other: map,
      code: "chair:isDescendantOf(map)",
      run: () => isAncestor(map, chair),
      note: "isAncestorOf asks the same question from the other end: map:isAncestorOf(chair).",
    },
    {
      label: "getFullName",
      self: chair,
      code: "chair:getFullName()",
      run: () => fullName(chair),
      note: "Every name from the root down, joined with a dot. Good for a log line, not for finding it again.",
    },
  ]

  function flatten(node: Node, depth = 0, out: { node: Node; depth: number }[] = []) {
    out.push({ node, depth })
    for (const child of node.children) flatten(child, depth + 1, out)
    return out
  }

  function describe(value: Result) {
    if (value === null) return "nil"
    if (typeof value === "boolean") return String(value)
    if (typeof value === "string") return `"${value}"`
    if (Array.isArray(value)) return `{ ${value.map((n) => n.name).join(", ")} }`
    return value.name
  }

  const rows = flatten(world)

  let picked = $state(0)
  const call = $derived(CALLS[picked])
  const result = $derived(call.run())
  const hits = $derived(
    Array.isArray(result) ? result : result && typeof result === "object" ? [result] : [],
  )

  function markOf(node: Node) {
    if (hits.includes(node)) return "hit"
    if (node === call.self) return "tree-self"
    if (node === call.other) return "tree-other"
    return ""
  }
</script>

<Demo label="Finding">
  <div class="demo-choice">
    <span class="demo-slider-name">call</span>
    <select class="demo-select" bind:value={picked}>
      {#each CALLS as entry, index (entry.label)}
        <option value={index}>{entry.label}</option>
      {/each}
    </select>
  </div>

  <ul class="demo-tree tree-list">
    {#each rows as row, index (index)}
      <li
        class="demo-tree-row tree-row {markOf(row.node)}"
        style="padding-left: {12 + row.depth * 18}px"
      >
        <img class="engine-icon" src="/art/icons/{iconOf(row.node.klass)}.png" alt="" width="16" height="16" />
        <span class="tree-name">{row.node.name}</span>
        {#if row.node.parent}
          <span class="tree-class"
            >{row.node.klass}{row.node.tags.length ? `  .${row.node.tags.join(" .")}` : ""}</span
          >
        {/if}
      </li>
    {/each}
  </ul>

  <CodePanel source={`${call.code}   -- ${describe(result)}`} />
  <Note>{call.note}</Note>
</Demo>
