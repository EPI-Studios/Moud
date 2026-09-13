local get, set, update, keys, lease, release, owner =
    __moud_store_get, __moud_store_set, __moud_store_update, __moud_store_keys,
    __moud_store_lease, __moud_store_release, __moud_store_owner
__moud_store_get, __moud_store_set, __moud_store_update, __moud_store_keys = nil, nil, nil, nil
__moud_store_lease, __moud_store_release, __moud_store_owner = nil, nil, nil

local LEASE = 60
local RENEW = 20

local Store = {}
Store.__index = Store

function Store:get(key)
    return get(self.name, tostring(key))
end

function Store:set(key, value)
    set(self.name, tostring(key), value)
end

function Store:remove(key)
    set(self.name, tostring(key), nil)
end

function Store:update(key, change)
    assert(type(change) == "function", "update wants a function that is given the old value and returns the new one")
    return update(self.name, tostring(key), change)
end

function Store:keys(prefix, limit)
    return keys(self.name, prefix or "", limit or 1000)
end

local Session = {}
Session.__index = Session

function Session:save()
    if self.released then error("this session was released", 2) end
    set(self.store, self.key, self.data)
end

function Session:release()
    if self.released then return end
    self:save()
    self.released = true
    task.cancel(self.renewing)
    release(self.store, self.key, owner())
end

function Store:session(key)
    key = tostring(key)
    if not lease(self.name, key, owner(), LEASE) then
        error("another server holds " .. self.name .. "/" .. key .. ", which is the save it is still writing", 2)
    end
    local session = setmetatable({ store = self.name, key = key, data = get(self.name, key) or {} }, Session)
    session.renewing = task.spawn(function()
        while not session.released do
            task.wait(RENEW)
            if not session.released then lease(session.store, session.key, owner(), LEASE) end
        end
    end)
    return session
end

function store(name)
    assert(type(name) == "string" and name ~= "", "store wants a name")
    return setmetatable({ name = name }, Store)
end
