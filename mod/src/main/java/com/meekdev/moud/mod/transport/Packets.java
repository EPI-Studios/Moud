package com.meekdev.moud.mod.transport;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Packets {

    public static final String NAMESPACE = "moud";

    public static final int BASELINE_CAP = 8 * 1024 * 1024;

    private static final int POST_CAP = 32767;

    public record Delta(byte[] bytes) implements CustomPacketPayload {

        public static final Type<Delta> TYPE = named("delta");

        public static final StreamCodec<FriendlyByteBuf, Delta> CODEC = CustomPacketPayload.codec(
                (delta, out) -> out.writeByteArray(delta.bytes()),
                in -> new Delta(in.readByteArray(BASELINE_CAP)));

        @Override
        public Type<Delta> type() {
            return TYPE;
        }
    }

    public record Up(int remote, byte[] args) implements CustomPacketPayload {

        public static final Type<Up> TYPE = named("up");

        public static final StreamCodec<FriendlyByteBuf, Up> CODEC = CustomPacketPayload.codec(
                (up, out) -> {
                    out.writeVarInt(up.remote());
                    out.writeByteArray(up.args());
                },
                in -> new Up(in.readVarInt(), in.readByteArray(POST_CAP)));

        @Override
        public Type<Up> type() {
            return TYPE;
        }
    }

    public record Down(int remote, byte[] args) implements CustomPacketPayload {

        public static final Type<Down> TYPE = named("down");

        public static final StreamCodec<FriendlyByteBuf, Down> CODEC = CustomPacketPayload.codec(
                (down, out) -> {
                    out.writeVarInt(down.remote());
                    out.writeByteArray(down.args());
                },
                in -> new Down(in.readVarInt(), in.readByteArray(POST_CAP)));

        @Override
        public Type<Down> type() {
            return TYPE;
        }
    }

    public record ChatDown(int kind, long id, int channel, int source, int body, String text, String prefix,
                           String metadata, long timestamp, String status) implements CustomPacketPayload {

        public static final int LINE = 0;
        public static final int EDIT = 1;
        public static final int DELETE = 2;
        public static final int STATUS = 3;
        public static final int CLEAR = 4;

        public static final Type<ChatDown> TYPE = named("chat_down");

        public static final StreamCodec<FriendlyByteBuf, ChatDown> CODEC = CustomPacketPayload.codec(
                (m, out) -> {
                    out.writeVarInt(m.kind());
                    out.writeVarLong(m.id());
                    out.writeVarInt(m.channel() + 1);
                    out.writeVarInt(m.source() + 1);
                    out.writeVarInt(m.body() + 1);
                    out.writeUtf(m.text(), CHAT_TEXT);
                    out.writeUtf(m.prefix(), CHAT_TEXT);
                    out.writeUtf(m.metadata(), CHAT_TEXT);
                    out.writeVarLong(m.timestamp());
                    out.writeUtf(m.status(), 64);
                },
                in -> new ChatDown(in.readVarInt(), in.readVarLong(), in.readVarInt() - 1, in.readVarInt() - 1,
                        in.readVarInt() - 1, in.readUtf(CHAT_TEXT), in.readUtf(CHAT_TEXT), in.readUtf(CHAT_TEXT),
                        in.readVarLong(), in.readUtf(64)));

        @Override
        public Type<ChatDown> type() {
            return TYPE;
        }
    }

    public record ChatUp(int channel, String text) implements CustomPacketPayload {

        public static final Type<ChatUp> TYPE = named("chat_up");

        public static final StreamCodec<FriendlyByteBuf, ChatUp> CODEC = CustomPacketPayload.codec(
                (m, out) -> {
                    out.writeVarInt(m.channel() + 1);
                    out.writeUtf(m.text(), 4096);
                },
                in -> new ChatUp(in.readVarInt() - 1, in.readUtf(4096)));

        @Override
        public Type<ChatUp> type() {
            return TYPE;
        }
    }

    public record PromptUp(int prompt, int kind) implements CustomPacketPayload {

        public static final int TRIGGERED = 0;
        public static final int HOLD_BEGAN = 1;
        public static final int HOLD_ENDED = 2;

        public static final Type<PromptUp> TYPE = named("prompt_up");

        public static final StreamCodec<FriendlyByteBuf, PromptUp> CODEC = CustomPacketPayload.codec(
                (m, out) -> {
                    out.writeVarInt(m.prompt());
                    out.writeVarInt(m.kind());
                },
                in -> new PromptUp(in.readVarInt(), in.readVarInt()));

        @Override
        public Type<PromptUp> type() {
            return TYPE;
        }
    }

    public record DebugDown(int kind, double[] numbers, String text, int argb, double seconds) implements CustomPacketPayload {

        public static final Type<DebugDown> TYPE = named("debug_down");

        public static final StreamCodec<FriendlyByteBuf, DebugDown> CODEC = CustomPacketPayload.codec(
                (m, out) -> {
                    out.writeVarInt(m.kind());
                    out.writeVarInt(m.numbers().length);
                    for (double n : m.numbers()) out.writeDouble(n);
                    out.writeUtf(m.text(), 1024);
                    out.writeInt(m.argb());
                    out.writeDouble(m.seconds());
                },
                in -> {
                    int kind = in.readVarInt();
                    int count = Math.min(in.readVarInt(), 16);
                    double[] numbers = new double[count];
                    for (int n = 0; n < count; n++) numbers[n] = in.readDouble();
                    return new DebugDown(kind, numbers, in.readUtf(1024), in.readInt(), in.readDouble());
                });

        @Override
        public Type<DebugDown> type() {
            return TYPE;
        }
    }

    public record PilotDown(int kind, double[] waypoints) implements CustomPacketPayload {

        public static final int WALK = 0;
        public static final int JUMP = 1;
        public static final int STOP = 2;

        public static final Type<PilotDown> TYPE = named("pilot_down");

        public static final StreamCodec<FriendlyByteBuf, PilotDown> CODEC = CustomPacketPayload.codec(
                (m, out) -> {
                    out.writeVarInt(m.kind());
                    out.writeVarInt(m.waypoints().length);
                    for (double n : m.waypoints()) out.writeDouble(n);
                },
                in -> {
                    int kind = in.readVarInt();
                    int count = Math.min(in.readVarInt(), 3 * 4096);
                    double[] waypoints = new double[count];
                    for (int n = 0; n < count; n++) waypoints[n] = in.readDouble();
                    return new PilotDown(kind, waypoints);
                });

        @Override
        public Type<PilotDown> type() {
            return TYPE;
        }
    }

    public record PilotUp(int kind) implements CustomPacketPayload {

        public static final int CANCELLED = 0;

        public static final Type<PilotUp> TYPE = named("pilot_up");

        public static final StreamCodec<FriendlyByteBuf, PilotUp> CODEC = CustomPacketPayload.codec(
                (m, out) -> out.writeVarInt(m.kind()), in -> new PilotUp(in.readVarInt()));

        @Override
        public Type<PilotUp> type() {
            return TYPE;
        }
    }

    public record ResyncUp() implements CustomPacketPayload {

        public static final Type<ResyncUp> TYPE = named("resync_up");

        public static final StreamCodec<FriendlyByteBuf, ResyncUp> CODEC = CustomPacketPayload.codec(
                (m, out) -> {}, in -> new ResyncUp());

        @Override
        public Type<ResyncUp> type() {
            return TYPE;
        }
    }

    private static final int CHAT_TEXT = 16384;

    private Packets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> named(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    public static void declare() {
        PayloadTypeRegistry<RegistryFriendlyByteBuf> down = PayloadTypeRegistry.clientboundPlay();
        down.registerLarge(Delta.TYPE, Delta.CODEC.cast(), BASELINE_CAP);
        down.register(Down.TYPE, Down.CODEC.cast());
        down.register(ChatDown.TYPE, ChatDown.CODEC.cast());
        down.register(DebugDown.TYPE, DebugDown.CODEC.cast());
        down.register(PilotDown.TYPE, PilotDown.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(Up.TYPE, Up.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ChatUp.TYPE, ChatUp.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(PromptUp.TYPE, PromptUp.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(PilotUp.TYPE, PilotUp.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ResyncUp.TYPE, ResyncUp.CODEC.cast());
    }
}
