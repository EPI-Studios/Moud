package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

// the body, ported rather than invented
//
// every number here is the one the game itself builds a player out of: the pivot each limb turns
// at, the box hung off that pivot, and how far the shell over it is pushed out. they are stated in
// the space that model is authored in -- sixteen units to the metre, y downward, x mirrored, the
// shoulder line at zero and the feet twenty four below -- and converted once, here, so the rest of
// the engine only ever sees metres with y up
//
// getting these approximately right is what makes a body look like a bad copy. they are exact
public final class Rig {

    public static final String HITBOX = "hitbox";

    // the second shell every part wears: hat, jacket, sleeves and trousers
    public static final String OVERLAY = "overlay";

    private static final double PX = 1.0 / 16.0;

    // the model is authored from the shoulder down; the feet are twenty four units below it
    private static final double STANDING = 24.0;

    private static final PropertyDef SIZE = Classes.PART.property("size");
    private static final PropertyDef CFRAME = Classes.PART.property("cframe");
    private static final PropertyDef PIVOT = Classes.PART.property("pivot");
    private static final PropertyDef VISIBLE = Classes.PART.property("visible");

    // a limb: where it turns, the box hung off that, and how far its shell stands proud
    private record Limb(String name, Vec3 pivot, Vec3 box, Vec3 size, double shell) {}

    private static final Limb[] BODY = {
            limb("head", 0, 0, 0, -4, -8, -4, 8, 8, 8, 0.5),
            limb("torso", 0, 0, 0, -4, 0, -2, 8, 12, 4, 0.25),
            limb("rightArm", -5, 2, 0, -3, -2, -2, 4, 12, 4, 0.25),
            limb("leftArm", 5, 2, 0, -1, -2, -2, 4, 12, 4, 0.25),
            limb("rightLeg", -1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25),
            limb("leftLeg", 1.9, 12, 0, -2, 0, -2, 4, 12, 4, 0.25),
    };

    private Rig() {}

    // model space is mirrored on x and runs downward on y, which is why every conversion is here
    // and nowhere else
    private static Limb limb(String name, double px, double py, double pz,
                             double bx, double by, double bz, double w, double h, double d,
                             double shell) {
        Vec3 pivot = new Vec3(-px * PX, (STANDING - py) * PX, pz * PX);
        // the middle of the box, measured from the pivot it hangs on
        Vec3 centre = new Vec3(-(bx + w * 0.5) * PX, -(by + h * 0.5) * PX, (bz + d * 0.5) * PX);
        return new Limb(name, pivot, centre, new Vec3(w * PX, h * PX, d * PX), shell * PX);
    }

    public static void build(Character character) {
        // a character builds its own body once. asking twice is a caller that did not know it
        // was already done, not a request for a second head
        if (character.child(HITBOX) != null) return;
        for (Limb limb : BODY) {
            Instances.create(Classes.PART, character, limb.name(), part -> {
                part.size = limb.size();
                part.cframe = CFrame.at(limb.pivot());
                // our pivot points from the middle of the box back to where it turns
                part.pivot = limb.box().neg();
                part.color = Color.WHITE;
                part.collides = false;
                part.anchored = true;
            });
            shell(character, limb);
        }
        Instances.create(Classes.PART, character, HITBOX, part -> {
            part.size = Vec3.ONE;
            part.color = Color.WHITE;
            part.collides = false;
            part.anchored = true;
        });
        apply(character);
    }

    // sizes are recomputed rather than scaled in place, so a place that writes scale twice gets
    // the same body both times instead of one that grew twice
    public static void apply(Character character) {
        double s = character.scale;
        boolean shown = character.display == CharacterDisplay.MODEL;

        for (Limb limb : BODY) {
            if (!(character.child(limb.name()) instanceof Part part)) continue;
            Instances.setObj(part, SIZE, limb.size().mul(s));
            Instances.setObj(part, CFRAME, part.cframe.withPosition(limb.pivot().mul(s)));
            Instances.setObj(part, PIVOT, limb.box().neg().mul(s));
            Instances.setBool(part, VISIBLE, shown);

            if (part.child(OVERLAY) instanceof Part over) {
                Vec3 grown = new Vec3(limb.shell() * 2, limb.shell() * 2, limb.shell() * 2);
                Instances.setObj(over, SIZE, limb.size().add(grown).mul(s));
                Instances.setBool(over, VISIBLE, shown);
            }
        }

        if (character.child(HITBOX) instanceof Part box) {
            Instances.setObj(box, SIZE,
                    new Vec3(character.radius * 2, character.height, character.radius * 2));
            Instances.setObj(box, CFRAME, CFrame.at(0, character.height * 0.5, 0));
            Instances.setBool(box, VISIBLE, character.display == CharacterDisplay.HITBOX);
        }
    }

    // the shell sits on the part, not on the character: it inherits the swing, the pivot and the
    // scale of whatever it covers
    private static void shell(Character character, Limb limb) {
        if (!(character.child(limb.name()) instanceof Part part)) return;
        Instances.create(Classes.PART, part, OVERLAY, over -> {
            over.size = part.size;
            over.color = Color.WHITE;
            over.collides = false;
            over.anchored = true;
        });
    }
}
