package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class AssetReferences {

    public record Reference(int instance, String label, @Nullable Path script, int line, String value) {}

    private static final int SEARCH_DEPTH = 12;

    private AssetReferences() {}

    public static List<Reference> find(SceneDocument document, String res, boolean folder) {
        List<Reference> found = new ArrayList<>();
        Instance world = document.world();
        if (world != null) instances(document, world, res, folder, found);
        scripts(res, folder, found);
        return found;
    }

    public static int apply(SceneDocument document, List<Reference> references, String from, String to) {
        List<Edit> edits = new ArrayList<>();
        int files = 0;
        for (Reference reference : references) {
            if (reference.script() != null) continue;
            Instance instance = document.find(reference.instance());
            if (instance == null) continue;
            String name = reference.label().substring(reference.label().lastIndexOf('.') + 1);
            PropertyDef property = instance.def().property(name);
            if (property == null) continue;
            edits.add(new SetProperty(document.ref(instance.id()), property.index(), to + reference.value().substring(from.length()), "Rename asset"));
        }
        if (!edits.isEmpty()) document.history().execute(new Batch("Rename asset", edits));
        List<Path> touched = references.stream().map(Reference::script).filter(p -> p != null).distinct().toList();
        for (Path script : touched) {
            try {
                String text = Files.readString(script, StandardCharsets.UTF_8);
                String updated = text.replace(from, to);
                String aliasFrom = alias(from);
                String aliasTo = alias(to);
                if (aliasFrom != null && aliasTo != null) updated = updated.replace("\"" + aliasFrom, "\"" + aliasTo);
                if (!updated.equals(text)) {
                    Files.writeString(script, updated, StandardCharsets.UTF_8);
                    files++;
                }
            } catch (IOException ignored) {
            }
        }
        return edits.size() + files;
    }

    private static void instances(SceneDocument document, Instance at, String res, boolean folder, List<Reference> found) {
        for (Instance child : at.children()) {
            if (document.editable(child)) {
                for (PropertyDef property : child.def().properties()) {
                    if (property.type() != PropertyType.STRING && property.type() != PropertyType.ASSET) continue;
                    if (!(property.getObj(child) instanceof String value) || value.isEmpty()) continue;
                    if (matches(value, res, folder)) {
                        found.add(new Reference(child.id(), child.name() + "." + property.name(), null, 0, value));
                    }
                }
            }
            instances(document, child, res, folder, found);
        }
    }

    private static void scripts(String res, boolean folder, List<Reference> found) {
        Path root = AssetFiles.root();
        String alias = alias(res);
        try (Stream<Path> walk = Files.walk(root, SEARCH_DEPTH)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(AssetReferences::isScript).toList()) {
                List<String> lines;
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue;
                }
                for (int n = 0; n < lines.size(); n++) {
                    String line = lines.get(n);
                    if (line.contains(res) || (alias != null && line.contains("\"" + alias))) {
                        found.add(new Reference(0, root.relativize(file) + ":" + (n + 1), file, n + 1, line.strip()));
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isScript(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return !AssetScanner.insideHiddenPublic(AssetFiles.root(), file) && (name.endsWith(".luau") || name.endsWith(".rv") || name.endsWith(".java"));
    }

    private static boolean matches(String value, String res, boolean folder) {
        return folder ? value.startsWith(res + "/") : value.equals(res);
    }

    static @Nullable String alias(String res) {
        if (!res.startsWith(Res.SCHEME)) return null;
        String path = res.substring(Res.SCHEME.length());
        if (!(path.startsWith("server/") || path.startsWith("client/") || path.startsWith("shared/"))) return null;
        int dot = path.lastIndexOf('.');
        return "@" + (dot > path.lastIndexOf('/') ? path.substring(0, dot) : path);
    }
}
