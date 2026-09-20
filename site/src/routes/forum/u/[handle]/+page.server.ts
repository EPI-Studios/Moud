import { error } from "@sveltejs/kit"
import { and, desc, eq, isNull } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import type { PageServerLoad } from "./$types"

export const load: PageServerLoad = async ({ params }) => {
  const person = await db.query.users.findFirst({
    where: eq(schema.users.handle, params.handle),
  })
  if (!person) error(404, "No such member")

  const badges = await db
    .select({
      id: schema.badges.id,
      name: schema.badges.name,
      blurb: schema.badges.blurb,
      icon: schema.badges.icon,
      tone: schema.badges.tone,
      grantedAt: schema.userBadges.grantedAt,
    })
    .from(schema.userBadges)
    .innerJoin(schema.badges, eq(schema.badges.id, schema.userBadges.badgeId))
    .where(eq(schema.userBadges.userId, person.id))
    .orderBy(schema.badges.position)

  const topics = await db
    .select({
      id: schema.topics.id,
      title: schema.topics.title,
      slug: schema.topics.slug,
      replyCount: schema.topics.replyCount,
      lastPostAt: schema.topics.lastPostAt,
      category: schema.categories.name,
    })
    .from(schema.topics)
    .innerJoin(schema.categories, eq(schema.categories.id, schema.topics.categoryId))
    .where(eq(schema.topics.authorId, person.id))
    .orderBy(desc(schema.topics.lastPostAt))
    .limit(10)

  const replies = await db
    .select({
      id: schema.posts.id,
      body: schema.posts.body,
      createdAt: schema.posts.createdAt,
      title: schema.topics.title,
      slug: schema.topics.slug,
    })
    .from(schema.posts)
    .innerJoin(schema.topics, eq(schema.topics.id, schema.posts.topicId))
    .where(and(eq(schema.posts.authorId, person.id), isNull(schema.posts.deletedAt)))
    .orderBy(desc(schema.posts.createdAt))
    .limit(10)

  return {
    person: {
      handle: person.handle,
      role: person.role,
      bio: person.bio,
      minecraftId: person.minecraftId,
      minecraftName: person.minecraftName,
      createdAt: person.createdAt,
      postCount: person.postCount,
      topicCount: person.topicCount,
    },
    badges,
    topics,
    replies,
  }
}
