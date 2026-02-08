package com.moud.api.collision;

public record AABB(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {
    public AABB {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid AABB min/max");
        }
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public double depth() {
        return maxZ - minZ;
    }

    public double centerX() {
        return (minX + maxX) * 0.5;
    }

    public double centerY() {
        return (minY + maxY) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ) * 0.5;
    }

    public AABB moved(double dx, double dy, double dz) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return this;
        }
        return new AABB(
                minX + dx,
                minY + dy,
                minZ + dz,
                maxX + dx,
                maxY + dy,
                maxZ + dz
        );
    }

    public AABB expanded(double x, double y, double z) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            return this;
        }
        double minX = this.minX - x;
        double minY = this.minY - y;
        double minZ = this.minZ - z;
        double maxX = this.maxX + x;
        double maxY = this.maxY + y;
        double maxZ = this.maxZ + z;
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return this;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public AABB union(AABB other) {
        if (other == null) {
            return this;
        }
        return new AABB(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ)
        );
    }

    public boolean intersects(AABB other) {
        if (other == null) {
            return false;
        }
        return maxX > other.minX && minX < other.maxX
                && maxY > other.minY && minY < other.maxY
                && maxZ > other.minZ && minZ < other.maxZ;
    }

    public boolean intersectsInYZ(AABB other) {
        if (other == null) {
            return false;
        }
        return maxY > other.minY && minY < other.maxY
                && maxZ > other.minZ && minZ < other.maxZ;
    }

    public boolean intersectsInXZ(AABB other) {
        if (other == null) {
            return false;
        }
        return maxX > other.minX && minX < other.maxX
                && maxZ > other.minZ && minZ < other.maxZ;
    }

    public boolean intersectsInXY(AABB other) {
        if (other == null) {
            return false;
        }
        return maxX > other.minX && minX < other.maxX
                && maxY > other.minY && minY < other.maxY;
    }
}

