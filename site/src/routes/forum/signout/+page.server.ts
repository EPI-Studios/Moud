import { redirect } from "@sveltejs/kit"
import { eq } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import type { Actions } from "./$types"

export const actions: Actions = {
  default: async ({ cookies }) => {
    const token = cookies.get("authjs.session-token")
    if (token) {
      await db.delete(schema.sessions).where(eq(schema.sessions.sessionToken, token))
      cookies.delete("authjs.session-token", { path: "/" })
    }
    redirect(303, "/forum")
  },
}
