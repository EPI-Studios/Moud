local animation = world:add("Animation", { name = "{clip}", animationId = "{res}" })
local track = body.animator:loadAnimation(animation)

track:getMarkerReachedSignal("{event}"):connect(function(payload)
{payload}end)

track:play()
