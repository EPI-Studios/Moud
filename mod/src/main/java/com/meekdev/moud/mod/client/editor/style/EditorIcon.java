package com.meekdev.moud.mod.client.editor.style;

import java.util.Locale;

public enum EditorIcon {
    PARTICLES_3D("GPUParticles3D"),
    SPRITE_2D,
    TILE_MAP,
    NODE_2D,
    CONTROL,
    NAVIGATION_AGENT_3D,
    NAVIGATION_REGION_3D,
    REMOTE_TRANSFORM_3D,
    WORLD_ENVIRONMENT,
    LABEL_3D,
    FOG_VOLUME,
    LIGHTMAP_PROBE,
    HINGE_JOINT_3D,
    AUDIO_LISTENER_3D,
    AUDIO_STREAM_PLAYER_3D,
    CHARACTER_BODY_2D,
    RIGID_BODY_2D,
    POINT_LIGHT_2D,
    COLLISION_SHAPE_2D,
    ANIMATED_SPRITE_2D,
    AUDIO_MICROPHONE("AudioStreamMicrophone"),
    DECAL,
    ACTION_COPY,
    ACTION_PASTE,
    ADD,
    ANIMATION,
    ATLAS_TEXTURE,
    AUDIO_STREAM,
    GRAPH_EDIT,
    PACKED_SCENE,
    SHADER,
    STANDARD_MATERIAL("StandardMaterial3D"),
    TEXTURE_2D,
    BUCKET,
    CANVAS_LAYER,
    ANIMATION_PLAYER,
    CAMERA_3D,
    CHARACTER_BODY_3D,
    COLLISION_SHAPE_3D,
    DIRECTIONAL_LIGHT_3D,
    COLOR_PICK,
    DUPLICATE,
    EDIT,
    ERASER,
    FILE,
    FOLDER,
    GRID,
    VISIBILITY_HIDDEN("GuiVisibilityHidden"),
    VISIBILITY_VISIBLE("GuiVisibilityVisible"),
    LINE,
    LOAD,
    LOCK,
    MESH,
    MESH_INSTANCE_3D,
    NODE_3D,
    OMNI_LIGHT_3D,
    PAUSE,
    PLAY,
    REDO,
    REMOVE,
    RIGID_BODY_3D,
    SAVE,
    SCRIPT,
    RECTANGLE,
    SNAP,
    SPOT_LIGHT_3D,
    STATIC_BODY_3D,
    STOP,
    TERRAIN_CONNECT,
    TERRAIN_MATCH_CORNERS,
    TERRAIN_MATCH_CORNERS_AND_SIDES,
    TERRAIN_MATCH_SIDES,
    TOOL_MOVE,
    TOOL_PIVOT,
    TOOL_ROTATE,
    TOOL_SCALE,
    TOOL_SELECT,
    UNLOCK,
    LINE_EDIT,
    SCROLL_CONTAINER,
    V_BOX_CONTAINER,
    GRID_CONTAINER,
    MARGIN_CONTAINER,
    ASPECT_RATIO_CONTAINER,
    TEXTURE_BUTTON,
    CANVAS_GROUP,
    GRADIENT_TEXTURE_1D,
    STYLE_BOX_FLAT,
    CONTAINER;

    private final String fileName;

    EditorIcon() {
        this.fileName = fileNameOf(name());
    }

    EditorIcon(String fileName) {
        this.fileName = fileName;
    }

    private static String fileNameOf(String constant) {
        StringBuilder out = new StringBuilder();
        for (String word : constant.split("_")) {
            if (Character.isDigit(word.charAt(0))) out.append(word);
            else out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    public String resourcePath() {
        return "/assets/moud/editor/icons/" + fileName + ".png";
    }
}
