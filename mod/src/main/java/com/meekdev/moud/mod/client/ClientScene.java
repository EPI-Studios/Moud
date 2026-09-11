package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Pose;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.render.PartLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

// the client side of the tree: a mirror of the server's, plus the state only rendering needs
public final class ClientScene {

    private static final Motion MOTION = new Motion();

    private ClientScene() {}

    public static @Nullable InstanceTree tree() {
        return Mirror.applier().tree();
    }

    public static @Nullable Instance world() {
        return Mirror.applier().world();
    }

    public static Motion motion() {
        return MOTION;
    }

    // the mirror is drained on the tick, because that is the clock the changes are produced on.
    // draining it per frame meant two ticks could land in one frame and none in the next, and the
    // interpolation then measured the render loop instead of the stream
    public static void tick() {
        Mirror.apply(change -> ClientPhysics.apply(tree(), change));
        InstanceTree tree = tree();
        if (tree == null) return;
        // the body is posed here, on the client, from the handful of numbers the server sent.
        // a place that wants the limbs to itself turns animate off and writes them instead
        LocalPlayer me = Minecraft.getInstance().player;
        String uuid = me == null ? null : me.getUUID().toString();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            // yours is driven from your own player rather than from what the mirror carries. the
            // mirror is the server's answer to a move you made several ticks ago, and a body that
            // arrives late is the one thing you are guaranteed to be looking at
            if (uuid != null && uuid.equals(character.owner)) Characters.drive(character, me);
            if (character.animate) Pose.apply(character);
        }
        MOTION.drain(tree);
        // the light a part stands in, once a tick. reading it per frame would be a chunk lookup per
        // part per frame for a value that changes when someone places a torch
        for (Part part : tree.ofClass(Classes.PART)) {
            PartLight.refresh(part, MOTION.sample(part).position());
        }
    }

    public static void frame() {
        ClientPhysics.attach(Minecraft.getInstance().level);
    }
}
