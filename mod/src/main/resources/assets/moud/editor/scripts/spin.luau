local part = script.parent :: Part
local degreesPerSecond = 90

game.stepped:connect(function(dt)
    part.cframe = part.cframe * cframe.angles(0, math.rad(degreesPerSecond * dt), 0)
end)
