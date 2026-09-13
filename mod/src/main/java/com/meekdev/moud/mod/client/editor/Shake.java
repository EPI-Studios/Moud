package com.meekdev.moud.mod.client.editor;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.adapter.physics.Bodies;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import com.meekdev.moud.core.math.Vector3;

public final class Shake {

    private static final int SAMPLES = 60;

    private static final double[] BODY = new double[SAMPLES];

    private static final double[] REACH = new double[SAMPLES];
    private static final double[] CAM_Y = new double[SAMPLES];
    private static final double[] CAM_X = new double[SAMPLES];
    private static final double[] CAM_Z = new double[SAMPLES];

    private static final double[] CAM_YAW = new double[SAMPLES];

    private static final double[] DECK_YAW = new double[SAMPLES];

    private static final double[] HOLD = new double[SAMPLES];

    private static final double[] TIME = new double[SAMPLES];

    private static int at;
    private static int filled;
    private static boolean seeded;
    private static double camWas;
    private static double deckWas;
    private static float bob;
    private static String riding = "no";

    private Shake() {}

    public static void sample() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer me = client.player;
        if (me == null) return;

        Vec3 eye = client.gameRenderer.getMainCamera().position();
        CAM_X[at] = eye.x;
        CAM_Y[at] = eye.y;
        CAM_Z[at] = eye.z;
        int was = (at + SAMPLES - 1) % SAMPLES;
        double rawCam = me.getYRot();
        double rawDeck = ClientPhysics.riddenYaw();
        boolean riddenNow = !Double.isNaN(rawDeck);

        CAM_YAW[at] = seeded ? CAM_YAW[was] + wrap(rawCam - camWas) : rawCam;
        DECK_YAW[at] = seeded && riddenNow
                ? DECK_YAW[was] + wrap(rawDeck - deckWas)
                : riddenNow ? rawDeck : 0;
        HOLD[at] = riddenNow ? CAM_YAW[at] - DECK_YAW[at] : seeded ? HOLD[was] : 0;
        camWas = rawCam;
        if (riddenNow) deckWas = rawDeck;

        Character body = Bodies.of(ClientScene.tree(), me.getUUID().toString());
        BODY[at] = body == null ? me.getY() : body.cframe.position().y();

        float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (body != null && body.child("head") instanceof Instance head) {
            Vector3 drawn =
                    ClientScene.motion().sample(head, pt).position();
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
        seeded = riddenNow;

        if (me instanceof AbstractClientPlayer avatar) {
            bob = avatar.avatarState().getInterpolatedBob(1.0f);
        }
        riding = riddenNow ? "deck" : me.onGround() ? "ground" : "air";
    }

    public static String body() {
        return toMillimetres(range(BODY));
    }

    public static String reach() {
        return toMillimetres(range(REACH));
    }

    public static String camera() {
        return toMillimetres(range(CAM_Y));
    }

    public static String cameraFlat() {
        return toMillimetres(Math.max(range(CAM_X), range(CAM_Z)));
    }

    public static String bobText() {
        return String.format("%.4f", bob);
    }

    public static String riding() {
        return riding;
    }

    public static String hold() {
        if (!seeded) return "not on a deck";
        return String.format("%.3f deg", range(HOLD));
    }

    public static String deckRate() {
        if (!seeded || filled < 5) return "not on a deck";
        return String.format("%.0f deg/s, spread %.0f", rate(DECK_YAW), rateSpread(DECK_YAW));
    }

    private static double rate(double[] window) {
        double seconds = elapsed();
        if (seconds <= 0) return 0;
        return (window[newest()] - window[oldest()]) / seconds;
    }

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

    private static double range(double[] window) {
        if (filled == 0) return 0;
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int n = 0; n < filled; n++) {
            low = Math.min(low, window[n]);
            high = Math.max(high, window[n]);
        }
        return high - low;
    }

    private static String toMillimetres(double blocks) {
        double mm = blocks * 1000.0;
        if (mm > 500) return "moving";
        return String.format("%.1f mm", mm);
    }
}
