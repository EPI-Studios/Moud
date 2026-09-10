package com.meekdev.moud.mod.server;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.script.api.PlayerRef;
import net.minecraft.server.level.ServerPlayer;

// what a script gets when someone joins. the entity stops here
final class JoinedPlayer implements PlayerRef {

    private final ServerPlayer player;

    JoinedPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public String name() {
        return player.getGameProfile().name();
    }

    @Override
    public Instance character() {
        return Physics.bodies().of(player, ServerScene.tree());
    }

    // the entity is moved, not the instance: the character follows the player every tick and a
    // write to the instance alone would be overwritten by the next one
    @Override
    public void spawn(Vec3 position) {
        player.teleportTo(position.x(), position.y(), position.z());
        Character character = Physics.bodies().of(player, ServerScene.tree());
        if (character != null) Characters.place(character, position, player.getYRot());
    }
}
