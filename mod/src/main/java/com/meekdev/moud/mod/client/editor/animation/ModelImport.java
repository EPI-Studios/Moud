package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.Vector3;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ModelImport {

    record Cube(Vector3 from, Vector3 to) {}

    record Group(String uuid, String name, String joint, Vector3 pivot, int cubes, int depth, List<Cube> shape) {}

    record Animation(String name, double length, String loop, int bones, int keys, int molang) {
        public boolean empty() {
            return keys == 0;
        }
    }

    record Summary(String file, String format, List<Group> groups, List<Animation> animations, int matched, int wanted) {
        public boolean playerRig() {
            return matched == wanted;
        }
    }

    record Choices(Map<String, String> joints, Set<String> animations, String folder, boolean model) {}

    record Outcome(boolean done, String message, List<Path> written, List<String> notes) {}

    Summary inspect(Path file) throws IOException;

    Outcome run(Path file, Summary summary, Choices choices);
}
