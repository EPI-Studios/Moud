package com.moud.plugin.api.services;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface SceneService {
    Path blueprintDirectory();
    List<String> listBlueprints();
    Optional<String> loadBlueprint(String name);
    void saveBlueprint(String name, String json);
}
