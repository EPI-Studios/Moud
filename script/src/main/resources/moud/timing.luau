-- waiting, repeating and holding back, on top of task and signals

local signalMethod = __moud_signal_method
__moud_signal_method = nil

-- runs fn the first time the signal fires, then lets go
signalMethod("once", function(self, fn)
    local connection
    connection = self:connect(function(...)
        connection:disconnect()
        fn(...)
    end)
    return connection
end)

-- yields until the signal fires and hands back what it fired with. nil after timeout seconds, when given
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

-- runs fn on every nth firing, like every tenth step
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

-- a repeating timer: fn every seconds until the handle is cancelled
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

-- fn once, after seconds, unless cancelled first
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

-- calls fn only once things have been quiet for seconds: each call pushes it back
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

-- calls fn at most once every seconds, dropping the calls in between
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

-- cooldowns kept per key and per name: if cooldown:ready(body, "dash", 2) then ... end
cooldown = { _until = {} }

-- whether it is ready, and if it is, starts the cooldown
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

-- how long until it is ready, zero when it is
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
