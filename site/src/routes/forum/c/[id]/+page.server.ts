import { error } from "@sveltejs/kit"
import { and, asc, count, desc, eq, sql } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import type { PageServerLoad } from "./$types"

const PER_PAGE = 25

const SORTS = {
  latest: "Latest reply",
  new: "Newest topic",
  replies: "Most replies",
  unanswered: "Unanswered",
} as const

export const load: PageServerLoad = async ({ params, url, locals }) => {
  const category = await db.query.categories.findFirst({
    where: eq(schema.categories.id, params.id),
  })
  if (!category) error(404, "No such category")

  const session = await locals.auth()
  const sort = (url.searchParams.get("sort") ?? "latest") as keyof typeof SORTS
  const at = Math.max(1, Number(url.searchParams.get("page") ?? 1))

  const order = {
    latest: [desc(schema.topics.pinned), desc(schema.topics.lastPostAt)],
    new: [desc(schema.topics.pinned), desc(schema.topics.createdAt)],
    replies: [desc(schema.topics.pinned), desc(schema.topics.replyCount)],
    unanswered: [desc(schema.topics.pinned), desc(schema.topics.createdAt)],
  }[sort in SORTS ? sort : "latest"]

  const where =
    sort === "unanswered"
      ? and(eq(schema.topics.categoryId, params.id), eq(schema.topics.replyCount, 0))
      : eq(schema.topics.categoryId, params.id)

  const [{ total }] = await db
    .select({ total: count() })
    .from(schema.topics)
    .where(where)

  const rows = await db
    .select({
      id: schema.topics.id,
      title: schema.topics.title,
      slug: schema.topics.slug,
      pinned: schema.topics.pinned,
      locked: schema.topics.locked,
      solvedPostId: schema.topics.solvedPostId,
      replyCount: schema.topics.replyCount,
      lastPostAt: schema.topics.lastPostAt,
      handle: schema.users.handle,
      minecraftId: schema.users.minecraftId,
      minecraftName: schema.users.minecraftName,
      readAt: schema.reads.readAt,
    })
    .from(schema.topics)
    .innerJoin(schema.users, eq(schema.users.id, schema.topics.authorId))
    .leftJoin(
      schema.reads,
      and(
        eq(schema.reads.topicId, schema.topics.id),
        eq(schema.reads.userId, session?.user.id ?? ""),
      ),
    )
    .where(where)
    .orderBy(...order)
    .limit(PER_PAGE)
    .offset((at - 1) * PER_PAGE)

  const topics = rows.map((row) => ({
    ...row,
    unread: Boolean(session?.user) && (!row.readAt || row.readAt < row.lastPostAt),
  }))

  return {
    category,
    topics,
    sort: sort in SORTS ? sort : "latest",
    sorts: SORTS,
    at,
    pages: Math.max(1, Math.ceil(total / PER_PAGE)),
    total,
  }
}
