local part = script.parent :: Part

part.touched:connect(function(other)
    local body = other:firstAncestorOfClass("Character")
    local humanoid = body and body:find("humanoid") :: Humanoid?
    if humanoid then
        humanoid.health = 0
    end
end)
