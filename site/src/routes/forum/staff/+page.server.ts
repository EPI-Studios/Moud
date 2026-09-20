import { error, redirect } from "@sveltejs/kit"
import { desc, eq } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import type { Actions, PageServerLoad } from "./$types"

async function staffOnly(locals: App.Locals) {
  const session = await locals.auth()
  if (!session?.user) redirect(303, "/forum/signin")
  if (session.user.role !== "staff") error(403, "Staff only")
  return session.user
}

export const load: PageServerLoad = async ({ locals }) => {
  await staffOnly(locals)

  const reports = await db
    .select({
      id: schema.reports.id,
      reason: schema.reports.reason,
      note: schema.reports.note,
      state: schema.reports.state,
      createdAt: schema.reports.createdAt,
      postId: schema.posts.id,
      body: schema.posts.body,
      hidden: schema.posts.hidden,
      authorHandle: schema.users.handle,
      topicSlug: schema.topics.slug,
      topicTitle: schema.topics.title,
    })
    .from(schema.reports)
    .innerJoin(schema.posts, eq(schema.posts.id, schema.reports.postId))
    .innerJoin(schema.users, eq(schema.users.id, schema.posts.authorId))
    .innerJoin(schema.topics, eq(schema.topics.id, schema.posts.topicId))
    .where(eq(schema.reports.state, "open"))
    .orderBy(desc(schema.reports.createdAt))
    .limit(50)

  const held = await db
    .select({
      id: schema.posts.id,
      body: schema.posts.body,
      createdAt: schema.posts.createdAt,
      authorHandle: schema.users.handle,
      topicSlug: schema.topics.slug,
      topicTitle: schema.topics.title,
    })
    .from(schema.posts)
    .innerJoin(schema.users, eq(schema.users.id, schema.posts.authorId))
    .innerJoin(schema.topics, eq(schema.topics.id, schema.posts.topicId))
    .where(eq(schema.posts.hidden, true))
    .orderBy(desc(schema.posts.createdAt))
    .limit(50)

  const log = await db
    .select({
      id: schema.modLog.id,
      action: schema.modLog.action,
      subject: schema.modLog.subject,
      note: schema.modLog.note,
      createdAt: schema.modLog.createdAt,
      staffHandle: schema.users.handle,
    })
    .from(schema.modLog)
    .innerJoin(schema.users, eq(schema.users.id, schema.modLog.staffId))
    .orderBy(desc(schema.modLog.createdAt))
    .limit(30)

  return { reports, held, log }
}

export const actions: Actions = {
  resolve: async ({ request, locals }) => {
    const staff = await staffOnly(locals)
    const form = await request.formData()
    const id = String(form.get("id") ?? "")
    const outcome = String(form.get("outcome") ?? "closed")

    const report = await db.query.reports.findFirst({ where: eq(schema.reports.id, id) })
    if (!report) error(404, "No such report")

    if (outcome === "remove") {
      await db
        .update(schema.posts)
        .set({ deletedAt: new Date(), body: "" })
        .where(eq(schema.posts.id, report.postId))
    }

    await db
      .update(schema.reports)
      .set({ state: outcome === "remove" ? "removed" : "closed", handledById: staff.id, handledAt: new Date() })
      .where(eq(schema.reports.id, id))

    await db.insert(schema.modLog).values({
      staffId: staff.id,
      action: outcome === "remove" ? "removed a reported post" : "closed a report",
      subject: report.postId,
    })

    return { done: true }
  },

  release: async ({ request, locals }) => {
    const staff = await staffOnly(locals)
    const form = await request.formData()
    const postId = String(form.get("postId") ?? "")

    await db.update(schema.posts).set({ hidden: false }).where(eq(schema.posts.id, postId))
    await db.insert(schema.modLog).values({
      staffId: staff.id,
      action: "released a held post",
      subject: postId,
    })

    return { done: true }
  },

  punish: async ({ request, locals }) => {
    const staff = await staffOnly(locals)
    const form = await request.formData()
    const handle = String(form.get("handle") ?? "").trim()
    const what = String(form.get("what") ?? "mute")
    const days = Math.max(0, Number(form.get("days") ?? 1))
    const reason = String(form.get("reason") ?? "").trim() || null

    const person = await db.query.users.findFirst({ where: eq(schema.users.handle, handle) })
    if (!person) error(404, "No such member")

    const until = days === 0 ? null : new Date(Date.now() + days * 24 * 60 * 60 * 1000)

    await db
      .update(schema.users)
      .set(what === "ban" ? { bannedUntil: until, banReason: reason } : { mutedUntil: until })
      .where(eq(schema.users.id, person.id))

    await db.insert(schema.modLog).values({
      staffId: staff.id,
      action: days === 0 ? `lifted the ${what}` : `${what}ned ${handle} for ${days} days`,
      subject: person.id,
      note: reason,
    })

    return { done: true }
  },
}
