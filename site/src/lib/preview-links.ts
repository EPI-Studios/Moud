export function lonelyLinks(html: string) {
  const found = new Set<string>()
  for (const match of html.matchAll(/<p><a href="(https?:\/\/[^"]+)"[^>]*>[^<]*<\/a><\/p>/g)) {
    found.add(match[1])
  }
  return [...found]
}
