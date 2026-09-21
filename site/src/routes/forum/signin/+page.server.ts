import { fail, redirect } from "@sveltejs/kit"
import { peekCode, redeemCode } from "$lib/server/api"
import { startSession, userForMinecraft } from "$lib/server/auth"
import { grant } from "$lib/server/badges"
import { clientAddress, take } from "$lib/server/limit"
import type { Actions, PageServerLoad } from "./$types"

const PER_HOUR = 20

export const load: PageServerLoad = async ({ locals }) => {
  const session = await locals.auth()
  if (session?.user) redirect(303, "/forum")
  return { dev: !import.meta.env.PROD }
}

function readCode(form: FormData) {
  return String(form.get("code") ?? "")
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
}

export const actions: Actions = {
  check: async ({ request, getClientAddress }) => {
    const allowance = await take(
      `signin:${clientAddress(request, getClientAddress())}`,
      PER_HOUR,
      60 * 60 * 1000,
    )
    if (!allowance.ok) return fail(429, { message: "Too many tries. Wait an hour." })

    const code = readCode(await request.formData())
    if (!/^[A-Z0-9]{6}$/.test(code)) return fail(400, { message: "A code is six letters and digits." })

    const claim = await peekCode(code)
    if (!claim.ok) return fail(400, { message: claim.error })

    return { found: { code, name: claim.name } }
  },

  confirm: async ({ request, cookies, url }) => {
    const code = readCode(await request.formData())
    if (!/^[A-Z0-9]{6}$/.test(code)) return fail(400, { message: "A code is six letters and digits." })

    const claim = await redeemCode(code)
    if (!claim.ok) return fail(400, { message: claim.error })

    const userId = await userForMinecraft(claim.uuid, claim.name)
    await grant(userId, "linked")
    await startSession(userId, cookies, url)

    redirect(303, "/forum")
  },
}
