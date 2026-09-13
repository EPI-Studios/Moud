-- the delivery helpers a place hands to a channel's shouldDeliver
--
--   general.shouldDeliver = game.chat.within(30)
--   team.shouldDeliver = game.chat.all(game.chat.within(80), function(message, source) ... end)

local chat = game.chat

-- a message reaches players whose body is within range metres of where the sender stood. a message with
-- no sender, or a player with no body, is let through
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

-- every check has to pass
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

-- any one check is enough
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

-- how far the sender stood from this player, or nil when either has no body
function chat.distance(message, source)
    local from = message.position
    local body = source and source.body
    if from == nil or body == nil then
        return nil
    end
    return (body.position - from).magnitude
end

-- within range, and nothing solid between the two bodies: walls stop voices
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

-- only players whose body carries the tag hear it
function chat.sameTag(tag)
    return function(message, source)
        local body = source and source.body
        return body ~= nil and body:hasTag(tag)
    end
end
