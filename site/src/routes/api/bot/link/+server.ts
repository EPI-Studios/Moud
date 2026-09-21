import { json } from "@sveltejs/kit"
import { eq } from "drizzle-orm"
import { redeemCode } from "$lib/server/api"
import { userForMinecraft } from "$lib/server/auth"
import { grant } from "$lib/server/badges"
import { fromBot, profileOf } from "$lib/server/bot"
import { db, schema } from "$lib/server/db"
import type { RequestHandler } from "./$types"

type Body = { code?: string; discordId?: string; discordName?: string }

export const POST: RequestHandler = async ({ request }) => {
  const asked = await fromBot<Body>(request)
  if (!asked.ok) return json({ ok: false, error: asked.error }, { status: asked.status })

  const code = (asked.value.code ?? "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "")
  const discordId = (asked.value.discordId ?? "").trim()
  const discordName = (asked.value.discordName ?? "").trim().slice(0, 64)

  if (!/^[A-Z0-9]{6}$/.test(code)) return json({ ok: false, error: "a code is six letters and digits" })
  if (!/^\d{5,25}$/.test(discordId)) return json({ ok: false, error: "bad discord id" })

  const claim = await redeemCode(code)
  if (!claim.ok) return json({ ok: false, error: claim.error })

  const userId = await userForMinecraft(claim.uuid, claim.name)

  const holder = await db.query.users.findFirst({ where: eq(schema.users.discordId, discordId) })
  if (holder && holder.id !== userId) {
    await db
      .update(schema.users)
      .set({ discordId: null, discordName: null })
      .where(eq(schema.users.id, holder.id))
  }

  await db
    .update(schema.users)
    .set({ discordId, discordName })
    .where(eq(schema.users.id, userId))

  await grant(userId, "linked")

  return json({ ok: true, profile: await profileOf(userId) })
}
