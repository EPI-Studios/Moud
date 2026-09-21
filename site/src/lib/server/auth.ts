import { SvelteKitAuth } from "@auth/sveltekit"
import { DrizzleAdapter } from "@auth/drizzle-adapter"
import { eq } from "drizzle-orm"
import type { Cookies } from "@sveltejs/kit"
import { db, schema } from "./db"

const PLAIN = "authjs.session-token"
const SECURE = "__Secure-authjs.session-token"
const DAYS = 30

export const { handle, signOut } = SvelteKitAuth({
  trustHost: true,
  adapter: DrizzleAdapter(db, {
    usersTable: schema.users,
    accountsTable: schema.accounts,
    sessionsTable: schema.sessions,
    verificationTokensTable: schema.verificationTokens,
  }),
  providers: [],
  pages: { signIn: "/forum/signin" },
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

function handleFrom(name: string) {
  const base = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 24)
  return base || "player"
}

async function freeHandle(wanted: string, exceptId?: string) {
  let candidate = wanted
  for (let attempt = 0; attempt < 40; attempt++) {
    const taken = await db.query.users.findFirst({ where: eq(schema.users.handle, candidate) })
    if (!taken || taken.id === exceptId) return candidate
    candidate = `${wanted}-${attempt + 2}`
  }
  return `${wanted}-${crypto.randomUUID().slice(0, 6)}`
}

export async function userForMinecraft(uuid: string, name: string) {
  const known = await db.query.users.findFirst({ where: eq(schema.users.minecraftId, uuid) })
  if (known) {
    if (known.minecraftName !== name) {
      await db
        .update(schema.users)
        .set({ minecraftName: name, handle: await freeHandle(handleFrom(name), known.id) })
        .where(eq(schema.users.id, known.id))
    }
    return known.id
  }

  const [made] = await db
    .insert(schema.users)
    .values({
      name,
      handle: await freeHandle(handleFrom(name)),
      minecraftId: uuid,
      minecraftName: name,
    })
    .returning({ id: schema.users.id })
  return made.id
}

export async function startSession(userId: string, cookies: Cookies, url: URL) {
  const secure = url.protocol === "https:"
  const sessionToken = crypto.randomUUID()
  const expires = new Date(Date.now() + DAYS * 24 * 60 * 60 * 1000)
  await db.insert(schema.sessions).values({ sessionToken, userId, expires })
  cookies.set(secure ? SECURE : PLAIN, sessionToken, {
    path: "/",
    httpOnly: true,
    sameSite: "lax",
    secure,
    expires,
  })
}

export async function endSession(cookies: Cookies) {
  for (const name of [SECURE, PLAIN]) {
    const token = cookies.get(name)
    if (!token) continue
    await db.delete(schema.sessions).where(eq(schema.sessions.sessionToken, token))
    cookies.delete(name, { path: "/" })
  }
}
