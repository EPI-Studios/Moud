package com.meekdev.moud.mod.client.editor;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.adapter.render.BodyArc;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
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

    // how far the body is drawn from the camera
    //
    // the one number that shows the fault the others could not: BODY below is the body's live tick
    // position, and a wobble that lives entirely inside a tick's interpolation can never appear in
    // it. this is the interpolated head, the one actually drawn, measured against the camera -- so a
    // body that will not sit still in front of your eyes reads here and nowhere else
    private static final double[] REACH = new double[SAMPLES];
    private static final double[] CAM_Y = new double[SAMPLES];
    private static final double[] CAM_X = new double[SAMPLES];
    private static final double[] CAM_Z = new double[SAMPLES];

    // where the camera is aiming, because on a platform that turns, "shaking about the vertical" is
    // a different fault from "shaking up and down" and neither shows in the other's number
    private static final double[] CAM_YAW = new double[SAMPLES];

    // and the deck's own heading, which is what the rider's yaw is turned from. a shudder here is
    // the platform arriving unevenly, and no amount of fixing the turn would help
    private static final double[] DECK_YAW = new double[SAMPLES];

    // the difference between the two, which is frame rate independent and is the whole diagnosis
    private static final double[] HOLD = new double[SAMPLES];

    // when each sample was taken, because an overlay frame is not a fixed interval and every rate
    // below is per second rather than per sample
    private static final double[] TIME = new double[SAMPLES];

    private static int at;
    private static int filled;
    private static boolean seeded;
    private static double camWas;
    private static double deckWas;
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
        // the player's own yaw, because that is what aims the camera in first person, and the deck's
        // own heading beside it
        //
        // each unwrapped against the previous raw reading -- the only thing either can be unwrapped
        // against. it used to be unwrapped against a modulo of the running total, which is both a
        // precedence mistake and wrong in principle once the total passes a full turn
        int was = (at + SAMPLES - 1) % SAMPLES;
        double rawCam = me.getYRot();
        double rawDeck = ClientPhysics.riddenYaw();
        boolean riddenNow = !Double.isNaN(rawDeck);

        CAM_YAW[at] = seeded ? CAM_YAW[was] + wrap(rawCam - camWas) : rawCam;
        DECK_YAW[at] = seeded && riddenNow
                ? DECK_YAW[was] + wrap(rawDeck - deckWas)
                : riddenNow ? rawDeck : 0;
        // the rider's heading in the deck's frame: a constant whenever the turn is faithful
        HOLD[at] = riddenNow ? CAM_YAW[at] - DECK_YAW[at] : seeded ? HOLD[was] : 0;
        camWas = rawCam;
        if (riddenNow) deckWas = rawDeck;

        Character body = Bodies.of(ClientScene.tree(), me.getUUID().toString());
        BODY[at] = body == null ? me.getY() : body.cframe.position().y();

        float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (body != null && body.child("head") instanceof Instance head) {
            com.meekdev.moud.core.math.Vec3 drawn =
                    ClientScene.motion().sample(head, pt).position().add(BodyArc.of(head, pt));
            REACH[at] = Math.sqrt(
                    Math.pow(drawn.x() - eye.x, 2)
                            + Math.pow(drawn.y() - eye.y, 2)
                            + Math.pow(drawn.z() - eye.z, 2));
        } else if (filled > 0) {
            REACH[at] = REACH[was];
        }

        TIME[at] = System.nanoTime() / 1.0e9;
        at = (at + 1) % SAMPLES;
        if (filled < SAMPLES) filled++;
        // seeded only while it is on a deck, so stepping off and back on starts a fresh window
        // rather than folding a jump across the gap into the reading
        seeded = riddenNow;

        if (me instanceof AbstractClientPlayer avatar) {
            bob = avatar.avatarState().getInterpolatedBob(1.0f);
        }
        riding = riddenNow ? "deck" : me.onGround() ? "ground" : "air";
    }

    public static String body() {
        return millimetres(spread(BODY));
    }

    // the body as drawn, against the camera. standing still this is a constant whatever the ground
    // is doing, so its spread is how far the body moves in front of your eyes
    public static String reach() {
        return millimetres(spread(REACH));
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

    // how far the rider is turned away from where the deck would put it
    //
    // this is the reading that means something. the difference between the two headings is a constant
    // whenever the turn is faithful -- it does not care how fast the frames come, how unevenly the
    // deck's pose arrives, or how fast the deck is going round -- so its spread is the fault itself,
    // in degrees
    //
    // a second difference of the heading was measured here first, and that was a mistake: samples are
    // taken once per overlay frame at whatever interval the frame rate gives, so a perfectly steady
    // turn read as tens of degrees of shudder. it was measuring the frame pacing
    public static String hold() {
        if (!seeded) return "not on a deck";
        return String.format("%.3f deg", spread(HOLD));
    }

    // how fast the deck is turning, and how steady that rate is
    //
    // in degrees a second, off the wall clock, so it is the same number at any frame rate. a deck
    // driven evenly reads its own turn rate with a small spread; one whose pose arrives in bursts
    // reads the same average with a large one, and that is the platform's fault rather than the
    // rider's
    public static String deckRate() {
        if (!seeded || filled < 5) return "not on a deck";
        return String.format("%.0f deg/s, spread %.0f", rate(DECK_YAW), rateSpread(DECK_YAW));
    }

    private static double rate(double[] window) {
        double seconds = elapsed();
        if (seconds <= 0) return 0;
        return (window[newest()] - window[oldest()]) / seconds;
    }

    // the fastest and the slowest a few samples ever ran, which is what bursty delivery looks like
    private static double rateSpread(double[] window) {
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int n = 3; n < filled; n++) {
            double dt = TIME[n] - TIME[n - 3];
            if (dt <= 1.0e-6) continue;
            double r = (window[n] - window[n - 3]) / dt;
            low = Math.min(low, r);
            high = Math.max(high, r);
        }
        return high < low ? 0 : high - low;
    }

    private static int newest() {
        return (at + SAMPLES - 1) % SAMPLES;
    }

    private static int oldest() {
        return filled < SAMPLES ? 0 : at;
    }

    private static double elapsed() {
        return TIME[newest()] - TIME[oldest()];
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
