import { json } from "@sveltejs/kit"
import { searchIndex } from "$lib/docs/pages"
import type { RequestHandler } from "./$types"

export const prerender = true

export const GET: RequestHandler = async () => {
  return json(searchIndex())
}
