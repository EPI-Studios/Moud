package com.moud.client.fabric.scripting.api;


public final class InputApi {

    private boolean prevJump;
    private boolean prevSprint;
    private boolean prevSneak;
    private boolean prevForward;
    private boolean prevBack;
    private boolean prevLeft;
    private boolean prevRight;

    private boolean curJump;
    private boolean curSprint;
    private boolean curSneak;
    private boolean curForward;
    private boolean curBack;
    private boolean curLeft;
    private boolean curRight;
    private float   curMoveX;
    private float   curMoveZ;
    private float   cursorX;
    private float   cursorY;

    public InputApi() {
    }


    public record InputStateSnapshot(
            boolean jump,
            boolean sprint,
            boolean sneak,
            float moveX,
            float moveZ,
            float cursorX,
            float cursorY) {

        public static final InputStateSnapshot EMPTY =
                new InputStateSnapshot(false, false, false, 0f, 0f, 0f, 0f);
    }


    public void updateEdges(InputStateSnapshot snapshot) {
        prevJump    = curJump;
        prevSprint  = curSprint;
        prevSneak   = curSneak;
        prevForward = curForward;
        prevBack    = curBack;
        prevLeft    = curLeft;
        prevRight   = curRight;

        if (snapshot == null) snapshot = InputStateSnapshot.EMPTY;

        curJump    = snapshot.jump();
        curSprint  = snapshot.sprint();
        curSneak   = snapshot.sneak();

        float mx = snapshot.moveX();
        float mz = snapshot.moveZ();
        curMoveX   = mx;
        curMoveZ   = mz;
        curRight    = mx >  1.0e-5f;
        curLeft     = mx < -1.0e-5f;
        curForward  = mz >  1.0e-5f;
        curBack     = mz < -1.0e-5f;
        cursorX     = snapshot.cursorX();
        cursorY     = snapshot.cursorY();
    }


    public boolean isDown(String key) {
        if (key == null) return false;
        return switch (key) {
            case "jump"    -> curJump;
            case "sprint"  -> curSprint;
            case "sneak"   -> curSneak;
            case "forward" -> curForward;
            case "back"    -> curBack;
            case "left"    -> curLeft;
            case "right"   -> curRight;
            default        -> false;
        };
    }

    public boolean isPressed(String key) {
        if (key == null) return false;
        return switch (key) {
            case "jump"    -> curJump    && !prevJump;
            case "sprint"  -> curSprint  && !prevSprint;
            case "sneak"   -> curSneak   && !prevSneak;
            case "forward" -> curForward && !prevForward;
            case "back"    -> curBack    && !prevBack;
            case "left"    -> curLeft    && !prevLeft;
            case "right"   -> curRight   && !prevRight;
            default        -> false;
        };
    }

    public boolean isReleased(String key) {
        if (key == null) return false;
        return switch (key) {
            case "jump"    -> !curJump    && prevJump;
            case "sprint"  -> !curSprint  && prevSprint;
            case "sneak"   -> !curSneak   && prevSneak;
            case "forward" -> !curForward && prevForward;
            case "back"    -> !curBack    && prevBack;
            case "left"    -> !curLeft    && prevLeft;
            case "right"   -> !curRight   && prevRight;
            default        -> false;
        };
    }

    public float[] moveAxis() {
        return new float[]{curMoveX, curMoveZ};
    }

    public float[] cursorPos() {
        return new float[]{cursorX, cursorY};
    }
}