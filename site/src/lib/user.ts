export type Identity = {
  handle: string
  minecraftId?: string | null
  minecraftName?: string | null
}

const VISAGE = "https://visage.surgeplay.com"
const STEVE = "8667ba71b85a4004af54457a9734eed7"

export function displayName(user: Identity) {
  return user.minecraftName ?? user.handle
}

function who(user: Identity) {
  return user.minecraftId ?? user.minecraftName ?? STEVE
}

export function bustUrl(user: Identity, size = 128) {
  return `${VISAGE}/bust/${size}/${encodeURIComponent(who(user))}.png`
}

export function faceUrl(user: Identity, size = 64) {
  return `${VISAGE}/face/${size}/${encodeURIComponent(who(user))}.png`
}

export function fullUrl(user: Identity, size = 256) {
  return `${VISAGE}/full/${size}/${encodeURIComponent(who(user))}.png`
}

export function isLinked(user: Identity) {
  return Boolean(user.minecraftId || user.minecraftName)
}
