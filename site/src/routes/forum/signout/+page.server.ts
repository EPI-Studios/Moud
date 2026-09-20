import { redirect } from "@sveltejs/kit"
import { endSession } from "$lib/server/auth"
import type { Actions } from "./$types"

export const actions: Actions = {
  default: async ({ cookies }) => {
    await endSession(cookies)
    redirect(303, "/forum")
  },
}
