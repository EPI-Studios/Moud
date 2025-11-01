package com.moud.server.bridge;

import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.NetworkBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;


final class ManipulateEntityMessage {

    private ManipulateEntityMessage() {
    }

    static List<Entry> parse(byte[] payload) {
        NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(payload));
        int count = buffer.read(NetworkBuffer.VAR_INT);
        List<Entry> entries = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            UUID uuid = buffer.read(NetworkBuffer.UUID);
            byte flags = buffer.read(NetworkBuffer.BYTE);

            EnumSet<Relative> relatives = EnumSet.noneOf(Relative.class);
            Pos position = null;

            if (flags != (byte) 0xFF) {
                if ((flags & 0x01) != 0) relatives.add(Relative.X);
                if ((flags & 0x02) != 0) relatives.add(Relative.Y);
                if ((flags & 0x04) != 0) relatives.add(Relative.Z);
                if ((flags & 0x08) != 0) relatives.add(Relative.Y_ROT);
                if ((flags & 0x10) != 0) relatives.add(Relative.X_ROT);

                double x = buffer.read(NetworkBuffer.DOUBLE);
                double y = buffer.read(NetworkBuffer.DOUBLE);
                double z = buffer.read(NetworkBuffer.DOUBLE);
                float yaw = buffer.read(NetworkBuffer.FLOAT);
                float pitch = buffer.read(NetworkBuffer.FLOAT);
                position = new Pos(x, y, z, yaw, pitch);
            }

            BinaryTag nbt = buffer.read(NetworkBuffer.NBT);
            PassengerManipulation passenger = buffer.read(NetworkBuffer.Enum(PassengerManipulation.class));

            if (passenger == PassengerManipulation.ADD_LIST || passenger == PassengerManipulation.REMOVE_LIST) {
                int passengerCount = buffer.read(NetworkBuffer.VAR_INT);
                for (int p = 0; p < passengerCount; p++) {
                    buffer.read(NetworkBuffer.UUID); // discard passenger UUIDs
                }
            }

            entries.add(new Entry(uuid, relatives, position, nbt, passenger));
        }
        return entries;
    }

    enum Relative {
        X, Y, Z, Y_ROT, X_ROT
    }

    enum PassengerManipulation {
        NONE,
        REMOVE_ALL,
        ADD_LIST,
        REMOVE_LIST
    }

    record Entry(UUID uuid,
                 EnumSet<Relative> relative,
                 Pos position,
                 BinaryTag nbt,
                 PassengerManipulation passengerManipulation) {
    }
}
