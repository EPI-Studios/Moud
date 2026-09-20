import crypto from "node:crypto"

const WINDOW_MS = 5 * 60 * 1000

export function signedBySite(request: Request, body: string) {
  const secret = process.env.SITE_SECRET ?? ""
  if (!secret) return false

  const at = request.headers.get("x-moud-at") ?? ""
  const given = request.headers.get("x-moud-signature") ?? ""
  if (!/^\d{10,15}$/.test(at) || !/^[0-9a-f]{64}$/.test(given)) return false
  if (Math.abs(Date.now() - Number(at)) > WINDOW_MS) return false

  const wanted = crypto.createHmac("sha256", secret).update(`${at}.${body}`).digest()
  const shown = Buffer.from(given, "hex")
  return wanted.length === shown.length && crypto.timingSafeEqual(wanted, shown)
}
