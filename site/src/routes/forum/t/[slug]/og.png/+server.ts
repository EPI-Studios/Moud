import { error } from "@sveltejs/kit"
import { asc, eq } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { card } from "$lib/server/card"
import { excerpt } from "$lib/render"
import { displayName, faceUrl } from "$lib/user"
import type { RequestHandler } from "./$types"

export const GET: RequestHandler = async ({ params }) => {
  const topic = await db.query.topics.findFirst({ where: eq(schema.topics.slug, params.slug) })
  if (!topic) error(404, "No such topic")

  const category = await db.query.categories.findFirst({
    where: eq(schema.categories.id, topic.categoryId),
  })

  const [first] = await db
    .select({
      body: schema.posts.body,
      handle: schema.users.handle,
      minecraftId: schema.users.minecraftId,
      minecraftName: schema.users.minecraftName,
    })
    .from(schema.posts)
    .innerJoin(schema.users, eq(schema.users.id, schema.posts.authorId))
    .where(eq(schema.posts.topicId, topic.id))
    .orderBy(asc(schema.posts.createdAt))
    .limit(1)

  const png = await card({
    eyebrow: category?.name ?? "Forum",
    title: topic.title,
    blurb: first ? excerpt(first.body, 150) : undefined,
    author: first ? { name: displayName(first), head: faceUrl(first, 96) } : undefined,
    facts: [
      `${topic.replyCount} ${topic.replyCount === 1 ? "reply" : "replies"}`,
      topic.solvedPostId ? "solved" : `${topic.viewCount} views`,
    ],
  })

  return new Response(new Uint8Array(png), {
    headers: {
      "content-type": "image/png",
      "cache-control": "public, max-age=600, s-maxage=3600",
    },
  })
}
