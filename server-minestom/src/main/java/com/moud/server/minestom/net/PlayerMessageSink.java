package com.moud.server.minestom.net;

import com.moud.net.protocol.Message;
import com.moud.net.transport.Lane;
import java.util.UUID;

@FunctionalInterface
public interface PlayerMessageSink {
    void send(UUID playerUuid, Lane lane, Message message);

    PlayerMessageSink NOOP = (uuid, lane, message) -> {};
}
