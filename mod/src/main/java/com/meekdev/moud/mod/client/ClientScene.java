package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Limb;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.instance.Animators;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.instance.Stages;
import com.meekdev.moud.core.instance.Touches;
import com.meekdev.moud.core.instance.Zones;
import com.meekdev.moud.core.instance.Pose;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.render.PartLight;
import net.minecraft.client.Minecraft;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.transport.Post;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.UUID;
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
        // and whatever the server said, after the tree it talks about has caught up. who this client
        // is never has to be said: the connection a delivery came down is the identity
        Post.drainToClient(tree());
        InstanceTree tree = tree();
        if (tree == null) return;
        // the body is posed here, on the client, from the handful of numbers the server sent.
        // a place that wants the limbs to itself turns animate off and writes them instead
        // before the pose, which places a joint at the shape the rig has just settled on
        Rig.follow(tree);
        LocalPlayer me = Minecraft.getInstance().player;
        Character own = own();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            // a body somebody is wearing is driven here, off the player wearing it, and the
            // server sends none of it
            //
            // §4.3: the character *is* the player entity. so the game is already sending that
            // entity's position, rotation, crouch, swing, held item and walk to every client that
            // can see it, and every number drive works out comes from exactly those -- ours would
            // be the same facts a second time, down a channel whose ordering is the wrong promise
            // for a stream of samples. and for your own body it would be worse than redundant: the
            // mirror is the server's answer to a move you made several ticks ago, and a body that
            // walks after you do is not the same body
            //
            // what the body looks like comes from here too. the server has no view of a player's
            // sheet, or of the lagging position a cape reads
            AbstractClientPlayer wearer = Skins.wearerOf(character);
            Player driver = character == own && me != null ? me : wearer;
            if (driver != null) Characters.drive(character, driver);
            if (wearer != null) {
                Characters.fit(character, wearer);
                Characters.dress(character, wearer, 1.0f);
            }
            if (character.animate) {
                Pose.apply(character, age(character));
            } else {
                // the engine's own walk is off, but a track a place is playing is not the
                // engine's walk. animate says who owns the floor, not who may speak
                Animators.apply(character);
            }
        }
        // last, and the only writer of a limb's frame: the rig has settled where the joints are
        // and the pose has settled the turn at them
        Stages.run(tree, Stage.COMPOSE, 0);
        Touches.step(tree);
        Zones.step(tree, Addons.classes(), System.nanoTime() / 1e9);
        ZoneSounds.tick(tree);
        MOTION.drain(tree);
        // the light a part stands in, once a tick. reading it per frame would be a chunk lookup per
        // part per frame for a value that changes when someone places a torch
        for (Part part : tree.ofClass(Classes.PART)) {
            // a limb takes the light of the body it belongs to, once, below. sampling each of the
            // thirty boxes a body is made of would be thirty chunk lookups for one answer, and it
            // would be the wrong answer: a body straddling the edge of a torch's reach would have
            // one arm lit and the other dark, which the game never does
            if (part instanceof Limb) continue;
            PartLight.refresh(part, MOTION.sample(part).position());
        }
        // one sample for a whole body, at the body's own position, the way the game takes one for a
        // whole entity
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            PartLight.refresh(instance, MOTION.sample(instance).position());
        }
    }

    // the body this client drives. a place's own characters carry an owner nobody can look up,
    // which is exactly what tells them apart from a player's
    public static @Nullable Character own() {
        InstanceTree tree = tree();
        LocalPlayer me = Minecraft.getInstance().player;
        if (tree == null || me == null) return null;
        String uuid = me.getUUID().toString();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (instance instanceof Character character && uuid.equals(character.owner)) {
                return character;
            }
        }
        return null;
    }

    // the age the model animates the idle sway on, which is the body's own and not the world's:
    // vanilla reads it off the entity, so two players who joined at different times sway out of
    // phase. a character no entity backs falls back to the clock everything shares
    private static double age(Character character) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0;
        if (!character.owner.isEmpty()) {
            try {
                Player player = level.getPlayerByUUID(UUID.fromString(character.owner));
                if (player != null) return player.tickCount;
            } catch (IllegalArgumentException ignored) {
                // an owner that is not a uuid is a place's own character, and no entity backs it
            }
        }
        return level.getGameTime();
    }

    public static void frame() {
        ClientPhysics.attach(Minecraft.getInstance().level);
    }
}
