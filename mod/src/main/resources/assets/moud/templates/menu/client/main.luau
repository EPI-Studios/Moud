local world = game.world

local function button(parent: Instance, text: string, y: number): TextButton
    local made = parent:add("TextButton", {
        position = udim2(0.5, 0, 0, y),
        anchorX = 0.5,
        size = udim2.fromOffset(200, 34),
        text = text,
        textSize = 18,
        textColor = color(1, 1, 1),
        backgroundColor = color(0.16, 0.19, 0.25),
        cornerRadius = 8,
        borderSize = 1,
        borderColor = color(0.35, 0.4, 0.5),
    }) :: TextButton
    made.mouseEnter:connect(function()
        made:tween({ size = udim2.fromOffset(214, 36) }, { time = 0.12, easing = "back", direction = "out" })
    end)
    made.mouseLeave:connect(function()
        made:tween({ size = udim2.fromOffset(200, 34) }, { time = 0.12 })
    end)
    return made
end

local function title(parent: Instance, text: string, y: number)
    parent:add("TextLabel", {
        position = udim2(0, 0, 0, y),
        size = udim2(1, 0, 0, 36),
        backgroundTransparency = 1,
        text = text,
        textSize = 27,
        textColor = color(1, 1, 1),
    })
end

-- the main menu
local menuGui = world:add("ScreenGui", { name = "MainMenu", displayOrder = 10 }) :: ScreenGui
local panel = menuGui:add("Frame", {
    position = udim2(0, 40, 0.5, 0),
    anchorY = 0.5,
    size = udim2.fromOffset(260, 230),
    backgroundColor = color(0.06, 0.07, 0.1),
    backgroundTransparency = 0.25,
    cornerRadius = 12,
}) :: Frame
title(panel, "My Game", 20)
local play = button(panel, "Play", 80)
local menuSettings = button(panel, "Settings", 124)
local menuQuit = button(panel, "Quit", 168)

-- the pause menu, opened with Escape once playing
local pauseGui = world:add("ScreenGui", { name = "PauseMenu", displayOrder = 20, enabled = false }) :: ScreenGui
pauseGui:add("Frame", { size = udim2.fromScale(1, 1), backgroundColor = color(0, 0, 0), backgroundTransparency = 0.45 })
local card = pauseGui:add("Frame", {
    position = udim2(0.5, 0, 0.5, 0),
    anchorX = 0.5, anchorY = 0.5,
    size = udim2.fromOffset(260, 210),
    backgroundColor = color(0.06, 0.07, 0.1),
    cornerRadius = 12,
}) :: Frame
title(card, "Paused", 18)
local resume = button(card, "Resume", 64)
local pauseSettings = button(card, "Settings", 108)
local pauseQuit = button(card, "Quit", 152)

local playing = false

local function setPaused(on: boolean)
    pauseGui.enabled = on
    game.paused = on
    if on then input:releaseMouse() else input:lockMouse() end
end

task.spawn(function()
    local orbit = world:waitForChild("MenuOrbit") :: CameraPath
    local remote = world:waitForChild("Menu") :: Remote
    local flyover = camera:play(orbit)
    input:releaseMouse()

    play.activated:connect(function()
        if playing then return end
        playing = true
        flyover:stop()
        menuGui.enabled = false
        input:lockMouse()
        remote:fireServer("play")
    end)
end)

menuSettings.activated:connect(function() game:openSettings() end)
menuQuit.activated:connect(function() game:quit() end)

game.pauseRequested:connect(function(reason)
    if not playing then return end
    if reason == "focusLost" and pauseGui.enabled then return end
    setPaused(not pauseGui.enabled)
end)
resume.activated:connect(function() setPaused(false) end)
pauseSettings.activated:connect(function() game:openSettings() end)
pauseQuit.activated:connect(function() game:quit() end)
