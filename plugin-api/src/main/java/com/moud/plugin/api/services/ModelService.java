package com.moud.plugin.api.services;

import com.moud.plugin.api.models.ModelData;
import com.moud.plugin.api.services.model.ModelDefinition;
import com.moud.plugin.api.services.model.ModelHandle;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ModelService {
    ModelHandle spawn(ModelDefinition definition);
    Optional<ModelHandle> get(long id);
    Collection<ModelHandle> all();
    Map<String, ModelData> AllModelData();

    ModelData getModelData(String modelId);

    ModelData register(ModelData data);
}
