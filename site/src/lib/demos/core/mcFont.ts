export type TextBox = { x: number; y: number; w: number; h: number }

type Glyph = { x: number; y: number; advance: number }

type Font = { ready: boolean; image: HTMLImageElement; glyphs: Record<string, Glyph> }

let font: Font | null = null
const tinted: Record<string, HTMLCanvasElement> = {}

function atlasOf(image: CanvasImageSource, color?: string) {
  const canvas = document.createElement("canvas")
  canvas.width = canvas.height = 128
  const ctx = canvas.getContext("2d")
  if (!ctx) throw new Error("2d canvas context unavailable")
  ctx.drawImage(image, 0, 0)
  if (color) {
    ctx.globalCompositeOperation = "source-in"
    ctx.fillStyle = color
    ctx.fillRect(0, 0, 128, 128)
  }
  return { canvas, ctx }
}

function ready() {
  if (font) return font.ready
  const current: Font = { ready: false, image: new Image(), glyphs: {} }
  font = current
  current.image.onload = () => {
    const { ctx } = atlasOf(current.image)
    const px = ctx.getImageData(0, 0, 128, 128).data
    MC_FONT.rows.forEach((row, gy) => {
      Array.from(row).forEach((ch, gx) => {
        let width = 0
        for (let col = 7; col >= 0 && !width; col--) {
          for (let r = 0; r < 8; r++) {
            if (px[((gy * 8 + r) * 128 + gx * 8 + col) * 4 + 3] > 0) {
              width = col + 1
              break
            }
          }
        }
        current.glyphs[ch] = { x: gx * 8, y: gy * 8, advance: ch === " " ? 4 : width + 1 }
      })
    })
    current.ready = true
  }
  current.image.src = MC_FONT.image
  return false
}

export function mcColor(css: string) {
  const probe = document.createElement("canvas").getContext("2d")
  if (!probe) return css
  probe.fillStyle = css
  return probe.fillStyle
}

function atlas(color: string) {
  if (tinted[color]) return tinted[color]
  if (!font) throw new Error("font atlas requested before the font loaded")
  tinted[color] = atlasOf(font.image, color).canvas
  return tinted[color]
}

function shadowOf(color: string) {
  const hex = mcColor(color)
  if (hex.charAt(0) !== "#") return "rgba(0,0,0,0.6)"
  const n = parseInt(hex.slice(1), 16)
  return `rgb(${Math.floor(((n >> 16) & 255) / 4)},${Math.floor(((n >> 8) & 255) / 4)},${Math.floor((n & 255) / 4)})`
}

export function mcTextWidth(text: string) {
  if (!ready() || !font) return text.length * 6
  let w = 0
  for (let i = 0; i < text.length; i++) {
    const glyph = font.glyphs[text.charAt(i)] ?? font.glyphs["?"]
    w += glyph ? glyph.advance : 6
  }
  return w
}

export function mcText(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  scale = 2,
  color = "#ffffff",
  shadow = true,
): TextBox | null {
  if (!ready() || !font) return null
  const glyphs = font.glyphs
  ctx.save()
  ctx.imageSmoothingEnabled = false
  const pass = (sheet: HTMLCanvasElement, ox: number, oy: number) => {
    let at = x + ox
    for (let i = 0; i < text.length; i++) {
      const ch = text.charAt(i)
      const glyph = glyphs[ch] ?? glyphs["?"]
      if (!glyph) continue
      if (ch !== " ") ctx.drawImage(sheet, glyph.x, glyph.y, 8, 8, at, y + oy, 8 * scale, 8 * scale)
      at += glyph.advance * scale
    }
  }
  if (shadow) pass(atlas(shadowOf(color)), scale, scale)
  pass(atlas(mcColor(color)), 0, 0)
  ctx.restore()
  return { x, y, w: mcTextWidth(text) * scale, h: 8 * scale }
}

export const MC_FONT = {
  image: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACAAQMAAAD58POIAAAABlBMVEX///////9VfPVsAAAAAXRSTlMAQObYZgAAAtJJREFUeNrtk6GPG1cQxj90eqBSnoIWVPKoCgg02qtK7oECwyXlBgcMH+vCAQWjolWRoVUVWEUGBQUBgYaBkUL2L4gWXQZEt5l5jq3bVZILDMjn2efVb3+aefukxffMwruGhJh4B0sooO0aTikRgGgAypHAtLuAJtPGABQKKkZIBwduJFiaYdUhpTNg2JQNiP2WeboDWDLlXOlSc/YRnCFrkeecRAQgJMGWQlgNHESYk1LAnpZLCUPMLcCIEUdKQYOQBJ9JBCER80kiQJqArJpDtllr5gim2TY8eciDait9EBbJaMXCzJJCx6+Pgj90YOn7rabQ834v+E/MNKEYshX8cwGlh+CFWAqQE2DxKZylz6JlCmax562KR3vuCfAOZEvTBE7UmGE9HBBFAwKwSCgGERPhDJq9UOJYQCdi4LUQfwQbyeRdSPvQA/MoGIyAJZgDs6PkoKw4AeRD3thuDxw6fjVkIBxlK0mOHHa8FwFaYVF/9/DyBMTCyfGOpRhDbttyXJ15+XIKQMYlCSXEfDsBidMWOGwObS8eNQDguD1y8vOQyEzwqZwN7EnoZAwth2QH1LTpBDjwQC21EpSYC7js5NvNEogTsAKeT0ACCJ7Jwc6NLwMFdgDmUsl6jWlih5Jn3Xngx5vVCqfkdWmJ7hlwSXhoDIOD0uPx/PTbm6qqaqCurSpA/v19CoqhNfSuvr+r1Y23xaiu6qurs6F6d2/G3f296rlHbVUDqNyYAO8xBZUVnKD8PgsW9fXNu/F6fDdejOUSsAIW4+h1fWOPi4HiVKWBL4+DUpVdhkcMT8ZPAKvFwv4mRvXAGMfxZrG4GUe17QxPfEjtl7/EV4Jpfvl5Bn5NM/Anz8BfOgN/82M9/i9T5qEUf4jAGvjRF7wnoqeRbjsg9p0C0JieRmwddKQnI0bFujKQ9NQjRtyiQtcNbqC4WwAN4Iaqxg6eJc6JDz6OD74blGMNvZoZAAAAAElFTkSuQmCC",
  rows: ["\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000","\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"," !\"#$%&'()*+,-./","0123456789:;<=>?","@ABCDEFGHIJKLMNO","PQRSTUVWXYZ[\\]^_","`abcdefghijklmno","pqrstuvwxyz{|}~\u0000","\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000","\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000£\u0000\u0000ƒ","\u0000\u0000\u0000\u0000\u0000\u0000ªº\u0000\u0000¬\u0000\u0000\u0000«»","░▒▓│┤╡╢╖╕╣║╗╝╜╛┐","└┴┬├─┼╞╟╚╔╩╦╠═╬╧","╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀","\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000∅∈\u0000","≡±≥≤⌠⌡÷≈°∙\u0000√ⁿ²■\u0000"],
  ascent: 7,
}
