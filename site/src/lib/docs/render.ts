import { micromark } from "micromark"
import { gfm, gfmHtml } from "micromark-extension-gfm"
import { highlightLuau } from "$lib/render"
import { iconSvg } from "$lib/icons"

const ALERT_ICONS: Record<string, string> = {
  note: "info",
  tip: "lightbulb",
  important: "seal-warning",
  warning: "warning",
  caution: "warning-octagon",
}

const FENCE = /^```([a-z]*)\n([\s\S]*?)^```[ \t]*$/gm
const ALERT = /^> \[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\][ \t]*\n((?:^>.*\n?)*)/gm
const DEMO = /^<!--\s*demo:([a-z0-9-]+)\s*-->[ \t]*$/gm

export type Heading = { id: string; name: string }

function escapeHtml(text: string) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

function slug(text: string) {
  return text
    .toLowerCase()
    .replace(/<[^>]+>/g, "")
    .replace(/[^a-z0-9 -]/g, "")
    .trim()
    .replace(/\s+/g, "-")
}

function codeBlock(lang: string, code: string) {
  const body = lang === "lua" || lang === "luau" ? highlightLuau(code) : escapeHtml(code)
  const count = code.split("\n").length
  const gutter =
    count > 1
      ? `<pre class="gutter" aria-hidden="true">${Array.from({ length: count }, (_, i) => i + 1).join("\n")}</pre>`
      : ""
  const name = lang === "lua" ? "luau" : lang
  const label = name ? `<span class="code-lang">${name}</span>` : ""
  return `<div class="doc-code">${label}<div class="code-body">${gutter}<pre><code>${body}</code></pre></div></div>`
}

export function renderDoc(source: string): { html: string; headings: Heading[] } {
  const blocks: string[] = []
  const stash = (html: string) => {
    blocks.push(html)
    return `\n\nCODEBLOCK${blocks.length - 1}END\n\n`
  }

  let text = source.replace(DEMO, (_whole, name: string) => stash(`{{demo:${name}}}`))

  text = text.replace(ALERT, (_whole, kind: string, inner: string) => {
    const body = renderDoc(
      inner
        .split("\n")
        .map((line) => line.replace(/^> ?/, ""))
        .join("\n")
        .trim(),
    ).html
    const icon = iconSvg(ALERT_ICONS[kind.toLowerCase()], "18px", "alert-icon")
    return stash(
      `<div class="alert alert-${kind.toLowerCase()}">${icon}` +
        `<div class="alert-body">${body}</div></div>`,
    )
  })

  text = text.replace(FENCE, (_whole, lang: string, code: string) =>
    stash(codeBlock(lang, code.replace(/\n$/, ""))),
  )

  text = text.replace(/\\\|/g, "PIPEESCAPE")

  let html = micromark(text, {
    extensions: [gfm()],
    htmlExtensions: [gfmHtml()],
    allowDangerousHtml: true,
  })

  html = html.replace(/PIPEESCAPE/g, "|")

  const headings: Heading[] = []
  html = html.replace(/<h([1-4])>([\s\S]*?)<\/h\1>/g, (_whole, level: string, inner: string) => {
    const name = inner.replace(/<[^>]+>/g, "").split(" — extends")[0]
    const id = slug(name)
    if (level === "2") headings.push({ id, name })
    const anchor = `<a class="anchor" href="#${id}">#</a>`
    return `<h${level} id="${id}">${inner}${anchor}</h${level}>`
  })

  html = html.replace(/<table>([\s\S]*?)<\/table>/g, (_whole, inner: string) => {
    const firsts = [...inner.matchAll(/<tr>\s*<td>([\s\S]*?)<\/td>/g)].map((m) =>
      m[1].replace(/<[^>]+>/g, ""),
    )
    const long = firsts.some((cell) => cell.length > 26)
    return `<div class="table-wrap"><table${long ? ' class="long-first"' : ""}>${inner}</table></div>`
  })

  html = html.replace(
    /(<\/h2>)\s*<p>Extends (<a href="#[^"]+">[^<]+<\/a>)\.<\/p>/g,
    '$1<p class="extends-line">Extends $2</p>',
  )

  html = html.replace(
    /href="([a-z0-9-]+)\.md(#[^"]*)?"/g,
    (_whole, stem: string, hash: string | undefined) =>
      `href="/docs/${stem === "README" ? "" : stem}${hash ?? ""}"`,
  )

  html = html.replace(
    /<p>CODEBLOCK(\d+)END<\/p>/g,
    (_whole, index: string) => blocks[Number(index)],
  )

  return { html, headings }
}

export function plain(source: string) {
  return source
    .replace(/```[a-z]*\n/g, "")
    .replace(/\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]/g, "")
    .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
    .replace(/\\\|/g, "/")
    .replace(/[`*|#>]/g, " ")
    .replace(/-{3,}/g, " ")
    .replace(/\s+/g, " ")
    .trim()
}
