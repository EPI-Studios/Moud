package com.moud.server.bridge;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.network.NetworkBuffer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


final class MarkerDataMessage {

    private static final byte FLAG_HAS_REGION = 1;
    private static final byte FLAG_HAS_LINE_COLOR = 2;
    private static final byte FLAG_HAS_LINE_THICKNESS = 4;
    private static final byte FLAG_HAS_FACE_COLOR = 8;

    private MarkerDataMessage() {
    }

    static byte[] encode(Collection<MarkerInfo> changed, Collection<UUID> removed) {
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, changed.size());
            for (MarkerInfo info : changed) {
                buffer.write(NetworkBuffer.UUID, info.uuid());
                buffer.write(NetworkBuffer.VECTOR3D, info.position());
                writeOptionalString(buffer, info.displayName());

                byte flags = 0;
                MarkerRegion region = info.region();
                if (region != null) {
                    flags |= FLAG_HAS_REGION;
                    if (region.lineArgb() != 0) flags |= FLAG_HAS_LINE_COLOR;
                    if (region.lineThickness() != 0.0f) flags |= FLAG_HAS_LINE_THICKNESS;
                    if (region.faceArgb() != 0) flags |= FLAG_HAS_FACE_COLOR;
                }
                buffer.write(NetworkBuffer.BYTE, flags);

                if (region != null) {
                    buffer.write(NetworkBuffer.VECTOR3D, region.min());
                    buffer.write(NetworkBuffer.VECTOR3D, region.max());
                    if ((flags & FLAG_HAS_LINE_COLOR) != 0) {
                        buffer.write(NetworkBuffer.INT, region.lineArgb());
                    }
                    if ((flags & FLAG_HAS_LINE_THICKNESS) != 0) {
                        buffer.write(NetworkBuffer.FLOAT, region.lineThickness());
                    }
                    if ((flags & FLAG_HAS_FACE_COLOR) != 0) {
                        buffer.write(NetworkBuffer.INT, region.faceArgb());
                    }
                }
            }

            buffer.write(NetworkBuffer.VAR_INT, removed.size());
            for (UUID uuid : removed) {
                buffer.write(NetworkBuffer.UUID, uuid);
            }
        });
    }

    record MarkerInfo(UUID uuid, Pos position, String displayName, MarkerRegion region) {
    }

    record MarkerRegion(Vec min, Vec max, int lineArgb, float lineThickness, int faceArgb) {
    }

    private static void writeOptionalString(NetworkBuffer buffer, String value) {
        if (value == null) {
            buffer.write(NetworkBuffer.BOOLEAN, false);
        } else {
            buffer.write(NetworkBuffer.BOOLEAN, true);
            buffer.write(NetworkBuffer.STRING, value);
        }
    }
}
