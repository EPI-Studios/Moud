package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.character.Animators;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Limb;
import com.meekdev.moud.core.character.Pose;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Stage;
import com.meekdev.moud.core.instance.Stages;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Touches;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.core.zone.Zones;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.physics.PlayerMirror;
import com.meekdev.moud.mod.adapter.render.PartLight;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.zone.ZoneSounds;
import com.meekdev.moud.mod.transport.Post;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class ClientScene {

    private static final double TICK_SECONDS = 0.05;

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

    public static void tick() {
        Mirror.apply(change -> ClientPhysics.apply(tree(), change));
        ClientPhysics.settle();
        Post.drainToClient(tree());
        InstanceTree tree = tree();
        if (tree == null) return;
        Rig.follow(tree);
        if (!EditMode.editing()) Animators.step(tree, TICK_SECONDS, false);
        LocalPlayer me = Minecraft.getInstance().player;
        Character own = own();
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            if (!(instance instanceof Character character)) continue;
            AbstractClientPlayer wearer = Skins.wearerOf(character);
            Player driver = character == own && me != null ? me : wearer;
            if (driver != null) Characters.drive(character, driver);
            if (wearer != null) {
                PlayerMirror.skinModel(character, wearer);
                PlayerMirror.cape(character, wearer, 1.0f);
            }
            if (character.animate) {
                Pose.apply(character, age(character));
            } else {
                Animators.apply(character);
            }
        }
        Stages.run(tree, Stage.COMPOSE, 0);
        Touches.step(tree);
        Zones.step(tree, Addons.classes(), System.nanoTime() / 1e9);
        ZoneSounds.tick(tree);
        MOTION.drain(tree);
        for (Part part : tree.ofClass(Classes.PART)) {
            if (part instanceof Limb || ViewportFrame.inside(part)) continue;
            PartLight.refresh(part, MOTION.sample(part).position());
        }
        for (Instance instance : tree.ofClass(Classes.CHARACTER)) {
            PartLight.refresh(instance, MOTION.sample(instance).position());
        }
    }

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

    private static double age(Character character) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return 0;
        if (!character.owner.isEmpty()) {
            try {
                Player player = level.getPlayerByUUID(UUID.fromString(character.owner));
                if (player != null) return player.tickCount;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return level.getGameTime();
    }

    public static void frame() {
        ClientPhysics.attach(Minecraft.getInstance().level);
    }
}
