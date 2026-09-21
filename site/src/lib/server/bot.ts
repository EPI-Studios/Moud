import { eq, inArray, isNotNull } from "drizzle-orm"
import { db, schema } from "./db"
import { signedByBot } from "./service"
import { displayName, faceUrl } from "$lib/user"

export const SITE = "https://moud.epistudios.fr"

export type Profile = {
  handle: string
  name: string
  role: string
  minecraftId: string | null
  minecraftName: string | null
  discordId: string | null
  badges: { id: string; name: string; tone: string }[]
  postCount: number
  topicCount: number
  joinedAt: string
  url: string
  face: string
}

export async function profileOf(userId: string): Promise<Profile | null> {
  const person = await db.query.users.findFirst({
    where: eq(schema.users.id, userId),
  })
  if (!person) return null

  const held = await db
    .select({
      id: schema.badges.id,
      name: schema.badges.name,
      tone: schema.badges.tone,
    })
    .from(schema.userBadges)
    .innerJoin(schema.badges, eq(schema.badges.id, schema.userBadges.badgeId))
    .where(eq(schema.userBadges.userId, userId))
    .orderBy(schema.badges.position)

  return {
    handle: person.handle,
    name: displayName(person),
    role: person.role,
    minecraftId: person.minecraftId,
    minecraftName: person.minecraftName,
    discordId: person.discordId,
    badges: held,
    postCount: person.postCount,
    topicCount: person.topicCount,
    joinedAt: person.createdAt.toISOString(),
    url: `${SITE}/forum/u/${person.handle}`,
    face: faceUrl(person, 128),
  }
}

export async function everyLinkedMember() {
  const people = await db
    .select({
      id: schema.users.id,
      handle: schema.users.handle,
      role: schema.users.role,
      discordId: schema.users.discordId,
      minecraftName: schema.users.minecraftName,
    })
    .from(schema.users)
    .where(isNotNull(schema.users.discordId))

  if (!people.length) return []

  const held = await db
    .select({
      userId: schema.userBadges.userId,
      badgeId: schema.userBadges.badgeId,
    })
    .from(schema.userBadges)
    .where(
      inArray(
        schema.userBadges.userId,
        people.map((person) => person.id),
      ),
    )

  return people.map((person) => ({
    discordId: person.discordId,
    handle: person.handle,
    role: person.role,
    minecraftName: person.minecraftName,
    badges: held
      .filter((row) => row.userId === person.id)
      .map((row) => row.badgeId),
  }))
}

export async function fromBot<T>(request: Request) {
  const raw = await request.text()
  if (!signedByBot(request, raw)) {
    return { ok: false as const, error: "not signed by the bot", status: 401 }
  }
  try {
    return { ok: true as const, value: (raw ? JSON.parse(raw) : {}) as T }
  } catch {
    return { ok: false as const, error: "bad body", status: 400 }
  }
}
