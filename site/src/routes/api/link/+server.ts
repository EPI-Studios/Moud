import { json } from "@sveltejs/kit"
import { and, eq, gt, sql } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { grant } from "$lib/server/badges"
import { clientAddress, take } from "$lib/server/limit"
import { signedByService } from "$lib/server/service"
import type { RequestHandler } from "./$types"

type Body = { code?: string; uuid?: string; name?: string }

const TRIES_PER_CODE = 5
const TRIES_PER_ADDRESS = 20
const HOUR = 60 * 60 * 1000

export const POST: RequestHandler = async ({ request, getClientAddress }) => {
  const from = clientAddress(request, getClientAddress())
  const allowance = await take(`link:${from}`, TRIES_PER_ADDRESS, HOUR)
  if (!allowance.ok) {
    return json(
      { ok: false, error: "too many attempts, try again later" },
      { status: 429, headers: { "retry-after": String(Math.ceil((allowance.resetAt.getTime() - Date.now()) / 1000)) } },
    )
  }

  const raw = await request.text()
  if (!signedByService(request, raw)) {
    return json({ ok: false, error: "not signed by the api" }, { status: 401 })
  }

  let body: Body
  try {
    body = JSON.parse(raw) as Body
  } catch {
    return json({ ok: false, error: "bad body" }, { status: 400 })
  }

  const code = (body.code ?? "").trim().toUpperCase()
  const uuid = (body.uuid ?? "").replace(/-/g, "").toLowerCase()
  const name = (body.name ?? "").trim()

  if (!/^[A-Z0-9]{6}$/.test(code)) return json({ ok: false, error: "bad code" }, { status: 400 })
  if (!/^[0-9a-f]{32}$/.test(uuid)) return json({ ok: false, error: "bad uuid" }, { status: 400 })
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) return json({ ok: false, error: "bad name" }, { status: 400 })

  const open = await db.query.linkCodes.findFirst({
    where: and(eq(schema.linkCodes.code, code), gt(schema.linkCodes.expires, new Date())),
  })

  if (!open) {
    return json({ ok: false, error: "no such code" }, { status: 404 })
  }

  if (open.attempts >= TRIES_PER_CODE) {
    await db.delete(schema.linkCodes).where(eq(schema.linkCodes.code, code))
    return json({ ok: false, error: "that code was tried too often and is gone" }, { status: 429 })
  }

  await db
    .update(schema.linkCodes)
    .set({ attempts: sql`${schema.linkCodes.attempts} + 1` })
    .where(eq(schema.linkCodes.code, code))

  const taken = await db.query.users.findFirst({ where: eq(schema.users.minecraftId, uuid) })
  if (taken && taken.id !== open.userId) {
    await db
      .update(schema.users)
      .set({ minecraftId: null, minecraftName: null })
      .where(eq(schema.users.id, taken.id))
  }

  await db
    .update(schema.users)
    .set({ minecraftId: uuid, minecraftName: name })
    .where(eq(schema.users.id, open.userId))

  await grant(open.userId, "linked")
  await db.delete(schema.linkCodes).where(eq(schema.linkCodes.code, code))

  return json({ ok: true, name })
}
