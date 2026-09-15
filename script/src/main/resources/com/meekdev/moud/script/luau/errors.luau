local message = ...
local rawPcall = pcall
local rawXpcall = xpcall

local function settle(ok, ...)
    if ok then return true, ... end
    return false, message((...))
end

pcall = function(fn, ...)
    return settle(rawPcall(fn, ...))
end

xpcall = function(fn, handler, ...)
    return rawXpcall(fn, function(err) return handler(message(err)) end, ...)
end
