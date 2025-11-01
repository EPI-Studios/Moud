package com.moud.server.bridge;

import net.minestom.server.network.NetworkBuffer;

final class EnableMessage {

    private EnableMessage() {
    }

    static byte[] encode(boolean enable,
                         int maxBufferSize,
                         int blueprintVersion,
                         int customDataOverrides,
                         int rotationOverrides) {
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.BOOLEAN, enable);
            buffer.write(NetworkBuffer.INT, maxBufferSize);
            buffer.write(NetworkBuffer.VAR_INT, blueprintVersion);
            buffer.write(NetworkBuffer.VAR_INT, customDataOverrides);
            buffer.write(NetworkBuffer.VAR_INT, rotationOverrides);
        });
    }
}
