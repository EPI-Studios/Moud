import crypto from "node:crypto"

const CHALLENGE_MS = 2 * 60 * 1000
const CLAIM_MS = 10 * 60 * 1000
const LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

type Challenge = { madeAt: number; from: string }
type Claim = { uuid: string; name: string; madeAt: number }

const challenges = new Map<string, Challenge>()
const claims = new Map<string, Claim>()

export function newChallenge(from: string) {
  const serverId = crypto.randomBytes(10).toString("hex")
  challenges.set(serverId, { madeAt: Date.now(), from })
  return serverId
}

export function takeChallenge(serverId: string, from: string) {
  const found = challenges.get(serverId)
  if (!found) return false
  challenges.delete(serverId)
  if (Date.now() - found.madeAt > CHALLENGE_MS) return false
  return found.from === from
}

function freshCode() {
  let code = ""
  const bytes = crypto.randomBytes(6)
  for (const byte of bytes) code += LETTERS[byte % LETTERS.length]
  return code
}

export function newClaim(uuid: string, name: string) {
  for (const [code, claim] of claims) {
    if (claim.uuid === uuid) claims.delete(code)
  }

  let code = freshCode()
  while (claims.has(code)) code = freshCode()

  claims.set(code, { uuid, name, madeAt: Date.now() })
  return { code, expiresIn: CLAIM_MS / 1000 }
}

export function readClaim(code: string) {
  const found = claims.get(code)
  if (!found) return null
  if (Date.now() - found.madeAt > CLAIM_MS) {
    claims.delete(code)
    return null
  }
  return { uuid: found.uuid, name: found.name }
}

export function takeClaim(code: string) {
  const found = readClaim(code)
  if (found) claims.delete(code)
  return found
}

export type Window = { ok: boolean; retryAfter: number }

const hits = new Map<string, { count: number; resetAt: number }>()

export function take(key: string, limit: number, windowMs: number): Window {
  const now = Date.now()
  const found = hits.get(key)

  if (!found || found.resetAt < now) {
    hits.set(key, { count: 1, resetAt: now + windowMs })
    return { ok: true, retryAfter: 0 }
  }

  found.count += 1
  return { ok: found.count <= limit, retryAfter: Math.ceil((found.resetAt - now) / 1000) }
}

export function sweep() {
  const now = Date.now()
  for (const [id, found] of challenges) {
    if (now - found.madeAt > CHALLENGE_MS) challenges.delete(id)
  }
  for (const [code, found] of claims) {
    if (now - found.madeAt > CLAIM_MS) claims.delete(code)
  }
  for (const [key, found] of hits) {
    if (found.resetAt < now) hits.delete(key)
  }
}

setInterval(sweep, 60_000).unref()
