import { error } from "@sveltejs/kit"
import { doc, groups, guideTiles } from "$lib/docs/pages"
import type { PageServerLoad } from "./$types"

export const load: PageServerLoad = async ({ params }) => {
  const page = doc(params.slug ?? "")
  if (!page) error(404, "No such page")

  return { ...page, groups: groups(), tiles: guideTiles() }
}
