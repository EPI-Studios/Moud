package com.moud.api.rendering.model;

import com.moud.api.rendering.mesh.Mesh;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a strategy for loading meshes from an input stream.
 */
@FunctionalInterface
public interface ModelLoader {
    Mesh load(InputStream stream) throws IOException;
}
