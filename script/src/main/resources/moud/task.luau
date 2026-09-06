local spawn, cancel = __moud_spawn, __moud_cancel
__moud_spawn, __moud_cancel = nil, nil

task = {
    wait = function(seconds)
        return coroutine.yield(seconds or 0)
    end,

    spawn = function(fn)
        return spawn(fn)
    end,

    delay = function(seconds, fn)
        return spawn(function()
            coroutine.yield(seconds or 0)
            fn()
        end)
    end,

    cancel = function(handle)
        cancel(handle)
    end,
}
