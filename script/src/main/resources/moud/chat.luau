
local chat = game.chat

function chat.within(range)
    local limit = range * range
    return function(message, source)
        local from = message.position
        local body = source and source.body
        if from == nil or body == nil then
            return true
        end
        local to = body.position
        local dx, dy, dz = to.x - from.x, to.y - from.y, to.z - from.z
        return dx * dx + dy * dy + dz * dz <= limit
    end
end

function chat.all(...)
    local checks = { ... }
    return function(message, source)
        for _, check in checks do
            if check(message, source) == false then
                return false
            end
        end
        return true
    end
end

function chat.any(...)
    local checks = { ... }
    return function(message, source)
        for _, check in checks do
            if check(message, source) ~= false then
                return true
            end
        end
        return false
    end
end

function chat.distance(message, source)
    local from = message.position
    local body = source and source.body
    if from == nil or body == nil then
        return nil
    end
    return (body.position - from).magnitude
end

function chat.audible(range)
    local near = chat.within(range)
    return function(message, source)
        if not near(message, source) then
            return false
        end
        local from = message.body
        local to = source and source.body
        if from == nil or to == nil then
            return true
        end
        return from:canSee(to)
    end
end

function chat.sameTag(tag)
    return function(message, source)
        local body = source and source.body
        return body ~= nil and body:hasTag(tag)
    end
end
