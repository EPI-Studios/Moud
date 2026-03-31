local script = {}

function script:_ready(api)
    api.log("ready: {{name}}")
end

function script:_process(api, dt)
end

function script:_physics_process(api, dt)
end

return script
