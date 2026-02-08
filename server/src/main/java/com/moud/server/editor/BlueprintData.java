package com.moud.server.editor;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintData {
    public String name;
    public float[] origin = new float[3];
    public float[] size = new float[3];
    public List<SceneObjectData> objects = new ArrayList<>();
    public List<MarkerData> markers = new ArrayList<>();
    public BlockData blocks;

    public static final class SceneObjectData {
        public String type;
        public String label;
        public float[] position;
        public float[] rotation;
        public float[] scale;
        public String modelPath;
        public String texture;
        public float[] boundsMin;
        public float[] boundsMax;
    }

    public static final class MarkerData {
        public String name;
        public float[] position;
    }

    public static final class BlockData {
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public List<String> palette;
        public byte[] voxels;
    }
}
