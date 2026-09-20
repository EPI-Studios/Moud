import { error, fail, redirect } from "@sveltejs/kit"
import { asc, eq, sql } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { grant } from "$lib/server/badges"
import {
  assertCanPost,
  assertCanReport,
  assertLength,
  countPost,
  looksLikeSpam,
  MAX_BODY,
} from "$lib/server/guard"
import { lonelyLinks, preview } from "$lib/server/preview"
import { renderPost } from "$lib/render"
import type { Actions, PageServerLoad } from "./$types"

export const load: PageServerLoad = async ({ params, locals }) => {
  const topic = await db.query.topics.findFirst({ where: eq(schema.topics.slug, params.slug) })
  if (!topic) error(404, "No such topic")

  const category = await db.query.categories.findFirst({
    where: eq(schema.categories.id, topic.categoryId),
  })

  const session = await locals.auth()

  const everything = await db
    .select({
      id: schema.posts.id,
      authorId: schema.posts.authorId,
      body: schema.posts.body,
      hidden: schema.posts.hidden,
      deletedAt: schema.posts.deletedAt,
      createdAt: schema.posts.createdAt,
      editedAt: schema.posts.editedAt,
      handle: schema.users.handle,
      role: schema.users.role,
      minecraftId: schema.users.minecraftId,
      minecraftName: schema.users.minecraftName,
    })
    .from(schema.posts)
    .innerJoin(schema.users, eq(schema.users.id, schema.posts.authorId))
    .where(eq(schema.posts.topicId, topic.id))
    .orderBy(asc(schema.posts.createdAt))

  const posts = everything.filter(
    (post) =>
      !post.hidden ||
      session?.user.role === "staff" ||
      session?.user.id === post.authorId,
  )

  await db
    .update(schema.topics)
    .set({ viewCount: sql`${schema.topics.viewCount} + 1` })
    .where(eq(schema.topics.id, topic.id))

  if (session?.user) {
    await db
      .insert(schema.reads)
      .values({ userId: session.user.id, topicId: topic.id, readAt: new Date() })
      .onConflictDoUpdate({
        target: [schema.reads.userId, schema.reads.topicId],
        set: { readAt: new Date() },
      })
  }

  const rendered = posts.map((post) => ({ ...post, html: renderPost(post.body) }))
  const links = [...new Set(rendered.flatMap((post) => lonelyLinks(post.html)))].slice(0, 8)
  const previews = Object.fromEntries(
    (await Promise.all(links.map(async (url) => [url, await preview(url)] as const)))
      .filter(([, found]) => found),
  )

  return { topic, category, posts: rendered, previews }
}

export const actions: Actions = {
  reply: async ({ request, locals, params }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const body = String(form.get("body") ?? "").trim()
    if (body.length < 2) return fail(400, { message: "Write something first." })
    assertLength(body, MAX_BODY, "post")

    const topic = await db.query.topics.findFirst({ where: eq(schema.topics.slug, params.slug) })
    if (!topic) error(404, "No such topic")
    if (topic.locked) return fail(403, { message: "This topic is locked." })

    await assertCanPost(session.user, "post")

    await db.insert(schema.posts).values({
      topicId: topic.id,
      authorId: session.user.id,
      body,
      hidden: looksLikeSpam(body),
    })
    await countPost(session.user.id, "post")
    await db
      .update(schema.topics)
      .set({ replyCount: sql`${schema.topics.replyCount} + 1`, lastPostAt: new Date() })
      .where(eq(schema.topics.id, topic.id))

    return { posted: true }
  },

  edit: async ({ request, locals, params }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const postId = String(form.get("postId") ?? "")
    const body = String(form.get("body") ?? "").trim()
    if (body.length < 2) return fail(400, { message: "Write something first." })
    assertLength(body, MAX_BODY, "post")

    const post = await db.query.posts.findFirst({ where: eq(schema.posts.id, postId) })
    if (!post) error(404, "No such post")
    if (post.authorId !== session.user.id && session.user.role !== "staff") {
      error(403, "Not your post")
    }

    await db
      .update(schema.posts)
      .set({ body, editedAt: new Date() })
      .where(eq(schema.posts.id, postId))

    return { edited: true }
  },

  remove: async ({ request, locals }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const postId = String(form.get("postId") ?? "")

    const post = await db.query.posts.findFirst({ where: eq(schema.posts.id, postId) })
    if (!post) error(404, "No such post")
    if (post.authorId !== session.user.id && session.user.role !== "staff") {
      error(403, "Not your post")
    }

    await db
      .update(schema.posts)
      .set({ deletedAt: new Date(), body: "" })
      .where(eq(schema.posts.id, postId))

    return { removed: true }
  },

  answer: async ({ request, locals, params }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const postId = String(form.get("postId") ?? "")

    const topic = await db.query.topics.findFirst({ where: eq(schema.topics.slug, params.slug) })
    if (!topic) error(404, "No such topic")
    if (topic.authorId !== session.user.id && session.user.role !== "staff") {
      error(403, "Only the person who asked can mark the answer")
    }

    const answered = topic.solvedPostId === postId ? null : postId
    await db.update(schema.topics).set({ solvedPostId: answered }).where(eq(schema.topics.id, topic.id))

    if (answered) {
      const post = await db.query.posts.findFirst({ where: eq(schema.posts.id, postId) })
      if (post && post.authorId !== topic.authorId) await grant(post.authorId, "answerer")
    }

    return { answered: Boolean(answered) }
  },

  report: async ({ request, locals }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const postId = String(form.get("postId") ?? "")
    const reason = String(form.get("reason") ?? "other")
    const note = String(form.get("note") ?? "").trim() || null

    const post = await db.query.posts.findFirst({ where: eq(schema.posts.id, postId) })
    if (!post) error(404, "No such post")

    await assertCanReport(session.user.id)

    await db.insert(schema.reports).values({
      postId,
      reporterId: session.user.id,
      reason,
      note,
    })

    return { reported: true }
  },

  moderate: async ({ request, locals, params }) => {
    const session = await locals.auth()
    if (session?.user.role !== "staff") error(403, "Staff only")

    const form = await request.formData()
    const what = String(form.get("what") ?? "")
    const topic = await db.query.topics.findFirst({ where: eq(schema.topics.slug, params.slug) })
    if (!topic) error(404, "No such topic")

    if (what === "pin") {
      await db.update(schema.topics).set({ pinned: !topic.pinned }).where(eq(schema.topics.id, topic.id))
    } else if (what === "lock") {
      await db.update(schema.topics).set({ locked: !topic.locked }).where(eq(schema.topics.id, topic.id))
    }

    return { moderated: true }
  },
}
