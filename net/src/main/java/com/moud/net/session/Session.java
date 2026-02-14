package com.moud.net.session;

import com.moud.core.ProtocolVersions;
import com.moud.net.protocol.Hello;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.ServerHello;
import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import com.moud.net.wire.WireMessages;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Session {
    private final SessionRole role;
    private final Transport transport;
    private SessionState state = SessionState.DISCONNECTED;
    private Consumer<String> logSink = s -> {
    };
    private BiConsumer<Lane, Message> messageHandler = (lane, message) -> {
    };

    public Session(SessionRole role, Transport transport) {
        this.role = Objects.requireNonNull(role);
        this.transport = Objects.requireNonNull(transport);
        this.transport.setReceiver(this::onReceive);
    }

    public SessionState state() {
        return state;
    }

    public void setLogSink(Consumer<String> logSink) {
        this.logSink = Objects.requireNonNull(logSink);
    }

    public void setMessageHandler(BiConsumer<Lane, Message> messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler);
    }

    public void start() {
        if (state != SessionState.DISCONNECTED) {
            throw new IllegalStateException("Session already started: " + state);
        }
        state = SessionState.HANDSHAKING;
        if (role == SessionRole.CLIENT) {
            send(Lane.CONTROL, new Hello(ProtocolVersions.PROTOCOL_VERSION));
            logSink.accept("client: sent Hello");
        } else {
            logSink.accept("server: waiting Hello");
        }
    }

    public void tick() {
        transport.tick();
    }

    public void send(Lane lane, Message message) {
        transport.send(Objects.requireNonNull(lane), WireMessages.encode(Objects.requireNonNull(message)));
    }

    private void onReceive(Lane lane, byte[] payload) {
        Message message = WireMessages.decode(payload);
        if (lane == Lane.CONTROL) {
            switch (role) {
                case CLIENT -> onClientControl(message);
                case SERVER -> onServerControl(message);
            }
            return;
        }

        if (state != SessionState.CONNECTED) {
            return;
        }
        messageHandler.accept(lane, message);
    }

    private void onClientControl(Message message) {
        if (state != SessionState.HANDSHAKING) {
            return;
        }
        if (!(message instanceof ServerHello(int protocolVersion))) {
            fail("client: expected ServerHello, got " + message.type());
            return;
        }
        if (protocolVersion != ProtocolVersions.PROTOCOL_VERSION) {
            fail("client: protocol mismatch " + protocolVersion);
            return;
        }
        state = SessionState.CONNECTED;
        logSink.accept("client: connected");
    }

    private void onServerControl(Message message) {
        if (state != SessionState.HANDSHAKING) {
            return;
        }
        if (!(message instanceof Hello(int protocolVersion))) {
            fail("server: expected Hello, got " + message.type());
            return;
        }
        if (protocolVersion != ProtocolVersions.PROTOCOL_VERSION) {
            send(Lane.CONTROL, new ServerHello(protocolVersion));
            fail("server: protocol mismatch " + protocolVersion);
            return;
        }
        send(Lane.CONTROL, new ServerHello(ProtocolVersions.PROTOCOL_VERSION));
        state = SessionState.CONNECTED;
        logSink.accept("server: connected");
    }

    private void fail(String msg) {
        state = SessionState.FAILED;
        logSink.accept(msg);
    }
}
