package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.amnetic.client.pipeline.FrameContext;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.EditorView;
import com.meekdev.moud.mod.client.ClientScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

public final class Effects {

    record View(Vector3 eye, Vector3 right, Vector3 up, Vector3 forward) {}

    private static final double LONGEST_FRAME = 0.1;
    private static final int FULL_LIGHT = 240 | 240 << 16;

    private static final QuadBatch BATCH = new QuadBatch();
    private static long last;
    private static double clock;
    private static boolean broken;

    private Effects() {}

    public static void register() {
        Pipeline.add(RenderStage.AFTER_WATER, 50, "moud effects", context -> EditorView.filled(() -> draw(context)));
        Highlights.register();
    }

    public static void frame() {
        long now = System.nanoTime();
        double seconds = last == 0 ? 0 : Math.min((now - last) / 1e9, LONGEST_FRAME);
        last = now;
        Minecraft client = Minecraft.getInstance();
        if (client.isPaused()) seconds = 0;
        clock += seconds;
        InstanceTree tree = ClientScene.tree();
        if (tree == null || broken) return;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        try {
            Emitters.step(tree, seconds, partialTick);
            Trails.step(tree, clock, partialTick);
        } catch (RuntimeException e) {
            broken = true;
            MoudMod.LOG.error("effects failed to step and are off until restart", e);
        }
    }

    private static void draw(FrameContext context) {
        CameraSnapshot camera = context.camera();
        InstanceTree tree = ClientScene.tree();
        if (camera == null || tree == null || broken) return;
        try {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Matrix4f view = camera.view;
            View seen = new View(new Vector3(camera.eye.x, camera.eye.y, camera.eye.z),
                    new Vector3(view.m00(), view.m10(), view.m20()),
                    new Vector3(view.m01(), view.m11(), view.m21()),
                    new Vector3(-view.m02(), -view.m12(), -view.m22()));
            BATCH.begin(camera.eye.x, camera.eye.y, camera.eye.z);
            SurfaceDecals.draw(BATCH, tree, partialTick);
            Emitters.draw(BATCH, seen);
            Beams.draw(BATCH, seen, tree, partialTick, clock);
            Trails.draw(BATCH, seen, clock);
            Selections.draw(BATCH, tree, partialTick);
            Ropes.draw(BATCH, seen, tree);
            BATCH.draw(camera);
        } catch (RuntimeException e) {
            broken = true;
            MoudMod.LOG.error("effects failed to draw and are off until restart", e);
        }
    }

    static CFrame world(Instance instance, float partialTick) {
        return ClientScene.motion().sample(instance, partialTick);
    }

    static int light(Vector3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return FULL_LIGHT;
        return LevelRenderer.getLightCoords(level, BlockPos.containing(at.x(), at.y(), at.z()));
    }
}
