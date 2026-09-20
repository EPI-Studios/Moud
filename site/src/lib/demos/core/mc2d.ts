import { mcColor, mcText, mcTextWidth, type TextBox } from "./mcFont"
import { canvasOf, context2d, loadTextures, type LoadedTexture, type TextureStore } from "./mcTextures"

const UP = 1.0
const NS = 0.8
const EW = 0.6
const AO = [1.0, 0.8, 0.6, 0.5]
const CLOUD_Y = 192.33
const CLOUD_THICK = 4
const CLOUD_CELL = 12
const CLOUD_FOG_END = 2048
const CLOUD_FACE = { bottom: 0.7, ns: 0.8, ew: 0.9 }
const FOG = [192 / 255, 216 / 255, 255 / 255]
const WATER: Rgb = [63, 118, 228]
const VIEW_CHUNKS = 12
const SKY_END = VIEW_CHUNKS * 16
const PLAYER_SCALE = 0.9375
const MOONS = [
  "full_moon",
  "waning_gibbous",
  "third_quarter",
  "waning_crescent",
  "new_moon",
  "waxing_crescent",
  "first_quarter",
  "waxing_gibbous",
]

function clamp(v: number, lo: number, hi: number) {
  return v < lo ? lo : v > hi ? hi : v
}

export type Rgb = [number, number, number]
export type Point2 = [number, number]

let textures: TextureStore | null = null
const bakedSkins: Record<string, HTMLCanvasElement> = {}
let starField: [number, number, number, number][] | null = null

function frac(v: number) {
  return v - Math.floor(v)
}

function mix(a: number, b: number, t: number) {
  return a + (b - a) * t
}

function colormap(tex: TextureStore, name: string, temperature: number, downfall: number): Rgb {
  const t = tex["colormap/" + name]
  if (!t) return [255, 255, 255]
  const temp = clamp(temperature, 0, 1)
  const down = clamp(downfall, 0, 1) * temp
  const x = Math.floor((1 - temp) * 255)
  const y = Math.floor((1 - down) * 255)
  const i = (y * t.w + x) * 4
  return [t.data[i], t.data[i + 1], t.data[i + 2]]
}

function hsv(h: number, s: number, v: number): Rgb {
  const j = Math.floor(h * 6) % 6
  const k = h * 6 - Math.floor(h * 6)
  const l = v * (1 - s)
  const m = v * (1 - k * s)
  const n = v * (1 - (1 - k) * s)
  const wheel: Rgb[] = [
    [v, n, l],
    [m, v, l],
    [l, v, n],
    [l, m, v],
    [n, l, v],
    [v, l, m],
  ]
  return wheel[j]
}

function skyBase(temperature: number) {
  const g = clamp(temperature / 3, -1, 1)
  return hsv(0.62222224 - g * 0.05, 0.5 + g * 0.1, 1)
}

function celestial(ticks: number) {
  const d = frac(ticks / 24000 - 0.25)
  const e = 0.5 - Math.cos(d * Math.PI) / 2
  return (d * 2 + e) / 3
}

function skyLight(angle: number) {
  const g = 1 - clamp(1 - (Math.cos(angle * Math.PI * 2) * 2 + 0.2), 0, 1)
  const darken = g * 0.8 + 0.2
  let c = darken * 0.95 + 0.05
  c = clamp(mix(c, 0.75, 0.04), 0, 1)
  const notGamma = 1 - Math.pow(1 - c, 4)
  c = mix(c, notGamma, 0.5)
  return clamp(mix(c, 0.75, 0.04), 0, 1)
}

function skyColors(angle: number, temperature: number) {
  const h = clamp(Math.cos(angle * Math.PI * 2) * 2 + 0.5, 0, 1)
  const base = skyBase(temperature)
  const sky: Rgb = [base[0] * h, base[1] * h, base[2] * h]
  const fog: Rgb = [
    FOG[0] * (h * 0.94 + 0.06),
    FOG[1] * (h * 0.94 + 0.06),
    FOG[2] * (h * 0.91 + 0.09),
  ]
  let p = clamp(Math.min(512 / 16, VIEW_CHUNKS) / 32, 0.25, 1)
  p = 1 - Math.pow(p, 0.25)
  const blended: Rgb = [mix(fog[0], sky[0], p), mix(fog[1], sky[1], p), mix(fog[2], sky[2], p)]
  return { sky, fog: blended }
}

function sunrise(angle: number): [number, number, number, number] | null {
  const g = Math.cos(angle * Math.PI * 2)
  if (g < -0.4 || g > 0.4) return null
  const i = (g / 0.4) * 0.5 + 0.5
  const j = 1 - (1 - Math.sin(i * Math.PI)) * 0.99
  return [i * 0.3 + 0.7, i * i * 0.7 + 0.2, 0.2, j * j]
}

function starBrightness(angle: number) {
  const g = clamp(1 - (Math.cos(angle * Math.PI * 2) * 2 + 0.25), 0, 1)
  return g * g * 0.5
}

function cloudColor(angle: number): Rgb {
  const g = clamp(Math.cos(angle * Math.PI * 2) * 2 + 0.5, 0, 1)
  return [g * 0.9 + 0.1, g * 0.9 + 0.1, g * 0.85 + 0.15]
}

class JavaRandom {
  private lo: number
  private hi: number

  constructor(seed: number) {
    this.lo = (seed % 16777216) ^ 0xece66d
    this.hi = (Math.floor(seed / 16777216) % 16777216) ^ 0x5de
  }

  next(bits: number) {
    const p = this.lo * 0xece66d + 0xb
    const lo = p % 16777216
    const hi = (this.hi * 0xece66d + this.lo * 0x5de + Math.floor(p / 16777216)) % 16777216
    this.lo = lo
    this.hi = hi
    if (bits <= 24) return Math.floor(hi / Math.pow(2, 24 - bits))
    return hi * Math.pow(2, bits - 24) + Math.floor(lo / Math.pow(2, 48 - bits))
  }

  nextFloat() {
    return this.next(24) / 16777216
  }

  nextDouble() {
    return (this.next(26) * 134217728 + this.next(27)) / 9007199254740992
  }
}

function stars() {
  if (starField) return starField
  const random = new JavaRandom(10842)
  const list: [number, number, number, number][] = []
  for (let i = 0; i < 1500; i++) {
    const g = random.nextFloat() * 2 - 1
    const h = random.nextFloat() * 2 - 1
    const j = random.nextFloat() * 2 - 1
    const k = 0.15 + random.nextFloat() * 0.1
    const l = g * g + h * h + j * j
    if (l <= 0.010000001 || l >= 1) continue
    const n = Math.sqrt(l)
    random.nextDouble()
    list.push([g / n, h / n, j / n, k])
  }
  starField = list
  return list
}

type TintName = "grass" | "foliage" | "water"

type FaceSpec = { t: string; tint?: TintName; over?: string; overTint?: TintName }

type BlockDef = {
  all?: string
  top?: FaceSpec
  side?: FaceSpec
  tint?: TintName
  see?: boolean
  self?: boolean
  liquid?: boolean
  occludes?: boolean
}

const BLOCKS: Record<string, BlockDef> = {
  grass_block: {
    top: { t: "grass_block_top", tint: "grass" },
    side: { t: "grass_block_side", over: "grass_block_side_overlay", overTint: "grass" },
  },
  dirt: { all: "dirt" },
  stone: { all: "stone" },
  cobblestone: { all: "cobblestone" },
  oak_planks: { all: "oak_planks" },
  oak_log: { top: { t: "oak_log_top" }, side: { t: "oak_log" } },
  oak_leaves: { all: "oak_leaves", tint: "foliage", occludes: true, see: true },
  sand: { all: "sand" },
  glass: { all: "glass", see: true, self: true },
  water: { all: "water_still", tint: "water", see: true, self: true, liquid: true },
  smooth_stone: { all: "smooth_stone" },
  stone_bricks: { all: "stone_bricks" },
  iron_block: { all: "iron_block" },
  barrel: { top: { t: "barrel_top" }, side: { t: "barrel_side" } },
}

type Tints = { grass: Rgb; foliage: Rgb; water: Rgb; blocks: Record<string, Block> }

type Face = {
  tex: LoadedTexture | null
  tint: Rgb | null
  over: LoadedTexture | null
  overTint: Rgb | null
  tiles: Record<string, HTMLCanvasElement> | null
}

type Block = {
  id: string
  top: Face
  side: Face
  opaque: boolean
  occludes: boolean
  self: boolean
  liquid: boolean
  animated: boolean
}

function resolveFace(tex: TextureStore, spec: FaceSpec, tints: Tints): Face {
  return {
    tex: tex["block/" + spec.t] ?? null,
    tint: spec.tint ? tints[spec.tint] : null,
    over: spec.over ? (tex["block/" + spec.over] ?? null) : null,
    overTint: spec.overTint ? tints[spec.overTint] : null,
    tiles: null,
  }
}

function resolveBlock(tex: TextureStore, id: string, tints: Tints): Block {
  const def = BLOCKS[id] || BLOCKS.stone
  const topSpec: FaceSpec = def.all ? { t: def.all, tint: def.tint } : (def.top ?? { t: "stone" })
  const sideSpec: FaceSpec = def.all ? { t: def.all, tint: def.tint } : (def.side ?? { t: "stone" })
  const top = resolveFace(tex, topSpec, tints)
  const side = resolveFace(tex, sideSpec, tints)
  return {
    id,
    top,
    side,
    opaque: !def.see,
    occludes: !def.see || !!def.occludes,
    self: !!def.self,
    liquid: !!def.liquid,
    animated: !!top.tex && top.tex.frames > 1,
  }
}

class Raster {
  readonly w: number
  readonly h: number
  readonly canvas: HTMLCanvasElement
  private ctx: CanvasRenderingContext2D
  private image: ImageData
  private d: Uint8ClampedArray

  constructor(w: number, h: number) {
    this.w = w
    this.h = h
    this.canvas = canvasOf(w, h)
    this.ctx = context2d(this.canvas)
    this.image = this.ctx.createImageData(Math.max(1, w), Math.max(1, h))
    this.d = this.image.data
  }

  put(px: number, py: number, r: number, g: number, b: number, a: number) {
    if (px < 0 || py < 0 || px >= this.w || py >= this.h || a <= 0) return
    const d = this.d
    const i = (py * this.w + px) * 4
    if (a >= 1) {
      d[i] = r
      d[i + 1] = g
      d[i + 2] = b
      d[i + 3] = 255
      return
    }
    const ba = d[i + 3] / 255
    const oa = a + ba * (1 - a)
    const keep = ba * (1 - a)
    d[i] = (r * a + d[i] * keep) / oa
    d[i + 1] = (g * a + d[i + 1] * keep) / oa
    d[i + 2] = (b * a + d[i + 2] * keep) / oa
    d[i + 3] = oa * 255
  }

  flush() {
    this.ctx.putImageData(this.image, 0, 0)
    return this.canvas
  }
}

type PaintBox = {
  stretch: boolean
  cropTop: number
  texH: number
  depthTexels: number
  frame: number
  alpha: number
}

const PIX: [number, number, number, number] = [0, 0, 0, 0]

function texel(
  face: Face,
  u: number,
  v: number,
  frame: number,
  mul: number,
  alpha: number,
  out: [number, number, number, number],
) {
  const t = face.tex
  if (!t) return false
  const f = frame % t.frames
  const i = ((f * t.w + (v & (t.w - 1))) * t.w + (u & (t.w - 1))) * 4
  let a = t.data[i + 3]
  let r = t.data[i]
  let g = t.data[i + 1]
  let b = t.data[i + 2]
  if (face.tint) {
    r = (r * face.tint[0]) / 255
    g = (g * face.tint[1]) / 255
    b = (b * face.tint[2]) / 255
  }
  if (face.over) {
    const o = face.over
    const j = ((v & (o.w - 1)) * o.w + (u & (o.w - 1))) * 4
    if (o.data[j + 3] > 0) {
      const tint = face.overTint || [255, 255, 255]
      r = (o.data[j] * tint[0]) / 255
      g = (o.data[j + 1] * tint[1]) / 255
      b = (o.data[j + 2] * tint[2]) / 255
      a = 255
    }
  }
  if (a === 0) return false
  out[0] = r * mul
  out[1] = g * mul
  out[2] = b * mul
  out[3] = (a / 255) * alpha
  return true
}

function paintFront(
  R: Raster,
  x0: number,
  y0: number,
  w: number,
  h: number,
  face: Face,
  mul: number,
  box: PaintBox,
) {
  for (let py = 0; py < h; py++) {
    const vy = py + box.cropTop
    if (vy >= box.texH) break
    const v = box.stretch ? Math.floor(((vy + 0.5) * 16) / box.texH) : vy
    for (let px = 0; px < w; px++) {
      const u = box.stretch ? Math.floor(((px + 0.5) * 16) / w) : px
      if (texel(face, u, v, box.frame, mul, box.alpha, PIX)) {
        R.put(x0 + px, y0 + py, PIX[0], PIX[1], PIX[2], PIX[3])
      }
    }
  }
}

function paintTop(
  R: Raster,
  x0: number,
  y0: number,
  w: number,
  tw: number,
  th: number,
  face: Face,
  mul: number,
  aoL: number,
  aoR: number,
  box: PaintBox,
) {
  if (th <= 0) return
  for (let py = -th; py < 0; py++) {
    const t = -(py + 0.5) / th
    const v = box.stretch ? Math.floor((1 - t) * 16) : Math.floor((1 - t) * box.depthTexels)
    const shift = t * tw
    const from = Math.floor(shift)
    for (let px = from; px <= from + w; px++) {
      const s = px + 0.5 - shift
      if (s < 0 || s >= w) continue
      const u = box.stretch ? Math.floor((s * 16) / w) : Math.floor(s)
      const ao = mix(aoL, aoR, s / w)
      if (texel(face, u, v, box.frame, mul * ao, box.alpha, PIX)) {
        R.put(x0 + px, y0 + py, PIX[0], PIX[1], PIX[2], PIX[3])
      }
    }
  }
}

function paintEast(
  R: Raster,
  x0: number,
  y0: number,
  h: number,
  tw: number,
  th: number,
  face: Face,
  mul: number,
  aoT: number,
  aoB: number,
  box: PaintBox,
) {
  if (tw <= 0) return
  for (let px = 0; px < tw; px++) {
    const t = (px + 0.5) / tw
    const u = box.stretch ? Math.floor(t * 16) : Math.floor(t * box.depthTexels)
    const lift = t * th
    const from = Math.floor(-lift) - 1
    for (let py = from; py <= from + h + 1; py++) {
      const vv = py + 0.5 + lift - box.cropTop
      if (vv < 0 || vv >= h - box.cropTop) continue
      const vy = vv + box.cropTop
      const v = box.stretch ? Math.floor((vy * 16) / box.texH) : Math.floor(vy)
      const ao = mix(aoT, aoB, vy / h)
      if (texel(face, u, v, box.frame, mul * ao, box.alpha, PIX)) {
        R.put(x0 + px, y0 + py, PIX[0], PIX[1], PIX[2], PIX[3])
      }
    }
  }
}

type PartName = "head" | "body" | "rightArm" | "leftArm" | "rightLeg" | "leftLeg"

type Vec3 = [number, number, number]

type BoxFace = { v: Vec3[]; uv: [number, number, number, number]; n: Vec3 }

type SkinBox = {
  name: PartName
  origin: Vec3
  size: Vec3
  tex: [number, number]
  inflate: number
  layer?: boolean
  faces: BoxFace[]
}

function cuboid(box: Omit<SkinBox, "faces">): BoxFace[] {
  const o = box.origin
  const s = box.size
  const f = box.inflate
  const x0 = o[0] - f
  const x1 = o[0] + s[0] + f
  const y0 = o[1] - f
  const y1 = o[1] + s[1] + f
  const z0 = o[2] - f
  const z1 = o[2] + s[2] + f
  const V: Vec3[] = [
    [x0, y0, z0],
    [x1, y0, z0],
    [x1, y1, z0],
    [x0, y1, z0],
    [x0, y0, z1],
    [x1, y0, z1],
    [x1, y1, z1],
    [x0, y1, z1],
  ]
  const i = box.tex[0]
  const j = box.tex[1]
  const sx = s[0]
  const sy = s[1]
  const sz = s[2]
  const W = i
  const X = i + sz
  const Y = i + sz + sx
  const Z = i + sz + sx + sx
  const BA = i + sz + sx + sz
  const BB = i + sz + sx + sz + sx
  const BC = j
  const BD = j + sz
  const BE = j + sz + sy
  return [
    { v: [V[5], V[4], V[0], V[1]], uv: [X, BC, Y, BD], n: [0, -1, 0] },
    { v: [V[2], V[3], V[7], V[6]], uv: [Y, BD, Z, BC], n: [0, 1, 0] },
    { v: [V[0], V[4], V[7], V[3]], uv: [W, BD, X, BE], n: [-1, 0, 0] },
    { v: [V[1], V[0], V[3], V[2]], uv: [X, BD, Y, BE], n: [0, 0, -1] },
    { v: [V[5], V[1], V[2], V[6]], uv: [Y, BD, BA, BE], n: [1, 0, 0] },
    { v: [V[4], V[5], V[6], V[7]], uv: [BA, BD, BB, BE], n: [0, 0, 1] },
  ]
}

const BOXES: SkinBox[] = (
  [
    { name: "head", origin: [-4, -8, -4], size: [8, 8, 8], tex: [0, 0], inflate: 0 },
    { name: "body", origin: [-4, 0, -2], size: [8, 12, 4], tex: [16, 16], inflate: 0 },
    { name: "rightArm", origin: [-3, -2, -2], size: [4, 12, 4], tex: [40, 16], inflate: 0 },
    { name: "leftArm", origin: [-1, -2, -2], size: [4, 12, 4], tex: [32, 48], inflate: 0 },
    { name: "rightLeg", origin: [-2, 0, -2], size: [4, 12, 4], tex: [0, 16], inflate: 0 },
    { name: "leftLeg", origin: [-2, 0, -2], size: [4, 12, 4], tex: [16, 48], inflate: 0 },
    { name: "head", origin: [-4, -8, -4], size: [8, 8, 8], tex: [32, 0], inflate: 0.5, layer: true },
    { name: "body", origin: [-4, 0, -2], size: [8, 12, 4], tex: [16, 32], inflate: 0.25, layer: true },
    { name: "rightArm", origin: [-3, -2, -2], size: [4, 12, 4], tex: [40, 32], inflate: 0.25, layer: true },
    { name: "leftArm", origin: [-1, -2, -2], size: [4, 12, 4], tex: [48, 48], inflate: 0.25, layer: true },
    { name: "rightLeg", origin: [-2, 0, -2], size: [4, 12, 4], tex: [0, 32], inflate: 0.25, layer: true },
    { name: "leftLeg", origin: [-2, 0, -2], size: [4, 12, 4], tex: [0, 48], inflate: 0.25, layer: true },
  ] satisfies Omit<SkinBox, "faces">[]
).map((box) => ({ ...box, faces: cuboid(box) }))

function baked(tex: TextureStore, name: string, mul: number) {
  const key = name + "|" + Math.round(mul * 200)
  if (bakedSkins[key]) return bakedSkins[key]
  const t = tex[name]
  const canvas = canvasOf(t.w, t.h)
  const ctx = context2d(canvas)
  const img = ctx.createImageData(t.w, t.h)
  for (let i = 0; i < t.data.length; i += 4) {
    img.data[i] = t.data[i] * mul
    img.data[i + 1] = t.data[i + 1] * mul
    img.data[i + 2] = t.data[i + 2] * mul
    img.data[i + 3] = t.data[i + 3]
  }
  ctx.putImageData(img, 0, 0)
  bakedSkins[key] = canvas
  return canvas
}

function aoVertex(edge1: boolean, edge2: boolean, corner: boolean) {
  const count = (edge1 ? 1 : 0) + (edge2 ? 1 : 0) + ((edge1 && edge2) || corner ? 1 : 0)
  return AO[Math.min(count, 3)]
}

export type OverlayColors = {
  bg: string
  text: string
  muted: string
  light: string
  accent: string
  yellow: string
  red: string
  green: string
  blue: string
  mono: string
}

function palette(): OverlayColors {
  const css = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => (css.getPropertyValue(name) || "").trim() || fallback
  return {
    bg: read("--bg", "#181818"),
    text: read("--text-main", "#f0f0f0"),
    muted: read("--text-muted", "#9a9a9a"),
    light: read("--text-light", "#6a6a6a"),
    accent: read("--accent", "#c6c6c6"),
    yellow: read("--yellow", "#f2c14e"),
    red: read("--red", "#e06c6c"),
    green: read("--green", "#7fbf7f"),
    blue: read("--blue", "#6ea4ff"),
    mono: read("--font-mono", "monospace"),
  }
}

export type ViewRect = { x0: number; x1: number; y0: number; y1: number }
export type ViewZoom = { x: number; y: number; zoom: number }
export type ViewSpec = ViewRect | ViewZoom
export type ViewFn = (width: number, height: number) => ViewSpec

export type SceneView = {
  dpr: number
  w: number
  h: number
  dW: number
  dH: number
  U: number
  unit: number
  originX: number
  originY: number
  rect: ViewRect
  ox: number
  oy: number
  X: (x: number, d?: number) => number
  Y: (y: number, d?: number) => number
  x: (x: number, d?: number) => number
  y: (y: number, d?: number) => number
  worldX: (px: number, d?: number) => number
  worldY: (py: number, d?: number) => number
}

export type TextOptions = {
  scale?: number
  align?: "left" | "center" | "right"
  color?: string
  shadow?: boolean
}

export type TagOptions = TextOptions & { background?: string }

export type LineOptions = {
  dash?: number[]
  halo?: string | false
  color?: string
  width?: number
}

export type OverlayView = SceneView & {
  colors: OverlayColors
  font: (size?: number) => string
  text: (text: string, x: number, y: number, options?: TextOptions) => TextBox | null
  tag: (text: string, x: number, y: number, options?: TagOptions) => TextBox
  line: (points: Point2[], options?: LineOptions) => void
}

export type PartSpec = {
  block?: string
  x?: number
  y?: number
  w?: number
  h?: number
  d?: number
  z?: number
  stretch?: boolean
  alpha?: number
  visible?: boolean
}

export type Part = {
  prism: false
  block: string
  x: number
  y: number
  w: number
  h: number
  d: number
  z: number
  stretch: boolean
  alpha: number
  visible: boolean
  cache: { canvas: HTMLCanvasElement; tw: number; th: number } | null
  key: string
}

export type PrismSpec = {
  block?: string
  points?: Point2[]
  d?: number
  z?: number
  alpha?: number
  visible?: boolean
}

export type Prism = {
  prism: true
  block: string
  points: Point2[]
  d: number
  z: number
  alpha: number
  visible: boolean
  readonly x: number
  readonly y: number
}

export type Piece = Part | Prism

export type Rope = { points: Point2[]; visible: boolean }

export type Limbs = { rightArm: number; leftArm: number; rightLeg: number; leftLeg: number }

export type PlayerSpec = { x?: number; y?: number; z?: number; facing?: number; shadow?: boolean }

export type PlayerEntity = {
  x: number
  y: number
  z: number
  facing: number
  pitch: number
  crouch: boolean
  shadow: boolean
  visible: boolean
  skin: string
  limbs: Limbs
  walkSpeed: number
  walkPos: number
  age: number
  walk: (dt: number, speed: number) => PlayerEntity
  still: () => PlayerEntity
}

export type SceneOptions = {
  oblique?: [number, number]
  temperature?: number
  downfall?: number
  time?: number
  clouds?: boolean
  horizon?: number
  view?: ViewSpec | ViewFn
  height?: number
  aspect?: number
  minHeight?: number
  maxHeight?: number
  maxUnit?: number
  ambient?: boolean
  overlay?: (ctx: CanvasRenderingContext2D, view: OverlayView) => void
  step?: (dt: number) => void | boolean
  ready?: (scene: Scene) => void
}

export type Scene = {
  time: number
  clock: number
  clouds: boolean
  horizon: number | undefined
  view: SceneView | null
  overlay: ((ctx: CanvasRenderingContext2D, view: OverlayView) => void) | null
  step: ((dt: number) => void | boolean) | null
  fit: ViewSpec | ViewFn | null
  set: (x: number, y: number, id: string | null) => Scene
  fill: (x0: number, y0: number, x1: number, y1: number, id: string | null) => Scene
  get: (x: number, y: number) => string | null
  clear: () => Scene
  part: (spec: PartSpec) => Part
  prism: (spec: PrismSpec) => Prism
  rope: (points?: Point2[]) => Rope
  player: (spec?: PlayerSpec) => PlayerEntity
  remove: (thing: Piece | Rope | PlayerEntity) => void
  draw: () => Scene
  render: () => Scene
  frame: (dt?: number) => Scene
  start: () => Scene
  stop: () => Scene
  isVisible: () => boolean
  dirty: () => Scene
  setTime: (ticks: number) => Scene
  destroy: () => void
}

type Cell = { x: number; y: number; id: string }

export function createScene(canvas: HTMLCanvasElement, options: SceneOptions = {}): Scene {
  const TW = Math.round((options.oblique ? options.oblique[0] : 3 / 16) * 16)
  const TH = Math.round((options.oblique ? options.oblique[1] : 6 / 16) * 16)
  const ox = TW / 16
  const oy = TH / 16
  const temperature = options.temperature === undefined ? 0.8 : options.temperature
  const downfall = options.downfall === undefined ? 0.4 : options.downfall
  let cells: Record<string, Cell> = {}
  const parts: Piece[] = []
  const ropes: Rope[] = []
  const entities: PlayerEntity[] = []
  let world: { canvas: HTMLCanvasElement; minX: number; maxY: number; animated: boolean; frame: number } | null =
    null
  let worldDirty = true
  let tints: Tints | null = null
  const ctx = context2d(canvas)
  const cache: {
    sky: HTMLCanvasElement | null
    skyKey: string
    clouds: HTMLCanvasElement | null
    cloudKey: string
    cloudClock: number
  } = { sky: null, skyKey: "", clouds: null, cloudKey: "", cloudClock: -1e9 }
  let visible = typeof IntersectionObserver === "undefined"
  let animating = false
  let request = 0
  let last: number | null = null
  let drivenAt = 0
  let ambient: ReturnType<typeof setTimeout> | null = null
  let watcher: IntersectionObserver | null = null
  let sizeWatcher: ResizeObserver | null = null

  if (options.height) canvas.style.height = options.height + "px"

  const key = (x: number, y: number) => x + "," + y

  function ensureTints(store: TextureStore): Tints {
    if (!tints) {
      tints = {
        grass: colormap(store, "grass", temperature, downfall),
        foliage: colormap(store, "foliage", temperature, downfall),
        water: WATER,
        blocks: {},
      }
    }
    return tints
  }

  function blockOf(store: TextureStore, id: string) {
    const t = ensureTints(store)
    if (!t.blocks[id]) t.blocks[id] = resolveBlock(store, id, t)
    return t.blocks[id]
  }

  function solid(store: TextureStore, x: number, y: number) {
    const c = cells[key(x, y)]
    return c ? blockOf(store, c.id).occludes : false
  }

  function culls(store: TextureStore, block: Block, x: number, y: number) {
    const c = cells[key(x, y)]
    if (!c) return false
    const other = blockOf(store, c.id)
    if (other.opaque) return true
    return block.self && c.id === block.id
  }

  function frameAt(block: Block) {
    if (!block.animated || !block.top.tex) return 0
    const t = block.top.tex
    return Math.floor(scene.clock / t.frametime) % t.frames
  }

  function light() {
    return skyLight(celestial(scene.time))
  }

  function buildWorld(store: TextureStore) {
    const list = Object.keys(cells).map((k) => cells[k])
    if (!list.length) {
      world = null
      return
    }
    let minX = Infinity
    let maxX = -Infinity
    let minY = Infinity
    let maxY = -Infinity
    list.forEach((c) => {
      minX = Math.min(minX, c.x)
      maxX = Math.max(maxX, c.x)
      minY = Math.min(minY, c.y)
      maxY = Math.max(maxY, c.y)
      blockOf(store, c.id)
    })
    const R = new Raster((maxX - minX + 1) * 16 + TW, (maxY - minY + 1) * 16 + TH)
    const lit = light()
    let animated = false
    const level = (edge: boolean) => aoVertex(edge, false, false)
    const geometry = (c: Cell, block: Block) => {
      const above = cells[key(c.x, c.y + 1)]
      const height = block.liquid && !(above && above.id === c.id) ? 8 / 9 : 1
      const crop = Math.round(16 * (1 - height))
      return {
        x0: (c.x - minX) * 16,
        y0: TH + (maxY - c.y) * 16,
        crop,
        box: {
          stretch: false,
          cropTop: crop,
          texH: 16,
          depthTexels: 16,
          frame: frameAt(block),
          alpha: 1,
        } satisfies PaintBox,
      }
    }
    list.forEach((c) => {
      const block = blockOf(store, c.id)
      if (block.animated) animated = true
      const g = geometry(c, block)
      if (!culls(store, block, c.x, c.y + 1) || g.crop > 0) {
        paintTop(
          R,
          g.x0,
          g.y0 + g.crop,
          16,
          TW,
          TH,
          block.top,
          UP * lit,
          level(solid(store, c.x - 1, c.y + 1)),
          level(solid(store, c.x + 1, c.y + 1)),
          g.box,
        )
      }
      if (!culls(store, block, c.x + 1, c.y)) {
        paintEast(
          R,
          g.x0 + 16,
          g.y0,
          16,
          TW,
          TH,
          block.side,
          EW * lit,
          level(solid(store, c.x + 1, c.y + 1)),
          level(solid(store, c.x + 1, c.y - 1)),
          g.box,
        )
      }
    })
    list.forEach((c) => {
      const block = blockOf(store, c.id)
      const g = geometry(c, block)
      paintFront(R, g.x0, g.y0 + g.crop, 16, 16 - g.crop, block.side, NS * lit, g.box)
    })
    world = { canvas: R.flush(), minX, maxY, animated, frame: -1 }
  }

  function buildPart(store: TextureStore, part: Part) {
    const block = blockOf(store, part.block)
    const lit = light()
    const W = Math.max(1, Math.round(part.w * 16))
    const H = Math.max(1, Math.round(part.h * 16))
    const tw = Math.round(part.d * TW)
    const th = Math.round(part.d * TH)
    const k = [
      part.block,
      W,
      H,
      tw,
      th,
      part.stretch,
      part.alpha,
      Math.round(lit * 500),
      frameAt(block),
    ].join("|")
    if (part.key === k && part.cache) return part.cache
    const R = new Raster(W + tw, H + th)
    const box: PaintBox = {
      stretch: part.stretch,
      cropTop: 0,
      texH: H,
      depthTexels: Math.round(part.d * 16),
      frame: frameAt(block),
      alpha: part.alpha,
    }
    paintTop(R, 0, th, W, tw, th, block.top, UP * lit, 1, 1, box)
    paintEast(R, W, th, H, tw, th, block.side, EW * lit, 1, 1, box)
    paintFront(R, 0, th, W, H, block.side, NS * lit, box)
    part.key = k
    part.cache = { canvas: R.flush(), tw, th }
    return part.cache
  }

  function measure(): SceneView {
    const dpr = globalThis.devicePixelRatio || 1
    const cssW = canvas.clientWidth || canvas.parentElement?.clientWidth || 600
    const cssH = options.aspect
      ? clamp(Math.round(cssW * options.aspect), options.minHeight || 0, options.maxHeight || 9999)
      : canvas.clientHeight || options.height || 240
    if (options.aspect && canvas.style.height !== cssH + "px") canvas.style.height = cssH + "px"
    const dW = Math.round(cssW * dpr)
    const dH = Math.round(cssH * dpr)
    if (canvas.width !== dW || canvas.height !== dH) {
      canvas.width = dW
      canvas.height = dH
    }
    const wanted = scene.fit || options.view || { x0: -10, x1: 10, y0: 60, y1: 70 }
    const spec = typeof wanted === "function" ? wanted(cssW, cssH) : wanted
    let rect: ViewRect
    if ("zoom" in spec) {
      const hw = cssW / 2 / spec.zoom
      const hh = cssH / 2 / spec.zoom
      rect = { x0: spec.x - hw, x1: spec.x + hw, y0: spec.y - hh, y1: spec.y + hh }
    } else {
      rect = spec
    }
    let U = Math.min(dW / (rect.x1 - rect.x0), dH / (rect.y1 - rect.y0))
    if (options.maxUnit) U = Math.min(U, options.maxUnit * dpr)
    const n = U / 16
    if (n >= 1 && (n - Math.floor(n)) / n <= 0.18) U = Math.floor(n) * 16
    const cx = (rect.x0 + rect.x1) / 2 - ox / 2
    const cy = (rect.y0 + rect.y1) / 2 - oy / 2
    const originX = Math.round(dW / 2 - cx * U)
    const originY = Math.round(dH / 2 + cy * U)
    const X = (x: number, d = 0) => originX + (x + d * ox) * U
    const Y = (y: number, d = 0) => originY - (y + d * oy) * U
    const v: SceneView = {
      dpr,
      w: cssW,
      h: cssH,
      dW,
      dH,
      U,
      unit: U / dpr,
      originX,
      originY,
      rect,
      ox,
      oy,
      X,
      Y,
      x: (x, d = 0) => X(x, d) / dpr,
      y: (y, d = 0) => Y(y, d) / dpr,
      worldX: (px, d = 0) => (px * dpr - originX) / U - d * ox,
      worldY: (py, d = 0) => (originY - py * dpr) / U - d * oy,
    }
    scene.view = v
    return v
  }

  function drawSky(v: SceneView, angle: number, colors: { sky: Rgb; fog: Rgb }) {
    const eye = scene.horizon === undefined ? v.worldY(v.h / 2) : scene.horizon
    const hy = v.Y(eye)
    const F = v.dH * 0.9
    const k = [v.dH, Math.round(hy), angle.toFixed(4), temperature].join("|")
    if (cache.skyKey !== k) {
      const c = canvasOf(1, v.dH)
      const x = context2d(c)
      const img = x.createImageData(1, v.dH)
      for (let y = 0; y < v.dH; y++) {
        let fog = 1
        const above = hy - (y + 0.5)
        if (above > 0) {
          const phi = Math.atan(above / F)
          const sph = 16 / Math.sin(phi)
          const cyl = Math.max(16 / Math.tan(phi), 16)
          fog = clamp(Math.max(sph, cyl) / SKY_END, 0, 1)
        }
        img.data[y * 4] = mix(colors.sky[0], colors.fog[0], fog) * 255
        img.data[y * 4 + 1] = mix(colors.sky[1], colors.fog[1], fog) * 255
        img.data[y * 4 + 2] = mix(colors.sky[2], colors.fog[2], fog) * 255
        img.data[y * 4 + 3] = 255
      }
      x.putImageData(img, 0, 0)
      cache.sky = c
      cache.skyKey = k
    }
    ctx.imageSmoothingEnabled = true
    if (cache.sky) ctx.drawImage(cache.sky, 0, 0, v.dW, v.dH)
    return { eye, hy, F }
  }

  function drawCelestial(store: TextureStore, v: SceneView, horizon: { hy: number }, angle: number) {
    const hy = horizon.hy
    if (hy <= 0) return
    const R = clamp(hy * 0.82, 40 * v.dpr, v.dW * 0.44)
    const cx = v.dW / 2
    ctx.save()
    ctx.beginPath()
    ctx.rect(0, 0, v.dW, hy)
    ctx.clip()
    const theta = angle * Math.PI * 2
    const glow = sunrise(angle)
    if (glow) {
      const side = -Math.sin(theta) >= 0 ? 1 : -1
      const gx = cx + side * R
      ctx.save()
      ctx.translate(gx, hy)
      ctx.scale(1, 0.75)
      const grad = ctx.createRadialGradient(0, 0, 0, 0, 0, R * 0.95)
      const rgb =
        Math.round(glow[0] * 255) + "," + Math.round(glow[1] * 255) + "," + Math.round(glow[2] * 255)
      grad.addColorStop(0, "rgba(" + rgb + "," + glow[3].toFixed(3) + ")")
      grad.addColorStop(1, "rgba(" + rgb + ",0)")
      ctx.fillStyle = grad
      ctx.fillRect(-R * 1.2, -R * 1.4, R * 2.4, R * 1.4)
      ctx.restore()
    }
    ctx.globalCompositeOperation = "lighter"
    const bright = starBrightness(angle)
    if (bright > 0) {
      const shade = Math.round(bright * 255)
      ctx.fillStyle = "rgb(" + shade + "," + shade + "," + shade + ")"
      stars().forEach((s) => {
        const wx = -(s[1] * Math.sin(theta) + s[2] * Math.cos(theta))
        const wy = s[1] * Math.cos(theta) - s[2] * Math.sin(theta)
        if (wy <= 0) return
        const r = (Math.acos(clamp(-s[0], -1, 1)) / (Math.PI / 2)) * R
        const a = Math.atan2(wy, wx)
        const size = v.dpr * (s[3] > 0.2 ? 1.5 : 1)
        ctx.fillRect(cx + Math.cos(a) * r - size / 2, hy - Math.sin(a) * r - size / 2, size, size)
      })
    }
    ctx.imageSmoothingEnabled = false
    const sun = store["environment/celestial/sun"]
    const sx = -Math.sin(theta)
    const sy = Math.cos(theta)
    const half = (R * Math.atan(0.3)) / (Math.PI / 2)
    if (sun) ctx.drawImage(sun.canvas, cx + sx * R - half, hy - sy * R - half, half * 2, half * 2)
    const moon = store["environment/celestial/moon/" + MOONS[Math.floor(scene.time / 24000) % 8]]
    const mh = (R * Math.atan(0.2)) / (Math.PI / 2)
    if (moon) ctx.drawImage(moon.canvas, cx - sx * R - mh, hy + sy * R - mh, mh * 2, mh * 2)
    ctx.restore()
  }

  function drawClouds(
    store: TextureStore,
    v: SceneView,
    horizon: { eye: number; hy: number; F: number },
    angle: number,
  ) {
    if (!scene.clouds) return
    const height = CLOUD_Y - horizon.eye
    if (height <= 0 || horizon.hy <= 0) return
    const tex = store["environment/clouds"]
    if (!tex) return
    const cw = Math.ceil(v.w / 2)
    const hyc = horizon.hy / v.dpr / 2
    const ch = Math.min(Math.ceil(hyc), Math.ceil(v.h / 2))
    const F = horizon.F / v.dpr / 2
    const camX = v.worldX(v.w / 2)
    const k = [
      cw,
      ch,
      hyc.toFixed(1),
      F.toFixed(1),
      camX.toFixed(2),
      height.toFixed(2),
      angle.toFixed(4),
    ].join("|")
    if (cache.clouds && cache.cloudKey === k && Math.abs(scene.clock - cache.cloudClock) < 5) {
      ctx.imageSmoothingEnabled = true
      ctx.drawImage(cache.clouds, 0, 0, cw, ch, 0, 0, cw * 2 * v.dpr, ch * 2 * v.dpr)
      return
    }
    if (!cache.clouds || cache.clouds.width < cw || cache.clouds.height < ch) {
      cache.clouds = canvasOf(cw, ch)
    }
    const cctx = context2d(cache.clouds)
    const img = cctx.createImageData(cw, ch)
    const d = img.data
    const color = cloudColor(angle)
    const drift = scene.clock * 0.03 + camX
    const td = tex.data
    const cloud = (cx: number, cz: number) => {
      const u = ((cx % 256) + 256) % 256
      const w = ((cz % 256) + 256) % 256
      return td[(w * 256 + u) * 4 + 3] > 0
    }
    for (let j = 0; j < ch; j++) {
      const tan = (hyc - (j + 0.5)) / F
      if (tan <= 0) continue
      const t0 = height / tan
      const t1 = (height + CLOUD_THICK) / tan
      for (let i = 0; i < cw; i++) {
        const lat = (i + 0.5 - cw / 2) / F
        let t = t0 + 1e-4
        const X = drift + lat * t
        const Z = -t
        let cx = Math.floor(X / CLOUD_CELL)
        let cz = Math.floor(Z / CLOUD_CELL)
        let shade = 0
        if (cloud(cx, cz)) shade = CLOUD_FACE.bottom
        else {
          for (let step = 0; step < 12; step++) {
            let tz = -cz * CLOUD_CELL
            const tx =
              lat > 0
                ? t0 + ((cx + 1) * CLOUD_CELL - (drift + lat * t0)) / lat
                : lat < 0
                  ? t0 + (cx * CLOUD_CELL - (drift + lat * t0)) / lat
                  : Infinity
            if (tz <= t) tz = t + 1e-4
            const next = Math.min(tz, tx)
            if (next > t1) break
            t = next + 1e-4
            if (tz <= tx) {
              cz -= 1
              if (cloud(cx, cz)) {
                shade = CLOUD_FACE.ns
                break
              }
            } else {
              cx += lat > 0 ? 1 : -1
              if (cloud(cx, cz)) {
                shade = CLOUD_FACE.ew
                break
              }
            }
          }
        }
        if (!shade) continue
        const dist = t * Math.sqrt(1 + tan * tan + lat * lat)
        const a = 0.8 * (1 - clamp(dist / CLOUD_FOG_END, 0, 1))
        if (a <= 0.01) continue
        const o = (j * cw + i) * 4
        d[o] = color[0] * shade * 255
        d[o + 1] = color[1] * shade * 255
        d[o + 2] = color[2] * shade * 255
        d[o + 3] = a * 255
      }
    }
    cctx.clearRect(0, 0, cache.clouds.width, cache.clouds.height)
    cctx.putImageData(img, 0, 0)
    cache.cloudKey = k
    cache.cloudClock = scene.clock
    ctx.imageSmoothingEnabled = true
    ctx.drawImage(cache.clouds, 0, 0, cw, ch, 0, 0, cw * 2 * v.dpr, ch * 2 * v.dpr)
  }

  function blit(v: SceneView, img: CanvasImageSource, x: number, y: number, w: number, h: number) {
    ctx.imageSmoothingEnabled = v.U / 16 < 1
    ctx.imageSmoothingQuality = "high"
    const px = Math.round(x)
    const py = Math.round(y)
    ctx.drawImage(img, px, py, Math.round(x + w) - px, Math.round(y + h) - py)
  }

  function drawWorld(store: TextureStore, v: SceneView) {
    if (worldDirty || (world && world.animated && world.frame !== Math.floor(scene.clock / 2))) {
      buildWorld(store)
      worldDirty = false
      if (world) world.frame = Math.floor(scene.clock / 2)
    }
    if (!world) return
    const k = v.U / 16
    blit(
      v,
      world.canvas,
      v.X(world.minX),
      v.Y(world.maxY + 1) - TH * k,
      world.canvas.width * k,
      world.canvas.height * k,
    )
  }

  function drawPart(store: TextureStore, v: SceneView, part: Part) {
    const built = buildPart(store, part)
    const k = v.U / 16
    const z0 = part.z - part.d / 2
    blit(
      v,
      built.canvas,
      v.X(part.x - part.w / 2, z0),
      v.Y(part.y + part.h / 2, z0) - built.th * k,
      built.canvas.width * k,
      built.canvas.height * k,
    )
  }

  function tile(face: Face, mul: number) {
    const k = [face.tex ? face.tex.w : 0, face.tint, face.over ? 1 : 0, Math.round(mul * 500)].join("|")
    if (!face.tiles) face.tiles = {}
    if (face.tiles[k]) return face.tiles[k]
    const R = new Raster(16, 16)
    const box: PaintBox = { stretch: false, cropTop: 0, texH: 16, depthTexels: 16, frame: 0, alpha: 1 }
    paintFront(R, 0, 0, 16, 16, face, mul, box)
    face.tiles[k] = R.flush()
    return face.tiles[k]
  }

  function drawPrism(store: TextureStore, v: SceneView, prism: Prism) {
    const block = blockOf(store, prism.block)
    const lit = light()
    const pts = prism.points
    if (pts.length < 3) return
    let area = 0
    for (let i = 0; i < pts.length; i++) {
      const a = pts[i]
      const b = pts[(i + 1) % pts.length]
      area += a[0] * b[1] - b[0] * a[1]
    }
    const turn = area > 0 ? 1 : -1
    const z0 = prism.z - prism.d / 2
    const z1 = prism.z + prism.d / 2
    const k = v.U / 16
    ctx.save()
    ctx.globalAlpha = prism.alpha
    ctx.imageSmoothingEnabled = k < 1
    const fill = (points: Point2[], img: HTMLCanvasElement, m: number[]) => {
      const pattern = ctx.createPattern(img, "repeat")
      if (!pattern) return
      pattern.setTransform(new DOMMatrix(m))
      ctx.fillStyle = pattern
      ctx.beginPath()
      points.forEach((q, j) => {
        if (j) ctx.lineTo(q[0], q[1])
        else ctx.moveTo(q[0], q[1])
      })
      ctx.closePath()
      ctx.fill()
    }
    for (let e = 0; e < pts.length; e++) {
      const p0 = pts[e]
      const p1 = pts[(e + 1) % pts.length]
      const dx = p1[0] - p0[0]
      const dy = p1[1] - p0[1]
      const len = Math.hypot(dx, dy) || 1
      const nx = (turn * dy) / len
      const ny = (-turn * dx) / len
      if (nx * ox + ny * oy <= 1e-4) continue
      const up = ny > 0.5
      const shade = up ? UP : ny < -0.5 ? 0.5 : EW
      const img = tile(up ? block.top : block.side, shade * lit)
      const A: Point2 = [v.X(p0[0], z0), v.Y(p0[1], z0)]
      const B: Point2 = [v.X(p1[0], z0), v.Y(p1[1], z0)]
      const C: Point2 = [v.X(p1[0], z1), v.Y(p1[1], z1)]
      const D: Point2 = [v.X(p0[0], z1), v.Y(p0[1], z1)]
      const ux = (B[0] - A[0]) / (len * 16)
      const uy = (B[1] - A[1]) / (len * 16)
      const wx = (D[0] - A[0]) / (prism.d * 16)
      const wy = (D[1] - A[1]) / (prism.d * 16)
      fill([A, B, C, D], img, [ux, uy, wx, wy, A[0], A[1]])
    }
    const front = pts.map((q): Point2 => [v.X(q[0], z0), v.Y(q[1], z0)])
    fill(front, tile(block.side, NS * lit), [k, 0, 0, k, v.X(0, z0), v.Y(0, z0)])
    ctx.restore()
  }

  function sample(pts: Point2[], t: number): Point2 {
    const lengths = [0]
    for (let i = 1; i < pts.length; i++) {
      lengths.push(lengths[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]))
    }
    const want = t * lengths[lengths.length - 1]
    for (let j = 1; j < pts.length; j++) {
      if (want <= lengths[j] || j === pts.length - 1) {
        const span = lengths[j] - lengths[j - 1] || 1
        const f = clamp((want - lengths[j - 1]) / span, 0, 1)
        return [mix(pts[j - 1][0], pts[j][0], f), mix(pts[j - 1][1], pts[j][1], f)]
      }
    }
    return pts[pts.length - 1]
  }

  function drawRopes(v: SceneView) {
    const lit = light()
    ropes.forEach((rope) => {
      if (!rope.visible || rope.points.length < 2) return
      const pts = rope.points
      const total = 24
      const width = Math.max(1.5 * v.dpr, v.U * 0.07)
      ctx.lineWidth = width
      ctx.lineCap = "butt"
      for (let i = 0; i < total; i++) {
        const a = sample(pts, i / total)
        const b = sample(pts, (i + 1) / total)
        const o = i % 2 === 0 ? 0.7 : 1.0
        ctx.strokeStyle =
          "rgb(" +
          Math.round(0.5 * o * lit * 255) +
          "," +
          Math.round(0.4 * o * lit * 255) +
          "," +
          Math.round(0.3 * o * lit * 255) +
          ")"
        ctx.beginPath()
        ctx.moveTo(v.X(a[0], 0.5), v.Y(a[1], 0.5))
        ctx.lineTo(v.X(b[0], 0.5), v.Y(b[1], 0.5))
        ctx.stroke()
      }
    })
  }

  function surfaces(store: TextureStore, e: PlayerEntity) {
    const out: { x0: number; x1: number; y: number; z0: number; z1: number }[] = []
    const r = 0.5
    const low = Math.floor(e.y - 0.5)
    for (let cx = Math.floor(e.x - r); cx <= Math.floor(e.x + r - 1e-6); cx++) {
      for (let y = Math.floor(e.y + 1e-6); y >= low; y--) {
        const c = cells[key(cx, y - 1)]
        if (c && blockOf(store, c.id).opaque) {
          out.push({ x0: Math.max(cx, e.x - r), x1: Math.min(cx + 1, e.x + r), y, z0: 0, z1: 1 })
          break
        }
      }
    }
    parts.forEach((p) => {
      if (!p.visible || p.prism) return
      const top = p.y + p.h / 2
      if (top > e.y + 0.02 || top < low) return
      const x0 = Math.max(p.x - p.w / 2, e.x - r)
      const x1 = Math.min(p.x + p.w / 2, e.x + r)
      if (x1 <= x0) return
      out.push({ x0, x1, y: top, z0: p.z - p.d / 2, z1: p.z + p.d / 2 })
    })
    return out
  }

  function drawShadow(store: TextureStore, v: SceneView, e: PlayerEntity) {
    const shadow = store["misc/shadow"]
    if (!shadow) return
    const r = 0.5
    surfaces(store, e).forEach((s) => {
      const weight = 1 - (e.y - s.y) / 2
      const alpha = weight * 0.5
      if (alpha <= 0) return
      ctx.save()
      ctx.beginPath()
      ctx.moveTo(v.X(s.x0, s.z0), v.Y(s.y, s.z0))
      ctx.lineTo(v.X(s.x1, s.z0), v.Y(s.y, s.z0))
      ctx.lineTo(v.X(s.x1, s.z1), v.Y(s.y, s.z1))
      ctx.lineTo(v.X(s.x0, s.z1), v.Y(s.y, s.z1))
      ctx.closePath()
      ctx.clip()
      ctx.globalAlpha = Math.min(1, alpha)
      const ax = v.X(e.x - r, e.z - r)
      const ay = v.Y(s.y, e.z - r)
      const bx = v.X(e.x + r, e.z - r)
      const by = v.Y(s.y, e.z - r)
      const cx = v.X(e.x - r, e.z + r)
      const cy = v.Y(s.y, e.z + r)
      ctx.setTransform(bx - ax, by - ay, cx - ax, cy - ay, ax, ay)
      ctx.imageSmoothingEnabled = true
      ctx.drawImage(shadow.canvas, 0, 0, 1, 1)
      ctx.restore()
    })
  }

  function pose(e: PlayerEntity): Record<PartName, { pivot: Vec3; rot: number }> {
    const L = e.limbs
    const posed: Record<PartName, { pivot: Vec3; rot: number }> = {
      head: { pivot: [0, 0, 0], rot: e.pitch },
      body: { pivot: [0, 0, 0], rot: 0 },
      rightArm: { pivot: [-5, 2, 0], rot: L.rightArm },
      leftArm: { pivot: [5, 2, 0], rot: L.leftArm },
      rightLeg: { pivot: [-1.9, 12, 0], rot: L.rightLeg },
      leftLeg: { pivot: [1.9, 12, 0], rot: L.leftLeg },
    }
    if (e.crouch) {
      posed.body.rot = 0.5
      posed.rightArm.rot += 0.4
      posed.leftArm.rot += 0.4
      posed.rightLeg.pivot = [-1.9, 12.2, 4]
      posed.leftLeg.pivot = [1.9, 12.2, 4]
      posed.head.pivot = [0, 4.2, 0]
      posed.body.pivot = [0, 3.2, 0]
      posed.rightArm.pivot = [-5, 5.2, 0]
      posed.leftArm.pivot = [5, 5.2, 0]
    }
    return posed
  }

  function drawEntity(store: TextureStore, v: SceneView, e: PlayerEntity, lit: number) {
    const posed = pose(e)
    const f = e.facing >= 0 ? 1 : -1
    const s = PLAYER_SCALE / 16
    const lift = e.crouch ? -0.125 : 0
    const toWorld = (p: Vec3, part: { pivot: Vec3; rot: number }): Vec3 => {
      const c = Math.cos(part.rot)
      const n = Math.sin(part.rot)
      const y = p[1] * c - p[2] * n
      const z = p[1] * n + p[2] * c
      const mx = part.pivot[0] + p[0]
      const my = part.pivot[1] + y
      const mz = part.pivot[2] + z
      return [e.x + f * -mz * s, e.y + lift + (24 - my) * s, e.z + f * mx * s]
    }
    const list = BOXES.map((box, index) => {
      const part = posed[box.name]
      const centre = toWorld(
        [
          box.origin[0] + box.size[0] / 2,
          box.origin[1] + box.size[1] / 2,
          box.origin[2] + box.size[2] / 2,
        ],
        part,
      )
      return { box, part, depth: centre[2], height: centre[1], index }
    })
    list.sort((a, b) => {
      if (Math.abs(a.depth - b.depth) > 1e-3) return b.depth - a.depth
      if (Math.abs(a.height - b.height) > 1e-3 && !a.box.layer === !b.box.layer) return a.height - b.height
      return a.index - b.index
    })
    const smooth = v.U < 16
    list.forEach((item) => {
      item.box.faces.forEach((face) => {
        const c = Math.cos(item.part.rot)
        const n = Math.sin(item.part.rot)
        const ny = face.n[1] * c - face.n[2] * n
        const nz = face.n[1] * n + face.n[2] * c
        const wn: Vec3 = [f * -nz, -ny, f * face.n[0]]
        if (wn[0] * ox + wn[1] * oy - wn[2] <= 1e-4) return
        const rest: Vec3 = [f * -face.n[2], -face.n[1], f * face.n[0]]
        const shade =
          rest[1] > 0.5 ? UP : rest[1] < -0.5 ? 0.5 : Math.abs(rest[0]) > 0.5 ? EW : NS
        const P = face.v.map((p): Point2 => {
          const w = toWorld(p, item.part)
          return [v.X(w[0], w[2]), v.Y(w[1], w[2])]
        })
        const uv = face.uv
        const u0 = uv[0]
        const v0 = uv[1]
        const u1 = uv[2]
        const v1 = uv[3]
        const A = P[0]
        const B = P[1]
        const C = P[2]
        const at = (u: number, vv: number): Point2 => {
          const a = (u - u0) / (u1 - u0)
          const b = (vv - v0) / (v1 - v0)
          return [
            B[0] + a * (A[0] - B[0]) + b * (C[0] - B[0]),
            B[1] + a * (A[1] - B[1]) + b * (C[1] - B[1]),
          ]
        }
        const umin = Math.min(u0, u1)
        const vmin = Math.min(v0, v1)
        const du = Math.abs(u1 - u0)
        const dv = Math.abs(v1 - v0)
        const o = at(umin, vmin)
        const ex = at(umin + du, vmin)
        const ey = at(umin, vmin + dv)
        const ax = ex[0] - o[0]
        const ay = ex[1] - o[1]
        const bx = ey[0] - o[0]
        const by = ey[1] - o[1]
        const grow = 0.45
        const la = Math.hypot(ax, ay) || 1
        const lb = Math.hypot(bx, by) || 1
        const ga = grow / la
        const gb = grow / lb
        ctx.setTransform(
          ax * (1 + 2 * ga),
          ay * (1 + 2 * ga),
          bx * (1 + 2 * gb),
          by * (1 + 2 * gb),
          o[0] - ax * ga - bx * gb,
          o[1] - ay * ga - by * gb,
        )
        ctx.imageSmoothingEnabled = smooth
        ctx.drawImage(baked(store, e.skin, shade * lit), umin, vmin, du, dv, 0, 0, 1, 1)
      })
    })
    ctx.setTransform(1, 0, 0, 1, 0, 0)
  }

  function overlayKit(v: SceneView): OverlayView {
    const colors = palette()
    return Object.assign(v, {
      colors,
      font: (size?: number) => (size || 11) + "px " + colors.mono,
      text: (text: string, x: number, y: number, o: TextOptions = {}) => {
        const s = o.scale || (v.w < 420 ? 1 : 2)
        const w = mcTextWidth(text) * s
        const left = o.align === "center" ? x - w / 2 : o.align === "right" ? x - w : x
        return mcText(
          ctx,
          text,
          Math.round(left),
          Math.round(y - 4 * s),
          s,
          o.color || "#ffffff",
          o.shadow !== false,
        )
      },
      tag: (text: string, x: number, y: number, o: TagOptions = {}) => {
        const s = o.scale || (v.w < 420 ? 1 : 2)
        const w = (mcTextWidth(text) + 3) * s
        const h = 10 * s
        const placed = o.align === "center" ? x - w / 2 : o.align === "right" ? x - w : x
        const left = Math.round(clamp(placed, 2, v.w - w - 2))
        const top = Math.round(y - h / 2)
        ctx.fillStyle = o.background || "rgba(0,0,0,0.42)"
        ctx.fillRect(left, top, w, h)
        mcText(ctx, text, left + 2 * s, top + s, s, o.color ? mcColor(o.color) : "#ffffff", true)
        return { x: left, y: top, w, h }
      },
      line: (points: Point2[], o: LineOptions = {}) => {
        ctx.save()
        ctx.lineCap = o.dash ? "butt" : "round"
        ctx.lineJoin = "round"
        ctx.setLineDash(o.dash || [])
        ctx.beginPath()
        points.forEach((p, i) => {
          if (i) ctx.lineTo(p[0], p[1])
          else ctx.moveTo(p[0], p[1])
        })
        if (o.halo !== false) {
          ctx.strokeStyle = o.halo || "rgba(0,0,0,0.22)"
          ctx.lineWidth = (o.width || 1.5) + 1.5
          ctx.stroke()
        }
        ctx.strokeStyle = o.color || "rgba(255,255,255,0.9)"
        ctx.lineWidth = o.width || 1.5
        ctx.stroke()
        ctx.restore()
      },
    })
  }

  function render() {
    const store = textures
    if (!store) return
    ensureTints(store)
    const v = measure()
    const angle = celestial(scene.time)
    const colors = skyColors(angle, temperature)
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.globalAlpha = 1
    ctx.globalCompositeOperation = "source-over"
    const horizon = drawSky(v, angle, colors)
    drawCelestial(store, v, horizon, angle)
    drawClouds(store, v, horizon, angle)
    drawWorld(store, v)
    const lit = light()
    parts
      .slice()
      .sort((a, b) => {
        if (Math.abs(a.z - b.z) > 1e-3) return b.z - a.z
        if (Math.abs(a.y - b.y) > 1e-3) return a.y - b.y
        return a.x - b.x
      })
      .forEach((p) => {
        if (!p.visible) return
        if (p.prism) drawPrism(store, v, p)
        else drawPart(store, v, p)
      })
    drawRopes(v)
    entities.forEach((e) => {
      if (e.visible && e.shadow) drawShadow(store, v, e)
    })
    entities
      .slice()
      .sort((a, b) => b.z - a.z)
      .forEach((e) => {
        if (e.visible) drawEntity(store, v, e, lit)
      })
    if (scene.overlay) {
      ctx.setTransform(v.dpr, 0, 0, v.dpr, 0, 0)
      ctx.imageSmoothingEnabled = true
      scene.overlay(ctx, overlayKit(v))
      ctx.setTransform(1, 0, 0, 1, 0, 0)
    }
  }

  function frame(now: number) {
    request = 0
    if (animating && visible) {
      const dt = last === null ? 0 : Math.min(0.05, (now - last) / 1000)
      last = now
      scene.clock += dt * 20
      if (scene.step && scene.step(dt) === false) animating = false
    }
    render()
    if (animating && visible && !request) request = requestAnimationFrame(frame)
  }

  function kick() {
    if (!request) request = requestAnimationFrame(frame)
  }

  function breathe() {
    if (ambient) clearTimeout(ambient)
    if (!visible || options.ambient === false) return
    ambient = setTimeout(() => {
      if (!animating && Date.now() - drivenAt > 400 && scene.clouds) {
        scene.clock += 5
        kick()
      }
      breathe()
    }, 250)
  }

  const scene: Scene = {
    time: options.time === undefined ? 1000 : options.time,
    clock: 0,
    clouds: options.clouds !== false,
    horizon: options.horizon,
    view: null,
    overlay: options.overlay || null,
    step: options.step || null,
    fit: null,

    set(x, y, id) {
      if (id) cells[key(x, y)] = { x, y, id }
      else delete cells[key(x, y)]
      worldDirty = true
      return scene
    },
    fill(x0, y0, x1, y1, id) {
      for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
        for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) scene.set(x, y, id)
      }
      return scene
    },
    get(x, y) {
      const c = cells[key(Math.floor(x), Math.floor(y))]
      return c ? c.id : null
    },
    clear() {
      cells = {}
      worldDirty = true
      return scene
    },
    part(spec) {
      const part: Part = {
        prism: false,
        block: spec.block || "smooth_stone",
        x: spec.x || 0,
        y: spec.y || 0,
        w: spec.w || 1,
        h: spec.h || 1,
        d: spec.d === undefined ? 1 : spec.d,
        z: spec.z === undefined ? 0.5 : spec.z,
        stretch: !!spec.stretch,
        alpha: spec.alpha === undefined ? 1 : spec.alpha,
        visible: spec.visible !== false,
        cache: null,
        key: "",
      }
      parts.push(part)
      return part
    },
    prism(spec) {
      const prism: Prism = {
        prism: true,
        block: spec.block || "stone",
        points: spec.points || [],
        d: spec.d === undefined ? 1 : spec.d,
        z: spec.z === undefined ? 0.5 : spec.z,
        alpha: spec.alpha === undefined ? 1 : spec.alpha,
        visible: spec.visible !== false,
        get x() {
          return Math.min(...prism.points.map((q) => q[0]))
        },
        get y() {
          return Math.min(...prism.points.map((q) => q[1]))
        },
      }
      parts.push(prism)
      return prism
    },
    rope(points) {
      const rope: Rope = { points: points || [], visible: true }
      ropes.push(rope)
      return rope
    },
    player(spec = {}) {
      const e: PlayerEntity = {
        x: spec.x || 0,
        y: spec.y || 0,
        z: spec.z === undefined ? 0.5 : spec.z,
        facing: spec.facing || 1,
        pitch: 0,
        crouch: false,
        shadow: spec.shadow !== false,
        visible: true,
        skin: "entity/player/wide/steve",
        limbs: { rightArm: 0, leftArm: 0, rightLeg: 0, leftLeg: 0 },
        walkSpeed: 0,
        walkPos: 0,
        age: 0,
        walk(dt, speed) {
          const ticks = dt * 20
          const g = Math.min((Math.abs(speed) / 20) * 4, 1)
          e.walkSpeed += (g - e.walkSpeed) * (1 - Math.pow(0.6, ticks))
          e.walkPos += e.walkSpeed * ticks
          e.age += ticks
          const phase = e.walkPos * 0.6662
          const amount = Math.min(e.walkSpeed, 1)
          const bob = Math.sin(e.age * 0.067) * 0.05
          e.limbs.rightLeg = Math.cos(phase) * 1.4 * amount
          e.limbs.leftLeg = Math.cos(phase + Math.PI) * 1.4 * amount
          e.limbs.rightArm = Math.cos(phase + Math.PI) * amount + bob
          e.limbs.leftArm = Math.cos(phase) * amount - bob
          return e
        },
        still() {
          e.walkSpeed = 0
          e.limbs.rightArm = e.limbs.leftArm = e.limbs.rightLeg = e.limbs.leftLeg = 0
          return e
        },
      }
      entities.push(e)
      return e
    },
    remove(thing) {
      const lists: unknown[][] = [parts, entities, ropes]
      lists.forEach((list) => {
        const i = list.indexOf(thing)
        if (i !== -1) list.splice(i, 1)
      })
    },
    draw() {
      kick()
      return scene
    },
    render() {
      render()
      return scene
    },
    frame(dt) {
      drivenAt = Date.now()
      scene.clock += (dt || 0) * 20
      render()
      return scene
    },
    start() {
      if (!animating) {
        animating = true
        last = null
      }
      if (visible) kick()
      return scene
    },
    stop() {
      animating = false
      return scene
    },
    isVisible() {
      return visible
    },
    dirty() {
      worldDirty = true
      parts.forEach((p) => {
        if (!p.prism) p.key = ""
      })
      const blocks = tints?.blocks
      if (blocks) {
        Object.keys(blocks).forEach((id) => {
          blocks[id].top.tiles = null
          blocks[id].side.tiles = null
        })
      }
      return scene
    },
    setTime(ticks) {
      scene.time = ticks
      return scene.dirty()
    },
    destroy() {
      animating = false
      if (request) cancelAnimationFrame(request)
      request = 0
      if (ambient) clearTimeout(ambient)
      ambient = null
      watcher?.disconnect()
      sizeWatcher?.disconnect()
      watcher = null
      sizeWatcher = null
    },
  }

  if (typeof IntersectionObserver !== "undefined") {
    watcher = new IntersectionObserver((entries) => {
      visible = entries[entries.length - 1].isIntersecting
      if (visible) {
        last = null
        kick()
        breathe()
      } else if (ambient) {
        clearTimeout(ambient)
      }
    })
    watcher.observe(canvas)
  } else {
    breathe()
  }
  if (typeof ResizeObserver !== "undefined") {
    sizeWatcher = new ResizeObserver(() => kick())
    sizeWatcher.observe(canvas)
  }
  loadTextures().then((store) => {
    textures = store
    if (options.ready) options.ready(scene)
    kick()
  })

  return scene
}
