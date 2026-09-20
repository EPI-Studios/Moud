import fs from "node:fs/promises"
import path from "node:path"
import { env } from "$env/dynamic/private"
import { cleanImage } from "./image-clean"

export const MAX_BYTES = 8 * 1024 * 1024

type Kind = { type: string; extension: string; sniff: (bytes: Uint8Array) => boolean }

function starts(bytes: Uint8Array, signature: number[], at = 0) {
  return signature.every((byte, index) => bytes[at + index] === byte)
}

function reads(bytes: Uint8Array, text: string, at: number) {
  return [...text].every((letter, index) => bytes[at + index] === letter.charCodeAt(0))
}

const KINDS: Kind[] = [
  { type: "image/png", extension: "png", sniff: (b) => starts(b, [0x89, 0x50, 0x4e, 0x47]) },
  { type: "image/jpeg", extension: "jpg", sniff: (b) => starts(b, [0xff, 0xd8, 0xff]) },
  { type: "image/gif", extension: "gif", sniff: (b) => reads(b, "GIF8", 0) },
  {
    type: "image/webp",
    extension: "webp",
    sniff: (b) => reads(b, "RIFF", 0) && reads(b, "WEBP", 8),
  },
  { type: "video/mp4", extension: "mp4", sniff: (b) => reads(b, "ftyp", 4) },
  { type: "video/webm", extension: "webm", sniff: (b) => starts(b, [0x1a, 0x45, 0xdf, 0xa3]) },
]

export async function store(file: File) {
  if (file.size > MAX_BYTES) throw new Error("that file is bigger than 8 MB")
  if (file.size === 0) throw new Error("that file is empty")

  const raw = new Uint8Array(await file.arrayBuffer())
  const kind = KINDS.find((candidate) => candidate.sniff(raw))
  if (!kind) throw new Error("only png, jpeg, gif, webp, mp4 and webm are allowed")

  let bytes: Uint8Array
  try {
    bytes = cleanImage(kind.type, raw)
  } catch {
    throw new Error("that file is not a readable image")
  }

  const name = `${crypto.randomUUID()}.${kind.extension}`

  if (env.BLOB_READ_WRITE_TOKEN) {
    const { put } = await import("@vercel/blob")
    const blob = await put(`uploads/${name}`, Buffer.from(bytes), {
      access: "public",
      token: env.BLOB_READ_WRITE_TOKEN,
      contentType: kind.type,
      addRandomSuffix: false,
    })
    return { url: blob.url, bytes: bytes.length, kind: kind.type }
  }

  if (process.env.NODE_ENV === "production") {
    throw new Error("uploads are not configured on this server")
  }

  const folder = path.resolve("static/uploads")
  await fs.mkdir(folder, { recursive: true })
  await fs.writeFile(path.join(folder, name), bytes)
  return { url: `/uploads/${name}`, bytes: bytes.length, kind: kind.type }
}
