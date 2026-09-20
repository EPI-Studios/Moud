import { json } from "@sveltejs/kit"
import { db, schema } from "$lib/server/db"
import { take } from "$lib/server/limit"
import { store } from "$lib/server/uploads"
import type { RequestHandler } from "./$types"

const PER_HOUR = 30

export const POST: RequestHandler = async ({ request, locals }) => {
  const session = await locals.auth()
  if (!session?.user) return json({ error: "sign in first" }, { status: 401 })

  const allowance = await take(`upload:${session.user.id}`, PER_HOUR, 60 * 60 * 1000)
  if (!allowance.ok) {
    return json({ error: "that is a lot of uploads in an hour" }, { status: 429 })
  }

  const form = await request.formData()
  const file = form.get("file")
  if (!(file instanceof File)) return json({ error: "no file" }, { status: 400 })

  try {
    const saved = await store(file)
    await db.insert(schema.uploads).values({
      userId: session.user.id,
      url: saved.url,
      kind: saved.kind,
      bytes: saved.bytes,
    })
    return json(saved)
  } catch (problem) {
    return json({ error: (problem as Error).message }, { status: 400 })
  }
}
