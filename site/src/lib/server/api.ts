import crypto from "node:crypto"
import { env } from "$env/dynamic/private"

export type Claim = { ok: true; uuid: string; name: string }
export type Refusal = { ok: false; error: string }

async function ask(path: string, payload: unknown): Promise<Claim | Refusal> {
  const base = env.API_URL
  const secret = env.SITE_SECRET
  if (!base || !secret) return { ok: false, error: "account linking is not configured" }

  const body = JSON.stringify(payload)
  const at = Date.now().toString()
  const signature = crypto.createHmac("sha256", secret).update(`${at}.${body}`).digest("hex")

  try {
    const answer = await fetch(`${base}${path}`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-moud-at": at,
        "x-moud-signature": signature,
      },
      body,
      signal: AbortSignal.timeout(8000),
    })

    const result = (await answer.json().catch(() => ({}))) as {
      ok?: boolean
      uuid?: string
      name?: string
      error?: string
    }

    if (!answer.ok || !result.ok || !result.uuid || !result.name) {
      return { ok: false, error: result.error ?? `the api answered ${answer.status}` }
    }
    return { ok: true, uuid: result.uuid, name: result.name }
  } catch {
    return { ok: false, error: "the api did not answer" }
  }
}

export function peekCode(code: string) {
  return ask("/v1/peek", { code })
}

export function redeemCode(code: string) {
  return ask("/v1/redeem", { code })
}
