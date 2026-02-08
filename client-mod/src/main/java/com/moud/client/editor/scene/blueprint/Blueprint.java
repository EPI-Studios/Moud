package com.moud.client.editor.scene.blueprint;

import java.util.ArrayList;
import java.util.List;

public final class Blueprint {
    public String name;
    public float[] origin = new float[3];
    public float[] size = new float[3];
    public List<BlueprintObject> objects = new ArrayList<>();
    public List<BlueprintMarker> markers = new ArrayList<>();
    public BlockVolume blocks;

    public static final class BlockVolume {
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public List<String> palette;
        public byte[] voxels;
        public boolean useShortIndices;
        public List<BlockEntityData> blockEntities;
    }

    public static final class BlockEntityData {
        public int x;
        public int y;
        public int z;
        public String nbt;
    }
}
