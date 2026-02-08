package com.moud.server.proxy;

import com.moud.server.editor.SceneDefaults;
import com.moud.server.editor.SceneManager;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;


public final class SceneProxy {

    @HostAccess.Export
    public Map<String, Object> get() {
        var snapshot = SceneManager.getInstance().createSnapshot(SceneDefaults.DEFAULT_SCENE_ID);
        Map<String, Object> out = new HashMap<>();
        out.put("id", SceneDefaults.DEFAULT_SCENE_ID);
        out.put("version", snapshot.version());
        out.put("objects", snapshot.objects());
        return out;
    }

    @HostAccess.Export
    public void edit(Value options) {
        if (options == null || !options.hasMembers()) {
            return;
        }

        String sceneId = options.getMember("sceneId").asString();
        String action = options.getMember("action").asString();
        Value payloadValue = options.getMember("payload");
        Map<String, Object> payload = payloadValue.as(Map.class);

        long clientVersion = 0;
        if (options.hasMember("clientVersion")) {
            clientVersion = options.getMember("clientVersion").asLong();
        }

        SceneManager.getInstance().applyEdit(sceneId, action, payload, clientVersion);
    }
}
