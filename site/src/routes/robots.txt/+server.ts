import type { RequestHandler } from "./$types"

export const GET: RequestHandler = async ({ url }) => {
  const body = `User-agent: *\nAllow: /\n\nSitemap: ${url.origin}/sitemap.xml\n`
  return new Response(body, { headers: { "content-type": "text/plain" } })
}
