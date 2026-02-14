package com.moud.client.fabric.net;

import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import com.moud.net.transport.TransportFrames;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FabricEngineTransport implements Transport {
    private final Queue<TransportFrames.DecodedFrame> inbound = new ArrayDeque<>();
    private BiConsumer<Lane, byte[]> receiver = (lane, payload) -> {
    };

    public void acceptServerPayload(byte[] data) {
        if (data == null) {
            return;
        }
        try {
            TransportFrames.DecodedFrame decoded = TransportFrames.decode(data);
            inbound.add(decoded);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void setReceiver(BiConsumer<Lane, byte[]> receiver) {
        this.receiver = Objects.requireNonNull(receiver);
    }

    @Override
    public void send(Lane lane, byte[] payload) {
        byte[] frame = TransportFrames.encode(lane, payload);
        ClientPlayNetworking.send(new EnginePayload(frame));
    }

    @Override
    public void tick() {
        TransportFrames.DecodedFrame frame;
        while ((frame = inbound.poll()) != null) {
            receiver.accept(frame.lane(), frame.payload());
        }
    }
}
