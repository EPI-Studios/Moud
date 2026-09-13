package com.meekdev.moud.mod.adapter.chat;

import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.mod.transport.payload.ChatDownPayload;
import java.util.HashMap;
import java.util.Map;

public final class ChatLine {

    public final long id;
    public int channel = -1;
    public int source = -1;
    public int body = -1;
    public String text = "";
    public String prefix = "";
    public String metadata = "";
    public long timestamp;
    public String status = "Success";

    public ChatLine(long id) {
        this.id = id;
    }

    public static ChatLine of(ChatDownPayload down) {
        ChatLine line = new ChatLine(down.id());
        line.channel = down.channel();
        line.source = down.source();
        line.body = down.body();
        line.text = down.text();
        line.prefix = down.prefix();
        line.metadata = down.metadata();
        line.timestamp = down.timestamp();
        line.status = down.status();
        return line;
    }

    public ChatDownPayload packet(int kind) {
        return new ChatDownPayload(kind, id, channel, source, body, text, prefix, metadata, timestamp, status);
    }

    public Map<String, Object> toMap(InstanceTree tree) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", (double) id);
        out.put("text", text);
        out.put("prefix", prefix);
        out.put("metadata", metadata);
        out.put("timestamp", (double) timestamp);
        out.put("status", status);
        out.put("plain", RichText.plain(text));
        if (tree != null) {
            if (channel >= 0 && tree.byId(channel) != null) out.put("channel", tree.byId(channel));
            if (source >= 0 && tree.byId(source) != null) out.put("source", tree.byId(source));
            if (body >= 0 && tree.byId(body) != null) {
                out.put("body", tree.byId(body));
                out.put("position", Transforms.world(tree.byId(body)).position());
            }
        }
        return out;
    }

    public void apply(Object changes) {
        if (!(changes instanceof Map<?, ?> map)) return;
        if (map.get("text") instanceof String changed) text = changed;
        if (map.get("prefix") instanceof String changed) prefix = changed;
        if (map.get("metadata") instanceof String changed) metadata = changed;
    }

    public Map<String, Object> copyChanges() {
        return new HashMap<>(Map.of("text", text, "prefix", prefix, "metadata", metadata));
    }
}
