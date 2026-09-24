package com.meekdev.moud.core.image;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.mesh.EditableMesh;
import java.util.ArrayDeque;
import java.util.List;

public final class EditableImage {

    public static final int LARGEST = 1024;
    private static final int SAMPLES = 4;

    private int width;
    private int height;
    private int[] argb;
    private int version;

    public EditableImage(int width, int height) {
        check(width, height);
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public static EditableImage of(int width, int height, int[] argb) {
        EditableImage image = new EditableImage(width, height);
        if (argb.length != width * height) throw new IllegalArgumentException("expected " + width * height + " pixels, got " + argb.length);
        System.arraycopy(argb, 0, image.argb, 0, argb.length);
        return image;
    }

    private static void check(int width, int height) {
        if (width < 1 || height < 1 || width > LARGEST || height > LARGEST) {
            throw new IllegalArgumentException("an image is 1 to " + LARGEST + " pixels on each side, not " + width + " by " + height);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int version() {
        return version;
    }

    public int[] pixels() {
        return argb;
    }

    public void touched() {
        version++;
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return argb[y * width + x];
    }

    public void set(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        argb[y * width + x] = value;
        version++;
    }

    public static int pack(float r, float g, float b, float a) {
        return Math.round(Math.clamp(a, 0f, 1f) * 255) << 24 | Math.round(Math.clamp(r, 0f, 1f) * 255) << 16
                | Math.round(Math.clamp(g, 0f, 1f) * 255) << 8 | Math.round(Math.clamp(b, 0f, 1f) * 255);
    }

    public static Color color(int value) {
        return new Color(((value >> 16) & 0xFF) / 255f, ((value >> 8) & 0xFF) / 255f, (value & 0xFF) / 255f, 1f);
    }

    public static float alpha(int value) {
        return ((value >>> 24) & 0xFF) / 255f;
    }

    private void blend(int x, int y, Paint paint, float coverage) {
        if (coverage <= 0 || x < 0 || y < 0 || x >= width || y >= height) return;
        int at = y * width + x;
        argb[at] = mix(argb[at], paint, Math.min(1f, coverage));
    }

    static int mix(int dst, Paint paint, float coverage) {
        float da = ((dst >>> 24) & 0xFF) / 255f;
        float dr = ((dst >> 16) & 0xFF) / 255f;
        float dg = ((dst >> 8) & 0xFF) / 255f;
        float db = (dst & 0xFF) / 255f;
        float sa = paint.a() * coverage;
        return switch (paint.blend()) {
            case OVER -> {
                float oa = sa + da * (1 - sa);
                if (oa <= 0) yield 0;
                yield pack((paint.r() * sa + dr * da * (1 - sa)) / oa, (paint.g() * sa + dg * da * (1 - sa)) / oa,
                        (paint.b() * sa + db * da * (1 - sa)) / oa, oa);
            }
            case REPLACE -> pack(dr + (paint.r() - dr) * coverage, dg + (paint.g() - dg) * coverage,
                    db + (paint.b() - db) * coverage, da + (paint.a() - da) * coverage);
            case ADD -> pack(dr + paint.r() * sa, dg + paint.g() * sa, db + paint.b() * sa, Math.max(da, sa));
            case MULTIPLY -> pack(dr * (1 - sa + paint.r() * sa), dg * (1 - sa + paint.g() * sa), db * (1 - sa + paint.b() * sa), da);
            case ERASE -> pack(dr, dg, db, da * (1 - sa));
        };
    }

    public void fill(Paint paint) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) blend(x, y, paint, 1);
        version++;
    }

    public void setPixel(int x, int y, Paint paint) {
        blend(x, y, paint, 1);
        version++;
    }

    public void rectangle(double x, double y, double w, double h, Paint paint, boolean filled, double thickness, double corner, boolean smooth) {
        if (w <= 0 || h <= 0) return;
        double radius = Math.clamp(corner, 0, Math.min(w, h) / 2);
        if (filled && radius <= 0 && !smooth) {
            int x0 = (int) Math.max(0, Math.round(x));
            int y0 = (int) Math.max(0, Math.round(y));
            int x1 = (int) Math.min(width, Math.round(x + w));
            int y1 = (int) Math.min(height, Math.round(y + h));
            for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) blend(px, py, paint, 1);
            version++;
            return;
        }
        double cx = x + w / 2;
        double cy = y + h / 2;
        double hx = w / 2;
        double hy = h / 2;
        double t = Math.max(1, thickness);
        shape(x, y, x + w, y + h, paint, smooth, (px, py) -> {
            double inside = -roundedBox(px - cx, py - cy, hx, hy, radius);
            return filled ? inside : Math.min(inside, t - inside);
        });
    }

    private static double roundedBox(double px, double py, double hx, double hy, double r) {
        double qx = Math.abs(px) - hx + r;
        double qy = Math.abs(py) - hy + r;
        double outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - r;
    }

    public void circle(double cx, double cy, double radius, Paint paint, boolean filled, double thickness, boolean smooth) {
        if (radius <= 0) return;
        double ox = cx + 0.5;
        double oy = cy + 0.5;
        double t = Math.max(1, thickness);
        shape(ox - radius, oy - radius, ox + radius, oy + radius, paint, smooth, (px, py) -> {
            double inside = radius - Math.hypot(px - ox, py - oy);
            return filled ? inside : Math.min(inside, t - inside);
        });
    }

    public void ellipse(double cx, double cy, double rx, double ry, Paint paint, boolean filled, double thickness, boolean smooth) {
        if (rx <= 0 || ry <= 0) return;
        double ox = cx + 0.5;
        double oy = cy + 0.5;
        double t = Math.max(1, thickness);
        double scale = Math.min(rx, ry);
        shape(ox - rx, oy - ry, ox + rx, oy + ry, paint, smooth, (px, py) -> {
            double k = Math.hypot((px - ox) / rx, (py - oy) / ry);
            double inside = (1 - k) * scale;
            return filled ? inside : Math.min(inside, t - inside);
        });
    }

    public void line(double x1, double y1, double x2, double y2, Paint paint, double thickness, boolean smooth) {
        double t = Math.max(1, thickness);
        if (!smooth && t <= 1) {
            bresenham((int) Math.round(x1), (int) Math.round(y1), (int) Math.round(x2), (int) Math.round(y2), paint);
            return;
        }
        double ax = x1 + 0.5;
        double ay = y1 + 0.5;
        double bx = x2 + 0.5;
        double by = y2 + 0.5;
        double r = t / 2;
        shape(Math.min(ax, bx) - r, Math.min(ay, by) - r, Math.max(ax, bx) + r, Math.max(ay, by) + r, paint, smooth,
                (px, py) -> r - segment(px, py, ax, ay, bx, by));
    }

    private static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = dx * dx + dy * dy;
        double t = length < 1e-12 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / length, 0, 1);
        return Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
    }

    private void bresenham(int x0, int y0, int x1, int y1, Paint paint) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int guard = (dx - dy) * 2 + 2;
        while (guard-- > 0) {
            blend(x0, y0, paint, 1);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
        version++;
    }

    public void polygon(List<double[]> points, Paint paint, boolean smooth) {
        if (points.size() < 3) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        int x0 = (int) Math.max(0, Math.floor(minX));
        int y0 = (int) Math.max(0, Math.floor(minY));
        int x1 = (int) Math.min(width - 1, Math.ceil(maxX));
        int y1 = (int) Math.min(height - 1, Math.ceil(maxY));
        int samples = smooth ? SAMPLES : 1;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                int hit = 0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        if (inside(points, px + (sx + 0.5) / samples, py + (sy + 0.5) / samples)) hit++;
                    }
                }
                blend(px, py, paint, hit / (float) (samples * samples));
            }
        }
        version++;
    }

    private static boolean inside(List<double[]> points, double x, double y) {
        boolean in = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double[] a = points.get(i);
            double[] b = points.get(j);
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
        }
        return in;
    }

    public void gradient(double x, double y, double w, double h, Paint from, Paint to, double degrees) {
        if (w <= 0 || h <= 0) return;
        double angle = Math.toRadians(degrees);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double half = (Math.abs(dx) * w + Math.abs(dy) * h) / 2;
        double cx = x + w / 2;
        double cy = y + h / 2;
        int x0 = (int) Math.max(0, Math.round(x));
        int y0 = (int) Math.max(0, Math.round(y));
        int x1 = (int) Math.min(width, Math.round(x + w));
        int y1 = (int) Math.min(height, Math.round(y + h));
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                double along = ((px + 0.5 - cx) * dx + (py + 0.5 - cy) * dy);
                float t = half < 1e-9 ? 0 : (float) Math.clamp(along / (2 * half) + 0.5, 0, 1);
                blend(px, py, new Paint(from.r() + (to.r() - from.r()) * t, from.g() + (to.g() - from.g()) * t,
                        from.b() + (to.b() - from.b()) * t, from.a() + (to.a() - from.a()) * t, from.blend()), 1);
            }
        }
        version++;
    }

    public int flood(int x, int y, Paint paint, double tolerance) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        int target = argb[y * width + x];
        int limit = (int) Math.round(Math.clamp(tolerance, 0, 1) * 255);
        boolean[] seen = new boolean[width * height];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        open.add(y * width + x);
        seen[y * width + x] = true;
        int filled = 0;
        int[] before = argb.clone();
        while (!open.isEmpty()) {
            int at = open.poll();
            argb[at] = mix(before[at], paint, 1);
            filled++;
            int px = at % width;
            int py = at / width;
            int[] around = {px > 0 ? at - 1 : -1, px < width - 1 ? at + 1 : -1, py > 0 ? at - width : -1, py < height - 1 ? at + width : -1};
            for (int next : around) {
                if (next < 0 || seen[next] || !near(before[next], target, limit)) continue;
                seen[next] = true;
                open.add(next);
            }
        }
        version++;
        return filled;
    }

    private static boolean near(int a, int b, int limit) {
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)) > limit) return false;
        }
        return true;
    }

    public void image(EditableImage source, int sx, int sy, int sw, int sh, double x, double y, double w, double h,
                      double transparency, Blend blend, boolean smooth) {
        if (sw <= 0 || sh <= 0 || w <= 0 || h <= 0) return;
        Sampler sampler = new Sampler(source, sx, sy, sw, sh, smooth, source == this);
        float opacity = opacity(transparency);
        int left = (int) Math.max(0, Math.floor(x));
        int top = (int) Math.max(0, Math.floor(y));
        int right = (int) Math.min(width, Math.ceil(x + w));
        int bottom = (int) Math.min(height, Math.ceil(y + h));
        for (int py = top; py < bottom; py++) {
            for (int px = left; px < right; px++) {
                double u = (px + 0.5 - x) / w * sw;
                double v = (py + 0.5 - y) / h * sh;
                if (sampler.covers(u, v)) stamp(px, py, sampler.at(u, v), opacity, blend);
            }
        }
        version++;
    }

    public void transformed(EditableImage source, int sx, int sy, int sw, int sh, double x, double y, double scaleX, double scaleY,
                            double degrees, double pivotX, double pivotY, double transparency, Blend blend, boolean smooth) {
        if (sw <= 0 || sh <= 0 || scaleX == 0 || scaleY == 0) return;
        Sampler sampler = new Sampler(source, sx, sy, sw, sh, smooth, source == this);
        float opacity = opacity(transparency);
        double angle = Math.toRadians(degrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        Bounds bounds = new Bounds();
        for (double cornerX : new double[] {0, sw}) {
            for (double cornerY : new double[] {0, sh}) {
                double ox = (cornerX - pivotX) * scaleX;
                double oy = (cornerY - pivotY) * scaleY;
                bounds.add(x + ox * cos - oy * sin, y + ox * sin + oy * cos);
            }
        }

        for (int py = bounds.top(); py < bounds.bottom(height); py++) {
            for (int px = bounds.left(); px < bounds.right(width); px++) {
                double dx = px + 0.5 - x;
                double dy = py + 0.5 - y;
                double u = (dx * cos + dy * sin) / scaleX + pivotX;
                double v = (dy * cos - dx * sin) / scaleY + pivotY;
                if (sampler.covers(u, v)) stamp(px, py, sampler.at(u, v), opacity, blend);
            }
        }
        version++;
    }

    public void projected(EditableMesh mesh, EditableImage decal, Vector3 position, Vector3 direction, Vector3 up, Vector3 size,
                          double fadeAngle, double transparency, Blend blend, boolean smooth) {
        Projector projector = Projector.of(position, direction, up, size, fadeAngle);
        if (projector == null) return;
        Sampler sampler = new Sampler(decal, 0, 0, decal.width, decal.height, smooth, decal == this);
        float opacity = opacity(transparency);
        boolean[] painted = new boolean[width * height];
        EditableMesh.Baked baked = mesh.bake();

        for (int face = 0; face < baked.faces(); face++) {
            Triangle triangle = Triangle.onImage(baked, face, width, height);
            if (triangle == null) continue;
            for (int py = triangle.top(); py <= triangle.bottom(); py++) {
                for (int px = triangle.left(); px <= triangle.right(); px++) {
                    if (painted[py * width + px]) continue;
                    Weights weights = triangle.weights(px + 0.5, py + 0.5);
                    if (weights == null) continue;
                    Vector3 point = weights.mix(baked.position(face, 0), baked.position(face, 1), baked.position(face, 2));
                    Vector3 normal = weights.mix(baked.normal(face, 0), baked.normal(face, 1), baked.normal(face, 2));
                    Projector.Spot spot = projector.spot(point, normal.normalize());
                    if (spot == null) continue;
                    painted[py * width + px] = true;
                    int sample = sampler.at(spot.u() * decal.width, spot.v() * decal.height);
                    stamp(px, py, sample, (float) (opacity * spot.strength()), blend);
                }
            }
        }
        version++;
    }

    private static float opacity(double transparency) {
        return (float) (1 - Math.clamp(transparency, 0, 1));
    }

    private void stamp(int x, int y, int sample, float opacity, Blend blend) {
        float a = alpha(sample) * opacity;
        if (a <= 0 && blend != Blend.REPLACE) return;
        Color c = color(sample);
        int at = y * width + x;
        argb[at] = mix(argb[at], new Paint(c.r(), c.g(), c.b(), a, blend), 1);
    }

    private static final class Sampler {
        private final int[] pixels;
        private final int stride;
        private final int rows;
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final boolean smooth;

        Sampler(EditableImage image, int left, int top, int width, int height, boolean smooth, boolean copy) {
            this.pixels = copy ? image.argb.clone() : image.argb;
            this.stride = image.width;
            this.rows = image.height;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.smooth = smooth;
        }

        boolean covers(double u, double v) {
            return u >= 0 && v >= 0 && u < width && v < height;
        }

        int at(double u, double v) {
            if (!smooth) return pixel(left + (int) u, top + (int) v);
            double su = Math.clamp(u - 0.5, 0, width - 1);
            double sv = Math.clamp(v - 0.5, 0, height - 1);
            int u0 = (int) su;
            int v0 = (int) sv;
            int u1 = Math.min(u0 + 1, width - 1);
            int v1 = Math.min(v0 + 1, height - 1);
            double across = su - u0;
            double down = sv - v0;
            int topLeft = pixel(left + u0, top + v0);
            int topRight = pixel(left + u1, top + v0);
            int bottomLeft = pixel(left + u0, top + v1);
            int bottomRight = pixel(left + u1, top + v1);
            int out = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                double upper = lerp(channel(topLeft, shift), channel(topRight, shift), across);
                double lower = lerp(channel(bottomLeft, shift), channel(bottomRight, shift), across);
                out |= ((int) Math.round(lerp(upper, lower, down)) & 0xFF) << shift;
            }
            return out;
        }

        private int pixel(int x, int y) {
            if (x < 0 || y < 0 || x >= stride || y >= rows) return 0;
            return pixels[y * stride + x];
        }

        private static int channel(int value, int shift) {
            return (value >>> shift) & 0xFF;
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        void add(double x, double y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        int left() {
            return (int) Math.max(0, Math.floor(minX));
        }

        int top() {
            return (int) Math.max(0, Math.floor(minY));
        }

        int right(int limit) {
            return (int) Math.min(limit, Math.ceil(maxX));
        }

        int bottom(int limit) {
            return (int) Math.min(limit, Math.ceil(maxY));
        }
    }

    private record Weights(double a, double b, double c) {
        Vector3 mix(Vector3 first, Vector3 second, Vector3 third) {
            return first.mul(a).add(second.mul(b)).add(third.mul(c));
        }
    }

    private record Triangle(double ax, double ay, double bx, double by, double cx, double cy, double area,
                            int left, int top, int right, int bottom) {

        private static final double EDGE = 1e-9;

        static Triangle onImage(EditableMesh.Baked baked, int face, int width, int height) {
            double ax = baked.u(face, 0) * width;
            double ay = baked.v(face, 0) * height;
            double bx = baked.u(face, 1) * width;
            double by = baked.v(face, 1) * height;
            double cx = baked.u(face, 2) * width;
            double cy = baked.v(face, 2) * height;
            double area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
            if (Math.abs(area) < EDGE) return null;
            int left = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
            int top = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
            int right = (int) Math.min(width - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int bottom = (int) Math.min(height - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
            return new Triangle(ax, ay, bx, by, cx, cy, area, left, top, right, bottom);
        }

        Weights weights(double x, double y) {
            double a = ((bx - x) * (cy - y) - (cx - x) * (by - y)) / area;
            double b = ((cx - x) * (ay - y) - (ax - x) * (cy - y)) / area;
            double c = 1 - a - b;
            if (a < -EDGE || b < -EDGE || c < -EDGE) return null;
            return new Weights(a, b, c);
        }
    }

    public EditableImage copy() {
        return of(width, height, argb);
    }

    public EditableImage crop(int x, int y, int w, int h) {
        EditableImage out = new EditableImage(w, h);
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) out.argb[py * w + px] = get(x + px, y + py);
        return out;
    }

    public void resize(int w, int h, boolean smooth) {
        check(w, h);
        EditableImage old = copy();
        width = w;
        height = h;
        argb = new int[w * h];
        image(old, 0, 0, old.width, old.height, 0, 0, w, h, 0, Blend.REPLACE, smooth);
        version++;
    }

    public void flip(boolean horizontal) {
        int[] old = argb.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = horizontal ? old[y * width + (width - 1 - x)] : old[(height - 1 - y) * width + x];
            }
        }
        version++;
    }

    public void rotate(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        for (int n = 0; n < turns; n++) {
            int[] old = argb.clone();
            int ow = width;
            int oh = height;
            width = oh;
            height = ow;
            argb = new int[width * height];
            for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) argb[x * width + (oh - 1 - y)] = old[y * ow + x];
        }
        version++;
    }

    @FunctionalInterface
    private interface Distance {
        double inside(double x, double y);
    }

    private void shape(double minX, double minY, double maxX, double maxY, Paint paint, boolean smooth, Distance distance) {
        int x0 = (int) Math.max(0, Math.floor(minX) - 1);
        int y0 = (int) Math.max(0, Math.floor(minY) - 1);
        int x1 = (int) Math.min(width - 1, Math.ceil(maxX) + 1);
        int y1 = (int) Math.min(height - 1, Math.ceil(maxY) + 1);
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                double inside = distance.inside(px + 0.5, py + 0.5);
                blend(px, py, paint, smooth ? (float) Math.clamp(inside + 0.5, 0, 1) : inside >= 0 ? 1 : 0);
            }
        }
        version++;
    }
}
