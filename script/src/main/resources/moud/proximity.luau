-- watching two things come near each other and split up, and how loud something is from far away

local proximity = game.proximity

local function newSignal()
    local handlers = {}
    local signal = {}
    function signal:connect(fn)
        handlers[fn] = true
        return {
            disconnect = function()
                handlers[fn] = nil
            end,
        }
    end
    function signal:fire(...)
        for fn in handlers do
            task.spawn(fn, ...)
        end
    end
    return signal
end

-- a watcher with entered and left, checked every step while it lives. stop() ends it
function proximity:watch(a, b, range)
    local watcher = { entered = newSignal(), left = newSignal(), near = false }
    local limit = range * range
    local function check()
        if a.parent == nil or b.parent == nil then
            return
        end
        local d = a.position - b.position
        local near = d.x * d.x + d.y * d.y + d.z * d.z <= limit
        if near ~= watcher.near then
            watcher.near = near
            if near then
                watcher.entered:fire(a, b)
            else
                watcher.left:fire(a, b)
            end
        end
    end
    local connections = {}
    table.insert(connections, game.stepped:connect(check))
    table.insert(connections, game.renderStepped:connect(check))
    function watcher:stop()
        for _, connection in connections do
            connection:disconnect()
        end
    end
    return watcher
end

-- a volume from 0 to 1 at a distance: full inside min, silent past max, falling off in between. rolloff
-- above 1 drops faster near the start
function proximity.falloff(distance, min, max, rolloff)
    if distance <= min then
        return 1
    end
    if distance >= max then
        return 0
    end
    local t = (distance - min) / (max - min)
    return (1 - t) ^ (rolloff or 1)
end
