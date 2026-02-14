package com.moud.net.transport;

import java.util.function.BiConsumer;

public interface Transport {
    void setReceiver(BiConsumer<Lane, byte[]> receiver);

    void send(Lane lane, byte[] payload);

    void tick();
}
