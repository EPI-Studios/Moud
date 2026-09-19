package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.math.Vector3;
import java.util.Arrays;
import java.util.Random;

public final class PrecipitationField {

    public interface Ceiling {
        double top(double x, double z);
    }

    public record Fall(double speed, double sway, double jitter) {

        public static final Fall RAIN = new Fall(16, 0, 1.5);
        public static final Fall SNOW = new Fall(1.8, 0.6, 0.4);
    }

    public static final int LIMIT = 6000;

    private static final double ABOVE = 16;
    private static final double BELOW = 6;

    private final Random random;
    private double carry;
    private int count;
    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private double[] vx = new double[64];
    private double[] vy = new double[64];
    private double[] vz = new double[64];
    private double[] age = new double[64];
    private double[] phase = new double[64];

    public PrecipitationField(Random random) {
        this.random = random;
    }

    public int count() {
        return count;
    }

    public void clear() {
        count = 0;
        carry = 0;
    }

    public int due(double rate, double seconds) {
        if (rate <= 0 || seconds <= 0) {
            carry = 0;
            return 0;
        }
        carry += rate * seconds;
        int whole = (int) Math.min(carry, LIMIT);
        carry -= whole;
        return whole;
    }

    public int spawn(Vector3 eye, double radius, Fall fall, Vector3 wind, Ceiling ceiling, int amount) {
        int made = 0;
        double drift = ABOVE / Math.max(0.1, fall.speed());
        for (int n = 0; n < amount && count < LIMIT; n++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double reach = Math.sqrt(random.nextDouble()) * radius;
            double px = eye.x() + Math.cos(angle) * reach - wind.x() * drift * 0.5;
            double pz = eye.z() + Math.sin(angle) * reach - wind.z() * drift * 0.5;
            double py = eye.y() - BELOW + random.nextDouble() * (ABOVE + BELOW);
            if (py <= ceiling.top(px, pz)) continue;
            grow();
            int i = count++;
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            vx[i] = wind.x() + (random.nextDouble() - 0.5) * fall.jitter();
            vy[i] = -fall.speed() * (0.85 + random.nextDouble() * 0.3) + wind.y();
            vz[i] = wind.z() + (random.nextDouble() - 0.5) * fall.jitter();
            age[i] = 0;
            phase[i] = random.nextDouble() * Math.PI * 2;
            made++;
        }
        return made;
    }

    public void step(double seconds, Vector3 eye, double radius, Fall fall, Ceiling ceiling) {
        if (seconds <= 0) return;
        double reach = (radius + 2) * (radius + 2);
        int n = 0;
        while (n < count) {
            age[n] += seconds;
            double swing = fall.sway() * Math.cos(age[n] * 2.1 + phase[n]);
            x[n] += (vx[n] + swing) * seconds;
            y[n] += vy[n] * seconds;
            z[n] += (vz[n] + fall.sway() * Math.sin(age[n] * 1.7 + phase[n])) * seconds;
            double dx = x[n] - eye.x();
            double dz = z[n] - eye.z();
            boolean gone = y[n] <= ceiling.top(x[n], z[n]) || y[n] < eye.y() - BELOW * 2
                    || y[n] > eye.y() + ABOVE * 2 || dx * dx + dz * dz > reach;
            if (gone) {
                remove(n);
                continue;
            }
            n++;
        }
    }

    public Vector3 position(int i) {
        return new Vector3(x[i], y[i], z[i]);
    }

    public Vector3 velocity(int i) {
        return new Vector3(vx[i], vy[i], vz[i]);
    }

    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    public double z(int i) {
        return z[i];
    }

    public double age(int i) {
        return age[i];
    }

    private void remove(int i) {
        int last = --count;
        x[i] = x[last];
        y[i] = y[last];
        z[i] = z[last];
        vx[i] = vx[last];
        vy[i] = vy[last];
        vz[i] = vz[last];
        age[i] = age[last];
        phase[i] = phase[last];
    }

    private void grow() {
        if (count < x.length) return;
        int size = x.length * 2;
        x = Arrays.copyOf(x, size);
        y = Arrays.copyOf(y, size);
        z = Arrays.copyOf(z, size);
        vx = Arrays.copyOf(vx, size);
        vy = Arrays.copyOf(vy, size);
        vz = Arrays.copyOf(vz, size);
        age = Arrays.copyOf(age, size);
        phase = Arrays.copyOf(phase, size);
    }
}
