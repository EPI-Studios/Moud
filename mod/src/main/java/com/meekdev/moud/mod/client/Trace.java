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

// every number behind one frame of your own body, written to a file
//
// a shake is the one bug that cannot be screenshotted and cannot be described: "it trembles" is true
// of a millimetre and of a hand's width, and asking someone to read five numbers off an overlay and
// report them back is a slow way to be wrong twice. this writes the lot -- the entity, the camera,
// the body, the limbs, the deck and the three poses a rider is projected through -- one row a frame,
// so whoever is looking can see which column moves and when
//
// it starts when you stand on something that moves and stops after ten seconds of it. an unbounded
// trace is a full disk, and ten seconds at a hundred frames is two hundred turns of a deck
public final class Trace {

    private static final Logger LOGGER = LoggerFactory.getLogger("moud/trace");

    // ten seconds at a hundred frames a second, which is two hundred ticks of anything to look at
    private static final int ROWS = 1000;

    private static @Nullable Writer out;
    private static int written;
    private static boolean done;

    // it starts the first time you stand on something that moves and then keeps writing whatever you
    // do, so stepping off and back on is in the file too. waiting for a deck every row would mean a
    // file that says nothing about the moment the fault appears or stops
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
            // flushed every row, because the usual way a session ends is being killed and a buffered
            // trace that was never flushed is an empty file
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
                // the clock this frame was drawn on
                "nanos", "tick", "partial",
                // the entity, which is what the game's own camera is placed from
                "eX", "eY", "eZ", "eOldX", "eOldY", "eOldZ",
                "eYaw", "eYawO", "eBodyYaw", "eBodyYawO", "eHeadYaw",
                "eVelX", "eVelY", "eVelZ", "onGround", "eyeHeight",
                // where the camera actually ended up, after everything had its say
                "camX", "camY", "camZ",
                // the body: what it hangs off, its own frame, and where that puts it
                "parent", "localX", "localY", "localZ", "worldX", "worldY", "worldZ",
                "drawnX", "drawnY", "drawnZ", "moving",
                // the head as drawn, and how far that is from the camera. standing still on anything
                // at all, that distance is a constant -- so this column is the shake itself
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
