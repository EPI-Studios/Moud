package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.*;
import org.graalvm.polyglot.HostAccess;

@TsExpose
public class MathProxy {

    public MathProxy() {}

    @HostAccess.Export
    public MathUtilsProxy utils = new MathUtilsProxy();

    @HostAccess.Export
    public GeometryUtilsProxy geometry = new GeometryUtilsProxy();

    @HostAccess.Export
    public float clamp(double value, double min, double max) {
        return utils.clamp(value, min, max);
    }

    @HostAccess.Export
    public float lerp(double a, double b, double t) {
        return utils.lerp(a, b, t);
    }

    @HostAccess.Export
    public float atan2(double y, double x) {
        return utils.atan2(y, x);
    }

    @HostAccess.Export
    public float sin(double radians) {
        return utils.sin(radians);
    }

    @HostAccess.Export
    public float cos(double radians) {
        return utils.cos(radians);
    }

    @HostAccess.Export
    public float tan(double radians) {
        return utils.tan(radians);
    }

    @HostAccess.Export
    public float asin(double value) {
        return utils.asin(value);
    }

    @HostAccess.Export
    public float acos(double value) {
        return utils.acos(value);
    }

    @HostAccess.Export
    public float atan(double value) {
        return utils.atan(value);
    }

    @HostAccess.Export
    public float sqrt(double value) {
        return utils.sqrt(value);
    }

    @HostAccess.Export
    public float abs(double value) {
        return utils.abs(value);
    }

    @HostAccess.Export
    public float min(double a, double b) {
        return utils.min(a, b);
    }

    @HostAccess.Export
    public float max(double a, double b) {
        return utils.max(a, b);
    }

    @HostAccess.Export
    public int floor(double value) {
        return (int) Math.floor(value);
    }

    @HostAccess.Export
    public int ceil(double value) {
        return (int) Math.ceil(value);
    }

    @HostAccess.Export
    public int round(double value) {
        return (int) Math.round(value);
    }

    @HostAccess.Export
    public float toRadians(double degrees) {
        return utils.toRadians(degrees);
    }

    @HostAccess.Export
    public float toDegrees(double radians) {
        return utils.toDegrees(radians);
    }

    @HostAccess.Export
    public Vector3 vector3(double x, double y, double z) {
        return new Vector3((float)x, (float)y, (float)z);
    }

    @HostAccess.Export
    public Vector3 vector3() {
        return Vector3.zero();
    }

    @HostAccess.Export
    public Quaternion quaternion(float x, float y, float z, float w) {
        return new Quaternion(x, y, z, w);
    }

    @HostAccess.Export
    public Quaternion quaternion(double x, double y, double z, double w) {
        return new Quaternion((float) x, (float) y, (float) z, (float) w);
    }

    @HostAccess.Export
    public Quaternion quaternion() {
        return Quaternion.identity();
    }

    @HostAccess.Export
    public Quaternion quaternionFromEuler(float pitch, float yaw, float roll) {
        return Quaternion.fromEuler(pitch, yaw, roll);
    }

    @HostAccess.Export
    public Quaternion quaternionFromEuler(double pitch, double yaw, double roll) {
        return Quaternion.fromEuler((float) pitch, (float) yaw, (float) roll);
    }

    @HostAccess.Export
    public Quaternion quaternionFromAxisAngle(Vector3 axis, float angle) {
        return Quaternion.fromAxisAngle(axis, angle);
    }

    @HostAccess.Export
    public Quaternion quaternionFromAxisAngle(Vector3 axis, double angle) {
        return Quaternion.fromAxisAngle(axis, (float) angle);
    }

    @HostAccess.Export
    public Matrix4 matrix4() {
        return Matrix4.identity();
    }

    @HostAccess.Export
    public Matrix4 matrix4Identity() {
        return Matrix4.identity();
    }

    @HostAccess.Export
    public Matrix4 matrix4Translation(Vector3 translation) {
        return Matrix4.translation(translation);
    }

    @HostAccess.Export
    public Matrix4 matrix4Rotation(Quaternion rotation) {
        return Matrix4.rotation(rotation);
    }

    @HostAccess.Export
    public Matrix4 matrix4Scaling(Vector3 scale) {
        return Matrix4.scaling(scale);
    }

    @HostAccess.Export
    public Matrix4 matrix4TRS(Vector3 translation, Quaternion rotation, Vector3 scale) {
        return Matrix4.trs(translation, rotation, scale);
    }

    @HostAccess.Export
    public Matrix4 matrix4Perspective(float fov, float aspect, float near, float far) {
        return Matrix4.perspective(fov, aspect, near, far);
    }

    @HostAccess.Export
    public Matrix4 matrix4Orthographic(float left, float right, float bottom, float top, float near, float far) {
        return Matrix4.orthographic(left, right, bottom, top, near, far);
    }

    @HostAccess.Export
    public Matrix4 matrix4LookAt(Vector3 eye, Vector3 target, Vector3 up) {
        return Matrix4.lookAt(eye, target, up);
    }

    @HostAccess.Export
    public Transform transform() {
        return Transform.identity();
    }

    @HostAccess.Export
    public Transform transform(Vector3 position, Quaternion rotation, Vector3 scale) {
        return new Transform(position, rotation, scale);
    }

    @HostAccess.Export
    public Vector3 getVector3Zero() {
        return Vector3.zero();
    }

    @HostAccess.Export
    public Vector3 getVector3One() {
        return Vector3.one();
    }

    @HostAccess.Export
    public Vector3 getVector3Up() {
        return Vector3.up();
    }

    @HostAccess.Export
    public Vector3 getVector3Down() {
        return Vector3.down();
    }

    @HostAccess.Export
    public Vector3 getVector3Left() {
        return Vector3.left();
    }

    @HostAccess.Export
    public Vector3 getVector3Right() {
        return Vector3.right();
    }

    @HostAccess.Export
    public Vector3 getVector3Forward() {
        return Vector3.forward();
    }

    @HostAccess.Export
    public Vector3 getVector3Backward() {
        return Vector3.backward();
    }

    @HostAccess.Export
    public Quaternion getQuaternionIdentity() {
        return Quaternion.identity();
    }

    @HostAccess.Export
    public float getPI() {
        return MathUtils.PI;
    }

    @HostAccess.Export
    public float getTWO_PI() {
        return MathUtils.TWO_PI;
    }

    @HostAccess.Export
    public float getHALF_PI() {
        return MathUtils.HALF_PI;
    }

    @HostAccess.Export
    public float getDEG_TO_RAD() {
        return MathUtils.DEG_TO_RAD;
    }

    @HostAccess.Export
    public float getRAD_TO_DEG() {
        return MathUtils.RAD_TO_DEG;
    }

    @HostAccess.Export
    public float getEPSILON() {
        return MathUtils.EPSILON;
    }

    public static class MathUtilsProxy {
        @HostAccess.Export
        public float clamp(double value, double min, double max) {
            return MathUtils.clamp((float)value, (float)min, (float)max);
        }

        @HostAccess.Export
        public int clamp(int value, int min, int max) {
            return MathUtils.clamp(value, min, max);
        }

        @HostAccess.Export
        public float lerp(double a, double b, double t) {
            return MathUtils.lerp((float)a, (float)b, (float)t);
        }

        @HostAccess.Export
        public float atan2(double y, double x) {
            return MathUtils.atan2((float)y, (float)x);
        }

        @HostAccess.Export
        public float sin(double radians) {
            return MathUtils.sin((float)radians);
        }

        @HostAccess.Export
        public float cos(double radians) {
            return MathUtils.cos((float)radians);
        }

        @HostAccess.Export
        public float tan(double radians) {
            return MathUtils.tan((float)radians);
        }

        @HostAccess.Export
        public float asin(double value) {
            return MathUtils.asin((float)value);
        }

        @HostAccess.Export
        public float acos(double value) {
            return MathUtils.acos((float)value);
        }

        @HostAccess.Export
        public float atan(double value) {
            return MathUtils.atan((float)value);
        }

        @HostAccess.Export
        public float sqrt(double value) {
            return MathUtils.sqrt((float)value);
        }

        @HostAccess.Export
        public float abs(double value) {
            return MathUtils.abs((float)value);
        }

        @HostAccess.Export
        public float min(double a, double b) {
            return MathUtils.min((float)a, (float)b);
        }

        @HostAccess.Export
        public float max(double a, double b) {
            return MathUtils.max((float)a, (float)b);
        }

        @HostAccess.Export
        public int floor(double value) {
            return (int) Math.floor(value);
        }

        @HostAccess.Export
        public int ceil(double value) {
            return (int) Math.ceil(value);
        }

        @HostAccess.Export
        public int round(double value) {
            return (int) Math.round(value);
        }

        @HostAccess.Export
        public float toRadians(double degrees) {
            return MathUtils.toRadians((float)degrees);
        }

        @HostAccess.Export
        public float toDegrees(double radians) {
            return MathUtils.toDegrees((float)radians);
        }

        @HostAccess.Export
        public float PI = MathUtils.PI;

        @HostAccess.Export
        public float DEG_TO_RAD = MathUtils.DEG_TO_RAD;

        @HostAccess.Export
        public float RAD_TO_DEG = MathUtils.RAD_TO_DEG;
    }

    public static class GeometryUtilsProxy {
        @HostAccess.Export
        public float distancePointToLine(Vector3 point, Vector3 lineStart, Vector3 lineEnd) {
            return GeometryUtils.distancePointToLine(point, lineStart, lineEnd);
        }

        @HostAccess.Export
        public Vector3 closestPointOnLine(Vector3 point, Vector3 lineStart, Vector3 lineEnd) {
            return GeometryUtils.closestPointOnLine(point, lineStart, lineEnd);
        }

        @HostAccess.Export
        public boolean sphereIntersection(Vector3 center1, float radius1, Vector3 center2, float radius2) {
            return GeometryUtils.sphereIntersection(center1, radius1, center2, radius2);
        }
    }
}
