package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.adapter.physics.ClientPhysics;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Trace {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/trace");

    private static final int ROWS = 1000;

    private static @Nullable Writer out;
    private static int written;
    private static boolean done;

    private static boolean armed;

    private Trace() {}

    public static void frame(float partialTick) {
        if (done || !MoudMod.features().isOn(Feature.TRACE)) return;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer me = client.player;
        if (me == null) return;
        Character body = ClientScene.own();
        if (body == null) return;

        String deck = ClientPhysics.deckTrace(me.getX(), me.getY(), me.getZ());
        if (!armed) {
            if (deck.startsWith("none")) return;
            armed = true;
        }

        try {
            if (out == null) open();
            out.write(row(client, me, body, deck, partialTick));
            out.flush();
            written++;
            if (written >= ROWS) close();
        } catch (IOException failed) {
            LOGGER.warn("trace stopped: {}", failed.getMessage());
            done = true;
            out = null;
        }
    }

    private static void open() throws IOException {
        Path at = Path.of(System.getProperty("user.dir"), "moud-trace.tsv");
        out = Files.newBufferedWriter(at, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        out.write(columns());
        LOGGER.info("tracing a rider to {} for {} frames", at.toAbsolutePath(), ROWS);
    }

    private static void close() throws IOException {
        out.flush();
        out.close();
        out = null;
        done = true;
        LOGGER.info("trace finished, {} rows", written);
    }

    private static String columns() {
        return String.join("\t",
                "nanos", "tick", "partial",
                "eX", "eY", "eZ", "eOldX", "eOldY", "eOldZ",
                "eYaw", "eYawO", "eBodyYaw", "eBodyYawO", "eHeadYaw",
                "eVelX", "eVelY", "eVelZ", "onGround", "eyeHeight",
                "camX", "camY", "camZ",
                "parent", "localX", "localY", "localZ", "worldX", "worldY", "worldZ",
                "drawnX", "drawnY", "drawnZ", "moving",
                "headX", "headY", "headZ", "headToCam",
                ClientPhysics.deckColumns()) + "\n";
    }

    private static String row(Minecraft client, LocalPlayer me, Character body, String deck,
                              float partialTick) {
        net.minecraft.world.phys.Vec3 eye = client.gameRenderer.getMainCamera().position();
        net.minecraft.world.phys.Vec3 vel = me.getDeltaMovement();

        Instance hangs = body.parent();
        CFrame world = Transforms.world(body);
        CFrame drawn = ClientScene.motion().sample(body, partialTick);
        Vec3 head = headAt(body, partialTick);

        StringBuilder row = new StringBuilder(512);
        add(row, System.nanoTime());
        add(row, me.tickCount);
        add(row, partialTick);
        add(row, me.getX());
        add(row, me.getY());
        add(row, me.getZ());
        add(row, me.xOld);
        add(row, me.yOld);
        add(row, me.zOld);
        add(row, me.getYRot());
        add(row, me.yRotO);
        add(row, me.yBodyRot);
        add(row, me.yBodyRotO);
        add(row, me.yHeadRot);
        add(row, vel.x);
        add(row, vel.y);
        add(row, vel.z);
        row.append(me.onGround()).append('\t');
        add(row, me.getEyeHeight());
        add(row, eye.x);
        add(row, eye.y);
        add(row, eye.z);
        row.append(hangs == null ? "none" : hangs.name()).append('\t');
        add(row, body.cframe.position().x());
        add(row, body.cframe.position().y());
        add(row, body.cframe.position().z());
        add(row, world.position().x());
        add(row, world.position().y());
        add(row, world.position().z());
        add(row, drawn.position().x());
        add(row, drawn.position().y());
        add(row, drawn.position().z());
        row.append(ClientScene.motion().isMoving(body)).append('\t');
        add(row, head.x());
        add(row, head.y());
        add(row, head.z());
        add(row, Math.sqrt(Math.pow(head.x() - eye.x, 2)
                + Math.pow(head.y() - eye.y, 2)
                + Math.pow(head.z() - eye.z, 2)));
        row.append(deck).append('\n');
        return row.toString();
    }

    private static Vec3 headAt(Character body, float partialTick) {
        return body.child("head") instanceof Instance head
                ? ClientScene.motion().sample(head, partialTick).position()
                : Vec3.ZERO;
    }

    private static void add(StringBuilder row, double value) {
        row.append(String.format(Locale.ROOT, "%.6f", value)).append('\t');
    }

    private static void add(StringBuilder row, long value) {
        row.append(value).append('\t');
    }
}
