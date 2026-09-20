import { SvelteKitAuth } from "@auth/sveltekit"
import Discord from "@auth/sveltekit/providers/discord"
import { DrizzleAdapter } from "@auth/drizzle-adapter"
import { eq } from "drizzle-orm"
import { env } from "$env/dynamic/private"
import { db, schema } from "./db"

function handleFrom(name: string | null | undefined, id: string) {
  const base = (name ?? "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 24)
  return base || "user-" + id.slice(0, 6)
}

export const { handle, signIn, signOut } = SvelteKitAuth({
  trustHost: true,
  adapter: DrizzleAdapter(db, {
    usersTable: schema.users,
    accountsTable: schema.accounts,
    sessionsTable: schema.sessions,
    verificationTokensTable: schema.verificationTokens,
  }),
  providers: env.AUTH_DISCORD_ID
    ? [Discord({ clientId: env.AUTH_DISCORD_ID, clientSecret: env.AUTH_DISCORD_SECRET })]
    : [],
  pages: { signIn: "/forum/signin" },
  events: {
    async createUser({ user }) {
      if (!user.id) return
      const wanted = handleFrom(user.name, user.id)
      const taken = await db
        .select({ handle: schema.users.handle })
        .from(schema.users)
        .where(eq(schema.users.handle, wanted))
      const chosen = taken.length ? wanted + "-" + user.id.slice(0, 4) : wanted
      await db.update(schema.users).set({ handle: chosen }).where(eq(schema.users.id, user.id))
    },
  },
  callbacks: {
    async session({ session, user }) {
      const row = await db.query.users.findFirst({ where: eq(schema.users.id, user.id) })
      session.user.id = user.id
      session.user.handle = row?.handle ?? ""
      session.user.role = row?.role ?? "member"
      session.user.minecraftId = row?.minecraftId ?? null
      session.user.minecraftName = row?.minecraftName ?? null
      return session
    },
  },
})
