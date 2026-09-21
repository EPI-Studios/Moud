import { json } from "@sveltejs/kit"
import { eq } from "drizzle-orm"
import { fromBot, profileOf } from "$lib/server/bot"
import { db, schema } from "$lib/server/db"
import type { RequestHandler } from "./$types"

type Body = { discordId?: string; handle?: string; name?: string }

export const POST: RequestHandler = async ({ request }) => {
  const asked = await fromBot<Body>(request)
  if (!asked.ok) return json({ ok: false, error: asked.error }, { status: asked.status })

  const { discordId, handle, name } = asked.value
  const person = discordId
    ? await db.query.users.findFirst({ where: eq(schema.users.discordId, discordId) })
    : handle
      ? await db.query.users.findFirst({ where: eq(schema.users.handle, handle.toLowerCase()) })
      : name
        ? await db.query.users.findFirst({ where: eq(schema.users.minecraftName, name) })
        : null

  if (!person) return json({ ok: false, error: "no such member" })

  return json({ ok: true, profile: await profileOf(person.id) })
}
