package com.moud.client.editor.scene.blueprint;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintPreviewManager {
    private static final BlueprintPreviewManager INSTANCE = new BlueprintPreviewManager();

    public static BlueprintPreviewManager getInstance() {
        return INSTANCE;
    }

    private PreviewState current;

    private BlueprintPreviewManager() {}

    public void clear() {
        current = null;
    }

    public void show(Blueprint blueprint, float[] position) {
        if (blueprint == null) {
            current = null;
            return;
        }
        PreviewState state = new PreviewState();
        state.blueprint = blueprint;
        state.position = position.clone();
        state.boxes = new ArrayList<>();
        if (blueprint.blocks != null) {
            Blueprint.BlockVolume volume = blueprint.blocks;
            int sizeX = Math.max(1, volume.sizeX);
            int sizeY = Math.max(1, volume.sizeY);
            int sizeZ = Math.max(1, volume.sizeZ);
            state.boxes.add(new RelativeBox(0.0, 0.0, 0.0, sizeX, sizeY, sizeZ));
        }
        if (blueprint.objects != null) {
            for (BlueprintObject object : blueprint.objects) {
                if (object.boundsMin != null && object.boundsMax != null) {
                    state.boxes.add(RelativeBox.from(object.boundsMin, object.boundsMax));
                } else if (object.position != null) {
                    double size = 0.4;
                    state.boxes.add(new RelativeBox(
                            object.position[0] - size,
                            object.position[1] - size,
                            object.position[2] - size,
                            object.position[0] + size,
                            object.position[1] + size,
                            object.position[2] + size
                    ));
                }
            }
        }
        current = state;
    }

    public void move(float[] position) {
        PreviewState state = current;
        if (state == null) {
            return;
        }
        state.position = position.clone();
    }

    public void rotate(float yawDegrees) {
        PreviewState state = current;
        if (state == null) {
            return;
        }
        state.rotation[1] = yawDegrees;
    }

    public void setRotation(float pitch, float yaw, float roll) {
        PreviewState state = current;
        if (state == null) {
            return;
        }
        state.rotation[0] = pitch;
        state.rotation[1] = yaw;
        state.rotation[2] = roll;
    }

    public void setScale(float x, float y, float z) {
        PreviewState state = current;
        if (state == null) {
            return;
        }
        state.scale[0] = x;
        state.scale[1] = y;
        state.scale[2] = z;
    }

    public void setUniformScale(float scale) {
        setScale(scale, scale, scale);
    }

    public PreviewState getCurrent() {
        return current;
    }

    public static final class PreviewState {
        public Blueprint blueprint;
        public float[] position = new float[3];
        public float[] rotation = new float[3]; // pitch, yaw, roll in degrees
        public float[] scale = new float[]{1f, 1f, 1f};
        public List<RelativeBox> boxes = List.of();

        public float getYawDegrees() {
            return rotation[1];
        }
    }

    public static final class RelativeBox {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        public RelativeBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public static RelativeBox from(float[] min, float[] max) {
            return new RelativeBox(min[0], min[1], min[2], max[0], max[1], max[2]);
        }
    }

    private void addBlockColumns(PreviewState state, Blueprint.BlockVolume volume) {
        if (volume.palette == null || volume.palette.isEmpty() || volume.voxels == null) {
            return;
        }
        int sizeX = Math.max(1, volume.sizeX);
        int sizeY = Math.max(1, volume.sizeY);
        int sizeZ = Math.max(1, volume.sizeZ);

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int startY = -1;
                for (int y = 0; y < sizeY; y++) {
                    boolean solid = isSolid(volume, x, y, z);
                    if (solid) {
                        if (startY < 0) {
                            startY = y;
                        }
                    } else if (startY >= 0) {
                        state.boxes.add(new RelativeBox(
                                x,
                                startY,
                                z,
                                x + 1,
                                y,
                                z + 1
                        ));
                        startY = -1;
                    }
                }
                if (startY >= 0) {
                    state.boxes.add(new RelativeBox(
                            x,
                            startY,
                            z,
                            x + 1,
                            sizeY,
                            z + 1
                    ));
                }
            }
        }
    }

    private boolean isSolid(Blueprint.BlockVolume volume, int x, int y, int z) {
        int idx = getPaletteIndex(volume, x, y, z);
        if (idx <= 0 || idx >= volume.palette.size()) {
            return false;
        }
        String id = volume.palette.get(idx);
        if (id == null) {
            return false;
        }
        String normalized = id.toLowerCase();
        return !(normalized.contains("air") || normalized.contains("void"));
    }

    private int getPaletteIndex(Blueprint.BlockVolume volume, int x, int y, int z) {
        int sizeX = Math.max(1, volume.sizeX);
        int sizeY = Math.max(1, volume.sizeY);
        int sizeZ = Math.max(1, volume.sizeZ);
        x = Math.min(Math.max(0, x), sizeX - 1);
        y = Math.min(Math.max(0, y), sizeY - 1);
        z = Math.min(Math.max(0, z), sizeZ - 1);
        int idx = (y * sizeZ + z) * sizeX + x;
        if (volume.useShortIndices) {
            int offset = idx * 2;
            if (offset + 1 >= volume.voxels.length) {
                return 0;
            }
            int lo = volume.voxels[offset] & 0xFF;
            int hi = volume.voxels[offset + 1] & 0xFF;
            return (hi << 8) | lo;
        } else {
            if (idx >= volume.voxels.length) {
                return 0;
            }
            return volume.voxels[idx] & 0xFF;
        }
    }
}
