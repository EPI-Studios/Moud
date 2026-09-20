const TOKENS = {
  bg: ["--bg", "#181818"],
  bg2: ["--bg-2", "#1f1f1f"],
  bg3: ["--bg-3", "#262626"],
  bg4: ["--bg-4", "#303030"],
  line: ["--line", "#2a2a2a"],
  line2: ["--line-2", "#383838"],
  main: ["--text-main", "#f0f0f0"],
  text: ["--text", "#d0d0d0"],
  muted: ["--text-muted", "#9a9a9a"],
  light: ["--text-light", "#6a6a6a"],
  accent: ["--accent", "#c6c6c6"],
  red: ["--red", "#ff6a5c"],
  green: ["--green", "#5fd07a"],
  blue: ["--blue", "#6ea4ff"],
  yellow: ["--yellow", "#f2c14e"],
  purple: ["--purple", "#b692ff"],
  mono: ["--font-mono", "monospace"],
} satisfies Record<string, [string, string]>

export type Palette = Record<keyof typeof TOKENS, string>

export function palette(): Palette {
  const css = getComputedStyle(document.documentElement)
  const out = {} as Palette
  for (const name of Object.keys(TOKENS) as (keyof typeof TOKENS)[]) {
    const [token, fallback] = TOKENS[name]
    out[name] = css.getPropertyValue(token).trim() || fallback
  }
  return out
}

export type View = { ctx: CanvasRenderingContext2D; w: number; h: number }

export function fit(canvas: HTMLCanvasElement, height: number): View | null {
  const ctx = canvas.getContext("2d")
  if (!ctx) return null
  const ratio = window.devicePixelRatio || 1
  const width = canvas.clientWidth || 600
  const pixelWidth = Math.round(width * ratio)
  const pixelHeight = Math.round(height * ratio)
  if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
    canvas.width = pixelWidth
    canvas.height = pixelHeight
  }
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  return { ctx, w: width, h: height }
}

export function arrow(
  ctx: CanvasRenderingContext2D,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  color: string,
  width = 2,
) {
  const angle = Math.atan2(y2 - y1, x2 - x1)
  ctx.strokeStyle = color
  ctx.fillStyle = color
  ctx.lineWidth = width
  ctx.beginPath()
  ctx.moveTo(x1, y1)
  ctx.lineTo(x2, y2)
  ctx.stroke()
  if (Math.hypot(x2 - x1, y2 - y1) < 4) return
  ctx.beginPath()
  ctx.moveTo(x2, y2)
  ctx.lineTo(x2 - 9 * Math.cos(angle - 0.4), y2 - 9 * Math.sin(angle - 0.4))
  ctx.lineTo(x2 - 9 * Math.cos(angle + 0.4), y2 - 9 * Math.sin(angle + 0.4))
  ctx.closePath()
  ctx.fill()
}

export function label(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  color: string,
  align: CanvasTextAlign = "left",
) {
  ctx.fillStyle = color
  ctx.textAlign = align
  ctx.textBaseline = "middle"
  ctx.fillText(text, x, y)
}

export function num(value: number, places = 2) {
  const text = value.toFixed(places)
  return /^-0(\.0+)?$/.test(text) ? text.slice(1) : text
}

export type Scaled = { ctx: CanvasRenderingContext2D; unit: number }

export function fitScaled(canvas: HTMLCanvasElement, width: number, height: number): Scaled | null {
  const ctx = canvas.getContext("2d")
  if (!ctx) return null
  const ratio = window.devicePixelRatio || 1
  const shown = canvas.clientWidth || width
  const scale = (shown / width) * ratio
  const w = Math.round(width * scale)
  const h = Math.round(height * scale)
  if (canvas.width !== w || canvas.height !== h) {
    canvas.width = w
    canvas.height = h
  }
  ctx.setTransform(scale, 0, 0, scale, 0, 0)
  return { ctx, unit: width / shown }
}

export function cssVar(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

export function mono(unit: number, px: number) {
  return `${(px * unit).toFixed(2)}px ${cssVar("--font-mono")}`
}

export function fitLegacy(canvas: HTMLCanvasElement, height: number) {
  const view = fit(canvas, height)
  return view ? { ctx: view.ctx, width: view.w } : null
}
