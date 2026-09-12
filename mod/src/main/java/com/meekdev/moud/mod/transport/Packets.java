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
// custom payload past 32767 bytes and a clientbound one past a megabyte, and fabric's registerLarge
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

    private Packets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> named(String path) {
        return CustomPacketPayload.createType(
                Identifier.fromNamespaceAndPath(NAMESPACE, path).toString());
    }

    // the codecs a connection needs to know, registered on both sides because both sides have to read
    // them. a registry entry is what makes a payload ours rather than an unknown blob the game drops
    public static void declare() {
        PayloadTypeRegistry<RegistryFriendlyByteBuf> down = PayloadTypeRegistry.clientboundPlay();
        down.registerLarge(Delta.TYPE, Delta.CODEC.cast(), BASELINE_CAP);
        down.register(Down.TYPE, Down.CODEC.cast());
        PayloadTypeRegistry.serverboundPlay().register(Up.TYPE, Up.CODEC.cast());
    }
}
