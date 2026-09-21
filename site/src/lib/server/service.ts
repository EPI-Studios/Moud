import crypto from "node:crypto"
import { env } from "$env/dynamic/private"

const WINDOW_MS = 5 * 60 * 1000

export function signedByService(request: Request, body: string) {
  return signedWith(env.SITE_SECRET, request, body)
}

export function signedByBot(request: Request, body: string) {
  return signedWith(env.BOT_SECRET, request, body)
}

function signedWith(secret: string | undefined, request: Request, body: string) {
  if (!secret) return false

  const at = request.headers.get("x-moud-at") ?? ""
  const given = request.headers.get("x-moud-signature") ?? ""
  if (!/^\d{10,15}$/.test(at) || !/^[0-9a-f]{64}$/.test(given)) return false

  const age = Math.abs(Date.now() - Number(at))
  if (age > WINDOW_MS) return false

  const wanted = crypto.createHmac("sha256", secret).update(`${at}.${body}`).digest()
  const shown = Buffer.from(given, "hex")
  return wanted.length === shown.length && crypto.timingSafeEqual(wanted, shown)
}
