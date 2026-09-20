import { fail, redirect } from "@sveltejs/kit"
import { and, eq, ne } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { grant } from "$lib/server/badges"
import { peekCode, redeemCode } from "$lib/server/api"
import { clientAddress, take } from "$lib/server/limit"
import type { Actions, PageServerLoad } from "./$types"

const PER_HOUR = 20

export const load: PageServerLoad = async ({ locals }) => {
  const session = await locals.auth()
  if (!session?.user) redirect(303, "/forum/signin")

  return { minecraftName: session.user.minecraftName }
}

function readCode(form: FormData) {
  return String(form.get("code") ?? "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "")
}

export const actions: Actions = {
  check: async ({ request, locals, getClientAddress }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const allowance = await take(`link:${clientAddress(request, getClientAddress())}`, PER_HOUR, 60 * 60 * 1000)
    if (!allowance.ok) return fail(429, { message: "Too many tries. Wait an hour." })

    const code = readCode(await request.formData())
    if (!/^[A-Z0-9]{6}$/.test(code)) return fail(400, { message: "A code is six letters and digits." })

    const claim = await peekCode(code)
    if (!claim.ok) return fail(400, { message: claim.error })

    return { found: { code, name: claim.name } }
  },

  confirm: async ({ request, locals }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const code = readCode(await request.formData())
    if (!/^[A-Z0-9]{6}$/.test(code)) return fail(400, { message: "A code is six letters and digits." })

    const claim = await redeemCode(code)
    if (!claim.ok) return fail(400, { message: claim.error })

    await db
      .update(schema.users)
      .set({ minecraftId: null, minecraftName: null })
      .where(and(eq(schema.users.minecraftId, claim.uuid), ne(schema.users.id, session.user.id)))

    await db
      .update(schema.users)
      .set({ minecraftId: claim.uuid, minecraftName: claim.name })
      .where(eq(schema.users.id, session.user.id))

    await grant(session.user.id, "linked")

    return { linked: claim.name }
  },

  unlink: async ({ locals }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    await db
      .update(schema.users)
      .set({ minecraftId: null, minecraftName: null })
      .where(eq(schema.users.id, session.user.id))

    return { unlinked: true }
  },
}
