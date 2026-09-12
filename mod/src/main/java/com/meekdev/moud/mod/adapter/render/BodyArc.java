package com.meekdev.moud.mod.adapter.render;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jspecify.annotations.Nullable;

// how far to move a body from where interpolating it puts it
//
// a rider on something that turns travels an arc. a tick gives two points on that arc and
// interpolating between them cuts the corner -- zero error at both ends of the tick and worst in the
// middle, appearing and vanishing twenty times a second. bkun corrects a rider's camera this way
// already, so the camera travelled the arc while the body travelled the chord: in first person the
// camera is what everything else is judged against, so it is the body that appears to shake
//
// it lives here rather than in either batch because a body is drawn by both of them: the six limbs
// and everything worn go through the sheet, and a sword in a hand or a marker on a head is an
// ordinary part. they have to move together or the sword leaves the hand
public final class BodyArc {

    private static final Map<Character, Vec3> BY_BODY = new HashMap<>();

    // the frame the cache belongs to. two batches ask within one frame and neither knows about the
    // other, so the answer is worked out once and handed to whoever asks second
    private static float within = Float.NaN;

    private BodyArc() {}

    public static Vec3 of(@Nullable Instance part, float partialTick) {
        if (partialTick != within) {
            BY_BODY.clear();
            within = partialTick;
        }
        Character body = bodyAbove(part);
        if (body == null) return Vec3.ZERO;
        Vec3 known = BY_BODY.get(body);
        if (known != null) return known;

        // the tracking is on the entity, so a body nobody is wearing is left alone: a place's own
        // character standing on a deck is carried by the same physics but has no entity to ask
        AbstractClientPlayer wearer = Skins.wearerOf(body);
        Vec3 arc = ClientPhysics.deckArc(wearer,
                ClientScene.motion().sample(body, 0).position(),
                ClientScene.motion().sample(body, 1).position(),
                partialTick);
        BY_BODY.put(body, arc);
        return arc;
    }

    // the body a part belongs to, however deep it hangs. a plate of armour is two levels down, and
    // a torch a place hung on a grip is three
    private static @Nullable Character bodyAbove(@Nullable Instance part) {
        for (Instance up = part; up != null; up = up.parent()) {
            if (up instanceof Character body) return body;
        }
        return null;
    }

    public static void forget() {
        BY_BODY.clear();
        within = Float.NaN;
    }
}
