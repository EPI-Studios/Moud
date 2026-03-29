package com.moud.net.session;

import com.moud.core.ProtocolVersions;
import com.moud.net.protocol.Chunkable;
import com.moud.net.protocol.Hello;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.ServerHello;
import com.moud.net.transport.Lane;
import com.moud.net.transport.Transport;
import com.moud.net.transport.TransportFrames;
import com.moud.net.wire.WireMessages;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Session {
    private final SessionRole role;
    private final Transport transport;
    private SessionState state = SessionState.DISCONNECTED;
    private Consumer<String> logSink = s -> {
    };
    private BiConsumer<Lane, Message> messageHandler = (lane, message) -> {
    };
    private Supplier<ServerHello> serverHelloSupplier = () -> new ServerHello(ProtocolVersions.PROTOCOL_VERSION, false);
    private ServerHello serverHello;

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

    public void setServerHelloSupplier(Supplier<ServerHello> serverHelloSupplier) {
        this.serverHelloSupplier = Objects.requireNonNull(serverHelloSupplier);
    }

    public ServerHello serverHello() {
        return serverHello;
    }

    public void start() {
        if (state != SessionState.DISCONNECTED) {
            throw new IllegalStateException("Session already started: " + state);
        }
        serverHello = null;
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

    @SuppressWarnings("unchecked")
    public void send(Lane lane, Message message) {
        Objects.requireNonNull(lane);
        Objects.requireNonNull(message);
        if (message instanceof Chunkable<?> chunkable) {
            for (Message chunk : ((Chunkable<Message>) chunkable).chunk(TransportFrames.MAX_PAYLOAD_BYTES)) {
                transport.send(lane, WireMessages.encode(chunk));
            }
        } else {
            transport.send(lane, WireMessages.encode(message));
        }
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
        if (!(message instanceof ServerHello hello)) {
            fail("client: expected ServerHello, got " + message.type());
            return;
        }
        int protocolVersion = hello.protocolVersion();
        if (protocolVersion != ProtocolVersions.PROTOCOL_VERSION) {
            fail("client: protocol mismatch " + protocolVersion + " (server) != " + ProtocolVersions.PROTOCOL_VERSION + " (client)");
            return;
        }
        serverHello = hello;
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
            send(Lane.CONTROL, new ServerHello(ProtocolVersions.PROTOCOL_VERSION, false));
            fail("server: protocol mismatch " + protocolVersion + " (client) != " + ProtocolVersions.PROTOCOL_VERSION + " (server)");
            return;
        }
        ServerHello hello = serverHelloSupplier.get();
        if (hello == null || hello.protocolVersion() != ProtocolVersions.PROTOCOL_VERSION) {
            hello = new ServerHello(ProtocolVersions.PROTOCOL_VERSION, hello != null && hello.devMode());
        }
        send(Lane.CONTROL, hello);
        state = SessionState.CONNECTED;
        logSink.accept("server: connected");
    }

    private void fail(String msg) {
        state = SessionState.FAILED;
        logSink.accept(msg);
    }
}
