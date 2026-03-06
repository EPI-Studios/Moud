package com.moud.net.wire.codec;

import com.moud.core.NodeTypeDef;
import com.moud.core.PropertyDef;
import com.moud.core.PropertyType;
import com.moud.net.protocol.SceneInfo;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.wire.WireIo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaCodec {
    private SchemaCodec() {
    }

    public static void writeSchemaSnapshot(ByteBuffer out, SchemaSnapshot snapshot) {
        WireIo.writeLong(out, snapshot.schemaRevision());
        List<NodeTypeDef> types = snapshot.types();
        WireIo.writeVarInt(out, types.size());
        for (NodeTypeDef type : types) {
            WireIo.writeString(out, type.typeId());
            WireIo.writeString(out, type.displayName());
            WireIo.writeString(out, type.category());
            WireIo.writeVarInt(out, type.order());

            var props = type.properties();
            ArrayList<PropertyDef> propList = new ArrayList<>(props.values());
            propList.sort(Comparator
                    .comparing(PropertyDef::category)
                    .thenComparingInt(PropertyDef::order)
                    .thenComparing(PropertyDef::uiLabel)
                    .thenComparing(PropertyDef::key));
            WireIo.writeVarInt(out, propList.size());
            for (PropertyDef prop : propList) {
                WireIo.writeString(out, prop.key());
                WireIo.writeString(out, prop.type().name());

                String dv = prop.defaultValue();
                WireIo.writeVarInt(out, dv == null ? 0 : 1);
                if (dv != null) {
                    WireIo.writeString(out, dv);
                }

                WireIo.writeString(out, prop.displayName());
                WireIo.writeString(out, prop.category());
                WireIo.writeVarInt(out, prop.order());

                var hints = prop.editorHints();
                WireIo.writeVarInt(out, hints.size());
                for (var entry : hints.entrySet()) {
                    WireIo.writeString(out, entry.getKey());
                    WireIo.writeString(out, entry.getValue());
                }
            }
        }
    }

    public static SchemaSnapshot readSchemaSnapshot(ByteBuffer in) {
        long rev = WireIo.readLong(in);
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 1_000_000) {
            throw new IllegalArgumentException("Invalid type count: " + count);
        }
        List<NodeTypeDef> typeDefs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String typeId = WireIo.readString(in);
            String displayName = WireIo.readString(in);
            String category = WireIo.readString(in);
            int order = WireIo.readVarInt(in);

            int propCount = WireIo.readVarInt(in);
            if (propCount < 0 || propCount > 1_000_000) {
                throw new IllegalArgumentException("Invalid property count: " + propCount);
            }
            Map<String, PropertyDef> props = new LinkedHashMap<>();
            for (int p = 0; p < propCount; p++) {
                String key = WireIo.readString(in);
                String typeName = WireIo.readString(in);
                PropertyType type;
                try {
                    type = PropertyType.valueOf(typeName);
                } catch (Exception e) {
                    type = PropertyType.STRING;
                }

                boolean hasDefault = WireIo.readVarInt(in) != 0;
                String defaultValue = hasDefault ? WireIo.readString(in) : null;
                String propDisplayName = WireIo.readString(in);
                String propCategory = WireIo.readString(in);
                int propOrder = WireIo.readVarInt(in);

                int hintCount = WireIo.readVarInt(in);
                if (hintCount < 0 || hintCount > 1_000_000) {
                    throw new IllegalArgumentException("Invalid hint count: " + hintCount);
                }
                Map<String, String> hints = new LinkedHashMap<>();
                for (int h = 0; h < hintCount; h++) {
                    hints.put(WireIo.readString(in), WireIo.readString(in));
                }

                props.put(key, new PropertyDef(key, type, defaultValue, propDisplayName, propCategory, propOrder, hints));
            }
            typeDefs.add(new NodeTypeDef(typeId, displayName, category, order, props));
        }
        return new SchemaSnapshot(rev, List.copyOf(typeDefs));
    }

    public static void writeSceneList(ByteBuffer out, SceneList list) {
        List<SceneInfo> scenes = list.scenes() == null ? List.of() : list.scenes();
        WireIo.writeVarInt(out, scenes.size());
        for (SceneInfo scene : scenes) {
            if (scene == null) {
                WireIo.writeString(out, "");
                WireIo.writeString(out, "");
                continue;
            }
            WireIo.writeString(out, scene.sceneId());
            WireIo.writeString(out, scene.displayName());
        }
        WireIo.writeString(out, list.activeSceneId() == null ? "" : list.activeSceneId());
    }

    public static SceneList readSceneList(ByteBuffer in) {
        int count = WireIo.readVarInt(in);
        if (count < 0 || count > 100_000) {
            throw new IllegalArgumentException("Invalid scene count: " + count);
        }
        ArrayList<SceneInfo> scenes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = WireIo.readString(in);
            String name = WireIo.readString(in);
            if (id == null || id.isBlank()) {
                continue;
            }
            scenes.add(new SceneInfo(id, name));
        }
        String active = WireIo.readString(in);
        return new SceneList(List.copyOf(scenes), active == null ? "" : active);
    }

    // --- Size estimations ---

    public static int schemaSnapshotSize(SchemaSnapshot snapshot) {
        int size = WireIo.longSize(snapshot.schemaRevision());
        List<NodeTypeDef> types = snapshot.types();
        size += WireIo.varIntSize(types.size());
        for (NodeTypeDef type : types) {
            size += WireIo.stringSize(type.typeId());
            size += WireIo.stringSize(type.displayName());
            size += WireIo.stringSize(type.category());
            size += WireIo.varIntSize(type.order());

            var props = type.properties();
            size += WireIo.varIntSize(props.size());
            for (PropertyDef prop : props.values()) {
                size += WireIo.stringSize(prop.key());
                size += WireIo.stringSize(prop.type().name());

                String dv = prop.defaultValue();
                size += WireIo.varIntSize(dv == null ? 0 : 1);
                if (dv != null) {
                    size += WireIo.stringSize(dv);
                }

                size += WireIo.stringSize(prop.displayName());
                size += WireIo.stringSize(prop.category());
                size += WireIo.varIntSize(prop.order());

                var hints = prop.editorHints();
                size += WireIo.varIntSize(hints.size());
                for (var entry : hints.entrySet()) {
                    size += WireIo.stringSize(entry.getKey());
                    size += WireIo.stringSize(entry.getValue());
                }
            }
        }
        return size;
    }

    public static int sceneListSize(SceneList list) {
        int size = 0;
        List<SceneInfo> scenes = list.scenes() == null ? List.of() : list.scenes();
        size += WireIo.varIntSize(scenes.size());
        for (SceneInfo scene : scenes) {
            if (scene == null) {
                size += WireIo.stringSize("") + WireIo.stringSize("");
                continue;
            }
            size += WireIo.stringSize(scene.sceneId());
            size += WireIo.stringSize(scene.displayName());
        }
        size += WireIo.stringSize(list.activeSceneId());
        return size;
    }
}
