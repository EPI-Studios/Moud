package com.moud.server.assets.objatlas.obj;

import java.util.List;

/**
 * One face in an OBJ file. It can be a triangle, quad, or polygon.
 */
public record ObjFace(
        List<ObjVertexRef> vertices,
        String materialName,   // may be null if no usemtl
        String groupName,      // may be null if no "g"
        int smoothingGroup     // -1 means "off" or not specified
) {
}
