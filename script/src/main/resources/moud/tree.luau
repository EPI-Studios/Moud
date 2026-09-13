
local method = __moud_instance_method
__moud_instance_method = nil

method("waitForChild", function(self, name, timeout)
    local child = self:find(name)
    local waited = 0
    while child == nil do
        if timeout ~= nil and waited >= timeout then
            return nil
        end
        waited += task.wait(0) or 0
        child = self:find(name)
    end
    return child
end)

method("onChild", function(self, name, fn)
    local existing = self:find(name)
    if existing ~= nil then
        task.spawn(fn, existing)
    end
    return self.childAdded:connect(function(child)
        if child.name == name then
            fn(child)
        end
    end)
end)
