import { json } from "@sveltejs/kit"
import { everyLinkedMember, fromBot } from "$lib/server/bot"
import type { RequestHandler } from "./$types"

export const POST: RequestHandler = async ({ request }) => {
  const asked = await fromBot<unknown>(request)
  if (!asked.ok) return json({ ok: false, error: asked.error }, { status: asked.status })

  return json({ ok: true, members: await everyLinkedMember() })
}
