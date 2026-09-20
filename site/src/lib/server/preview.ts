import { eq } from "drizzle-orm"
import { db, schema } from "./db"
import { fetchPublic } from "./fetch-guard"

const STALE_DAYS = 14

function meta(html: string, name: string) {
  const patterns = [
    new RegExp(`<meta[^>]+property=["']${name}["'][^>]+content=["']([^"']+)["']`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+property=["']${name}["']`, "i"),
    new RegExp(`<meta[^>]+name=["']${name}["'][^>]+content=["']([^"']+)["']`, "i"),
  ]
  for (const pattern of patterns) {
    const found = html.match(pattern)
    if (found) return found[1]
  }
  return null
}

function decode(text: string) {
  return text
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

export async function preview(url: string) {
  const known = await db.query.linkPreviews.findFirst({ where: eq(schema.linkPreviews.url, url) })
  const stale = known && known.fetchedAt < new Date(Date.now() - STALE_DAYS * 864e5)
  if (known && !stale) return known

  try {
    const { body: html } = await fetchPublic(url)
    const width = Number(meta(html, "og:image:width") ?? 0)
    const card = meta(html, "twitter:card") ?? ""
    const image = meta(html, "og:image") ?? meta(html, "twitter:image")

    const row = {
      url,
      title: decode(meta(html, "og:title") ?? html.match(/<title>([^<]+)<\/title>/i)?.[1] ?? ""),
      blurb: decode(meta(html, "og:description") ?? meta(html, "description") ?? ""),
      image: image ? new URL(image, url).toString() : null,
      site: decode(meta(html, "og:site_name") ?? new URL(url).host),
      author: decode(meta(html, "article:author") ?? meta(html, "twitter:creator") ?? "") || null,
      accent: (meta(html, "theme-color") ?? "").match(/^#[0-9a-fA-F]{3,8}$/)?.[0] ?? null,
      large: card === "summary_large_image" || width >= 600,
      fetchedAt: new Date(),
    }

    await db
      .insert(schema.linkPreviews)
      .values(row)
      .onConflictDoUpdate({ target: schema.linkPreviews.url, set: row })

    return row
  } catch {
    return null
  }
}

export function lonelyLinks(html: string) {
  const found = new Set<string>()
  for (const match of html.matchAll(/<p><a href="(https?:\/\/[^"]+)"[^>]*>[^<]*<\/a><\/p>/g)) {
    found.add(match[1])
  }
  return [...found]
}
