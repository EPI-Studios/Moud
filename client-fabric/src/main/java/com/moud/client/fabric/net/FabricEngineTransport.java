package com.moud.client.fabric.net;

import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import com.moud.net.transport.TransportFragments;
import com.moud.net.transport.TransportFrames;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FabricEngineTransport implements Transport {
    private static final int MAX_PACKET_BYTES = 30_000;

    private final Queue<TransportFrames.DecodedFrame> inbound = new ArrayDeque<>();
    private final Map<Integer, TransportFragments.PartialMessage> partials = TransportFragments.newPartialStore();
    private final AtomicInteger nextMessageId = new AtomicInteger(1);
    private BiConsumer<Lane, byte[]> receiver = (lane, payload) -> {
    };

    public void acceptServerPayload(byte[] data) {
        if (data == null) {
            return;
        }
        try {
            byte[] frame = TransportFragments.accept(partials, data);
            if (frame == null) {
                return;
            }
            TransportFrames.DecodedFrame decoded = TransportFrames.decode(frame);
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
        List<byte[]> packets = TransportFragments.encode(frame, MAX_PACKET_BYTES, nextMessageId.getAndIncrement());
        for (byte[] packet : packets) {
            ClientPlayNetworking.send(new EnginePayload(packet));
        }
    }

    @Override
    public void tick() {
        TransportFrames.DecodedFrame frame;
        while ((frame = inbound.poll()) != null) {
            receiver.accept(frame.lane(), frame.payload());
        }
    }
}
