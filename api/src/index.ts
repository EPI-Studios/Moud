import { serve } from "@hono/node-server"
import { Hono } from "hono"
import { hasJoined } from "./mojang.ts"
import { signedBySite } from "./service.ts"
import { newChallenge, newClaim, readClaim, take, takeChallenge, takeClaim } from "./store.ts"

const app = new Hono()

function caller(request: Request, fallback = "unknown") {
  const forwarded = request.headers.get("x-forwarded-for")
  return forwarded ? forwarded.split(",")[0].trim() : fallback
}

function limited(key: string, limit: number, windowMs: number) {
  const window = take(key, limit, windowMs)
  return window.ok ? null : window.retryAfter
}

app.get("/v1/health", (context) => context.json({ ok: true }))

app.get("/v1/challenge", (context) => {
  const from = caller(context.req.raw)
  const wait = limited(`challenge:${from}`, 30, 60_000)
  if (wait) return context.json({ error: "slow down" }, 429, { "retry-after": String(wait) })

  return context.json({ serverId: newChallenge(from), expiresIn: 120 })
})

app.post("/v1/claim", async (context) => {
  const from = caller(context.req.raw)
  const wait = limited(`claim:${from}`, 20, 60 * 60_000)
  if (wait) return context.json({ error: "too many attempts" }, 429, { "retry-after": String(wait) })

  const body = (await context.req.json().catch(() => ({}))) as {
    serverId?: string
    username?: string
  }

  const serverId = (body.serverId ?? "").trim()
  const username = (body.username ?? "").trim()

  if (!/^[0-9a-f]{20}$/.test(serverId)) return context.json({ error: "bad challenge" }, 400)
  if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) return context.json({ error: "bad username" }, 400)

  if (!takeChallenge(serverId, from)) {
    return context.json({ error: "that challenge is spent or expired" }, 400)
  }

  let player
  try {
    player = await hasJoined(username, serverId)
  } catch {
    return context.json({ error: "the session server did not answer" }, 502)
  }

  if (!player) return context.json({ error: "Mojang did not confirm that login" }, 401)
  if (player.name.toLowerCase() !== username.toLowerCase()) {
    return context.json({ error: "that name is not the one Mojang returned" }, 401)
  }

  const claim = newClaim(player.uuid, player.name)
  return context.json({ ...claim, name: player.name })
})

async function fromSite(context: { req: { raw: Request; text(): Promise<string> } }) {
  const raw = await context.req.text()
  if (!signedBySite(context.req.raw, raw)) return null
  try {
    return JSON.parse(raw) as { code?: string }
  } catch {
    return null
  }
}

app.post("/v1/peek", async (context) => {
  const body = await fromSite(context)
  if (!body) return context.json({ error: "not signed by the site" }, 401)

  const code = (body.code ?? "").trim().toUpperCase()
  if (!/^[A-Z0-9]{6}$/.test(code)) return context.json({ error: "bad code" }, 400)

  const claim = readClaim(code)
  if (!claim) return context.json({ error: "no such code" }, 404)

  return context.json({ ok: true, ...claim })
})

app.post("/v1/redeem", async (context) => {
  const body = await fromSite(context)
  if (!body) return context.json({ error: "not signed by the site" }, 401)

  const code = (body.code ?? "").trim().toUpperCase()
  if (!/^[A-Z0-9]{6}$/.test(code)) return context.json({ error: "bad code" }, 400)

  const claim = takeClaim(code)
  if (!claim) return context.json({ error: "no such code" }, 404)

  return context.json({ ok: true, ...claim })
})

const port = Number(process.env.SERVER_PORT ?? process.env.PORT ?? 8787)
const hostname = process.env.HOST ?? (process.env.SERVER_PORT ? "0.0.0.0" : "127.0.0.1")

serve({ fetch: app.fetch, port, hostname }, (info) => {
  console.log(`moud api on ${info.address}:${info.port}`)
})
