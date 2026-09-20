import { error } from "@sveltejs/kit"
import { and, count, eq, gt, sql } from "drizzle-orm"
import { db, schema } from "./db"

const WINDOW_MINUTES = 10

const LIMITS = {
  topic: { fresh: 2, trusted: 10 },
  post: { fresh: 10, trusted: 40 },
}

export const MAX_BODY = 20_000
export const MAX_TITLE = 140

export function assertLength(text: string, limit: number, what: string) {
  if (text.length > limit) error(413, `That ${what} is longer than ${limit} characters.`)
}

export async function assertCanReport(userId: string) {
  const since = new Date(Date.now() - 60 * 60 * 1000)
  const [{ recent }] = await db
    .select({ recent: count() })
    .from(schema.reports)
    .where(and(eq(schema.reports.reporterId, userId), gt(schema.reports.createdAt, since)))

  if (recent >= 10) error(429, "That is a lot of reports in an hour. Give staff a moment to read them.")
}

export type Actor = {
  id: string
  role: string
  handle: string
}

export async function assertCanPost(user: Actor, what: "topic" | "post") {
  if (user.role === "staff") return

  const person = await db.query.users.findFirst({ where: eq(schema.users.id, user.id) })
  if (!person) error(403, "No such account")

  const now = new Date()
  if (person.bannedUntil && person.bannedUntil > now) {
    error(403, `You are banned until ${person.bannedUntil.toISOString().slice(0, 16).replace("T", " ")}. ${person.banReason ?? ""}`.trim())
  }
  if (person.mutedUntil && person.mutedUntil > now) {
    error(403, "You are muted and cannot post right now.")
  }

  const since = new Date(now.getTime() - WINDOW_MINUTES * 60 * 1000)
  const allowed = person.trusted ? LIMITS[what].trusted : LIMITS[what].fresh

  const [{ recent }] =
    what === "topic"
      ? await db
          .select({ recent: count() })
          .from(schema.topics)
          .where(and(eq(schema.topics.authorId, user.id), gt(schema.topics.createdAt, since)))
      : await db
          .select({ recent: count() })
          .from(schema.posts)
          .where(and(eq(schema.posts.authorId, user.id), gt(schema.posts.createdAt, since)))

  if (recent >= allowed) {
    error(429, `Slow down. ${allowed} ${what === "topic" ? "topics" : "posts"} every ${WINDOW_MINUTES} minutes is the limit until your account is trusted.`)
  }
}

export function looksLikeSpam(body: string) {
  const links = (body.match(/https?:\/\//g) ?? []).length
  const shouting = body.length > 40 && body === body.toUpperCase()
  return links > 4 || shouting
}

export async function countPost(userId: string, what: "topic" | "post") {
  await db
    .update(schema.users)
    .set(
      what === "topic"
        ? { topicCount: sql`${schema.users.topicCount} + 1` }
        : { postCount: sql`${schema.users.postCount} + 1` },
    )
    .where(eq(schema.users.id, userId))
}
