package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.SurfaceFace;
import java.util.Arrays;
import java.util.Random;

public final class ParticleField {

    public static final int LIMIT = 8192;

    private final Random random;
    private double carry;
    private int count;
    private double[] x = new double[32];
    private double[] y = new double[32];
    private double[] z = new double[32];
    private double[] vx = new double[32];
    private double[] vy = new double[32];
    private double[] vz = new double[32];
    private double[] age = new double[32];
    private double[] life = new double[32];
    private double[] rotation = new double[32];
    private double[] spin = new double[32];

    public ParticleField(Random random) {
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
        if (rate <= 0) carry = 0;
        if (rate <= 0 || seconds <= 0) return 0;
        carry += rate * seconds;
        int whole = (int) Math.min(carry, LIMIT);
        carry -= whole;
        return whole;
    }

    public void emit(ParticleLook look, CFrame frame, Vector3 volume, int amount) {
        Vector3 normal = normal(look.direction());
        for (int n = 0; n < amount && count < LIMIT; n++) {
            grow();
            Vector3 at = volume == null ? Vector3.ZERO : point(volume, look.shape());
            Vector3 heading = spread(normal, Math.toRadians(look.spreadAngle()));
            Vector3 velocity = heading.mul(between(look.speedMin(), look.speedMax()));
            if (!look.locked()) {
                at = frame.pointToWorld(at);
                velocity = frame.vectorToWorld(velocity);
            }
            int i = count++;
            x[i] = at.x();
            y[i] = at.y();
            z[i] = at.z();
            vx[i] = velocity.x();
            vy[i] = velocity.y();
            vz[i] = velocity.z();
            age[i] = 0;
            life[i] = Math.max(1e-3, between(look.lifetimeMin(), look.lifetimeMax()));
            rotation[i] = between(look.rotationMin(), look.rotationMax());
            spin[i] = between(look.rotSpeedMin(), look.rotSpeedMax());
        }
    }

    public void step(ParticleLook look, CFrame frame, double seconds) {
        if (seconds <= 0) return;
        Vector3 pull = look.locked() ? frame.vectorToObject(look.acceleration()) : look.acceleration();
        double keep = look.drag() > 0 ? Math.pow(2, -look.drag() * seconds) : 1;
        int n = 0;
        while (n < count) {
            age[n] += seconds;
            if (age[n] >= life[n]) {
                remove(n);
                continue;
            }
            vx[n] = (vx[n] + pull.x() * seconds) * keep;
            vy[n] = (vy[n] + pull.y() * seconds) * keep;
            vz[n] = (vz[n] + pull.z() * seconds) * keep;
            x[n] += vx[n] * seconds;
            y[n] += vy[n] * seconds;
            z[n] += vz[n] * seconds;
            rotation[n] += spin[n] * seconds;
            n++;
        }
    }

    public Vector3 position(int i, ParticleLook look, CFrame frame) {
        Vector3 at = new Vector3(x[i], y[i], z[i]);
        return look.locked() ? frame.pointToWorld(at) : at;
    }

    public Vector3 velocity(int i, ParticleLook look, CFrame frame) {
        Vector3 v = new Vector3(vx[i], vy[i], vz[i]);
        return look.locked() ? frame.vectorToWorld(v) : v;
    }

    public double progress(int i) {
        return Math.clamp(age[i] / life[i], 0, 1);
    }

    public double rotation(int i) {
        return rotation[i];
    }

    public static double over(double start, double end, double t) {
        return start + (end - start) * t;
    }

    public static Color over(Color start, Color end, double t) {
        return start.lerp(end, (float) t);
    }

    public static Vector3 normal(SurfaceFace face) {
        return switch (face) {
            case TOP -> Vector3.UP;
            case BOTTOM -> Vector3.UP.neg();
            case FRONT -> Vector3.FORWARD;
            case BACK -> Vector3.FORWARD.neg();
            case RIGHT -> Vector3.RIGHT;
            case LEFT -> Vector3.RIGHT.neg();
        };
    }

    private Vector3 point(Vector3 size, EmissionShape shape) {
        double px = (random.nextDouble() - 0.5) * size.x();
        double py = (random.nextDouble() - 0.5) * size.y();
        double pz = (random.nextDouble() - 0.5) * size.z();
        if (shape == EmissionShape.VOLUME) return new Vector3(px, py, pz);
        double xy = Math.abs(size.x() * size.y());
        double yz = Math.abs(size.y() * size.z());
        double xz = Math.abs(size.x() * size.z());
        double pick = random.nextDouble() * (xy + yz + xz);
        double side = random.nextBoolean() ? 0.5 : -0.5;
        if (pick < xy) return new Vector3(px, py, side * size.z());
        if (pick < xy + yz) return new Vector3(side * size.x(), py, pz);
        return new Vector3(px, side * size.y(), pz);
    }

    private Vector3 spread(Vector3 normal, double halfAngle) {
        if (halfAngle <= 0) return normal;
        double cosine = 1 - random.nextDouble() * (1 - Math.cos(Math.min(halfAngle, Math.PI)));
        double sine = Math.sqrt(Math.max(0, 1 - cosine * cosine));
        double turn = random.nextDouble() * Math.PI * 2;
        Vector3 side = Math.abs(normal.y()) < 0.99 ? normal.cross(Vector3.UP).normalize() : normal.cross(Vector3.RIGHT).normalize();
        Vector3 other = normal.cross(side);
        return normal.mul(cosine).add(side.mul(sine * Math.cos(turn))).add(other.mul(sine * Math.sin(turn)));
    }

    private double between(double low, double high) {
        return high <= low ? low : low + random.nextDouble() * (high - low);
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
        life[i] = life[last];
        rotation[i] = rotation[last];
        spin[i] = spin[last];
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
        life = Arrays.copyOf(life, size);
        rotation = Arrays.copyOf(rotation, size);
        spin = Arrays.copyOf(spin, size);
    }
}
