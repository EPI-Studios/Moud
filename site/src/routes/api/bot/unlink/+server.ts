import { json } from "@sveltejs/kit"
import { eq } from "drizzle-orm"
import { fromBot } from "$lib/server/bot"
import { db, schema } from "$lib/server/db"
import type { RequestHandler } from "./$types"

type Body = { discordId?: string }

export const POST: RequestHandler = async ({ request }) => {
  const asked = await fromBot<Body>(request)
  if (!asked.ok) return json({ ok: false, error: asked.error }, { status: asked.status })

  const discordId = (asked.value.discordId ?? "").trim()
  if (!/^\d{5,25}$/.test(discordId)) return json({ ok: false, error: "bad discord id" })

  const [cleared] = await db
    .update(schema.users)
    .set({ discordId: null, discordName: null })
    .where(eq(schema.users.discordId, discordId))
    .returning({ handle: schema.users.handle })

  if (!cleared) return json({ ok: false, error: "that discord account is not linked" })

  return json({ ok: true, handle: cleared.handle })
}
