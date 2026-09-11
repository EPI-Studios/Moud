package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.place.Errors;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.script.err.ScriptError;
import com.meekdev.moud.script.vm.Vm;
import com.meekdev.bkun.physics.MovementProfile;
import com.meekdev.bkun.sublevel.SubLevelIndex;
import com.meekdev.moud.mod.level.PolarChunks;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.List;

// the numbers and the errors, which are otherwise only in a log file nobody reads mid session
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
        // what the two batches last emitted. a part the tree has but neither batch
        // drew is a renderer bug, not a place one
        row("drawn still", Parts.stillCount());
        row("drawn moving", Parts.movingCount());
        // a body wearing a skin leaves the flat batches and lands here, so a zero with a
        // character in the tree means the skin never resolved
        row("drawn skinned", Skins.count());
        row("dirty", tree == null ? 0 : tree.dirtyCount());

        ImGui.separator();
        ImGui.text("collision");
        row("boxes (server)", Physics.boxes().size());
        // the client set is the one the player actually collides against
        row("boxes (client)", ClientPhysics.boxes().size());
        row("sub levels (server)", Physics.shapes().size());
        // what the client can actually collide with: bkun finds sub levels through this index, and
        // an entity the client never received is not in it
        row("sub levels (client)", clientSubLevels());

        ImGui.separator();
        ImGui.text("terrain");
        row("chunks filled", PolarChunks.filled());
        ImGui.text("blocks written");
        ImGui.sameLine(160);
        ImGui.textDisabled(String.valueOf(PolarChunks.blocks()));
        // straight off the client's own level: if this is not stone, the client has no floor,
        // which is both why nothing draws there and why nothing stops the player
        ImGui.text("client block 0,60,0");
        ImGui.sameLine(160);
        ImGui.textDisabled(blockAtSpawn());

        ImGui.separator();
        ImGui.text("character");
        LocalPlayer player = Minecraft.getInstance().player;
        // bkun's Physics against our own adapter's, the one clash 20.1 allows for
        MovementProfile profile = player == null
                ? null
                : com.meekdev.bkun.physics.Physics.getProfile(player).orElse(null);
        ImGui.text("profile");
        ImGui.sameLine(160);
        ImGui.textDisabled(profile == null ? "none, vanilla is driving" : "bkun");
        if (player != null && profile != null) {
            // what the place asked for, back in the units it asked in
            text("walk m/s", String.format("%.2f", profile.maxGroundSpeed() * 20.0));
            text("capsule", String.format("r %.2f h %.2f",
                    profile.moverRadius(), profile.moverHeight()));
            text("at", String.format("%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
            text("on ground", String.valueOf(player.onGround()));
        }

        ImGui.separator();
        ImGui.text("body");
        // yours, not whichever came first: a place makes characters of its own and one posed by
        // hand never moves, so reading that one says the body is stuck when nothing is wrong
        Character mine = ClientScene.own();
        Instance arm = mine == null ? null : mine.child("rightArm");
        if (mine == null) {
            ImGui.textDisabled("no character of yours in the tree");
        } else {
            text("name", mine.name());
            // against the entity's at, above. the same pair of numbers separates a body the
            // server never moved from one the mirror never heard about
            text("body at", fmt(Transforms.world(mine).position()));
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
        Vm vm = place == null ? null : place.vm();
        row("sleeping tasks", vm == null ? 0 : vm.scheduler().sleepingCount());

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

    private static String fmt(Vec3 v) {
        return String.format("%.2f %.2f %.2f", v.x(), v.y(), v.z());
    }

    // the panel opens on the title screen too, where there is no level to ask
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
