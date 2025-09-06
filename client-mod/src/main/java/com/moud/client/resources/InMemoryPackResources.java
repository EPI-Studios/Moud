package com.moud.client.resources;

import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class InMemoryPackResources implements ResourcePack {
    private final String id;
    private final Text displayName;
    private final ResourcePackInfo info;
    private Map<String, byte[]> resources;
    private Set<String> namespaces;

    public InMemoryPackResources(String id, Text displayName, Map<String, byte[]> initialResources) {
        this.id = id;
        this.displayName = displayName;
        this.info = new ResourcePackInfo(id, displayName, ResourcePackSource.BUILTIN, null);
        this.setResources(initialResources);
    }

    public void setResources(Map<String, byte[]> newResources) {
        this.resources = newResources;
        this.namespaces = newResources.keySet().stream()
                .filter(path -> path.startsWith("assets/"))
                .map(path -> path.substring("assets/".length()).split("/", 2)[0])
                .collect(Collectors.toSet());
    }

    @Override
    public @Nullable InputSupplier<InputStream> openRoot(String... segments) {
        return null;
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        if (type != ResourceType.CLIENT_RESOURCES) {
            return null;
        }
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        byte[] data = resources.get(path);
        return data != null ? () -> new ByteArrayInputStream(data) : null;
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        if (type != ResourceType.CLIENT_RESOURCES || !namespaces.contains(namespace)) {
            return;
        }
        String dir = "assets/" + namespace + "/" + prefix;
        for (var entry : resources.entrySet()) {
            String path = entry.getKey();
            if (path.startsWith(dir)) {
                String resourcePath = path.substring(("assets/" + namespace + "/").length());
                Identifier id = Identifier.of(namespace, resourcePath);
                consumer.accept(id, () -> new ByteArrayInputStream(entry.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return type == ResourceType.CLIENT_RESOURCES ? namespaces : Collections.emptySet();
    }

    @Nullable
    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) {
        return null;
    }

    @Override
    public ResourcePackInfo getInfo() {
        return info;
    }

    @Override
    public void close() {}
}