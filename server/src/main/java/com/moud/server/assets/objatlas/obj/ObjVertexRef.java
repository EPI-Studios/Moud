package com.moud.server.assets.objatlas.obj;

/**
 * Reference to a single OBJ vertex in a face.
 * Indices are 0-based (converted from OBJ's 1-based indices).
 * A value of -1 means "not present" (for vt or vn).
 */
public record ObjVertexRef(int positionIndex, int texCoordIndex, int normalIndex) {
}
