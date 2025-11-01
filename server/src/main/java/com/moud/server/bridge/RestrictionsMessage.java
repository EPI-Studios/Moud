package com.moud.server.bridge;

import net.minestom.server.coordinate.Point;
import net.minestom.server.network.NetworkBuffer;

import java.util.EnumSet;
import java.util.Set;


final class RestrictionsMessage {

    private RestrictionsMessage() {
    }

    static byte[] encode(EnumSet<AxiomPermission> allowed,
                         EnumSet<AxiomPermission> denied,
                         int infiniteReachLimit,
                         Set<PlotBox> bounds) {
        return NetworkBuffer.makeArray(buffer -> {
            writePermissionSet(buffer, allowed);
            writePermissionSet(buffer, denied);
            buffer.write(NetworkBuffer.INT, infiniteReachLimit);
            buffer.write(NetworkBuffer.VAR_INT, bounds.size());
            for (PlotBox plot : bounds) {
                buffer.write(NetworkBuffer.BLOCK_POSITION, plot.min());
                buffer.write(NetworkBuffer.BLOCK_POSITION, plot.max());
            }
        });
    }

    private static void writePermissionSet(NetworkBuffer buffer, EnumSet<AxiomPermission> permissions) {
        buffer.write(NetworkBuffer.VAR_INT, permissions.size());
        for (AxiomPermission permission : permissions) {
            buffer.write(NetworkBuffer.STRING, permission.getInternalName());
        }
    }

    record PlotBox(Point min, Point max) {
    }
}
