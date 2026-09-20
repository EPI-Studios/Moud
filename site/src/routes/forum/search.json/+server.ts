import { json } from "@sveltejs/kit"
import { desc, eq, ilike, or, sql } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { excerpt } from "$lib/render"
import { clientAddress, take } from "$lib/server/limit"
import type { RequestHandler } from "./$types"

const PER_MINUTE = 60

export const GET: RequestHandler = async ({ url, request, getClientAddress }) => {
  const query = (url.searchParams.get("q") ?? "").trim()
  if (query.length < 2) return json([])
  if (query.length > 80) return json([])

  const from = clientAddress(request, getClientAddress())
  const allowance = await take(`search:${from}`, PER_MINUTE, 60 * 1000)
  if (!allowance.ok) return json([], { status: 429 })

  const like = `%${query.replace(/[%_]/g, "")}%`

  const rows = await db
    .select({
      title: schema.topics.title,
      slug: schema.topics.slug,
      category: schema.categories.name,
      body: schema.posts.body,
      lastPostAt: schema.topics.lastPostAt,
    })
    .from(schema.posts)
    .innerJoin(schema.topics, eq(schema.topics.id, schema.posts.topicId))
    .innerJoin(schema.categories, eq(schema.categories.id, schema.topics.categoryId))
    .where(
      sql`${schema.posts.hidden} = false and (${or(
        ilike(schema.topics.title, like),
        ilike(schema.posts.body, like),
      )})`,
    )
    .orderBy(desc(schema.topics.lastPostAt))
    .limit(20)

  const seen = new Set<string>()
  const results = []
  for (const row of rows) {
    if (seen.has(row.slug)) continue
    seen.add(row.slug)
    results.push({
      page: row.category,
      heading: row.title,
      url: `/forum/t/${row.slug}`,
      text: excerpt(row.body, 240),
    })
  }

  return json(results)
}
