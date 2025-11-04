package com.moud.client.axiom;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;


public abstract class AbstractMoudMarkerScreen extends Screen {
    protected final UUID markerId;
    protected final NbtCompound originalData;

    private static Class<?> manipulateEntryClass;
    private static Class<?> manipulatePacketClass;
    private static Constructor<?> entryConstructor;
    private static Constructor<?> packetConstructor;

    static {
        try {

            manipulateEntryClass = Class.forName("com.moulberry.axiom.packets.AxiomServerboundManipulateEntity$ManipulateEntry");
            manipulatePacketClass = Class.forName("com.moulberry.axiom.packets.AxiomServerboundManipulateEntity");

            Constructor<?>[] constructors = manipulateEntryClass.getDeclaredConstructors();

            for (Constructor<?> constructor : constructors) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 3 && params[0] == UUID.class) {
                    if (NbtCompound.class.isAssignableFrom(params[2])) {
                        entryConstructor = constructor;
                        entryConstructor.setAccessible(true);
                        System.out.println("Found ManipulateEntry constructor with signature: " +
                            params[0].getSimpleName() + ", " +
                            params[1].getSimpleName() + ", " +
                            params[2].getSimpleName());
                        break;
                    }
                }
            }

            if (entryConstructor == null) {
                throw new RuntimeException("Could not find suitable ManipulateEntry constructor");
            }

            packetConstructor = manipulatePacketClass.getDeclaredConstructor(List.class);
            packetConstructor.setAccessible(true);

            System.out.println("Successfully initialized Axiom packet reflection");
        } catch (Exception e) {
            System.err.println("Failed to initialize Axiom packet classes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected AbstractMoudMarkerScreen(UUID markerId, NbtCompound data, Text title) {
        super(title);
        this.markerId = markerId;
        this.originalData = data == null ? new NbtCompound() : data.copy();
    }

    protected void sendPayload(NbtCompound payload) {
        if (payload == null) {
            return;
        }

        try {
            payload.putBoolean("axiom:modify", true);
            NbtCompound wrapper = new NbtCompound();
            wrapper.put("data", payload);

            Object entry = entryConstructor.newInstance(markerId, null, wrapper);

            Object packet = packetConstructor.newInstance(List.of(entry));
            ClientPlayNetworking.send((CustomPayload) packet);
        } catch (Exception e) {
            System.err.println("Failed to send Axiom manipulate entity packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected void closeScreen() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
