package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.render.CameraPath;

public interface ViewTools {

    CFrame view();

    void lookFrom(CFrame frame);

    void preview(CameraPath path);

    boolean previewing();

    void stopPreview();
}
