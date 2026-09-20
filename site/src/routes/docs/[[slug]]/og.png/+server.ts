import { error } from "@sveltejs/kit"
import { card } from "$lib/server/card"
import { doc } from "$lib/docs/pages"
import type { RequestHandler } from "./$types"

export const GET: RequestHandler = async ({ params }) => {
  const page = doc(params.slug ?? "")
  if (!page) error(404, "No such page")

  const png = await card({
    eyebrow: page.group,
    title: page.title,
    blurb: page.page.blurb || undefined,
    facts: [`${page.headings.length} sections`],
  })

  return new Response(new Uint8Array(png), {
    headers: { "content-type": "image/png", "cache-control": "public, max-age=3600" },
  })
}
