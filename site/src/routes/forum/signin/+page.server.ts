import { redirect } from "@sveltejs/kit"
import { env } from "$env/dynamic/private"
import type { PageServerLoad } from "./$types"

export const load: PageServerLoad = async ({ locals }) => {
  const session = await locals.auth()
  if (session?.user) redirect(303, "/forum")

  return { discordReady: Boolean(env.AUTH_DISCORD_ID && env.AUTH_DISCORD_SECRET), dev: !import.meta.env.PROD }
}
