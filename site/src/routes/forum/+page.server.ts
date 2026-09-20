import { asc, count, desc, eq, sql } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import type { PageServerLoad } from "./$types"

export const load: PageServerLoad = async () => {
  const categories = await db
    .select({
      id: schema.categories.id,
      name: schema.categories.name,
      blurb: schema.categories.blurb,
      icon: schema.categories.icon,
      topics: count(schema.topics.id),
      replies: sql<number>`coalesce(sum(${schema.topics.replyCount}), 0)`.mapWith(Number),
    })
    .from(schema.categories)
    .leftJoin(schema.topics, eq(schema.topics.categoryId, schema.categories.id))
    .groupBy(schema.categories.id)
    .orderBy(asc(schema.categories.position))

  const latest = await db
    .select({
      id: schema.topics.id,
      title: schema.topics.title,
      slug: schema.topics.slug,
      lastPostAt: schema.topics.lastPostAt,
      replyCount: schema.topics.replyCount,
      categoryName: schema.categories.name,
      handle: schema.users.handle,
      minecraftId: schema.users.minecraftId,
      minecraftName: schema.users.minecraftName,
    })
    .from(schema.topics)
    .innerJoin(schema.categories, eq(schema.categories.id, schema.topics.categoryId))
    .innerJoin(schema.users, eq(schema.users.id, schema.topics.authorId))
    .orderBy(desc(schema.topics.lastPostAt))
    .limit(8)

  return { categories, latest }
}
