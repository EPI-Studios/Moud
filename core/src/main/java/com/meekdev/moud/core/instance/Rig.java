package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

// the body every character is born with: six parts at the proportions a player standing next to a
// block has, each turning at the joint it really turns at, and a seventh part that is the
// collision volume itself
//
// the model is two metres and the capsule is 1.8, which is the same disagreement the game itself
// ships with. the hitbox display is what settles which of the two is true
public final class Rig {

    public static final String HITBOX = "hitbox";

    // the second shell every part wears: hat, jacket, sleeves and trousers. it is a child of the
    // part it covers, so it follows an arm that swings without anyone animating it twice
    public static final String OVERLAY = "overlay";

    // half a texel proud of the part underneath, which is what stops the two z fighting
    private static final double SHELL = 0.5 / 16.0;

    private static final double UNIT = 1.0 / 16.0;

    private static final Vec3 HEAD = new Vec3(8, 8, 8).mul(UNIT);
    private static final Vec3 TORSO = new Vec3(8, 12, 4).mul(UNIT);
    private static final Vec3 LIMB = new Vec3(4, 12, 4).mul(UNIT);

    private static final double HIP = 12 * UNIT;
    private static final double SHOULDER = HIP + TORSO.y();

    private static final PropertyDef SIZE = Classes.PART.property("size");
    private static final PropertyDef CFRAME = Classes.PART.property("cframe");
    private static final PropertyDef PIVOT = Classes.PART.property("pivot");
    private static final PropertyDef VISIBLE = Classes.PART.property("visible");

    private static final String[] BODY =
            {"torso", "head", "leftArm", "rightArm", "leftLeg", "rightLeg"};

    private Rig() {}

    public static void build(Character character) {
        part(character, "torso", TORSO, CFrame.at(0, HIP + TORSO.y() * 0.5, 0), Vec3.ZERO);
        part(character, "head", HEAD, CFrame.at(0, SHOULDER, 0), new Vec3(0, -HEAD.y() * 0.5, 0));

        limb(character, "leftArm", -(TORSO.x() + LIMB.x()) * 0.5, SHOULDER);
        limb(character, "rightArm", (TORSO.x() + LIMB.x()) * 0.5, SHOULDER);
        limb(character, "leftLeg", -LIMB.x() * 0.5, HIP);
        limb(character, "rightLeg", LIMB.x() * 0.5, HIP);

        part(character, HITBOX, Vec3.ONE, CFrame.IDENTITY, Vec3.ZERO);
        for (String name : BODY) shell(character, name);
        apply(character);
    }

    // the sizes are recomputed rather than scaled in place, so a place that writes scale twice
    // gets the same body both times instead of one that grew twice
    public static void apply(Character character) {
        double s = character.scale;
        size(character, "torso", TORSO.mul(s), CFrame.at(0, (HIP + TORSO.y() * 0.5) * s, 0),
                Vec3.ZERO);
        size(character, "head", HEAD.mul(s), CFrame.at(0, SHOULDER * s, 0),
                new Vec3(0, -HEAD.y() * 0.5 * s, 0));
        arm(character, "leftArm", -(TORSO.x() + LIMB.x()) * 0.5 * s, SHOULDER * s, s);
        arm(character, "rightArm", (TORSO.x() + LIMB.x()) * 0.5 * s, SHOULDER * s, s);
        arm(character, "leftLeg", -LIMB.x() * 0.5 * s, HIP * s, s);
        arm(character, "rightLeg", LIMB.x() * 0.5 * s, HIP * s, s);

        // the box the mover really sweeps, drawn where it really is
        size(character, HITBOX,
                new Vec3(character.radius * 2, character.height, character.radius * 2),
                CFrame.at(0, character.height * 0.5, 0), Vec3.ZERO);

        boolean body = character.display == CharacterDisplay.MODEL;
        for (String name : BODY) {
            visible(character, name, body);
            if (!(character.child(name) instanceof Part part)) continue;
            if (part.child(OVERLAY) instanceof Part over) {
                Instances.setBool(over, VISIBLE, body);
                Instances.setObj(over, SIZE, part.size.add(new Vec3(SHELL, SHELL, SHELL).mul(2)));
            }
        }
        visible(character, HITBOX, character.display == CharacterDisplay.HITBOX);
    }

    // the shell sits on the part, not on the character: it inherits the swing, the pivot and
    // the scale of whatever it covers
    private static void shell(Character character, String name) {
        if (!(character.child(name) instanceof Part part)) return;
        Instances.create(Classes.PART, part, OVERLAY, over -> {
            over.size = part.size;
            over.cframe = CFrame.IDENTITY;
            over.color = Color.WHITE;
            over.collides = false;
            over.anchored = true;
        });
    }

    private static void arm(Character character, String name, double x, double top, double s) {
        size(character, name, LIMB.mul(s), CFrame.at(x, top, 0), new Vec3(0, LIMB.y() * 0.5 * s, 0));
    }

    private static void limb(Character character, String name, double x, double top) {
        part(character, name, LIMB, CFrame.at(x, top, 0), new Vec3(0, LIMB.y() * 0.5, 0));
    }

    // every write goes through Instances like anything else: the rig is not allowed a private
    // path into the tree just because the engine built it
    private static void size(Character character, String name, Vec3 size, CFrame at, Vec3 pivot) {
        if (!(character.child(name) instanceof Part part)) return;
        Instances.setObj(part, SIZE, size);
        Instances.setObj(part, CFRAME, at);
        Instances.setObj(part, PIVOT, pivot);
    }

    private static void visible(Character character, String name, boolean shown) {
        if (character.child(name) instanceof Part part) Instances.setBool(part, VISIBLE, shown);
    }

    private static void part(Character character, String name, Vec3 size, CFrame at, Vec3 pivot) {
        Instances.create(Classes.PART, character, name, part -> {
            part.size = size;
            part.cframe = at;
            part.pivot = pivot;
            part.color = Color.WHITE;
            // the body is drawn, never collided with: one capsule moves and the parts are only
            // ever asked what was hit
            part.collides = false;
            part.anchored = true;
        });
    }
}
