package com.meekdev.moud.core.mesh;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EditableMesh {

    public static final int MOST_VERTICES = 65536;
    public static final int MOST_FACES = 65536;

    private static final int IDS_PER_FACE = 12;
    private static final double FLAT = 1e-12;
    private static final Kind[] ATTRIBUTES = {Kind.NORMAL, Kind.UV, Kind.COLOR};

    public enum Kind {
        VERTEX("vertex", 3),
        NORMAL("normal", 3),
        UV("uv", 2),
        COLOR("color", 4);

        final String word;
        final int width;

        Kind(String word, int width) {
            this.word = word;
            this.width = width;
        }
    }

    public record Hit(int face, Vector3 point, Vector3 weights) {}

    public record Baked(int faces, float[] positions, float[] normals, float[] uvs, float[] colors) {

        public Vector3 position(int face, int corner) {
            return vector(positions, face, corner);
        }

        public Vector3 normal(int face, int corner) {
            return vector(normals, face, corner);
        }

        public double u(int face, int corner) {
            return uvs[(face * 3 + corner) * 2];
        }

        public double v(int face, int corner) {
            return uvs[(face * 3 + corner) * 2 + 1];
        }

        public Color color(int face) {
            return new Color(colors[face * 4], colors[face * 4 + 1], colors[face * 4 + 2], colors[face * 4 + 3]);
        }

        private static Vector3 vector(float[] values, int face, int corner) {
            int at = (face * 3 + corner) * 3;
            return new Vector3(values[at], values[at + 1], values[at + 2]);
        }
    }

    private final Table vertices = new Table(Kind.VERTEX.width);
    private final Table normals = new Table(Kind.NORMAL.width);
    private final Table uvs = new Table(Kind.UV.width);
    private final Table colors = new Table(Kind.COLOR.width);
    private boolean[] automatic = new boolean[0];
    private int[] faceIds = new int[0];
    private boolean[] faceAlive = new boolean[0];
    private int faceTop;
    private int faceCount;
    private FaceList[] facesOfVertex = new FaceList[0];
    private int version;
    private int normalsAt = -1;

    public int version() {
        return version;
    }

    public int vertexCount() {
        return vertices.count;
    }

    public int faceCount() {
        return faceCount;
    }

    public boolean hasFace(int id) {
        return id >= 1 && id <= faceTop && faceAlive[id - 1];
    }

    public int addVertex(Vector3 position) {
        if (vertices.count >= MOST_VERTICES) throw new IllegalArgumentException("a mesh holds at most " + MOST_VERTICES + " vertices");
        int id = vertices.add(position.x(), position.y(), position.z());
        if (facesOfVertex.length < vertices.top) {
            facesOfVertex = Arrays.copyOf(facesOfVertex, Math.max(16, facesOfVertex.length * 2));
        }
        facesOfVertex[id - 1] = new FaceList();
        changed();
        return id;
    }

    public Vector3 position(int id) {
        need(Kind.VERTEX, id);
        return vertices.vector(id);
    }

    public void position(int id, Vector3 position) {
        need(Kind.VERTEX, id);
        vertices.set(id, position);
        changed();
    }

    public int addNormal(Vector3 normal) {
        int id = normals.add(normal == null ? new double[3] : new double[] {normal.x(), normal.y(), normal.z()});
        if (automatic.length < normals.top) automatic = Arrays.copyOf(automatic, Math.max(16, automatic.length * 2));
        automatic[id - 1] = normal == null;
        changed();
        return id;
    }

    public Vector3 normal(int id) {
        need(Kind.NORMAL, id);
        if (automatic[id - 1]) refreshNormals();
        return normals.vector(id);
    }

    public void normal(int id, Vector3 normal) {
        need(Kind.NORMAL, id);
        normals.set(id, normal);
        automatic[id - 1] = false;
        changed();
    }

    public void resetNormal(int id) {
        need(Kind.NORMAL, id);
        automatic[id - 1] = true;
        normalsAt = -1;
        changed();
    }

    public boolean automatic(int id) {
        need(Kind.NORMAL, id);
        return automatic[id - 1];
    }

    public int addUV(double u, double v) {
        int id = uvs.add(u, v);
        changed();
        return id;
    }

    public double[] uv(int id) {
        need(Kind.UV, id);
        return new double[] {uvs.get(id, 0), uvs.get(id, 1)};
    }

    public void uv(int id, double u, double v) {
        need(Kind.UV, id);
        uvs.set(id, 0, u);
        uvs.set(id, 1, v);
        changed();
    }

    public int addColor(Color color, double alpha) {
        int id = colors.add(color.r(), color.g(), color.b(), Math.clamp(alpha, 0, 1));
        changed();
        return id;
    }

    public Color color(int id) {
        need(Kind.COLOR, id);
        float red = (float) colors.get(id, 0);
        float green = (float) colors.get(id, 1);
        float blue = (float) colors.get(id, 2);
        float alpha = (float) colors.get(id, 3);
        return new Color(red, green, blue, alpha);
    }

    public void color(int id, Color color, double alpha) {
        need(Kind.COLOR, id);
        colors.set(id, 0, color.r());
        colors.set(id, 1, color.g());
        colors.set(id, 2, color.b());
        colors.set(id, 3, Math.clamp(alpha, 0, 1));
        changed();
    }

    public int addTriangle(int a, int b, int c) {
        need(Kind.VERTEX, a);
        need(Kind.VERTEX, b);
        need(Kind.VERTEX, c);
        if (a == b || b == c || a == c) throw new IllegalArgumentException("a triangle needs three different vertices");
        if (faceCount >= MOST_FACES) throw new IllegalArgumentException("a mesh holds at most " + MOST_FACES + " triangles");

        int face = newFace();
        int[] corners = {a, b, c};
        for (int corner = 0; corner < 3; corner++) {
            int vertex = corners[corner];
            setId(face, Kind.VERTEX, corner, vertex);
            FaceList around = facesOfVertex[vertex - 1];
            if (around.size() > 0) {
                shareAttributes(face, corner, around.get(0), vertex);
            } else {
                setId(face, Kind.NORMAL, corner, addNormal(null));
                setId(face, Kind.UV, corner, addUV(0, 0));
                setId(face, Kind.COLOR, corner, addColor(Color.WHITE, 1));
            }
        }

        for (int vertex : corners) facesOfVertex[vertex - 1].add(face);
        normalsAt = -1;
        changed();
        return face;
    }

    private int newFace() {
        if (faceTop == faceAlive.length) {
            int grown = Math.max(16, faceAlive.length * 2);
            faceAlive = Arrays.copyOf(faceAlive, grown);
            faceIds = Arrays.copyOf(faceIds, grown * IDS_PER_FACE);
        }
        int face = ++faceTop;
        faceAlive[face - 1] = true;
        faceCount++;
        return face;
    }

    private void shareAttributes(int face, int corner, int neighbour, int vertex) {
        int theirCorner = cornerOf(neighbour, vertex);
        for (Kind kind : ATTRIBUTES) setId(face, kind, corner, id(neighbour, kind, theirCorner));
    }

    public void removeFace(int id) {
        needFace(id);
        for (int corner = 0; corner < 3; corner++) facesOfVertex[vertexAt(id, corner) - 1].remove(id);
        faceAlive[id - 1] = false;
        faceCount--;
        normalsAt = -1;
        changed();
    }

    public int[] face(Kind kind, int id) {
        needFace(id);
        return new int[] {id(id, kind, 0), id(id, kind, 1), id(id, kind, 2)};
    }

    public void face(Kind kind, int id, int[] ids) {
        needFace(id);
        if (ids.length != 3) throw new IllegalArgumentException("a face takes 3 " + kind.word + " ids, got " + ids.length);
        for (int given : ids) need(kind, given);
        if (kind == Kind.VERTEX) {
            if (ids[0] == ids[1] || ids[1] == ids[2] || ids[0] == ids[2]) throw new IllegalArgumentException("a triangle needs three different vertices");
            for (int corner = 0; corner < 3; corner++) facesOfVertex[vertexAt(id, corner) - 1].remove(id);
            for (int vertex : ids) facesOfVertex[vertex - 1].add(id);
        }
        for (int corner = 0; corner < 3; corner++) setId(id, kind, corner, ids[corner]);
        normalsAt = -1;
        changed();
    }

    public List<Integer> ids(Kind kind) {
        return table(kind).ids();
    }

    public List<Integer> faces() {
        List<Integer> out = new ArrayList<>(faceCount);
        for (int face = 1; face <= faceTop; face++) {
            if (hasFace(face)) out.add(face);
        }
        return out;
    }

    public List<Integer> vertexFaces(int vertex) {
        need(Kind.VERTEX, vertex);
        FaceList around = facesOfVertex[vertex - 1];
        List<Integer> out = new ArrayList<>(around.size());
        for (int n = 0; n < around.size(); n++) out.add(around.get(n));
        return out;
    }

    public List<Integer> adjacentVertices(int vertex) {
        need(Kind.VERTEX, vertex);
        FaceList around = facesOfVertex[vertex - 1];
        Set<Integer> out = new LinkedHashSet<>();
        for (int n = 0; n < around.size(); n++) {
            for (int corner = 0; corner < 3; corner++) {
                int other = vertexAt(around.get(n), corner);
                if (other != vertex) out.add(other);
            }
        }
        return new ArrayList<>(out);
    }

    public List<Integer> adjacentFaces(int face) {
        needFace(face);
        Set<Integer> out = new LinkedHashSet<>();
        for (int corner = 0; corner < 3; corner++) {
            int start = vertexAt(face, corner);
            int end = vertexAt(face, (corner + 1) % 3);
            FaceList around = facesOfVertex[start - 1];
            for (int n = 0; n < around.size(); n++) {
                int other = around.get(n);
                if (other != face && cornerOf(other, end) >= 0) out.add(other);
            }
        }
        return new ArrayList<>(out);
    }

    public void removeUnused() {
        boolean removed = false;
        for (Kind kind : Kind.values()) {
            Table table = table(kind);
            boolean[] used = new boolean[table.top];
            for (int face = 1; face <= faceTop; face++) {
                if (!hasFace(face)) continue;
                for (int corner = 0; corner < 3; corner++) used[id(face, kind, corner) - 1] = true;
            }
            for (int id = 1; id <= table.top; id++) {
                if (table.has(id) && !used[id - 1]) {
                    table.remove(id);
                    removed = true;
                }
            }
        }
        if (removed) changed();
    }

    public void clear() {
        for (Kind kind : Kind.values()) table(kind).removeAll();
        Arrays.fill(faceAlive, 0, faceTop, false);
        Arrays.fill(facesOfVertex, null);
        Arrays.fill(automatic, false);
        faceCount = 0;
        normalsAt = -1;
        changed();
    }

    public EditableMesh copy() {
        EditableMesh out = new EditableMesh();
        for (Kind kind : Kind.values()) out.table(kind).copyFrom(table(kind));
        out.automatic = automatic.clone();
        out.faceIds = faceIds.clone();
        out.faceAlive = faceAlive.clone();
        out.faceTop = faceTop;
        out.faceCount = faceCount;
        out.version = version;
        out.facesOfVertex = new FaceList[facesOfVertex.length];
        for (int n = 0; n < facesOfVertex.length; n++) {
            if (facesOfVertex[n] != null) out.facesOfVertex[n] = facesOfVertex[n].copy();
        }
        return out;
    }

    public Vector3 faceNormal(int face) {
        needFace(face);
        return triangle(face).across().normalize();
    }

    public Integer closestVertex(Vector3 point) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int id = 1; id <= vertices.top; id++) {
            if (!vertices.has(id)) continue;
            double distance = distanceSquared(id, point);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = id;
            }
        }
        return best < 0 ? null : best;
    }

    public List<Integer> withinSphere(Vector3 center, double radius) {
        List<Integer> out = new ArrayList<>();
        double reach = radius * radius;
        for (int id = 1; id <= vertices.top; id++) {
            if (vertices.has(id) && distanceSquared(id, center) <= reach) out.add(id);
        }
        return out;
    }

    private double distanceSquared(int vertex, Vector3 point) {
        double dx = vertices.get(vertex, 0) - point.x();
        double dy = vertices.get(vertex, 1) - point.y();
        double dz = vertices.get(vertex, 2) - point.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public Hit closestPoint(Vector3 point) {
        Hit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int face = 1; face <= faceTop; face++) {
            if (!hasFace(face)) continue;
            Triangle triangle = triangle(face);
            Vector3 weights = triangle.closestWeights(point);
            Vector3 onFace = triangle.at(weights);
            double distance = onFace.sub(point).lengthSq();
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Hit(face, onFace, weights);
            }
        }
        return best;
    }

    public Hit raycast(Vector3 origin, Vector3 direction, double range) {
        Vector3 along = direction.normalize();
        if (along.lengthSq() == 0) return null;
        Hit best = null;
        double nearest = range;
        for (int face = 1; face <= faceTop; face++) {
            if (!hasFace(face)) continue;
            Crossing crossing = triangle(face).crossing(origin, along);
            if (crossing == null || crossing.distance() > nearest) continue;
            nearest = crossing.distance();
            best = new Hit(face, origin.add(along.mul(nearest)), crossing.weights());
        }
        return best;
    }

    public Vector3[] bounds() {
        if (vertices.count == 0) return new Vector3[] {Vector3.ZERO, Vector3.ZERO};
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int id = 1; id <= vertices.top; id++) {
            if (!vertices.has(id)) continue;
            for (int axis = 0; axis < 3; axis++) {
                double value = vertices.get(id, axis);
                min[axis] = Math.min(min[axis], value);
                max[axis] = Math.max(max[axis], value);
            }
        }
        return new Vector3[] {new Vector3(min[0], min[1], min[2]), new Vector3(max[0], max[1], max[2])};
    }

    public Baked bake() {
        refreshNormals();
        float[] bakedPositions = new float[faceCount * 3 * 3];
        float[] bakedNormals = new float[faceCount * 3 * 3];
        float[] bakedUVs = new float[faceCount * 3 * 2];
        float[] bakedColors = new float[faceCount * 4];
        int baked = 0;
        for (int face = 1; face <= faceTop; face++) {
            if (!hasFace(face)) continue;
            for (int corner = 0; corner < 3; corner++) {
                int slot = baked * 3 + corner;
                copyInto(bakedPositions, slot, vertices, id(face, Kind.VERTEX, corner));
                copyUnitInto(bakedNormals, slot, id(face, Kind.NORMAL, corner));
                copyInto(bakedUVs, slot, uvs, id(face, Kind.UV, corner));
                addThirdInto(bakedColors, baked, id(face, Kind.COLOR, corner));
            }
            baked++;
        }
        return new Baked(baked, bakedPositions, bakedNormals, bakedUVs, bakedColors);
    }

    private static void copyInto(float[] into, int slot, Table from, int id) {
        for (int axis = 0; axis < from.width; axis++) into[slot * from.width + axis] = (float) from.get(id, axis);
    }

    private void copyUnitInto(float[] into, int slot, int normal) {
        double x = normals.get(normal, 0);
        double y = normals.get(normal, 1);
        double z = normals.get(normal, 2);
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < FLAT) {
            x = 0;
            y = 1;
            z = 0;
            length = 1;
        }
        into[slot * 3] = (float) (x / length);
        into[slot * 3 + 1] = (float) (y / length);
        into[slot * 3 + 2] = (float) (z / length);
    }

    private void addThirdInto(float[] into, int face, int color) {
        for (int channel = 0; channel < 4; channel++) into[face * 4 + channel] += (float) (colors.get(color, channel) / 3);
    }

    private void refreshNormals() {
        if (normalsAt == version) return;

        for (int id = 1; id <= normals.top; id++) {
            if (automatic[id - 1]) normals.set(id, Vector3.ZERO);
        }

        for (int face = 1; face <= faceTop; face++) {
            if (!hasFace(face)) continue;
            Vector3 across = triangle(face).across();
            for (int corner = 0; corner < 3; corner++) {
                int normal = id(face, Kind.NORMAL, corner);
                if (automatic[normal - 1]) normals.set(normal, normals.vector(normal).add(across));
            }
        }

        for (int id = 1; id <= normals.top; id++) {
            if (!automatic[id - 1]) continue;
            Vector3 sum = normals.vector(id);
            double length = sum.length();
            if (length < FLAT) {
                normals.set(id, Vector3.UP);
                continue;
            }
            normals.set(id, new Vector3(sum.x() / length, sum.y() / length, sum.z() / length));
        }
        normalsAt = version;
    }

    private Triangle triangle(int face) {
        Vector3 a = vertices.vector(vertexAt(face, 0));
        Vector3 b = vertices.vector(vertexAt(face, 1));
        Vector3 c = vertices.vector(vertexAt(face, 2));
        return new Triangle(a, b, c);
    }

    private int id(int face, Kind kind, int corner) {
        return faceIds[slot(face, kind, corner)];
    }

    private void setId(int face, Kind kind, int corner, int id) {
        faceIds[slot(face, kind, corner)] = id;
    }

    private int vertexAt(int face, int corner) {
        return id(face, Kind.VERTEX, corner);
    }

    private static int slot(int face, Kind kind, int corner) {
        return (face - 1) * IDS_PER_FACE + kind.ordinal() * 3 + corner;
    }

    private int cornerOf(int face, int vertex) {
        for (int corner = 0; corner < 3; corner++) {
            if (vertexAt(face, corner) == vertex) return corner;
        }
        return -1;
    }

    private Table table(Kind kind) {
        return switch (kind) {
            case VERTEX -> vertices;
            case NORMAL -> normals;
            case UV -> uvs;
            case COLOR -> colors;
        };
    }

    private void need(Kind kind, int id) {
        if (!table(kind).has(id)) throw new IllegalArgumentException("there is no " + kind.word + " " + id);
    }

    private void needFace(int id) {
        if (!hasFace(id)) throw new IllegalArgumentException("there is no face " + id);
    }

    private void changed() {
        version++;
    }

    private record Crossing(double distance, Vector3 weights) {}

    private record Triangle(Vector3 a, Vector3 b, Vector3 c) {

        private static final double PARALLEL = 1e-12;
        private static final double DEGENERATE = 1e-18;

        Vector3 across() {
            return b.sub(a).cross(c.sub(a));
        }

        Vector3 at(Vector3 weights) {
            Vector3 fromA = a.mul(weights.x());
            Vector3 fromB = b.mul(weights.y());
            Vector3 fromC = c.mul(weights.z());
            return fromA.add(fromB).add(fromC);
        }

        Crossing crossing(Vector3 origin, Vector3 along) {
            Vector3 toB = b.sub(a);
            Vector3 toC = c.sub(a);
            Vector3 sideways = along.cross(toC);
            double determinant = toB.dot(sideways);
            if (Math.abs(determinant) < PARALLEL) return null;
            double inverse = 1 / determinant;

            Vector3 fromA = origin.sub(a);
            double towardB = fromA.dot(sideways) * inverse;
            if (towardB < 0 || towardB > 1) return null;
            Vector3 turned = fromA.cross(toB);
            double towardC = along.dot(turned) * inverse;
            if (towardC < 0 || towardB + towardC > 1) return null;

            double distance = toC.dot(turned) * inverse;
            if (distance < 0) return null;
            return new Crossing(distance, new Vector3(1 - towardB - towardC, towardB, towardC));
        }

        Vector3 closestWeights(Vector3 point) {
            Vector3 toB = b.sub(a);
            Vector3 toC = c.sub(a);

            Vector3 fromA = point.sub(a);
            double bFromA = toB.dot(fromA);
            double cFromA = toC.dot(fromA);
            if (bFromA <= 0 && cFromA <= 0) return new Vector3(1, 0, 0);

            Vector3 fromB = point.sub(b);
            double bFromB = toB.dot(fromB);
            double cFromB = toC.dot(fromB);
            if (bFromB >= 0 && cFromB <= bFromB) return new Vector3(0, 1, 0);

            double outsideAB = bFromA * cFromB - bFromB * cFromA;
            if (outsideAB <= 0 && bFromA >= 0 && bFromB <= 0) {
                double along = bFromA / (bFromA - bFromB);
                return new Vector3(1 - along, along, 0);
            }

            Vector3 fromC = point.sub(c);
            double bFromC = toB.dot(fromC);
            double cFromC = toC.dot(fromC);
            if (cFromC >= 0 && bFromC <= cFromC) return new Vector3(0, 0, 1);

            double outsideAC = bFromC * cFromA - bFromA * cFromC;
            if (outsideAC <= 0 && cFromA >= 0 && cFromC <= 0) {
                double along = cFromA / (cFromA - cFromC);
                return new Vector3(1 - along, 0, along);
            }

            double outsideBC = bFromB * cFromC - bFromC * cFromB;
            if (outsideBC <= 0 && cFromB - bFromB >= 0 && bFromC - cFromC >= 0) {
                double along = (cFromB - bFromB) / ((cFromB - bFromB) + (bFromC - cFromC));
                return new Vector3(0, 1 - along, along);
            }

            double total = outsideBC + outsideAC + outsideAB;
            if (Math.abs(total) < DEGENERATE) return new Vector3(1, 0, 0);
            double towardB = outsideAC / total;
            double towardC = outsideAB / total;
            return new Vector3(1 - towardB - towardC, towardB, towardC);
        }
    }

    private static final class Table {
        final int width;
        double[] data = new double[0];
        boolean[] alive = new boolean[0];
        int top;
        int count;

        Table(int width) {
            this.width = width;
        }

        int add(double... values) {
            if (top == alive.length) {
                int grown = Math.max(16, alive.length * 2);
                data = Arrays.copyOf(data, grown * width);
                alive = Arrays.copyOf(alive, grown);
            }
            System.arraycopy(values, 0, data, top * width, width);
            alive[top] = true;
            count++;
            return ++top;
        }

        boolean has(int id) {
            return id >= 1 && id <= top && alive[id - 1];
        }

        double get(int id, int at) {
            return data[(id - 1) * width + at];
        }

        void set(int id, int at, double value) {
            data[(id - 1) * width + at] = value;
        }

        Vector3 vector(int id) {
            return new Vector3(get(id, 0), get(id, 1), get(id, 2));
        }

        void set(int id, Vector3 value) {
            set(id, 0, value.x());
            set(id, 1, value.y());
            set(id, 2, value.z());
        }

        void remove(int id) {
            alive[id - 1] = false;
            count--;
        }

        void removeAll() {
            for (int id = 1; id <= top; id++) {
                if (has(id)) remove(id);
            }
        }

        void copyFrom(Table other) {
            data = other.data.clone();
            alive = other.alive.clone();
            top = other.top;
            count = other.count;
        }

        List<Integer> ids() {
            List<Integer> out = new ArrayList<>(count);
            for (int id = 1; id <= top; id++) {
                if (has(id)) out.add(id);
            }
            return out;
        }
    }

    private static final class FaceList {
        private int[] faces = new int[4];
        private int size;

        int size() {
            return size;
        }

        int get(int n) {
            return faces[n];
        }

        void add(int face) {
            if (size == faces.length) faces = Arrays.copyOf(faces, size * 2);
            faces[size++] = face;
        }

        void remove(int face) {
            for (int n = 0; n < size; n++) {
                if (faces[n] != face) continue;
                System.arraycopy(faces, n + 1, faces, n, size - n - 1);
                size--;
                return;
            }
        }

        FaceList copy() {
            FaceList out = new FaceList();
            out.faces = faces.clone();
            out.size = size;
            return out;
        }
    }
}
