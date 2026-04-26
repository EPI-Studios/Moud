package com.moud.server.minestom.scripting.api.modules.mesh;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshPrimitive;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.Surface;
import com.moud.core.mesh.build.MeshBuilder;
import com.moud.core.mesh.build.SurfaceTool;
import com.moud.core.mesh.sdf.MarchingCubes;
import com.moud.core.mesh.source.HashRefMesh;
import com.moud.core.mesh.source.MeshSourceCodec;
import com.moud.core.scene.Node;
import com.moud.core.scripts.luau.LuauExport;
import com.moud.server.minestom.engine.ServerScene;
import com.moud.server.minestom.mesh.MeshPublishService;
import org.graalvm.polyglot.HostAccess;

import java.util.List;

@LuauExport(name = "SurfaceToolHandle", doc = "Streaming surface builder. Add vertices, normals, uvs, colors, then commit.")
public final class SurfaceToolHandle {
    private final SurfaceTool tool;
    private final ServerScene scene;
    private final MeshPublishService publisher;

    SurfaceToolHandle(SurfaceTool tool, ServerScene scene, MeshPublishService publisher) {
        this.tool = tool;
        this.scene = scene;
        this.publisher = publisher;
    }

    public static SurfaceToolHandle begin(String primitive, ServerScene scene, MeshPublishService publisher) {
        return new SurfaceToolHandle(SurfaceTool.begin(MeshPrimitive.parse(primitive)), scene, publisher);
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle set_material(String id) {
        tool.setMaterial(id);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle set_normal(double x, double y, double z) {
        tool.setNormal((float) x, (float) y, (float) z);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle set_uv(double u, double v) {
        tool.setUv((float) u, (float) v);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle set_color(double r, double g, double b, double a) {
        tool.setColor((float) r, (float) g, (float) b, (float) a);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public int add_vertex(double x, double y, double z) {
        return tool.addVertex((float) x, (float) y, (float) z);
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle add_triangle(int a, int b, int c) {
        tool.addTriangle(a, b, c);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle add_triangle_verts(double ax, double ay, double az,
                                                double bx, double by, double bz,
                                                double cx, double cy, double cz) {
        tool.addTriangleVerts(
                (float) ax, (float) ay, (float) az,
                (float) bx, (float) by, (float) bz,
                (float) cx, (float) cy, (float) cz);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle generate_flat_normals() {
        tool.generateFlatNormals();
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle generate_smooth_normals(double angleDeg) {
        tool.generateSmoothNormals((float) angleDeg);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle add_vertex_batch(Object positions, Object uvs) {
        float[] p = toFloats(positions);
        if (p == null) return this;
        float[] u = uvs == null ? null : toFloats(uvs);
        tool.addVertexArrays(p, u, null);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle add_vertex_batch_colored(Object positions, Object uvs, Object colors) {
        float[] p = toFloats(positions);
        if (p == null) return this;
        float[] u = uvs == null ? null : toFloats(uvs);
        int[] c = colors == null ? null : toInts(colors);
        tool.addVertexArrays(p, u, c);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle add_triangle_batch(Object indices) {
        int[] idx = toInts(indices);
        if (idx == null) return this;
        tool.addTriangleArray(idx);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceHandle end_surface() {
        return new SurfaceHandle(tool.end());
    }

    @HostAccess.Export
    @LuauExport
    public String build_and_attach(long nodeId) {
        Surface surface = tool.end();
        ArrayMesh mesh = new MeshBuilder().addSurface(surface).build();
        MeshRegistry.instance().register(mesh);
        if (scene == null) return mesh.hash();
        Node node = scene.engine().sceneTree().getNode(nodeId);
        if (node == null) return mesh.hash();
        node.setProperty("mesh_source", MeshSourceCodec.encode(new HashRefMesh(mesh.hash())));
        if (publisher != null) publisher.register(node);
        scene.engine().bumpPhysicsRevision();
        return mesh.hash();
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle fill_heightmap(Object heights, int cols, double spacing) {
        float[] h = toFloats(heights);
        if (h == null || cols <= 1 || h.length < cols * cols) return this;
        double half = (cols - 1) * spacing * 0.5;
        float[] positions = new float[cols * cols * 3];
        float[] uvs = new float[cols * cols * 2];
        for (int z = 0; z < cols; z++) {
            for (int x = 0; x < cols; x++) {
                int i = z * cols + x;
                positions[i * 3]     = (float) (x * spacing - half);
                positions[i * 3 + 1] = h[i];
                positions[i * 3 + 2] = (float) (z * spacing - half);
                uvs[i * 2]     = x / (float) (cols - 1);
                uvs[i * 2 + 1] = z / (float) (cols - 1);
            }
        }
        int[] indices = new int[(cols - 1) * (cols - 1) * 6];
        int ii = 0;
        for (int z = 0; z < cols - 1; z++) {
            for (int x = 0; x < cols - 1; x++) {
                int a = z * cols + x;
                int b = a + 1;
                int c = a + cols;
                int d = c + 1;
                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }
        tool.addVertexArrays(positions, uvs, null);
        tool.addTriangleArray(indices);
        return this;
    }

    @HostAccess.Export
    @LuauExport
    public SurfaceToolHandle extract_iso_grid(Object samples, int nx, int ny, int nz,
                                              double x0, double y0, double z0,
                                              double x1, double y1, double z1,
                                              double iso) {
        float[] s = toFloats(samples);
        if (s == null) return this;
        int expected = (nx + 1) * (ny + 1) * (nz + 1);
        if (s.length < expected) return this;
        com.moud.core.mesh.sdf.ScalarField field = (fx, fy, fz) -> {
            int i = Math.max(0, Math.min(nx, Math.round((fx - (float) x0) / ((float) (x1 - x0) / nx))));
            int j = Math.max(0, Math.min(ny, Math.round((fy - (float) y0) / ((float) (y1 - y0) / ny))));
            int k = Math.max(0, Math.min(nz, Math.round((fz - (float) z0) / ((float) (z1 - z0) / nz))));
            return s[i + (nx + 1) * (j + (ny + 1) * k)];
        };
        Surface extracted = MarchingCubes.extract(field, (float) iso,
                (float) x0, (float) y0, (float) z0,
                (float) x1, (float) y1, (float) z1,
                nx, ny, nz, null);
        float[] positions = extracted.positions();
        float[] uvs = extracted.uvs();
        for (int v = 0; v + 2 < positions.length; v += 3) {
            tool.setUv(uvs[(v / 3) * 2], uvs[(v / 3) * 2 + 1]);
            tool.addVertex(positions[v], positions[v + 1], positions[v + 2]);
        }
        tool.addTriangleArray(extracted.indices());
        return this;
    }

    private static float[] toFloats(Object data) {
        return switch (data) {
            case float[] floats -> floats;
            case double[] doubles -> {
                float[] out = new float[doubles.length];
                for (int i = 0; i < doubles.length; i++) out[i] = (float) doubles[i];
                yield out;
            }
            case int[] ints -> {
                float[] out = new float[ints.length];
                for (int i = 0; i < ints.length; i++) out[i] = ints[i];
                yield out;
            }
            case Object[] objs -> {
                float[] out = new float[objs.length];
                for (int i = 0; i < objs.length; i++) {
                    out[i] = objs[i] instanceof Number n ? n.floatValue() : 0f;
                }
                yield out;
            }
            case List<?> list -> {
                float[] out = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    out[i] = list.get(i) instanceof Number n ? n.floatValue() : 0f;
                }
                yield out;
            }
            case null, default -> null;
        };
    }

    private static int[] toInts(Object data) {
        return switch (data) {
            case int[] ints -> ints;
            case long[] longs -> {
                int[] out = new int[longs.length];
                for (int i = 0; i < longs.length; i++) out[i] = (int) longs[i];
                yield out;
            }
            case double[] doubles -> {
                int[] out = new int[doubles.length];
                for (int i = 0; i < doubles.length; i++) out[i] = (int) doubles[i];
                yield out;
            }
            case Object[] objs -> {
                int[] out = new int[objs.length];
                for (int i = 0; i < objs.length; i++) {
                    out[i] = objs[i] instanceof Number n ? (int) n.longValue() : 0;
                }
                yield out;
            }
            case List<?> list -> {
                int[] out = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    out[i] = list.get(i) instanceof Number n ? (int) n.longValue() : 0;
                }
                yield out;
            }
            case null, default -> null;
        };
    }
}
// ai-written, needs rewrite