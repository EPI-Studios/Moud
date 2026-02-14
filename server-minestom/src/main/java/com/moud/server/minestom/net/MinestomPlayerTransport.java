package com.moud.server.minestom.net;

import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import com.moud.net.transport.TransportFrames;
import net.minestom.server.entity.Player;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiConsumer;

public final class MinestomPlayerTransport implements Transport {
    private final Player player;
    private final String channel;
    private final Queue<TransportFrames.DecodedFrame> inbound = new ArrayDeque<>();
    private BiConsumer<Lane, byte[]> receiver = (lane, payload) -> {
    };

    public MinestomPlayerTransport(Player player, String channel) {
        this.player = Objects.requireNonNull(player);
        this.channel = Objects.requireNonNull(channel);
    }

    public String channel() {
        return channel;
    }

    public void acceptPluginMessage(String identifier, byte[] message) {
        if (!channel.equals(identifier) || message == null) {
            return;
        }
        try {
            TransportFrames.DecodedFrame decoded = TransportFrames.decode(message);
            inbound.add(decoded);
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed frames; clients may send unexpected data on the channel.
        }
    }

    @Override
    public void setReceiver(BiConsumer<Lane, byte[]> receiver) {
        this.receiver = Objects.requireNonNull(receiver);
    }

    @Override
    public void send(Lane lane, byte[] payload) {
        byte[] frame = TransportFrames.encode(lane, payload);
        player.sendPluginMessage(channel, frame);
    }

    @Override
    public void tick() {
        TransportFrames.DecodedFrame frame;
        while ((frame = inbound.poll()) != null) {
            receiver.accept(frame.lane(), frame.payload());
        }
    }
}
