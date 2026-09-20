<script lang="ts">
  import { onMount } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type ClassNode = {
    name: string
    id: string
    parentId: string | null
    parent: ClassNode | null
    children: ClassNode[]
    own: string[]
  }

  const ICONS: Record<string, string> = {
    Folder: "Folder", Model: "PackedScene", WeldConstraint: "RemoteTransform3D", Constraint: "HingeJoint3D",
    Part: "MeshInstance3D", Limb: "Mesh", SpawnLocation: "NavigationRegion3D", Seat: "StaticBody3D",
    VehicleSeat: "RigidBody3D", Character: "CharacterBody3D", Humanoid: "CharacterBody3D", Camera: "Camera3D",
    CameraPath: "Line", Animator: "AnimationPlayer", AnimationTrack: "Animation", Animation: "Animation",
    KeyframeSequence: "AnimationPlayer", Keyframe: "Animation", Pose: "RemoteTransform3D", KeyframeMarker: "Line",
    ForceField: "FogVolume", Tool: "ToolSelect", Team: "CharacterBody3D", Leaderboard: "Folder",
    Leaderstats: "Folder", ReplicatedStorage: "Folder", ServerStorage: "Folder", ServerScriptService: "Script",
    StarterGui: "CanvasLayer", StarterPlayerScripts: "Script", StarterCharacterScripts: "Script", Backpack: "Folder",
    StarterPack: "Folder", Attachment: "RemoteTransform3D", PostEffect: "WorldEnvironment", PostShader: "Shader",
    Zone: "CollisionShape3D", Light: "OmniLight3D", SpotLight: "SpotLight3D", AreaLight: "Rectangle",
    TubeLight: "Line", Value: "File", Remote: "GraphEdit", ClickDetector: "ToolSelect", ScreenGui: "CanvasLayer",
    BillboardGui: "Label3D", SurfaceGui: "Decal", GuiObject: "Control", TextBox: "LineEdit",
    ImageButton: "TextureButton", ScrollingFrame: "ScrollContainer", CanvasGroup: "CanvasGroup",
    UIComponent: "Container", UIListLayout: "VBoxContainer", UIGridLayout: "GridContainer",
    UIPadding: "MarginContainer", UIAspectRatioConstraint: "AspectRatioContainer", UIScale: "ToolScale",
    UIStroke: "StyleBoxFlat", UICorner: "StyleBoxFlat", UIGradient: "GradientTexture1D",
    Sound: "AudioStreamPlayer3D", SoundBus: "AudioStream", SoundEffect: "AudioStream", Script: "Script",
    LocalScript: "Script", ModuleScript: "Script", CollisionGroup: "StaticBody3D", Joint: "HingeJoint3D",
    ParticleEmitter: "GPUParticles3D", Fire: "GPUParticles3D", Smoke: "FogVolume", Sparkles: "GPUParticles3D",
    Beam: "Line", Trail: "Line", Highlight: "StandardMaterial3D", Decal: "Decal", Texture: "Texture2D",
    SelectionBox: "CollisionShape3D", SelectionSphere: "CollisionShape3D", Lighting: "DirectionalLight3D",
    Sky: "WorldEnvironment", Atmosphere: "FogVolume", Clouds: "LightmapProbe", Explosion: "GPUParticles3D",
    Spatial: "Node3D",
  }

  function members(head: Element) {
    const names: string[] = []
    for (let at = head.nextElementSibling; at && at.tagName !== "H2"; at = at.nextElementSibling) {
      at.querySelectorAll("tbody tr td:first-child code").forEach((code) => {
        names.push(code.textContent ?? "")
      })
      if (at.tagName === "UL") {
        at.querySelectorAll("li > code:first-child").forEach((code) => {
          names.push((code.textContent ?? "").split("(")[0] + "()")
        })
      }
    }
    return names
  }

  function readClasses() {
    const found: Record<string, ClassNode> = {}
    const order: string[] = []
    document.querySelectorAll(".doc h2[id]").forEach((head) => {
      const id = head.id
      let link = head.querySelector<HTMLAnchorElement>('.extends a[href^="#"]')
      const after = head.nextElementSibling
      if (!link && after && after.tagName === "P" && /^\s*Extends\b/.test(after.textContent ?? "")) {
        link = after.querySelector<HTMLAnchorElement>('a[href^="#"]')
      }
      let name = ""
      head.childNodes.forEach((node) => {
        if (node.nodeType === 3) name += node.textContent
      })
      name = name.trim()
      if (!name) return
      if (link) {
        found[name] = {
          name,
          id,
          parentId: (link.getAttribute("href") ?? "").slice(1),
          parent: null,
          children: [],
          own: members(head),
        }
        order.push(name)
      } else if (id === "instance") {
        found[name] = { name, id, parentId: null, parent: null, children: [], own: members(head) }
        order.unshift(name)
      }
    })

    const byId: Record<string, ClassNode> = {}
    order.forEach((name) => (byId[found[name].id] = found[name]))
    let root: ClassNode | null = null
    order.forEach((name) => {
      const node = found[name]
      if (!node.parentId) {
        root = node
        return
      }
      const parent = byId[node.parentId]
      node.parent = parent ?? null
      if (parent) parent.children.push(node)
    })
    return { root: root as ClassNode | null, all: order.map((name) => found[name]) }
  }

  function iconFor(node: ClassNode) {
    for (let at: ClassNode | null = node; at; at = at.parent) {
      if (ICONS[at.name]) return ICONS[at.name]
    }
    return "Node3D"
  }

  function count(node: ClassNode): number {
    return node.children.reduce((total, child) => total + 1 + count(child), 0)
  }

  function chain(node: ClassNode) {
    const out: ClassNode[] = []
    for (let at: ClassNode | null = node; at; at = at.parent) out.unshift(at)
    return out
  }

  function flatten(node: ClassNode, depth: number, out: { node: ClassNode; depth: number }[]) {
    out.push({ node, depth })
    node.children.forEach((child) => flatten(child, depth + 1, out))
    return out
  }

  let all = $state.raw<ClassNode[]>([])
  let rows = $state.raw<{ node: ClassNode; depth: number }[]>([])
  let selected = $state.raw<ClassNode | null>(null)
  let collapsed = $state<Record<string, boolean>>({})
  let query = $state("")

  onMount(() => {
    const data = readClasses()
    if (!data.root || data.all.length < 3) return
    const shut: Record<string, boolean> = {}
    data.root.children.forEach((child) => {
      if (child.children.length > 6) shut[child.name] = true
    })
    collapsed = shut
    all = data.all
    rows = flatten(data.root, 0, [])
    selected = data.all.filter((node) => node.name === "TextButton")[0] ?? data.root
  })

  const keep = $derived.by(() => {
    const wanted = query.trim().toLowerCase()
    const names: Record<string, boolean> = {}
    if (!wanted) return names
    all.forEach((node) => {
      if (node.name.toLowerCase().indexOf(wanted) !== -1) {
        for (let at: ClassNode | null = node; at; at = at.parent) names[at.name] = true
      }
    })
    return names
  })

  function hits(node: ClassNode) {
    const wanted = query.trim().toLowerCase()
    return wanted !== "" && node.name.toLowerCase().indexOf(wanted) !== -1
  }

  function shown(node: ClassNode) {
    if (query.trim()) return keep[node.name] === true
    for (let at = node.parent; at; at = at.parent) {
      if (collapsed[at.name]) return false
    }
    return true
  }

  function open(node: ClassNode) {
    return query.trim() ? true : !collapsed[node.name]
  }

  function search(value: string) {
    query = value
    const wanted = value.trim().toLowerCase()
    if (!wanted) return
    const first = all.filter((node) => node.name.toLowerCase().indexOf(wanted) !== -1)[0]
    if (first) selected = first
  }

  const steps = $derived(selected ? chain(selected).reverse() : [])
  const total = $derived(steps.reduce((sum, step) => sum + step.own.length, 0))

  const note = $derived.by(() => {
    if (!selected) return ""
    let from: ClassNode | null = null
    const own = ICONS[selected.name]
    for (let at = selected.parent; at && !own; at = at.parent) {
      if (ICONS[at.name]) {
        from = at
        break
      }
    }
    const tail = own
      ? "The editor gives " + selected.name + " its own icon."
      : from
        ? "No icon of its own, so the editor uses the one " + from.name + " has."
        : "No class above it has an icon, so the editor uses the plain one."
    return total + " members in all. " + tail
  })

  const source = $derived.by(() => {
    if (!selected) return ""
    if (selected.name === "Instance") return 'thing:isA("Instance")   -- true for every class here'
    return (
      'thing:isA("' + selected.name + '")\n' +
      chain(selected)
        .slice(0, -1)
        .reverse()
        .map((step) => 'thing:isA("' + step.name + '")   -- true as well')
        .join("\n")
    )
  })
</script>

{#if rows.length}
  <Demo label="Class tree">
    <div class="demo-field">
      <input
        type="text"
        class="demo-input"
        placeholder="filter, e.g. Light or Sound"
        spellcheck="false"
        aria-label="filter classes"
        value={query}
        oninput={(event) => search(event.currentTarget.value)}
      />
    </div>

    <div class="ref-layout">
      <ul class="demo-tree ref-tree">
        {#each rows as row (row.node.name)}
          <li
            class="demo-tree-row ref-row"
            class:hit={hits(row.node)}
            style="padding-left: {8 + row.depth * 16}px"
            style:display={shown(row.node) ? "" : "none"}
            onmouseenter={() => (selected = row.node)}
          >
            <button
              type="button"
              class="ref-toggle"
              aria-label="expand {row.node.name}"
              aria-expanded={open(row.node)}
              style:visibility={row.node.children.length ? "" : "hidden"}
              onclick={() => (collapsed = { ...collapsed, [row.node.name]: !collapsed[row.node.name] })}
            >
              {row.node.children.length ? (open(row.node) ? "−" : "+") : ""}
            </button>
            <img
              class="engine-icon"
              src="/art/icons/{iconFor(row.node)}.png"
              alt=""
              width="16"
              height="16"
            />
            <a class="ref-name" href="#{row.node.id}" onfocus={() => (selected = row.node)}>
              {row.node.name}
            </a>
            {#if row.node.children.length}<span class="ref-count">{count(row.node)}</span>{/if}
          </li>
        {/each}
      </ul>

      <div class="ref-side">
        {#if selected}
          <div class="ref-side-head">
            <img class="engine-icon" src="/art/icons/{iconFor(selected)}.png" alt="" width="20" height="20" />
            <span>{selected.name}</span>
          </div>
          <div class="ref-chain">
            {#each steps as step, index (step.name)}
              {#if index}<span class="ref-arrow">extends</span>{/if}
              <a class="ref-chip" href="#{step.id}">{step.name}</a>
            {/each}
          </div>
          <ul class="ref-levels">
            {#each steps as step (step.name)}
              <li>
                <span class="ref-level-name">
                  {step === selected ? step.name + " itself" : "from " + step.name}
                </span>
                <span class="ref-level-list">
                  {step.own.length
                    ? step.own.slice(0, 6).join(", ") + (step.own.length > 6 ? ", ..." : "")
                    : "nothing new"}
                </span>
              </li>
            {/each}
          </ul>
          <p class="demo-note">{note}</p>
          <CodePanel {source} />
        {/if}
      </div>
    </div>
  </Demo>
{/if}
