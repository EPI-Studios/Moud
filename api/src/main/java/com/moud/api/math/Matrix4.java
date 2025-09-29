package com.moud.api.math;

import org.graalvm.polyglot.HostAccess;

public class Matrix4 {
    @HostAccess.Export
    public float[] m = new float[16];

    public Matrix4() {
        identity();
    }

    public Matrix4(float[] values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("Matrix4 requires 16 values");
        }
        System.arraycopy(values, 0, m, 0, 16);
    }

    public Matrix4(Matrix4 other) {
        System.arraycopy(other.m, 0, m, 0, 16);
    }

    @HostAccess.Export
    public static Matrix4 identity() {
        Matrix4 result = new Matrix4();
        result.m[0] = 1.0f;
        result.m[5] = 1.0f;
        result.m[10] = 1.0f;
        result.m[15] = 1.0f;
        return result;
    }

    @HostAccess.Export
    public static Matrix4 translation(Vector3 translation) {
        Matrix4 result = identity();
        result.m[12] = translation.x;
        result.m[13] = translation.y;
        result.m[14] = translation.z;
        return result;
    }

    @HostAccess.Export
    public static Matrix4 rotation(Quaternion rotation) {
        Matrix4 result = new Matrix4();
        float x = rotation.x, y = rotation.y, z = rotation.z, w = rotation.w;
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;

        result.m[0] = 1.0f - 2.0f * (yy + zz);
        result.m[1] = 2.0f * (xy + wz);
        result.m[2] = 2.0f * (xz - wy);
        result.m[3] = 0.0f;

        result.m[4] = 2.0f * (xy - wz);
        result.m[5] = 1.0f - 2.0f * (xx + zz);
        result.m[6] = 2.0f * (yz + wx);
        result.m[7] = 0.0f;

        result.m[8] = 2.0f * (xz + wy);
        result.m[9] = 2.0f * (yz - wx);
        result.m[10] = 1.0f - 2.0f * (xx + yy);
        result.m[11] = 0.0f;

        result.m[12] = 0.0f;
        result.m[13] = 0.0f;
        result.m[14] = 0.0f;
        result.m[15] = 1.0f;

        return result;
    }

    @HostAccess.Export
    public static Matrix4 scaling(Vector3 scale) {
        Matrix4 result = identity();
        result.m[0] = scale.x;
        result.m[5] = scale.y;
        result.m[10] = scale.z;
        return result;
    }

    @HostAccess.Export
    public static Matrix4 trs(Vector3 translation, Quaternion rotation, Vector3 scale) {
        Matrix4 t = translation(translation);
        Matrix4 r = rotation(rotation);
        Matrix4 s = scaling(scale);
        return t.multiply(r.multiply(s));
    }

    @HostAccess.Export
    public static Matrix4 perspective(float fov, float aspect, float near, float far) {
        Matrix4 result = new Matrix4();
        float tanHalfFov = MathUtils.tan(MathUtils.toRadians(fov) * 0.5f);

        result.m[0] = 1.0f / (aspect * tanHalfFov);
        result.m[5] = 1.0f / tanHalfFov;
        result.m[10] = -(far + near) / (far - near);
        result.m[11] = -1.0f;
        result.m[14] = -(2.0f * far * near) / (far - near);
        result.m[15] = 0.0f;

        return result;
    }

    @HostAccess.Export
    public static Matrix4 orthographic(float left, float right, float bottom, float top, float near, float far) {
        Matrix4 result = new Matrix4();

        result.m[0] = 2.0f / (right - left);
        result.m[5] = 2.0f / (top - bottom);
        result.m[10] = -2.0f / (far - near);
        result.m[12] = -(right + left) / (right - left);
        result.m[13] = -(top + bottom) / (top - bottom);
        result.m[14] = -(far + near) / (far - near);
        result.m[15] = 1.0f;

        return result;
    }

    @HostAccess.Export
    public static Matrix4 lookAt(Vector3 eye, Vector3 target, Vector3 up) {
        Vector3 f = target.subtract(eye).normalize();
        Vector3 s = f.cross(up).normalize();
        Vector3 u = s.cross(f);

        Matrix4 result = new Matrix4();
        result.m[0] = s.x;
        result.m[1] = u.x;
        result.m[2] = -f.x;
        result.m[3] = 0.0f;

        result.m[4] = s.y;
        result.m[5] = u.y;
        result.m[6] = -f.y;
        result.m[7] = 0.0f;

        result.m[8] = s.z;
        result.m[9] = u.z;
        result.m[10] = -f.z;
        result.m[11] = 0.0f;

        result.m[12] = -s.dot(eye);
        result.m[13] = -u.dot(eye);
        result.m[14] = f.dot(eye);
        result.m[15] = 1.0f;

        return result;
    }

    @HostAccess.Export
    public Matrix4 multiply(Matrix4 other) {
        Matrix4 result = new Matrix4();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result.m[i * 4 + j] =
                        m[i * 4] * other.m[j] +
                                m[i * 4 + 1] * other.m[4 + j] +
                                m[i * 4 + 2] * other.m[2 * 4 + j] +
                                m[i * 4 + 3] * other.m[3 * 4 + j];
            }
        }

        return result;
    }

    @HostAccess.Export
    public Vector3 transformPoint(Vector3 point) {
        float x = m[0] * point.x + m[4] * point.y + m[8] * point.z + m[12];
        float y = m[1] * point.x + m[5] * point.y + m[9] * point.z + m[13];
        float z = m[2] * point.x + m[6] * point.y + m[10] * point.z + m[14];
        float w = m[3] * point.x + m[7] * point.y + m[11] * point.z + m[15];

        if (MathUtils.abs(w) > MathUtils.EPSILON) {
            return new Vector3(x / w, y / w, z / w);
        }
        return new Vector3(x, y, z);
    }

    @HostAccess.Export
    public Vector3 transformDirection(Vector3 direction) {
        float x = m[0] * direction.x + m[4] * direction.y + m[8] * direction.z;
        float y = m[1] * direction.x + m[5] * direction.y + m[9] * direction.z;
        float z = m[2] * direction.x + m[6] * direction.y + m[10] * direction.z;
        return new Vector3(x, y, z);
    }

    @HostAccess.Export
    public Matrix4 transpose() {
        Matrix4 result = new Matrix4();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result.m[j * 4 + i] = m[i * 4 + j];
            }
        }
        return result;
    }

    @HostAccess.Export
    public float determinant() {
        return m[0] * (m[5] * (m[10] * m[15] - m[14] * m[11]) -
                m[6] * (m[9] * m[15] - m[13] * m[11]) +
                m[7] * (m[9] * m[14] - m[13] * m[10])) -
                m[1] * (m[4] * (m[10] * m[15] - m[14] * m[11]) -
                        m[6] * (m[8] * m[15] - m[12] * m[11]) +
                        m[7] * (m[8] * m[14] - m[12] * m[10])) +
                m[2] * (m[4] * (m[9] * m[15] - m[13] * m[11]) -
                        m[5] * (m[8] * m[15] - m[12] * m[11]) +
                        m[7] * (m[8] * m[13] - m[12] * m[9])) -
                m[3] * (m[4] * (m[9] * m[14] - m[13] * m[10]) -
                        m[5] * (m[8] * m[14] - m[12] * m[10]) +
                        m[6] * (m[8] * m[13] - m[12] * m[9]));
    }

    @HostAccess.Export
    public Matrix4 inverse() {
        float det = determinant();
        if (MathUtils.abs(det) < MathUtils.EPSILON) {
            return identity();
        }

        Matrix4 result = new Matrix4();
        float invDet = 1.0f / det;

        // Calculate inverse using cofactor matrix
        result.m[0] = invDet * (m[5] * (m[10] * m[15] - m[14] * m[11]) -
                m[6] * (m[9] * m[15] - m[13] * m[11]) +
                m[7] * (m[9] * m[14] - m[13] * m[10]));
        result.m[1] = invDet * -(m[1] * (m[10] * m[15] - m[14] * m[11]) -
                m[2] * (m[9] * m[15] - m[13] * m[11]) +
                m[3] * (m[9] * m[14] - m[13] * m[10]));
        result.m[2] = invDet * (m[1] * (m[6] * m[15] - m[14] * m[7]) -
                m[2] * (m[5] * m[15] - m[13] * m[7]) +
                m[3] * (m[5] * m[14] - m[13] * m[6]));
        result.m[3] = invDet * -(m[1] * (m[6] * m[11] - m[10] * m[7]) -
                m[2] * (m[5] * m[11] - m[9] * m[7]) +
                m[3] * (m[5] * m[10] - m[9] * m[6]));

        result.m[4] = invDet * -(m[4] * (m[10] * m[15] - m[14] * m[11]) -
                m[6] * (m[8] * m[15] - m[12] * m[11]) +
                m[7] * (m[8] * m[14] - m[12] * m[10]));
        result.m[5] = invDet * (m[0] * (m[10] * m[15] - m[14] * m[11]) -
                m[2] * (m[8] * m[15] - m[12] * m[11]) +
                m[3] * (m[8] * m[14] - m[12] * m[10]));
        result.m[6] = invDet * -(m[0] * (m[6] * m[15] - m[14] * m[7]) -
                m[2] * (m[4] * m[15] - m[12] * m[7]) +
                m[3] * (m[4] * m[14] - m[12] * m[6]));
        result.m[7] = invDet * (m[0] * (m[6] * m[11] - m[10] * m[7]) -
                m[2] * (m[4] * m[11] - m[8] * m[7]) +
                m[3] * (m[4] * m[10] - m[8] * m[6]));

        result.m[8] = invDet * (m[4] * (m[9] * m[15] - m[13] * m[11]) -
                m[5] * (m[8] * m[15] - m[12] * m[11]) +
                m[7] * (m[8] * m[13] - m[12] * m[9]));
        result.m[9] = invDet * -(m[0] * (m[9] * m[15] - m[13] * m[11]) -
                m[1] * (m[8] * m[15] - m[12] * m[11]) +
                m[3] * (m[8] * m[13] - m[12] * m[9]));
        result.m[10] = invDet * (m[0] * (m[5] * m[15] - m[13] * m[7]) -
                m[1] * (m[4] * m[15] - m[12] * m[7]) +
                m[3] * (m[4] * m[13] - m[12] * m[5]));
        result.m[11] = invDet * -(m[0] * (m[5] * m[11] - m[9] * m[7]) -
                m[1] * (m[4] * m[11] - m[8] * m[7]) +
                m[3] * (m[4] * m[9] - m[8] * m[5]));

        result.m[12] = invDet * -(m[4] * (m[9] * m[14] - m[13] * m[10]) -
                m[5] * (m[8] * m[14] - m[12] * m[10]) +
                m[6] * (m[8] * m[13] - m[12] * m[9]));
        result.m[13] = invDet * (m[0] * (m[9] * m[14] - m[13] * m[10]) -
                m[1] * (m[8] * m[14] - m[12] * m[10]) +
                m[2] * (m[8] * m[13] - m[12] * m[9]));
        result.m[14] = invDet * -(m[0] * (m[5] * m[14] - m[13] * m[6]) -
                m[1] * (m[4] * m[14] - m[12] * m[6]) +
                m[2] * (m[4] * m[13] - m[12] * m[5]));
        result.m[15] = invDet * (m[0] * (m[5] * m[10] - m[9] * m[6]) -
                m[1] * (m[4] * m[10] - m[8] * m[6]) +
                m[2] * (m[4] * m[9] - m[8] * m[5]));

        return result;
    }

    @HostAccess.Export
    public Vector3 getTranslation() {
        return new Vector3(m[12], m[13], m[14]);
    }

    @HostAccess.Export
    public Quaternion getRotation() {
        Vector3 scale = getScale();

        // Remove scaling from the matrix
        float m00 = m[0] / scale.x;
        float m01 = m[1] / scale.x;
        float m02 = m[2] / scale.x;
        float m10 = m[4] / scale.y;
        float m11 = m[5] / scale.y;
        float m12 = m[6] / scale.y;
        float m20 = m[8] / scale.z;
        float m21 = m[9] / scale.z;
        float m22 = m[10] / scale.z;

        float trace = m00 + m11 + m22;

        if (trace > 0.0f) {
            float s = MathUtils.sqrt(trace + 1.0f) * 2.0f;
            return new Quaternion(
                    (m12 - m21) / s,
                    (m20 - m02) / s,
                    (m01 - m10) / s,
                    0.25f * s
            );
        } else if (m00 > m11 && m00 > m22) {
            float s = MathUtils.sqrt(1.0f + m00 - m11 - m22) * 2.0f;
            return new Quaternion(
                    0.25f * s,
                    (m01 + m10) / s,
                    (m20 + m02) / s,
                    (m12 - m21) / s
            );
        } else if (m11 > m22) {
            float s = MathUtils.sqrt(1.0f + m11 - m00 - m22) * 2.0f;
            return new Quaternion(
                    (m01 + m10) / s,
                    0.25f * s,
                    (m12 + m21) / s,
                    (m20 - m02) / s
            );
        } else {
            float s = MathUtils.sqrt(1.0f + m22 - m00 - m11) * 2.0f;
            return new Quaternion(
                    (m20 + m02) / s,
                    (m12 + m21) / s,
                    0.25f * s,
                    (m01 - m10) / s
            );
        }
    }

    @HostAccess.Export
    public Vector3 getScale() {
        float scaleX = MathUtils.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
        float scaleY = MathUtils.sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
        float scaleZ = MathUtils.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);

        // Check if matrix has negative determinant (flipped)
        if (determinant() < 0) {
            scaleX = -scaleX;
        }

        return new Vector3(scaleX, scaleY, scaleZ);
    }

    @HostAccess.Export
    public float get(int index) {
        if (index < 0 || index >= 16) {
            throw new IndexOutOfBoundsException("Matrix4 index must be between 0 and 15");
        }
        return m[index];
    }

    @HostAccess.Export
    public void set(int index, float value) {
        if (index < 0 || index >= 16) {
            throw new IndexOutOfBoundsException("Matrix4 index must be between 0 and 15");
        }
        m[index] = value;
    }

    @HostAccess.Export
    public float[] toArray() {
        float[] result = new float[16];
        System.arraycopy(m, 0, result, 0, 16);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Matrix4(\n");
        for (int i = 0; i < 4; i++) {
            sb.append("  ");
            for (int j = 0; j < 4; j++) {
                sb.append(String.format("%8.3f", m[i * 4 + j]));
                if (j < 3) sb.append(", ");
            }
            sb.append("\n");
        }
        sb.append(")");
        return sb.toString();
    }
}