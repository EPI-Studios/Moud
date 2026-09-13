
local signalMethod = __moud_signal_method
__moud_signal_method = nil

signalMethod("once", function(self, fn)
    local connection
    connection = self:connect(function(...)
        connection:disconnect()
        fn(...)
    end)
    return connection
end)

signalMethod("wait", function(self, timeout)
    local fired, args = false, nil
    local connection = self:connect(function(...)
        fired = true
        args = table.pack(...)
    end)
    local waited = 0
    while not fired do
        if timeout ~= nil and waited >= timeout then
            connection:disconnect()
            return nil
        end
        waited += task.wait(0) or 0
    end
    connection:disconnect()
    return table.unpack(args, 1, args.n)
end)

signalMethod("every", function(self, n, fn)
    local count = 0
    return self:connect(function(...)
        count += 1
        if count >= n then
            count = 0
            fn(...)
        end
    end)
end)

function task.every(seconds, fn)
    local handle = { cancelled = false }
    function handle:cancel()
        self.cancelled = true
    end
    task.spawn(function()
        while not handle.cancelled do
            task.wait(seconds)
            if handle.cancelled then
                break
            end
            fn()
        end
    end)
    return handle
end

function task.after(seconds, fn)
    local handle = { cancelled = false }
    function handle:cancel()
        self.cancelled = true
    end
    task.spawn(function()
        task.wait(seconds)
        if not handle.cancelled then
            fn()
        end
    end)
    return handle
end

function task.debounce(fn, seconds)
    local generation = 0
    return function(...)
        generation += 1
        local mine = generation
        local args = table.pack(...)
        task.spawn(function()
            task.wait(seconds)
            if mine == generation then
                fn(table.unpack(args, 1, args.n))
            end
        end)
    end
end

function task.throttle(fn, seconds)
    local last = -math.huge
    return function(...)
        local now = clock()
        if now - last < seconds then
            return
        end
        last = now
        return fn(...)
    end
end

cooldown = { _until = {} }

function cooldown:ready(key, name, seconds)
    local id = tostring(key) .. "\0" .. name
    local now = clock()
    local ready = self._until[id]
    if ready ~= nil and now < ready then
        return false
    end
    self._until[id] = now + seconds
    return true
end

function cooldown:remaining(key, name)
    local ready = self._until[tostring(key) .. "\0" .. name]
    if ready == nil then
        return 0
    end
    return math.max(0, ready - clock())
end

function cooldown:reset(key, name)
    self._until[tostring(key) .. "\0" .. name] = nil
end
