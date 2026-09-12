package com.meekdev.moud.mod.client.editor;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

// how still things are standing, in millimetres of the last second
//
// a shake is the one bug a screenshot cannot show and a description cannot pin down: "it trembles"
// is true of a millimetre and of a hand's width. so the three things that can shake independently
// are measured apart -- where the body is, where the camera is, and where the camera is sideways --
// and the answer is a number
//
// they separate because they have separate owners. the body is the physics; the camera is the game
// in first person and ours in third. one of them moving while the other does not is the whole
// diagnosis
public final class Shake {

    // a second at sixty, which is long enough to catch a wobble at the tick rate
    private static final int SAMPLES = 60;

    private static final double[] BODY = new double[SAMPLES];
    private static final double[] CAM_Y = new double[SAMPLES];
    private static final double[] CAM_X = new double[SAMPLES];
    private static final double[] CAM_Z = new double[SAMPLES];

    // where the camera is aiming, because on a platform that turns, "shaking about the vertical" is
    // a different fault from "shaking up and down" and neither shows in the other's number
    private static final double[] CAM_YAW = new double[SAMPLES];

    private static int at;
    private static int filled;
    private static float bob;
    private static String riding = "no";

    private Shake() {}

    // read where everything is, once per overlay frame
    public static void sample() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer me = client.player;
        if (me == null) return;

        // the camera the frame was actually drawn through, not the one we asked for: the game
        // moves it after we have had our say, and the bob it adds is not in any pose we wrote
        Vec3 eye = client.gameRenderer.getMainCamera().position();
        CAM_X[at] = eye.x;
        CAM_Y[at] = eye.y;
        CAM_Z[at] = eye.z;
        // unwrapped against the last sample, so a turn through the wrap does not read as a jump of
        // three hundred and sixty degrees
        // the player's own yaw, sampled per frame, because that is the camera's yaw in first
        // person: the deck's turn is written into it once a frame and vanilla aims the camera at it
        double yaw = me.getYRot();
        int was = (at + SAMPLES - 1) % SAMPLES;
        CAM_YAW[at] = filled == 0 ? yaw : CAM_YAW[was] + wrap(yaw - CAM_YAW[was] % 360.0);

        Character body = Bodies.of(ClientScene.tree(), me.getUUID().toString());
        BODY[at] = body == null ? me.getY() : body.cframe.position().y();

        at = (at + 1) % SAMPLES;
        if (filled < SAMPLES) filled++;

        if (me instanceof AbstractClientPlayer avatar) {
            bob = avatar.avatarState().getInterpolatedBob(1.0f);
        }
        riding = me.onGround() ? "ground" : "air";
    }

    public static String body() {
        return millimetres(spread(BODY));
    }

    public static String camera() {
        return millimetres(spread(CAM_Y));
    }

    // a rotation carries a rider sideways, so a shake there is a different fault from a shake up
    public static String cameraFlat() {
        return millimetres(Math.max(spread(CAM_X), spread(CAM_Z)));
    }

    // what drives the view bob, which is the one wobble the game adds in first person and not in
    // third. a body standing still should read zero, and anything else means the game thinks it is
    // walking
    public static String bob() {
        return String.format("%.4f", bob);
    }

    public static String riding() {
        return riding;
    }

    // a shudder about the vertical, which is what a turning deck can hand a rider and what no
    // amount of measuring the camera's height will ever show
    public static String cameraYaw() {
        double spread = spread(CAM_YAW);
        // a rider on a deck turning at seventy degrees a second is meant to be turning: the shake is
        // whatever is left once that is taken out, so this reads the second difference rather than
        // the first
        return spread > 30 ? "turning" : String.format("%.3f deg", jitterOf(CAM_YAW));
    }

    // how far each step differs from the one before it. a steady turn has a constant step and reads
    // zero; a shudder has alternating steps and reads the size of the alternation
    private static double jitterOf(double[] window) {
        if (filled < 3) return 0;
        double worst = 0;
        for (int n = 2; n < filled; n++) {
            double second = (window[n] - window[n - 1]) - (window[n - 1] - window[n - 2]);
            worst = Math.max(worst, Math.abs(second));
        }
        return worst;
    }

    private static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    // the spread of a window rather than the last step: a sawtooth at the tick rate has tiny steps
    // between frames and a large swing across a tick, and only the swing is visible
    private static double spread(double[] window) {
        if (filled == 0) return 0;
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int n = 0; n < filled; n++) {
            low = Math.min(low, window[n]);
            high = Math.max(high, window[n]);
        }
        return high - low;
    }

    private static String millimetres(double blocks) {
        double mm = blocks * 1000.0;
        // a body walking moves metres, and the window would read that as a huge shake. the number
        // is only meaningful standing still, and saying so beats printing a misleading one
        if (mm > 500) return "moving";
        return String.format("%.1f mm", mm);
    }
}
