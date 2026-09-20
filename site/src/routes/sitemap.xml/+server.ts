import { desc, eq } from "drizzle-orm"
import { pages } from "$lib/docs/pages"
import { db, schema } from "$lib/server/db"
import type { RequestHandler } from "./$types"

export const GET: RequestHandler = async ({ url }) => {
  const origin = url.origin

  const topics = await db
    .select({ slug: schema.topics.slug, lastPostAt: schema.topics.lastPostAt })
    .from(schema.topics)
    .orderBy(desc(schema.topics.lastPostAt))
    .limit(2000)

  const categories = await db.select({ id: schema.categories.id }).from(schema.categories)

  const entries = [
    { loc: `${origin}/`, when: null },
    { loc: `${origin}/forum`, when: null },
    ...pages().map((page) => ({ loc: `${origin}/docs/${page.slug}`, when: null })),
    ...categories.map((row) => ({ loc: `${origin}/forum/c/${row.id}`, when: null })),
    ...topics.map((row) => ({ loc: `${origin}/forum/t/${row.slug}`, when: row.lastPostAt })),
  ]

  const body =
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    entries
      .map(
        (entry) =>
          `  <url><loc>${entry.loc}</loc>${entry.when ? `<lastmod>${entry.when.toISOString()}</lastmod>` : ""}</url>`,
      )
      .join("\n") +
    `\n</urlset>\n`

  return new Response(body, { headers: { "content-type": "application/xml" } })
}
