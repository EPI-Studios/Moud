package com.moud.client.editor.plugin;

import com.moud.client.editor.scene.SceneObject;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


public final class EditorPluginHost {
    private static final Logger LOGGER = LoggerFactory.getLogger("EditorPluginHost");
    private static final EditorPluginHost INSTANCE = new EditorPluginHost();

    private final Map<String, CopyOnWriteArrayList<RibbonContribution>> ribbonContributions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InspectorContribution> inspectorContributions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<EditorPluginDescriptor> descriptors = new CopyOnWriteArrayList<>();
    private final AtomicBoolean showcaseInstalled = new AtomicBoolean();

    private EditorPluginHost() {}

    public static EditorPluginHost getInstance() {
        return INSTANCE;
    }

    public void registerDescriptor(EditorPluginDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        descriptors.removeIf(existing -> existing.id().equals(descriptor.id()));
        descriptors.add(descriptor);
    }

    public List<EditorPluginDescriptor> getDescriptors() {
        return Collections.unmodifiableList(descriptors);
    }

    public void registerRibbonContribution(String tabId, RibbonContribution contribution) {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(contribution, "contribution");
        ribbonContributions.computeIfAbsent(tabId, key -> new CopyOnWriteArrayList<>()).add(contribution);
    }

    public boolean renderRibbonTab(String tabId) {
        List<RibbonContribution> contributions = ribbonContributions.get(tabId);
        if (contributions == null || contributions.isEmpty()) {
            return false;
        }
        for (RibbonContribution contribution : contributions) {
            try {
                contribution.render();
            } catch (Exception e) {
                LOGGER.error("Ribbon contribution '{}' failed", contribution, e);
            }
            ImGui.separator();
        }
        return true;
    }

    public void registerInspectorContribution(InspectorContribution contribution) {
        inspectorContributions.add(Objects.requireNonNull(contribution, "contribution"));
    }

    public void renderInspectorExtras(SceneObject selection) {
        for (InspectorContribution contribution : inspectorContributions) {
            try {
                if (contribution.supports(selection)) {
                    contribution.render(selection);
                }
            } catch (Exception e) {
                LOGGER.error("Inspector contribution '{}' failed", contribution, e);
            }
        }
    }



    public interface RibbonContribution {
        void render();
    }

    public interface InspectorContribution {
        boolean supports(SceneObject selection);

        void render(SceneObject selection);
    }

    public record EditorPluginDescriptor(
            String id,
            String displayName,
            String description
    ) {}
}
