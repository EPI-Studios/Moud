import { groups, guideTiles } from "$lib/docs/pages"
import { renderDoc } from "$lib/docs/render"
import type { PageServerLoad } from "./$types"

const SAMPLE = `\`\`\`lua
-- server/main.luau
local world = game.world

world:add("Part", {
    name = "floor",
    size = vec3(40, 1, 40),
    cframe = cframe(0, 64, 0),
    anchored = true,
})

game.players.joined:connect(function(player)
    player:spawn(vec3(0, 66, 0))
end)
\`\`\`
`

export const load: PageServerLoad = async () => {
  return { groups: groups(), tiles: guideTiles(), sample: renderDoc(SAMPLE).html }
}
