import type { Component } from "svelte"

const modules = import.meta.glob<{ default: Component }>("./pages/*.svelte")

function pathFor(name: string) {
  return `./pages/${name}.svelte`
}

export function hasDemo(name: string) {
  return pathFor(name) in modules
}

export async function loadDemo(name: string) {
  const load = modules[pathFor(name)]
  if (!load) return null
  return (await load()).default
}
