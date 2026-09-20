const SESSION = "https://sessionserver.mojang.com/session/minecraft/hasJoined"

export type Player = { uuid: string; name: string }

export function dashed(uuid: string) {
  const plain = uuid.replace(/-/g, "")
  return [
    plain.slice(0, 8),
    plain.slice(8, 12),
    plain.slice(12, 16),
    plain.slice(16, 20),
    plain.slice(20),
  ].join("-")
}

export async function hasJoined(username: string, serverId: string): Promise<Player | null> {
  const url = `${SESSION}?username=${encodeURIComponent(username)}&serverId=${encodeURIComponent(serverId)}`

  const answer = await fetch(url, {
    headers: { "user-agent": "MoudApi/1.0 (+https://moud.dev)" },
    signal: AbortSignal.timeout(6000),
  })

  if (answer.status === 204) return null
  if (!answer.ok) throw new Error(`session server answered ${answer.status}`)

  const profile = (await answer.json()) as { id?: string; name?: string }
  if (!profile.id || !profile.name) return null
  if (!/^[0-9a-f]{32}$/i.test(profile.id)) return null

  return { uuid: profile.id.toLowerCase(), name: profile.name }
}
