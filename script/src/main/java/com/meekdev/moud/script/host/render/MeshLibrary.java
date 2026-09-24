package com.meekdev.moud.script.host.render;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.mesh.EditableMesh;
import com.meekdev.moud.core.mesh.EditableMesh.Kind;
import com.meekdev.moud.core.mesh.MeshStore;
import com.meekdev.moud.script.api.MeshesRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostFunction;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class MeshLibrary {

    private MeshLibrary() {}

    public record Mesh(Host host, Set<Integer> owned, int id, EditableMesh data) implements HostObject {

        private static final Members METHODS = new Members("EditableMesh")
                .declare("uri", "string")
                .method("addVertex", "(position: Vector3) -> number", checked(a -> (double) of(a).addVertex(a.vector(1))))
                .method("getPosition", "(vertex: number) -> Vector3", checked(a -> of(a).position(a.integer(1))))
                .method("setPosition", "(vertex: number, position: Vector3) -> ()", checked(a -> {
                    of(a).position(a.integer(1), a.vector(2));
                    return null;
                }))
                .method("addNormal", "(normal: Vector3?) -> number", checked(a -> (double) of(a).addNormal(a.has(1) ? a.vector(1) : null)))
                .method("getNormal", "(normal: number) -> Vector3", checked(a -> of(a).normal(a.integer(1))))
                .method("setNormal", "(normal: number, value: Vector3) -> ()", checked(a -> {
                    of(a).normal(a.integer(1), a.vector(2));
                    return null;
                }))
                .method("resetNormal", "(normal: number) -> ()", checked(a -> {
                    of(a).resetNormal(a.integer(1));
                    return null;
                }))
                .method("addUV", "(uv: Vector3) -> number", checked(a -> {
                    Vector3 uv = a.vector(1);
                    return (double) of(a).addUV(uv.x(), uv.y());
                }))
                .method("getUV", "(uv: number) -> Vector3", checked(a -> {
                    double[] uv = of(a).uv(a.integer(1));
                    return new Vector3(uv[0], uv[1], 0);
                }))
                .method("setUV", "(uv: number, value: Vector3) -> ()", checked(a -> {
                    Vector3 uv = a.vector(2);
                    of(a).uv(a.integer(1), uv.x(), uv.y());
                    return null;
                }))
                .method("addColor", "(color: Color, transparency: number?) -> number",
                        checked(a -> (double) of(a).addColor(a.color(1), 1 - a.number(2, 0))))
                .method("getColor", "(color: number) -> (Color, number)", checked(a -> {
                    Color color = of(a).color(a.integer(1));
                    return Results.of(new Color(color.r(), color.g(), color.b()), 1.0 - color.a());
                }))
                .method("setColor", "(color: number, value: Color, transparency: number?) -> ()", checked(a -> {
                    EditableMesh mesh = of(a);
                    int id = a.integer(1);
                    double alpha = a.has(3) ? 1 - a.number(3) : mesh.color(id).a();
                    mesh.color(id, a.color(2), alpha);
                    return null;
                }))
                .method("addTriangle", "(vertex1: number, vertex2: number, vertex3: number) -> number",
                        checked(a -> (double) of(a).addTriangle(a.integer(1), a.integer(2), a.integer(3))))
                .method("removeFace", "(face: number) -> ()", checked(a -> {
                    of(a).removeFace(a.integer(1));
                    return null;
                }))
                .method("getFaceVertices", "(face: number) -> { number }", checked(a -> numbers(of(a).face(Kind.VERTEX, a.integer(1)))))
                .method("setFaceVertices", "(face: number, vertices: { number }) -> ()", setFace(Kind.VERTEX))
                .method("getFaceNormals", "(face: number) -> { number }", checked(a -> numbers(of(a).face(Kind.NORMAL, a.integer(1)))))
                .method("setFaceNormals", "(face: number, normals: { number }) -> ()", setFace(Kind.NORMAL))
                .method("getFaceUVs", "(face: number) -> { number }", checked(a -> numbers(of(a).face(Kind.UV, a.integer(1)))))
                .method("setFaceUVs", "(face: number, uvs: { number }) -> ()", setFace(Kind.UV))
                .method("getFaceColors", "(face: number) -> { number }", checked(a -> numbers(of(a).face(Kind.COLOR, a.integer(1)))))
                .method("setFaceColors", "(face: number, colors: { number }) -> ()", setFace(Kind.COLOR))
                .method("getVertices", "() -> { number }", checked(a -> numbers(of(a).ids(Kind.VERTEX))))
                .method("getFaces", "() -> { number }", checked(a -> numbers(of(a).faces())))
                .method("getNormals", "() -> { number }", checked(a -> numbers(of(a).ids(Kind.NORMAL))))
                .method("getUVs", "() -> { number }", checked(a -> numbers(of(a).ids(Kind.UV))))
                .method("getColors", "() -> { number }", checked(a -> numbers(of(a).ids(Kind.COLOR))))
                .method("getVertexFaces", "(vertex: number) -> { number }", checked(a -> numbers(of(a).vertexFaces(a.integer(1)))))
                .method("getAdjacentVertices", "(vertex: number) -> { number }", checked(a -> numbers(of(a).adjacentVertices(a.integer(1)))))
                .method("getAdjacentFaces", "(face: number) -> { number }", checked(a -> numbers(of(a).adjacentFaces(a.integer(1)))))
                .method("findClosestVertex", "(point: Vector3) -> number?", checked(a -> {
                    Integer closest = of(a).closestVertex(a.vector(1));
                    return closest == null ? null : (double) closest;
                }))
                .method("findVerticesWithinSphere", "(center: Vector3, radius: number) -> { number }",
                        checked(a -> numbers(of(a).withinSphere(a.vector(1), a.number(2)))))
                .method("findClosestPointOnSurface", "(point: Vector3) -> (number?, Vector3?, Vector3?)",
                        checked(a -> hit(of(a).closestPoint(a.vector(1)))))
                .method("raycast", "(origin: Vector3, direction: Vector3, range: number?) -> (number?, Vector3?, Vector3?)",
                        checked(a -> hit(of(a).raycast(a.vector(1), a.vector(2), a.number(3, 1000)))))
                .method("getSize", "() -> Vector3", checked(a -> {
                    Vector3[] bounds = of(a).bounds();
                    return bounds[1].sub(bounds[0]);
                }))
                .method("getCenter", "() -> Vector3", checked(a -> {
                    Vector3[] bounds = of(a).bounds();
                    return bounds[0].add(bounds[1]).mul(0.5);
                }))
                .method("removeUnused", "() -> ()", checked(a -> {
                    of(a).removeUnused();
                    return null;
                }))
                .method("clear", "() -> ()", checked(a -> {
                    of(a).clear();
                    return null;
                }))
                .method("copy", "() -> EditableMesh", checked(a -> {
                    Mesh self = a.self(Mesh.class);
                    return track(self.host(), self.owned(), self.live().copy());
                }))
                .method("destroy", "() -> ()", a -> {
                    a.self(Mesh.class).destroy();
                    return null;
                });

        MeshStore store() {
            return MeshStore.of(host.client());
        }

        EditableMesh live() {
            if (store().find(id) != data) throw new HostError("this mesh was destroyed");
            return data;
        }

        void destroy() {
            store().remove(id);
            owned.remove(id);
        }

        public String uri() {
            return MeshStore.uri(id);
        }

        @Override
        public String typeName() {
            return "EditableMesh";
        }

        @Override
        public Object get(String key) {
            if (key.equals("uri")) return uri();
            return METHODS.get(key);
        }
    }

    private static EditableMesh of(Args a) {
        return a.self(Mesh.class).live();
    }

    private static HostFunction checked(HostFunction body) {
        return a -> {
            try {
                return body.call(a);
            } catch (IllegalArgumentException e) {
                throw new HostError("%s", e.getMessage());
            }
        };
    }

    private static HostFunction setFace(Kind kind) {
        return checked(a -> {
            List<Object> given = a.list(2);
            int[] ids = new int[given.size()];
            for (int n = 0; n < ids.length; n++) {
                if (!(given.get(n) instanceof Number number)) throw new HostError("face ids are numbers");
                ids[n] = number.intValue();
            }
            of(a).face(kind, a.integer(1), ids);
            return null;
        });
    }

    private static List<Object> numbers(int[] ids) {
        List<Object> out = new ArrayList<>(ids.length);
        for (int id : ids) out.add((double) id);
        return out;
    }

    private static List<Object> numbers(List<Integer> ids) {
        List<Object> out = new ArrayList<>(ids.size());
        for (int id : ids) out.add((double) id);
        return out;
    }

    private static Results hit(EditableMesh.Hit hit) {
        if (hit == null) return Results.of(null, null, null);
        return Results.of((double) hit.face(), hit.point(), hit.weights());
    }

    public static void install(Host host) {
        Set<Integer> owned = ConcurrentHashMap.newKeySet();
        host.api().declare(Mesh.METHODS.decl());
        Members meshes = new Members("Meshes")
                .function("create", "() -> EditableMesh", a -> track(host, owned, new EditableMesh()))
                .function("load", "(source: string) -> EditableMesh", a -> {
                    String source = a.string(0);
                    EditableMesh editable = MeshStore.of(host.client()).find(source);
                    if (editable != null) return track(host, owned, editable.copy());
                    if (host.meshes() == null) throw new HostError("meshes are loaded in a LocalScript");
                    return waitFor(host, owned, host.meshes().load(source), source);
                });
        host.global("meshes", "Meshes", meshes);
        host.declare(meshes);
        host.onClose(() -> {
            for (int id : owned) MeshStore.of(host.client()).remove(id);
            owned.clear();
        });
    }

    static Mesh track(Host host, Set<Integer> owned, EditableMesh mesh) {
        Mesh made = new Mesh(host, owned, MeshStore.of(host.client()).add(mesh), mesh);
        owned.add(made.id());
        return made;
    }

    private static Suspend waitFor(Host host, Set<Integer> owned, CompletableFuture<EditableMesh> future, String source) {
        return new Suspend(dt -> {
            if (!future.isDone()) return null;
            try {
                return new Object[] {track(host, owned, future.join())};
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                return Suspend.failed("could not load %s: %s", source, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
        }, () -> future.cancel(true));
    }
}
