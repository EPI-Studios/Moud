package com.meekdev.moud.mod.transport;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// the three kinds of thing we put on the game's own connection
//
// on the payload cap, which is a real question with a real answer: the game refuses a serverbound
// custom payload past 32767 bytes and a clientbound one past a megabyte, and registerLarge
// takes a bigger cap and splits and reassembles for us. so nothing here chunks by hand -- a tick of
// changes is a few hundred bytes, and the one thing that can be large is a baseline, which is
// clientbound and declared large
//
// the connection is tcp, so everything here arrives, in order. that is worth being plain about: what
// an unreliable channel buys is not a lossy socket, it is a send queue that drops the stale ones when
// it falls behind instead of growing -- which is the behaviour that actually matters to a caller
//
// the two directions of a delivery are two records rather than one with a flag, because a payload's
// type() has to answer with the one it was registered as, and a record that had to remember which way
// it was read is a record that can be wrong about it
public final class Packets {

    public static final String NAMESPACE = "moud";

    // eight megabytes is far past any baseline a place should have, and still a bound -- which is the
    // point of declaring one
    public static final int BASELINE_CAP = 8 * 1024 * 1024;

    // the game's own cap for a serverbound payload. a channel's own limits are far inside it: sixteen
    // arguments and two hundred and fifty six values
    private static final int POST_CAP = 32767;

    // a tick of tree changes, or the whole tree on a join
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

    // a delivery from a client. it does not say who sent it: the far side knows, and a client that
    // could say would say whatever it liked
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

    // and one from the server, to one client or to all of them. which of the two it was is the
    // server's business and not on the wire: a client hears it either way
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

    // a chat line, an edit of one, a deletion, a status for the sender, or a clear. one shape for all of
    // them, since each is a few fields of the same message
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

    // what a player typed, and the channel they typed it into
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

    // a player pressing, holding or letting go of a proximity prompt
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

    // markup is longer than what it shows, and a place styling a line should not run out of room
    private static final int CHAT_TEXT = 16384;

    private Packets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> named(String path) {
        // createType takes a bare path and puts the game's own namespace on it
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    // the codecs a connection needs to know, registered on both sides because both sides have to read
    // them. a registry entry is what makes a payload ours rather than an unknown blob the game drops
    public static void declare() {
        PayloadTypeRegistry<RegistryFriendlyByteBuf> down = PayloadTypeRegistry.clientboundPlay();
        down.registerLarge(Delta.TYPE, Delta.CODEC.cast(), BASELINE_CAP);
        down.register(Down.TYPE, Down.CODEC.cast());
        down.register(ChatDown.TYPE, ChatDown.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(Up.TYPE, Up.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(ChatUp.TYPE, ChatUp.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(PromptUp.TYPE, PromptUp.CODEC.cast());
    }
}
