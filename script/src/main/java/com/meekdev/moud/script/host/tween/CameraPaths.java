package com.meekdev.moud.script.host.tween;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.interp.PathCurve;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.render.CameraMode;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CameraPaths {

    private static final class Playback implements HostObject {

        private static final Members METHODS = new Members("CameraPlayback")
                .declare("finished", "AnySignal")
                .declare("progress", "number")
                .declare("playing", "boolean")
                .method("stop", "() -> ()", a -> {
                    a.self(Playback.class).end(false);
                    return null;
                });

        private final Camera camera;
        private final CameraPath path;
        private final PathCurve curve;
        private final double duration;
        private final Easing easing;
        private final Easing.Direction direction;
        private final boolean restore;
        private final CameraMode before;
        private final HostSignal finished;
        private double elapsed;
        private boolean playing = true;

        Playback(Host host, Camera camera, CameraPath path, Map<String, Object> info) {
            this.camera = camera;
            this.path = path;
            List<CFrame> points = path.points();
            if (points.isEmpty()) throw new HostError("%s has no Attachment inside it to pass through", path.name());
            curve = new PathCurve(points, path.closed);
            duration = Math.max(0.01, TweenLibrary.number(info, "duration", path.duration));
            easing = TweenLibrary.option(info, "easing", Easing.class, path.easing);
            direction = TweenLibrary.option(info, "direction", Easing.Direction.class, path.direction);
            Object keep = info.get("restore");
            if (keep != null && !(keep instanceof Boolean)) throw new HostError("restore expects true or false");
            restore = keep == null || (Boolean) keep;
            before = camera.mode;
            finished = new HostSignal(host, "AnySignal", "camera.play.finished");
            set(camera.def().property("mode"), CameraMode.SCRIPTABLE);
            apply();
        }

        boolean step(double dt) {
            if (!playing) return false;
            if (!camera.isAlive() || !path.isAlive()) {
                end(false);
                return false;
            }
            elapsed += dt;
            if (elapsed >= duration && !path.looped) {
                elapsed = duration;
                apply();
                end(true);
                return false;
            }
            apply();
            return true;
        }

        private void apply() {
            double t = path.looped ? (elapsed % duration) / duration : Math.min(1, elapsed / duration);
            double alpha = easing.apply(t, direction);
            CFrame frame = curve.sample(alpha);
            if (path.lookAt instanceof Spatial target && target.isAlive()) {
                frame = CFrame.lookAt(frame.position(), Transforms.world(target).position());
            } else if (path.faceAlong) {
                frame = new CFrame(frame.position(), Quat.lookAt(curve.tangent(alpha), Vector3.UP));
            }
            set(camera.def().property("cframe"), frame);
        }

        void end(boolean completed) {
            if (!playing) return;
            playing = false;
            if (restore && camera.isAlive()) set(camera.def().property("mode"), before);
            finished.fire(completed);
        }

        private void set(PropertyDef property, Object value) {
            Instances.setObj(camera, property, value);
        }

        @Override
        public String typeName() {
            return "CameraPlayback";
        }

        @Override
        public Object get(String key) {
            return switch (key) {
                case "finished" -> finished;
                case "progress" -> Math.min(1, elapsed / duration);
                case "playing" -> playing;
                default -> METHODS.get(key);
            };
        }
    }

    private CameraPaths() {}

    static void install(Host host) {
        host.api().declare(Playback.METHODS.decl());
        host.api().alias("CameraPlayInfo", "{ duration: number?, easing: string?, direction: string?, restore: boolean? }");
        List<Playback> running = new ArrayList<>();
        host.onRenderStep(dt -> {
            for (Playback playback : new ArrayList<>(running)) {
                try {
                    if (!playback.step(dt)) running.remove(playback);
                } catch (RuntimeException e) {
                    running.remove(playback);
                    host.error("camera:play", e);
                }
            }
        });
        Members cameras = host.instances().of(Classes.CAMERA);
        cameras.method("play", "(path: CameraPath, info: CameraPlayInfo?) -> CameraPlayback", a -> {
            Camera camera = a.self(Camera.class);
            if (!(a.get(1) instanceof CameraPath path)) throw new HostError("camera:play expects a CameraPath");
            for (Playback other : new ArrayList<>(running)) {
                if (other.camera == camera) {
                    other.end(false);
                    running.remove(other);
                }
            }
            Playback playback = new Playback(host, camera, path, a.map(2, Map.of()));
            running.add(playback);
            host.ownership().onRelease(host.ownership().current(), () -> {
                playback.end(false);
                running.remove(playback);
            });
            return playback;
        });
    }
}
