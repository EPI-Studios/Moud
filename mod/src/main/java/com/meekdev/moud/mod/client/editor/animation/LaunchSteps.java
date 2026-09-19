package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.IKControl;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.EditMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class LaunchSteps {

    private static final String STEPS = "moud.animate.steps";

    private final AnimationWorkspace workspace;
    private final AnimationSession session;
    private final Deque<String> steps = new ArrayDeque<>();
    private int waiting;

    LaunchSteps(AnimationWorkspace workspace, AnimationSession session) {
        this.workspace = workspace;
        this.session = session;
        String given = System.getProperty(STEPS, "");
        for (String step : given.split(";")) {
            if (!step.isBlank()) steps.add(step.strip());
        }
    }

    void tick() {
        if (steps.isEmpty()) return;
        if (waiting > 0) {
            waiting--;
            return;
        }
        String step = steps.peek();
        boolean anywhere = step.startsWith("wait") || step.equals("play") || step.equals("edit");
        if (!anywhere && !(EditMode.editing() && workspace.active())) return;
        steps.poll();
        MoudMod.LOG.info("animate step {}", step);
        String[] parts = step.split(":", 3);
        switch (parts[0]) {
            case "wait" -> waiting = Integer.parseInt(parts[1]);
            case "import" -> workspace.confirmImport();
            case "select" -> session.joint(parts[1]);
            case "seek" -> session.seekFree(Double.parseDouble(parts[1]));
            case "turn" -> {
                String channel = session.channel(parts[1], workspace.retargets());
                Vector3 value = vector(parts[2]);
                double at = session.keyTime();
                session.edit("Rotate " + parts[1], changed -> KeyEdits.set(changed, channel, Channel.ROTATION, at, value));
                session.selectKeys(Set.of(new KeyRef(channel, Channel.ROTATION, at)));
            }
            case "save" -> session.save();
            case "savescene" -> workspace.document().save();
            case "control" -> {
                List<Instance> controls = workspace.controls();
                if (!controls.isEmpty()) session.control(controls.getFirst());
            }
            case "drag" -> {
                if (session.control() instanceof IKControl control) ControlHandles.nudge(workspace.document(), control, vector(parts[1]));
            }
            case "undo" -> workspace.document().history().undo();
            case "shot" -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> Screenshot.grab(minecraft.gameDirectory, parts[1] + ".png", minecraft.getMainRenderTarget(), 1, message -> { }));
            }
            case "play" -> EditMode.request(false);
            case "edit" -> EditMode.request(true);
            case "log" -> log();
            default -> MoudMod.LOG.warn("unknown animate step {}", step);
        }
    }

    private void log() {
        for (Map.Entry<String, JointPose> entry : workspace.pose().entrySet()) {
            CFrame at = entry.getValue().transform();
            MoudMod.LOG.info("animate pose {} {} {}", entry.getKey(), entry.getValue().rotation(), at.position());
        }
        for (Instance control : workspace.controls()) {
            if (control instanceof IKControl ik) MoudMod.LOG.info("animate control {} target {}", ik.name(), ik.targetCframe.position());
        }
    }

    private static Vector3 vector(String text) {
        String[] v = text.split(",");
        return new Vector3(Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]));
    }
}
