local world = game.world

-- players wait in the main menu without a body until they press Play
game.players.autoSpawn = false
game.players.respawnTime = 3

local menu = world:waitForChild("Menu") :: Remote

menu.onServerPlayer:connect(function(player, choice)
    if choice == "play" and player.character == nil then
        player:spawn()
    end
end)

game.players.spawned:connect(function(player)
    print(player.name, "is playing")
end)
