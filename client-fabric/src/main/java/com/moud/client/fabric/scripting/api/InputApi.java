package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.input.ClientInputMap;
import java.util.List;

public final class InputApi {

    private float curMoveX;
    private float curMoveZ;
    private float cursorX;
    private float cursorY;

    public InputApi() { }

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
        if (snapshot == null) snapshot = InputStateSnapshot.EMPTY;
        curMoveX = snapshot.moveX();
        curMoveZ = snapshot.moveZ();
        cursorX = snapshot.cursorX();
        cursorY = snapshot.cursorY();
    }

    public boolean isDown(String action) {
        return action != null && ClientInputMap.isDown(action);
    }

    public boolean isPressed(String action) {
        return action != null && ClientInputMap.isPressed(action);
    }

    public boolean isReleased(String action) {
        return action != null && ClientInputMap.isReleased(action);
    }

    public float[] moveAxis() {
        return new float[]{curMoveX, curMoveZ};
    }

    public float[] cursorPos() {
        return new float[]{cursorX, cursorY};
    }

    public String[] actions() {
        List<String> ids = ClientInputMap.actionIds();
        return ids.toArray(new String[0]);
    }

    public String[] getBindings(String action) {
        String token = ClientInputMap.boundKeyToken(action);
        return token == null || token.isEmpty() ? new String[0] : new String[]{token};
    }

    public boolean setBinding(String action, String token) {
        return ClientInputMap.setMoudBinding(action, token);
    }

    public void resetBindings(String action) {
        ClientInputMap.resetMoudBinding(action);
    }

    public void resetAllBindings() {
        for (String id : ClientInputMap.actionIds()) {
            if (ClientInputMap.isMoudAction(id)) {
                ClientInputMap.resetMoudBinding(id);
            }
        }
    }
}
