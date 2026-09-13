package com.meekdev.moud.script.types;

import com.meekdev.moud.core.clazz.CallbackDef;
import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.script.vm.Luau;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class Types {

    private static final String DIRECTORY = ".moud";
    private static final String FILE = "types.d.luau";

    private Types() {}

    public static Path write(Path place, ClassRegistry classes) throws IOException {
        Path directory = place.resolve(DIRECTORY);
        Files.createDirectories(directory);
        Path file = directory.resolve(FILE);
        Files.writeString(file, declare(classes));
        return file;
    }

    public static String declare(ClassRegistry classes) {
        StringBuilder out = new StringBuilder(65536);
        out.append(Luau.source("types/values.d.luau"));
        out.append(Luau.source("types/instance.d.luau"));
        for (ClassDef<?> def : classes.all()) {
            out.append(classDeclaration(def));
        }
        out.append(Luau.source("types/services.d.luau"));
        return out.toString();
    }

    private static String classDeclaration(ClassDef<?> def) {
        StringBuilder out = new StringBuilder(256);
        out.append("declare class ").append(def.name())
                .append(" extends ").append(def.parent() == null ? "Instance" : def.parent().name())
                .append('\n');

        PropertyDef frame = def.property("cframe");
        if (frame != null && frame.index() >= inheritedPropertyCount(def)) {
            out.append("    position: Vector3\n");
            out.append("    rotation: Quat\n");
            out.append("    worldCframe: CFrame\n");
        }

        for (CallbackDef callback : def.callbacks()) {
            if (def.parent() != null && def.parent().callback(callback.name()) != null) continue;
            out.append("    ").append(callback.name()).append(": ((...any) -> ...any)?\n");
        }

        for (EventDef event : def.events()) {
            String shape = def == Classes.CHAT_COMMAND ? "ChatCommandSignal"
                    : def == Classes.TEXT_CHANNEL ? "ChatMessageSignal" : "InstanceSignal";
            out.append("    ").append(event.name()).append(": ").append(shape).append("\n");
        }

        PropertyDef[] properties = def.properties();
        for (int i = inheritedPropertyCount(def); i < properties.length; i++) {
            out.append("    ").append(properties[i].name())
                    .append(": ").append(luau(properties[i])).append('\n');
        }
        out.append(classMethods(def.name()));
        return out.append("end\n\n").toString();
    }

    private static String classMethods(String className) {
        return Luau.optional("types/verbs/" + className + ".d.luau");
    }

    private static int inheritedPropertyCount(ClassDef<?> def) {
        return def.parent() == null ? 0 : def.parent().properties().length;
    }

    private static String luau(PropertyDef property) {
        if (property.type() != PropertyType.ENUM) return luau(property.type());
        List<String> names = Enums.names(property.defaultValue().getClass());
        return names.stream().map(name -> '"' + name + '"')
                .collect(Collectors.joining(" | "));
    }

    private static String luau(PropertyType type) {
        return switch (type) {
            case BOOL -> "boolean";
            case INT, NUM -> "number";
            case STRING, ASSET, ENUM -> "string";
            case VEC3 -> "Vector3";
            case QUAT -> "Quat";
            case CFRAME -> "CFrame";
            case COLOR -> "Color";
            case UDIM2 -> "UDim2";
            case REF -> "Instance?";
        };
    }
}
