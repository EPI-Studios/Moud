import { clamp } from "./pointer"

export type BlendMode = "over" | "replace" | "add" | "multiply" | "erase"
export type Paint = { r: number; g: number; b: number; a: number; blend: BlendMode }
export type ShapeOptions = { filled?: boolean; cornerRadius?: number; smooth?: boolean; thickness?: number }

export function paint(r: number, g: number, b: number, transparency = 0, blend: BlendMode = "over"): Paint {
  return { r, g, b, a: 1 - transparency, blend }
}

function roundedBox(px: number, py: number, hx: number, hy: number, radius: number) {
  const qx = Math.abs(px) - hx + radius
  const qy = Math.abs(py) - hy + radius
  const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
  return outside + Math.min(Math.max(qx, qy), 0) - radius
}

export class Raster {
  width: number
  height: number
  px: Float32Array

  constructor(width: number, height: number) {
    this.width = width
    this.height = height
    this.px = new Float32Array(width * height * 4)
  }

  blend(x: number, y: number, ink: Paint, coverage: number) {
    if (coverage <= 0 || x < 0 || y < 0 || x >= this.width || y >= this.height) return
    coverage = Math.min(1, coverage)
    const at = (y * this.width + x) * 4
    const p = this.px
    const dr = p[at]
    const dg = p[at + 1]
    const db = p[at + 2]
    const da = p[at + 3]
    const sa = ink.a * coverage
    let r: number
    let g: number
    let b: number
    let a: number
    if (ink.blend === "over") {
      a = sa + da * (1 - sa)
      if (a <= 0) {
        r = g = b = a = 0
      } else {
        r = (ink.r * sa + dr * da * (1 - sa)) / a
        g = (ink.g * sa + dg * da * (1 - sa)) / a
        b = (ink.b * sa + db * da * (1 - sa)) / a
      }
    } else if (ink.blend === "replace") {
      r = dr + (ink.r - dr) * coverage
      g = dg + (ink.g - dg) * coverage
      b = db + (ink.b - db) * coverage
      a = da + (ink.a - da) * coverage
    } else if (ink.blend === "add") {
      r = dr + ink.r * sa
      g = dg + ink.g * sa
      b = db + ink.b * sa
      a = Math.max(da, sa)
    } else if (ink.blend === "multiply") {
      r = dr * (1 - sa + ink.r * sa)
      g = dg * (1 - sa + ink.g * sa)
      b = db * (1 - sa + ink.b * sa)
      a = da
    } else {
      r = dr
      g = dg
      b = db
      a = da * (1 - sa)
    }
    p[at] = clamp(r, 0, 1)
    p[at + 1] = clamp(g, 0, 1)
    p[at + 2] = clamp(b, 0, 1)
    p[at + 3] = clamp(a, 0, 1)
  }

  shape(
    minX: number,
    minY: number,
    maxX: number,
    maxY: number,
    ink: Paint,
    smooth: boolean,
    inside: (x: number, y: number) => number,
  ) {
    const x0 = Math.max(0, Math.floor(minX) - 1)
    const y0 = Math.max(0, Math.floor(minY) - 1)
    const x1 = Math.min(this.width - 1, Math.ceil(maxX) + 1)
    const y1 = Math.min(this.height - 1, Math.ceil(maxY) + 1)
    for (let py = y0; py <= y1; py++) {
      for (let px = x0; px <= x1; px++) {
        const d = inside(px + 0.5, py + 0.5)
        this.blend(px, py, ink, smooth ? clamp(d + 0.5, 0, 1) : d >= 0 ? 1 : 0)
      }
    }
  }

  rectangle(x: number, y: number, w: number, h: number, ink: Paint, options: ShapeOptions = {}) {
    if (w <= 0 || h <= 0) return
    const filled = options.filled !== false
    const radius = clamp(options.cornerRadius || 0, 0, Math.min(w, h) / 2)
    if (filled && radius <= 0 && !options.smooth) {
      const x0 = Math.max(0, Math.round(x))
      const y0 = Math.max(0, Math.round(y))
      const x1 = Math.min(this.width, Math.round(x + w))
      const y1 = Math.min(this.height, Math.round(y + h))
      for (let py = y0; py < y1; py++) for (let px = x0; px < x1; px++) this.blend(px, py, ink, 1)
      return
    }
    const cx = x + w / 2
    const cy = y + h / 2
    const t = Math.max(1, options.thickness || 1)
    this.shape(x, y, x + w, y + h, ink, options.smooth === true, (px, py) => {
      const inside = -roundedBox(px - cx, py - cy, w / 2, h / 2, radius)
      return filled ? inside : Math.min(inside, t - inside)
    })
  }

  circle(cx: number, cy: number, radius: number, ink: Paint, options: ShapeOptions = {}) {
    if (radius <= 0) return
    const ox = cx + 0.5
    const oy = cy + 0.5
    const filled = options.filled !== false
    const t = Math.max(1, options.thickness || 1)
    this.shape(ox - radius, oy - radius, ox + radius, oy + radius, ink, options.smooth === true, (px, py) => {
      const inside = radius - Math.hypot(px - ox, py - oy)
      return filled ? inside : Math.min(inside, t - inside)
    })
  }

  toImageData() {
    const bytes = new Uint8ClampedArray(this.px.length)
    for (let i = 0; i < this.px.length; i++) bytes[i] = Math.round(this.px[i] * 255)
    return new ImageData(bytes, this.width, this.height)
  }
}

export function checker(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  size: number,
) {
  ctx.fillStyle = "#2c2c2c"
  ctx.fillRect(x, y, w, h)
  ctx.fillStyle = "#3a3a3a"
  for (let cy = 0; cy < h; cy += size) {
    for (let cx = (cy / size) % 2 ? size : 0; cx < w; cx += size * 2) {
      ctx.fillRect(x + cx, y + cy, Math.min(size, w - cx), Math.min(size, h - cy))
    }
  }
}
