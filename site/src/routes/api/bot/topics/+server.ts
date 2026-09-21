import { json } from "@sveltejs/kit"
import { and, asc, eq, gt } from "drizzle-orm"
import { fromBot, SITE } from "$lib/server/bot"
import { db, schema } from "$lib/server/db"
import { excerpt } from "$lib/render"
import { displayName, faceUrl } from "$lib/user"
import type { RequestHandler } from "./$types"

type Body = { since?: string; limit?: number }

export const POST: RequestHandler = async ({ request }) => {
  const asked = await fromBot<Body>(request)
  if (!asked.ok) return json({ ok: false, error: asked.error }, { status: asked.status })

  const when = new Date(asked.value.since ?? Date.now() - 10 * 60 * 1000)
  if (Number.isNaN(when.getTime())) return json({ ok: false, error: "bad since" })
  const limit = Math.min(Math.max(asked.value.limit ?? 10, 1), 25)

  const rows = await db
    .select({
      id: schema.topics.id,
      title: schema.topics.title,
      slug: schema.topics.slug,
      createdAt: schema.topics.createdAt,
      replyCount: schema.topics.replyCount,
      solved: schema.topics.solvedPostId,
      category: schema.categories.name,
      categoryId: schema.categories.id,
      icon: schema.categories.icon,
      handle: schema.users.handle,
      role: schema.users.role,
      minecraftId: schema.users.minecraftId,
      minecraftName: schema.users.minecraftName,
    })
    .from(schema.topics)
    .innerJoin(schema.categories, eq(schema.categories.id, schema.topics.categoryId))
    .innerJoin(schema.users, eq(schema.users.id, schema.topics.authorId))
    .where(gt(schema.topics.createdAt, when))
    .orderBy(asc(schema.topics.createdAt))
    .limit(limit)

  const topics = []
  for (const row of rows) {
    const [first] = await db
      .select({ body: schema.posts.body })
      .from(schema.posts)
      .where(and(eq(schema.posts.topicId, row.id), eq(schema.posts.hidden, false)))
      .orderBy(asc(schema.posts.createdAt))
      .limit(1)

    topics.push({
      id: row.id,
      title: row.title,
      url: `${SITE}/forum/t/${row.slug}`,
      card: `${SITE}/forum/t/${row.slug}/og.png`,
      category: { id: row.categoryId, name: row.category, icon: row.icon },
      author: {
        handle: row.handle,
        name: displayName(row),
        role: row.role,
        face: faceUrl(row, 128),
        url: `${SITE}/forum/u/${row.handle}`,
      },
      blurb: first ? excerpt(first.body, 280) : "",
      replyCount: row.replyCount,
      solved: Boolean(row.solved),
      createdAt: row.createdAt.toISOString(),
    })
  }

  return json({ ok: true, topics })
}
