local world = game.world
local body = world:find("npc") :: Character
local animation = world:add("Animation", { name = "{clip}", animationId = "{res}" }) :: Animation
local track = body.animator:loadAnimation(animation)

track:getMarkerReachedSignal("{event}"):connect(function(payload: any)
{payload}end)

track:play()
