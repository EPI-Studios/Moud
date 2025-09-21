package com.moud.client.network;

import com.moud.network.MoudPackets;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientPacketReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketReceiver.class);

    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(DataPayload.ID, (payload, context) -> {
            Identifier channel = payload.channel();
            byte[] data = payload.data();

            context.client().execute(() -> {
                try {
                    ClientPacketWrapper.handleIncoming(channel.toString(), data, context.player());
                } catch (Exception e) {
                    LOGGER.error("Failed to handle incoming Moud packet on channel {}", channel, e);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MoudPayload.ID, (payload, context) -> {
            Identifier channel = payload.channel();
            byte[] data = payload.data();
            context.client().execute(() -> {
                try {
                    ClientPacketWrapper.handleIncoming(channel.toString(), data, context.player());
                } catch (Exception e) {
                    LOGGER.error("Failed to handle incoming Moud packet on channel {}", channel, e);
                }
            });
        });


        LOGGER.info("Registered generic Moud packet receiver.");
    }
}