package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.adapter.physics.Characters;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.level.PolarChunks;
import com.meekdev.moud.mod.place.Errors;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.err.ScriptError;
import imgui.ImGui;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class PlaceInspector extends Inspector {

    public PlaceInspector() {
        super("moud", "place", true);
    }

    @Override
    public void render() {
        InstanceTree tree = ClientScene.tree();
        Place place = MoudServer.place();

        ImGui.text("instances");
        row("parts", tree == null ? 0 : tree.ofClass(Classes.PART).size());
        row("moving", ClientScene.motion().moving().size());
        row("drawn still", Parts.stillCount());
        row("drawn moving", Parts.movingCount());
        row("drawn skinned", Skins.count());
        row("dirty", tree == null ? 0 : tree.dirtyCount());

        ImGui.separator();
        ImGui.text("steadiness");
        Shake.sample();
        text("body y", Shake.body());
        text("body vs cam", Shake.reach());
        text("camera y", Shake.camera());
        text("camera xz", Shake.cameraFlat());
        text("yaw held", Shake.hold());
        text("deck rate", Shake.deckRate());
        text("bob", Shake.bobText());
        text("riding", Shake.riding());

        ImGui.separator();
        ImGui.text("collision");
        row("boxes (server)", Physics.boxes().size());
        row("boxes (client)", ClientPhysics.boxes().size());
        row("sub levels (server)", Physics.shapes().size());
        row("sub levels (client)", clientSubLevels());

        ImGui.separator();
        ImGui.text("terrain");
        row("chunks filled", PolarChunks.filled());
        ImGui.text("blocks written");
        ImGui.sameLine(160);
        ImGui.textDisabled(String.valueOf(PolarChunks.blocks()));
        ImGui.text("client block 0,60,0");
        ImGui.sameLine(160);
        ImGui.textDisabled(blockAtSpawn());

        ImGui.separator();
        ImGui.text("character");
        LocalPlayer player = Minecraft.getInstance().player;
        MovementProfile profile = player == null
                ? null
                : Characters.currentProfile(player);
        ImGui.text("profile");
        ImGui.sameLine(160);
        ImGui.textDisabled(profile == null ? "none, vanilla is driving" : "bkun");
        if (player != null && profile != null) {
            text("walk m/s", String.format("%.2f", profile.maxGroundSpeed() * 20.0));
            text("capsule", String.format("r %.2f h %.2f",
                    profile.moverRadius(), profile.moverHeight()));
            text("at", String.format("%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
            text("on ground", String.valueOf(player.onGround()));
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            text("speed m/s", String.format("%.2f", Math.sqrt(dx * dx + dz * dz) * 20.0));
        }

        ImGui.separator();
        ImGui.text("body");
        Character mine = ClientScene.own();
        Instance arm = mine == null ? null : mine.child("rightArm");
        if (mine == null) {
            ImGui.textDisabled("no character of yours in the tree");
        } else {
            text("name", mine.name());
            text("body at", fmt(Transforms.world(mine).position()));
            text("wash", String.format("white %.2f  red %s",
                    mine.whiteFlash, mine.hurt));
        }
        if (arm != null) {
            Spatial limb = (Spatial) arm;
            text("arm local", fmt(limb.cframe.position()));
            text("arm pivot", fmt(limb.pivot));
            var r = limb.cframe.rotation();
            text("arm turn", String.format("%.2f %.2f %.2f %.2f", r.x(), r.y(), r.z(), r.w()));
            text("arm world", fmt(Transforms.world(limb).position()));
            text("arm visible", String.valueOf(limb.visible));
        }

        ImGui.separator();
        ImGui.text("script");
        ScriptEngine vm = place == null ? null : place.vm();
        row("sleeping tasks", vm == null ? 0 : vm.sleepingTasks());

        List<ScriptError> errors = Errors.recent();
        ImGui.separator();
        if (errors.isEmpty()) {
            ImGui.textDisabled("no errors");
            return;
        }
        ImGui.text(errors.size() + " recent errors");
        for (ScriptError error : errors) {
            ImGui.textWrapped(error.getMessage());
        }
        if (ImGui.button("clear")) Errors.clear();
    }

    private static String fmt(Vector3 v) {
        return String.format("%.2f %.2f %.2f", v.x(), v.y(), v.z());
    }

    private static int clientSubLevels() {
        Level level = clientLevel();
        return level == null ? 0 : SubLevelIndex.in(level).size();
    }

    private static Level clientLevel() {
        return Minecraft.getInstance().level;
    }

    private static String blockAtSpawn() {
        Level level = clientLevel();
        if (level == null) return "no level";
        return level.getBlockState(new BlockPos(0, 60, 0)).getBlock().toString();
    }

    private static void text(String name, String value) {
        ImGui.textDisabled(name);
        ImGui.sameLine(160);
        ImGui.text(value);
    }

    private static void row(String name, int value) {
        ImGui.textDisabled(name);
        ImGui.sameLine(140);
        ImGui.text(String.valueOf(value));
    }
}
