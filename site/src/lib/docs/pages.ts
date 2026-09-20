import { plain, renderDoc, type Heading } from "./render"

const MARKDOWN = import.meta.glob("/docs/*.md", {
  eager: true,
  query: "?raw",
  import: "default",
}) as Record<string, string>

import CLASS_ICONS from "../../../../mod/src/main/resources/assets/moud/editor/class-icons.json"

const ENUM_FILES: Record<string, string> = {
  PARTICLES_3D: "GPUParticles3D",
  AUDIO_MICROPHONE: "AudioStreamMicrophone",
  STANDARD_MATERIAL: "StandardMaterial3D",
  VISIBILITY_HIDDEN: "GuiVisibilityHidden",
  VISIBILITY_VISIBLE: "GuiVisibilityVisible",
  SKELETON_IK_3D: "SkeletonIK3D",
}

const GROUPS: [string, string][] = [
  ["Start", "README"],
  ["The world", "instances"],
  ["Screen and sound", "interface"],
  ["Scripting", "mixins"],
  ["Reference", "reference"],
]

export const GUIDES: [string, string, string][] = [
  ["getting-started", "rocket-launch", "Your first place"],
  ["physics", "cube", "Physics"],
  ["zones-and-triggers", "target", "Zones"],
  ["animation", "film-strip", "Animation"],
  ["players", "users-three", "Players"],
  ["interface", "layout", "Interface"],
  ["lighting", "sun", "Lighting"],
  ["effects", "sparkle", "Effects"],
]

const PAGE_ICONS: Record<string, string> = {
  README: "p:house",
  "getting-started": "p:rocket-launch",
  place: "e:PackedScene",
  containers: "e:Folder",
  instances: "e:Node3D",
  tree: "p:tree-structure",
  character: "e:CharacterBody3D",
  animation: "e:Animation",
  players: "p:users-three",
  tools: "e:ToolSelect",
  movement: "p:person-simple-run",
  editor: "e:ToolMove",
  plugins: "p:puzzle-piece",
  physics: "e:RigidBody3D",
  "camera-and-input": "e:Camera3D",
  "game-and-screens": "e:Pause",
  window: "p:browser",
  queries: "e:Search",
  blocks: "e:Grid",
  "zones-and-triggers": "e:CollisionShape3D",
  interface: "e:Control",
  chat: "p:chat-circle-text",
  "rich-text": "e:Label3D",
  images: "e:Texture2D",
  sound: "e:AudioStreamPlayer3D",
  rendering: "e:MeshInstance3D",
  lighting: "e:DirectionalLight3D",
  effects: "e:GPUParticles3D",
  mixins: "e:Shader",
  languages: "e:Script",
  "tweens-and-timing": "p:timer",
  math: "p:function",
  saving: "e:Save",
  memory: "e:KeyValue",
  http: "p:globe",
  talking: "e:GraphEdit",
  debug: "p:bug",
  reference: "p:book-bookmark",
}

export type Page = { stem: string; slug: string; label: string; icon: string; blurb: string }
export type Group = { name: string; pages: Page[] }

function source(stem: string) {
  const found = MARKDOWN[`/docs/${stem}.md`]
  if (found === undefined) throw new Error(`no such doc: ${stem}`)
  return found
}

function stems() {
  return Object.keys(MARKDOWN)
    .map((file) => file.slice("/docs/".length, -".md".length))
    .sort()
}

export function pageIcon(stem: string) {
  return PAGE_ICONS[stem] ?? "p:file-text"
}

let cache: Page[] | null = null

export function pages(): Page[] {
  if (cache && process.env.NODE_ENV === "production") return cache

  const readme = source("README")
  const order: [string, string][] = [["README", "Overview"]]
  for (const match of readme.matchAll(/^- \[([^\]]+)\]\(([a-z0-9-]+)\.md\)/gm)) {
    order.push([match[2], match[1]])
  }

  const known = new Set(order.map(([stem]) => stem))
  for (const stem of stems()) {
    if (known.has(stem)) continue
    order.push([stem, stem.replace(/-/g, " ").replace(/^./, (c: string) => c.toUpperCase())])
  }

  const blurbs = new Map<string, string>()
  for (const match of readme.matchAll(/^- \[[^\]]+\]\(([a-z0-9-]+)\.md\):\s*(.+)$/gm)) {
    blurbs.set(match[1], match[2].trim())
  }

  cache = order.map(([stem, label]) => ({
    stem,
    slug: stem === "README" ? "" : stem,
    label,
    icon: pageIcon(stem),
    blurb: blurbs.get(stem) ?? "",
  }))
  return cache
}

export function groups(): Group[] {
  const starts = new Map(GROUPS.map(([name, stem]) => [stem, name]))
  const out: Group[] = []
  for (const page of pages()) {
    if (starts.has(page.stem) || out.length === 0) {
      out.push({ name: starts.get(page.stem) ?? "Start", pages: [] })
    }
    out[out.length - 1].pages.push(page)
  }
  return out
}

export function groupOf(stem: string) {
  return groups().find((group) => group.pages.some((page) => page.stem === stem))?.name ?? "Docs"
}

function fileNameOf(constant: string) {
  if (ENUM_FILES[constant]) return ENUM_FILES[constant]
  return constant
    .split("_")
    .map((word) => (/^\d/.test(word) ? word : word[0] + word.slice(1).toLowerCase()))
    .join("")
}

function classIcons() {
  const icons = new Map<string, string>()
  for (const [klass, constant] of Object.entries(CLASS_ICONS as Record<string, string>)) {
    icons.set(klass, fileNameOf(constant))
  }
  return icons
}

function classParents() {
  const parents = new Map<string, string>()
  const reference = source("reference")
  for (const match of reference.matchAll(/^## ([A-Za-z0-9]+)\n\nExtends \[([A-Za-z0-9]+)\]/gm)) {
    parents.set(match[1], match[2])
  }
  return parents
}

function withClassIcons(html: string) {
  let icons: Map<string, string>
  try {
    icons = classIcons()
  } catch {
    return html
  }
  const parents = classParents()

  const iconFor = (name: string) => {
    let at: string | undefined = name
    while (at) {
      const found = icons.get(at)
      if (found) return found
      at = parents.get(at)
    }
    return null
  }

  const isInstance = (name: string) => {
    let at: string | undefined = name
    while (at) {
      if (at === "Instance") return true
      at = parents.get(at)
    }
    return false
  }

  return html.replace(/(<h2 id="[^"]+">)([A-Z][A-Za-z0-9]*)/g, (whole, head: string, name: string) => {
    if (!isInstance(name) && !icons.has(name)) return whole
    const file = iconFor(name) ?? "Node3D"
    return `${head}<img class="class-icon" src="/art/icons/${file}.png" alt="" width="22" height="22">${name}`
  })
}

const BROWSE_LIST = /(<h2 id="browse-the-docs"[\s\S]*?<\/h2>)\s*<ul>[\s\S]*?<\/ul>/

export function doc(slug: string) {
  const all = pages()
  const index = all.findIndex((page) => page.slug === slug)
  if (index === -1) return null

  const page = all[index]
  const markdown = source(page.stem)
  let { html, headings } = renderDoc(markdown)
  if (page.stem === "README") html = html.replace(BROWSE_LIST, "$1{{browse}}")
  if (page.stem === "reference") html = withClassIcons(html)
  const title = markdown.match(/^# (.+)$/m)?.[1] ?? page.label

  return {
    page,
    title,
    html,
    headings,
    previous: index > 0 ? all[index - 1] : null,
    next: index < all.length - 1 ? all[index + 1] : null,
    group: groupOf(page.stem),
  }
}

export type SearchEntry = { page: string; url: string; heading: string; text: string }

export function searchIndex(): SearchEntry[] {
  const entries: SearchEntry[] = []

  for (const page of pages()) {
    const markdown = source(page.stem)
    const chunks = markdown.split(/^## (.+)$/m)
    const intro = chunks[0].replace(/^# .+$/m, "")
    const base = `/docs/${page.slug}`

    entries.push({ page: page.label, url: base, heading: "", text: plain(intro).slice(0, 600) })

    for (let i = 1; i < chunks.length; i += 2) {
      const name = chunks[i]
        .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
        .split(" — extends")[0]
        .replace(/[`*]/g, "")
        .trim()
      const anchor = name
        .toLowerCase()
        .replace(/[^a-z0-9 -]/g, "")
        .trim()
        .replace(/\s+/g, "-")
      entries.push({
        page: page.label,
        url: `${base}#${anchor}`,
        heading: name,
        text: plain(chunks[i + 1] ?? "").slice(0, 600),
      })
    }
  }

  return entries
}

export function guideTiles() {
  const blurbs = new Map(pages().map((page) => [page.stem, page.blurb]))
  return GUIDES.map(([stem, glyph, title]) => ({
    stem,
    glyph,
    title,
    blurb: (blurbs.get(stem) ?? "").replace(/^./, (c) => c.toUpperCase()),
  }))
}
