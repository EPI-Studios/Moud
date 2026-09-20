import { PARTICLES } from "./assets"

export type Rgb = [number, number, number]

type Entry = { image: HTMLImageElement; ready: boolean; tints: Record<string, HTMLCanvasElement> }

const entries: Record<string, Entry> = {}

function load(name: string) {
  let entry = entries[name]
  if (entry) return entry
  const image = new Image()
  entry = entries[name] = { image, ready: false, tints: {} }
  image.onload = () => {
    entry.ready = true
  }
  image.src = PARTICLES[name]
  if (image.complete && image.naturalWidth) entry.ready = true
  return entry
}

export function rgba(color: Rgb, alpha: number) {
  return `rgba(${color.map((v) => Math.round(v * 255)).join(",")},${alpha})`
}

export function tinted(name: string, color: Rgb, flip: boolean) {
  const entry = load(name)
  if (!entry.ready) return null

  const step = (v: number) => Math.round(Math.min(1, v) * 32) / 32
  const quantized: Rgb = [step(color[0]), step(color[1]), step(color[2])]
  const key = (flip ? "t" : "") + quantized.join(",")
  const cached = entry.tints[key]
  if (cached) return cached

  const { image } = entry
  const canvas = document.createElement("canvas")
  canvas.width = flip ? image.naturalHeight : image.naturalWidth
  canvas.height = flip ? image.naturalWidth : image.naturalHeight
  const ctx = canvas.getContext("2d")
  if (!ctx) return null
  ctx.imageSmoothingEnabled = false

  const paint = () => {
    if (flip) {
      ctx.setTransform(0, 1, 1, 0, 0, 0)
      ctx.drawImage(image, 0, 0)
      ctx.setTransform(1, 0, 0, 1, 0, 0)
    } else {
      ctx.drawImage(image, 0, 0)
    }
  }

  paint()
  ctx.globalCompositeOperation = "multiply"
  ctx.fillStyle = rgba(quantized, 1)
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.globalCompositeOperation = "destination-in"
  paint()
  ctx.globalCompositeOperation = "source-over"

  entry.tints[key] = canvas
  return canvas
}
