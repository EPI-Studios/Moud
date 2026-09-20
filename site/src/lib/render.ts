import { micromark } from "micromark"
import { gfm, gfmHtml } from "micromark-extension-gfm"

const KEYWORDS = new Set([
  "and", "break", "continue", "do", "else", "elseif", "end", "export", "false", "for",
  "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
  "true", "type", "typeof", "until", "while",
])

const TOKEN =
  /(--\[(=*)\[[\s\S]*?\]\2\]|--[^\n]*)|(\[(=*)\[[\s\S]*?\]\4\]|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)|(\b0x[0-9a-fA-F_]+\b|\b\d[\d_]*(?:\.\d+)?(?:e[+-]?\d+)?\b)|([A-Za-z_][A-Za-z0-9_]*)/g

function escapeHtml(text: string) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

export function highlightLuau(code: string) {
  let out = ""
  let last = 0
  for (const match of code.matchAll(TOKEN)) {
    const at = match.index ?? 0
    out += escapeHtml(code.slice(last, at))
    const text = escapeHtml(match[0])
    const after = code.slice(at + match[0].length, at + match[0].length + 2)
    if (match[1]) out += `<span class="syn-comment">${text}</span>`
    else if (match[3]) out += `<span class="syn-string">${text}</span>`
    else if (match[5]) out += `<span class="syn-num">${text}</span>`
    else if (match[0] === "true" || match[0] === "false" || match[0] === "nil")
      out += `<span class="syn-const">${text}</span>`
    else if (KEYWORDS.has(match[0])) out += `<span class="syn-keyword">${text}</span>`
    else if (/^\s*[({"']/.test(after)) out += `<span class="syn-func">${text}</span>`
    else out += text
    last = at + match[0].length
  }
  return out + escapeHtml(code.slice(last))
}

function unescapeHtml(text: string) {
  return text
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
}

function videoId(url: string) {
  const youtube = url.match(
    /^https:\/\/(?:www\.|m\.)?youtube\.com\/watch\?(?:[^&]*&)*v=([A-Za-z0-9_-]{6,20})/,
  )
  if (youtube) return youtube[1]
  const short = url.match(/^https:\/\/youtu\.be\/([A-Za-z0-9_-]{6,20})/)
  return short ? short[1] : null
}

function embed(url: string) {
  const id = videoId(url)
  if (id) {
    return (
      `<div class="post-embed"><iframe src="https://www.youtube-nocookie.com/embed/${id}"` +
      ` title="YouTube video" loading="lazy" allowfullscreen` +
      ` allow="accelerometer; clipboard-write; encrypted-media; picture-in-picture"></iframe></div>`
    )
  }

  const vimeo = url.match(/^https:\/\/vimeo\.com\/(\d{6,12})/)
  if (vimeo) {
    return (
      `<div class="post-embed"><iframe src="https://player.vimeo.com/video/${vimeo[1]}"` +
      ` title="Vimeo video" loading="lazy" allowfullscreen></iframe></div>`
    )
  }

  if (/^https:\/\/[^\s"']+\.(mp4|webm|ogv)(\?[^\s"']*)?$/i.test(url)) {
    return `<video class="post-video" controls preload="metadata" src="${url}"></video>`
  }

  if (/^https:\/\/[^\s"']+\.(png|jpe?g|gif|webp|avif)(\?[^\s"']*)?$/i.test(url)) {
    return `<img class="post-image" src="${url}" alt="" loading="lazy">`
  }

  return null
}

function withMentions(html: string) {
  return html.replace(/(^|[\s>(])@([A-Za-z0-9_-]{3,24})/g, (whole, before: string, name: string) => {
    if (whole.includes("</a>")) return whole
    return `${before}<a class="mention" href="/forum/u/${name}">@${name}</a>`
  })
}

export function renderPost(source: string) {
  const html = micromark(source, {
    extensions: [gfm()],
    htmlExtensions: [gfmHtml()],
    allowDangerousHtml: false,
    allowDangerousProtocol: false,
  })

  const withCode = html.replace(
    /<pre><code(?: class="language-(lua|luau)")?>([\s\S]*?)<\/code><\/pre>/g,
    (whole, lang: string | undefined, body: string) => {
      if (!lang) return whole
      const code = highlightLuau(unescapeHtml(body).replace(/\n$/, ""))
      return `<div class="post-code"><span class="code-lang">luau</span><pre><code>${code}</code></pre></div>`
    },
  )

  const withMedia = withCode.replace(
    /<p><a href="([^"]+)">[^<]*<\/a><\/p>/g,
    (whole, href: string) => embed(unescapeHtml(href)) ?? whole,
  )

  const withLinks = withMedia.replace(
    /<a href="([^"]+)">/g,
    (whole, href: string) => {
      const url = unescapeHtml(href)
      if (!/^https?:\/\//i.test(url)) return whole
      return `<a href="${href}" data-external rel="noopener noreferrer nofollow ugc">`
    },
  )

  const withImages = withLinks.replace(
    /<img src="([^"]+)" alt="([^"]*)"\s*\/?>/g,
    (whole, src: string, alt: string) => {
      const url = unescapeHtml(src)
      if (!/^https:\/\//.test(url)) return whole
      const video = embed(url)
      if (video && !video.startsWith("<img")) return video
      return `<img class="post-image" src="${src}" alt="${alt}" loading="lazy">`
    },
  )

  return withMentions(withImages)
}

export function excerpt(source: string, length = 180) {
  const text = source
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`([^`]*)`/g, "$1")
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[#>*_~|-]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
  return text.length > length ? text.slice(0, length).trimEnd() + "…" : text
}

export function slugify(title: string) {
  const base = title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 60)
  return (base || "topic") + "-" + crypto.randomUUID().slice(0, 6)
}
