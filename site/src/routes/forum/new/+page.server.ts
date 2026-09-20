import { fail, redirect } from "@sveltejs/kit"
import { asc, eq } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { slugify } from "$lib/render"
import { assertCanPost, countPost, looksLikeSpam } from "$lib/server/guard"
import type { Actions, PageServerLoad } from "./$types"

export const load: PageServerLoad = async ({ locals, url }) => {
  const session = await locals.auth()
  if (!session?.user) redirect(303, "/forum/signin")

  const categories = await db
    .select()
    .from(schema.categories)
    .orderBy(asc(schema.categories.position))

  return {
    categories: categories.filter((row) => !row.staffOnly || session.user.role === "staff"),
    chosen: url.searchParams.get("category") ?? "",
  }
}

export const actions: Actions = {
  default: async ({ request, locals }) => {
    const session = await locals.auth()
    if (!session?.user) redirect(303, "/forum/signin")

    const form = await request.formData()
    const categoryId = String(form.get("categoryId") ?? "")
    const title = String(form.get("title") ?? "").trim()
    const body = String(form.get("body") ?? "").trim()

    if (title.length < 6) return fail(400, { message: "Give it a longer title." })
    if (body.length < 10) return fail(400, { message: "Say a bit more in the post." })

    const category = await db.query.categories.findFirst({
      where: eq(schema.categories.id, categoryId),
    })
    if (!category) return fail(400, { message: "Pick a category." })
    if (category.staffOnly && session.user.role !== "staff") {
      return fail(403, { message: "That category is staff only." })
    }

    await assertCanPost(session.user, "topic")

    const slug = slugify(title)
    const [topic] = await db
      .insert(schema.topics)
      .values({ categoryId, authorId: session.user.id, title, slug })
      .returning({ id: schema.topics.id })

    await db.insert(schema.posts).values({
      topicId: topic.id,
      authorId: session.user.id,
      body,
      hidden: looksLikeSpam(body),
    })
    await countPost(session.user.id, "topic")
    await countPost(session.user.id, "post")

    redirect(303, `/forum/t/${slug}`)
  },
}
