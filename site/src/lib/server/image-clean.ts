const PNG_MAGIC = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

const PNG_KEEP = new Set(["IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "acTL", "fcTL", "fdAT"])

function chunkName(bytes: Uint8Array, at: number) {
  return String.fromCharCode(bytes[at], bytes[at + 1], bytes[at + 2], bytes[at + 3])
}

function readUint32(bytes: Uint8Array, at: number) {
  return ((bytes[at] << 24) | (bytes[at + 1] << 16) | (bytes[at + 2] << 8) | bytes[at + 3]) >>> 0
}

function cleanPng(bytes: Uint8Array) {
  const pieces: Uint8Array[] = [bytes.subarray(0, 8)]
  let at = 8

  while (at + 12 <= bytes.length) {
    const length = readUint32(bytes, at)
    const name = chunkName(bytes, at + 4)
    const end = at + 12 + length
    if (end > bytes.length) throw new Error("truncated png")

    if (PNG_KEEP.has(name)) pieces.push(bytes.subarray(at, end))
    at = end
    if (name === "IEND") break
  }

  if (at === 8) throw new Error("png has no chunks")
  return concat(pieces)
}

function cleanJpeg(bytes: Uint8Array) {
  const pieces: Uint8Array[] = [bytes.subarray(0, 2)]
  let at = 2

  while (at + 4 <= bytes.length) {
    if (bytes[at] !== 0xff) throw new Error("jpeg out of step")
    const marker = bytes[at + 1]

    if (marker === 0xd9) {
      pieces.push(bytes.subarray(at, at + 2))
      return concat(pieces)
    }

    const length = (bytes[at + 2] << 8) | bytes[at + 3]
    const end = at + 2 + length
    if (length < 2 || end > bytes.length) throw new Error("truncated jpeg")

    const metadata = marker >= 0xe0 && marker <= 0xef
    const comment = marker === 0xfe
    if (!metadata && !comment) pieces.push(bytes.subarray(at, end))

    if (marker === 0xda) {
      const rest = scanToEnd(bytes, end)
      pieces.push(bytes.subarray(end, rest))
      pieces.push(new Uint8Array([0xff, 0xd9]))
      return concat(pieces)
    }

    at = end
  }

  throw new Error("jpeg has no end")
}

function scanToEnd(bytes: Uint8Array, from: number) {
  for (let at = from; at + 1 < bytes.length; at++) {
    if (bytes[at] === 0xff && bytes[at + 1] === 0xd9) return at
  }
  return bytes.length
}

function cleanGif(bytes: Uint8Array) {
  for (let at = bytes.length - 1; at > 0; at--) {
    if (bytes[at] === 0x3b) return bytes.subarray(0, at + 1)
  }
  throw new Error("gif has no terminator")
}

function cleanWebp(bytes: Uint8Array) {
  const size = readUint32LE(bytes, 4)
  const end = 8 + size
  if (end > bytes.length) throw new Error("truncated webp")

  const pieces: Uint8Array[] = []
  let at = 12
  let kept = 0

  while (at + 8 <= end) {
    const name = chunkName(bytes, at)
    const length = readUint32LE(bytes, at + 4)
    const padded = length + (length % 2)
    const next = at + 8 + padded
    if (next > end) throw new Error("truncated webp chunk")

    if (name !== "EXIF" && name !== "XMP ") {
      pieces.push(bytes.subarray(at, next))
      kept += next - at
    }
    at = next
  }

  if (kept === 0) throw new Error("webp has no content")

  const head = new Uint8Array(12)
  head.set(bytes.subarray(0, 12))
  writeUint32LE(head, 4, kept + 4)
  return concat([head, ...pieces])
}

function readUint32LE(bytes: Uint8Array, at: number) {
  return (bytes[at] | (bytes[at + 1] << 8) | (bytes[at + 2] << 16) | (bytes[at + 3] << 24)) >>> 0
}

function writeUint32LE(bytes: Uint8Array, at: number, value: number) {
  bytes[at] = value & 0xff
  bytes[at + 1] = (value >>> 8) & 0xff
  bytes[at + 2] = (value >>> 16) & 0xff
  bytes[at + 3] = (value >>> 24) & 0xff
}

function concat(pieces: Uint8Array[]) {
  const total = pieces.reduce((sum, piece) => sum + piece.length, 0)
  const out = new Uint8Array(total)
  let at = 0
  for (const piece of pieces) {
    out.set(piece, at)
    at += piece.length
  }
  return out
}

export function cleanImage(type: string, bytes: Uint8Array) {
  if (type === "image/png") return cleanPng(bytes)
  if (type === "image/jpeg") return cleanJpeg(bytes)
  if (type === "image/gif") return cleanGif(bytes)
  if (type === "image/webp") return cleanWebp(bytes)
  return bytes
}

export function pngMagic() {
  return PNG_MAGIC
}
