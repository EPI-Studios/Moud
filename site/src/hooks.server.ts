import type { Handle } from "@sveltejs/kit"
import { sequence } from "@sveltejs/kit/hooks"
import { handle as authHandle } from "$lib/server/auth"

const CSP = [
  "default-src 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "object-src 'none'",
  "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
  "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
  "font-src 'self' https://fonts.gstatic.com data:",
  "img-src 'self' data: blob: https:",
  "media-src 'self' https:",
  "connect-src 'self' https:",
  "frame-src https://www.youtube-nocookie.com https://player.vimeo.com",
].join("; ")

const headers: Handle = async ({ event, resolve }) => {
  const answer = await resolve(event)

  answer.headers.set("content-security-policy", CSP)
  answer.headers.set("x-content-type-options", "nosniff")
  answer.headers.set("referrer-policy", "strict-origin-when-cross-origin")
  answer.headers.set("x-frame-options", "DENY")
  answer.headers.set("permissions-policy", "camera=(), microphone=(), geolocation=(), payment=()")
  answer.headers.set("cross-origin-opener-policy", "same-origin")

  if (event.url.protocol === "https:") {
    answer.headers.set("strict-transport-security", "max-age=31536000; includeSubDomains")
  }

  return answer
}

export const handle = sequence(authHandle, headers)
