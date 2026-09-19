package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.core.service.StarterCharacterScripts;
import java.util.ArrayList;
import java.util.List;

public final class Tools {

    public static final String HANDLE = "Handle";
    private static final String RIGHT_ARM = "rightArm";
    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");
    private static final PropertyDef ANCHORED = Classes.PART.property("anchored");
    private static final double REACH = 2.5;

    private Tools() {}

    public static Backpack backpack(Character body) {
        if (body.child(Rig.BACKPACK) instanceof Backpack backpack) return backpack;
        return Instances.create(Classes.BACKPACK, body, Rig.BACKPACK);
    }

    public static Tool held(Character body) {
        for (Instance child : body.children()) {
            if (child instanceof Tool tool) return tool;
        }
        return null;
    }

    public static List<Tool> carried(Character body) {
        List<Tool> tools = new ArrayList<>();
        for (Instance child : backpack(body).children()) {
            if (child instanceof Tool tool) tools.add(tool);
        }
        return tools;
    }

    public static boolean canEquip(Tool tool) {
        return !tool.requiresHandle || tool.child(HANDLE) instanceof Part;
    }

    public static void equip(Character body, Tool tool) {
        Tool current = held(body);
        if (current == tool || !canEquip(tool)) return;
        if (current != null) unequip(body);
        Instances.reparent(tool, body);
        tool.equipped.fire(tool);
    }

    public static void unequip(Character body) {
        Tool current = held(body);
        if (current == null) return;
        Instances.reparent(current, backpack(body));
        current.unequipped.fire(current);
    }

    public static void activate(Tool tool, boolean down) {
        if (!tool.enabled || !(tool.parent() instanceof Character)) return;
        if (down) tool.activated.fire(tool);
        else tool.deactivated.fire(tool);
    }

    public static void hold(Character body, boolean write) {
        Tool tool = held(body);
        if (tool == null || !(tool.child(HANDLE) instanceof Part handle)) return;
        if (!(body.child(RIGHT_ARM) instanceof Instance arm) || !(arm.child(Rig.GRIP) instanceof Spatial grip)) return;
        CFrame target = Transforms.localFor(handle, Transforms.world(grip).mul(tool.grip.inverse()));
        if (write) {
            if (handle.anchored) Instances.setBool(handle, ANCHORED, false);
            Instances.setObj(handle, CFRAME, target);
        } else {
            handle.cframe = target;
        }
    }

    public static void stock(Character body, InstanceTree tree, ClassRegistry classes) {
        Backpack backpack = backpack(body);
        for (StarterPack pack : tree.ofClass(Classes.STARTER_PACK)) {
            List<Instance> tools = new ArrayList<>();
            for (Instance child : pack.children()) {
                if (child instanceof Tool) tools.add(child);
            }
            if (!tools.isEmpty()) Scene.paste(Scene.save(tools), backpack, classes);
        }
        for (StarterCharacterScripts scripts : tree.ofClass(Classes.STARTER_CHARACTER_SCRIPTS)) {
            List<Instance> copies = new ArrayList<>(scripts.children());
            if (!copies.isEmpty()) Scene.paste(Scene.save(copies), body, classes);
        }
    }

    public static Character pickedUpBy(Tool tool, List<Character> bodies) {
        if (tool.parent() instanceof Character || Instance.outOfWorld(tool)) return null;
        if (!(tool.child(HANDLE) instanceof Part handle)) return null;
        Vector3 at = Transforms.world(handle).position();
        for (Character body : bodies) {
            if (!body.hasPlayer() || !body.isAlive()) continue;
            if (Rig.humanoid(body) instanceof Humanoid living && living.state == HumanoidState.DEAD) continue;
            if (Transforms.world(body).position().add(new Vector3(0, body.height * 0.5, 0)).distance(at) <= REACH) return body;
        }
        return null;
    }
}
