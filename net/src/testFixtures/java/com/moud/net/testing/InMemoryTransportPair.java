package com.moud.net.testing;

import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiConsumer;

public final class InMemoryTransportPair {
    public record Pair(Transport a, Transport b) {
    }

    public static Pair create() {
        Endpoint a = new Endpoint();
        Endpoint b = new Endpoint();
        a.peer = b;
        b.peer = a;
        return new Pair(a, b);
    }

    private static final class Endpoint implements Transport {
        private final Queue<Frame> inbox = new ArrayDeque<>();
        private BiConsumer<Lane, byte[]> receiver = (lane, payload) -> {
        };
        private Endpoint peer;

        @Override
        public void setReceiver(BiConsumer<Lane, byte[]> receiver) {
            this.receiver = Objects.requireNonNull(receiver);
        }

        @Override
        public void send(Lane lane, byte[] payload) {
            Objects.requireNonNull(lane);
            Objects.requireNonNull(payload);
            if (peer == null) {
                return;
            }
            peer.inbox.add(new Frame(lane, payload));
        }

        @Override
        public void tick() {
            Frame frame;
            while ((frame = inbox.poll()) != null) {
                receiver.accept(frame.lane, frame.payload);
            }
        }

        private record Frame(Lane lane, byte[] payload) {
        }
    }
}
