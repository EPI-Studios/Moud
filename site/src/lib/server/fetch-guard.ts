import dns from "node:dns/promises"

const BLOCKED_HOSTS = new Set(["localhost", "metadata.google.internal", "metadata"])

function isPrivate(address: string) {
  if (address.includes(":")) {
    const low = address.toLowerCase()
    return (
      low === "::1" ||
      low === "::" ||
      low.startsWith("fc") ||
      low.startsWith("fd") ||
      low.startsWith("fe80") ||
      low.startsWith("::ffff:")
    )
  }

  const parts = address.split(".").map(Number)
  if (parts.length !== 4 || parts.some((n) => Number.isNaN(n))) return true

  const [a, b] = parts
  if (a === 10 || a === 127 || a === 0) return true
  if (a === 169 && b === 254) return true
  if (a === 172 && b >= 16 && b <= 31) return true
  if (a === 192 && b === 168) return true
  if (a === 100 && b >= 64 && b <= 127) return true
  if (a >= 224) return true
  return false
}

export async function assertPublicUrl(raw: string) {
  const url = new URL(raw)

  if (url.protocol !== "https:") throw new Error("only https is fetched")
  if (url.username || url.password) throw new Error("credentials in url")
  if (BLOCKED_HOSTS.has(url.hostname.toLowerCase())) throw new Error("blocked host")
  if (url.hostname.endsWith(".internal") || url.hostname.endsWith(".local")) {
    throw new Error("blocked host")
  }

  const addresses = await dns.lookup(url.hostname, { all: true })
  if (addresses.length === 0) throw new Error("host does not resolve")
  if (addresses.some((found) => isPrivate(found.address))) throw new Error("private address")

  return url
}

export type SafeFetch = { body: string; type: string }

export async function fetchPublic(raw: string, limitBytes = 200_000, ms = 4000): Promise<SafeFetch> {
  let url = await assertPublicUrl(raw)

  for (let hop = 0; hop < 3; hop++) {
    const answer = await fetch(url, {
      redirect: "manual",
      headers: { "user-agent": "MoudForum/1.0 (+https://moud.dev)", accept: "text/html" },
      signal: AbortSignal.timeout(ms),
    })

    if (answer.status >= 300 && answer.status < 400) {
      const next = answer.headers.get("location")
      if (!next) throw new Error("redirect without a target")
      url = await assertPublicUrl(new URL(next, url).toString())
      continue
    }

    if (!answer.ok) throw new Error(`answered ${answer.status}`)

    const type = answer.headers.get("content-type") ?? ""
    if (!type.includes("html")) throw new Error("not a page")

    const reader = answer.body?.getReader()
    if (!reader) throw new Error("no body")

    const chunks: Uint8Array[] = []
    let read = 0
    while (read < limitBytes) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      read += value.length
    }
    await reader.cancel().catch(() => {})

    return { body: new TextDecoder().decode(Buffer.concat(chunks).subarray(0, limitBytes)), type }
  }

  throw new Error("too many redirects")
}
