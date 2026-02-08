package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.server.instance.InstanceManager;
import com.moud.server.project.ProjectLoader;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import org.graalvm.polyglot.HostAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@TsExpose
public class WorldManagerProxy {
    private final InstanceManager instanceManager;

    public WorldManagerProxy() {
        this.instanceManager = InstanceManager.getInstance();
    }

    @HostAccess.Export
    public WorldProxy createWorld(String name) {
        InstanceContainer instance = instanceManager.createInstance(name);
        return new WorldProxy(instance);
    }

    @HostAccess.Export
    public WorldProxy loadWorld(String name, String path) {
        Path worldPath = resolvePath(path);
        InstanceContainer instance = instanceManager.loadWorld(name, worldPath);
        return new WorldProxy(instance);
    }

    @HostAccess.Export
    public WorldProxy loadWorld(String name, String path, String sceneId) {
        Path worldPath = resolvePath(path);
        InstanceContainer instance = instanceManager.loadWorld(name, worldPath, sceneId);
        return new WorldProxy(instance);
    }

    @HostAccess.Export
    public WorldProxy getWorld(String name) {
        InstanceContainer instance = instanceManager.getInstance(name);
        if (instance == null) {
            return null;
        }
        return new WorldProxy(instance);
    }

    @HostAccess.Export
    public WorldProxy getDefaultWorld() {
        return new WorldProxy(instanceManager.getDefaultInstance());
    }

    @HostAccess.Export
    public void saveWorld(String name) {
        instanceManager.saveInstance(name);
    }

    @HostAccess.Export
    public void saveAllWorlds() {
        instanceManager.saveAllInstances();
    }

    @HostAccess.Export
    public void setDefaultWorld(String name) {
        instanceManager.setDefaultInstance(name);
    }

    @HostAccess.Export
    public WorldProxy createSharedWorld(String name, String parentWorldName) {
        InstanceContainer parent = instanceManager.getInstance(parentWorldName);
        if (parent == null) {
            throw new IllegalArgumentException("Parent world not found: " + parentWorldName);
        }
        SharedInstance sharedInstance = instanceManager.createSharedInstance(name, parent);
        return new WorldProxy(sharedInstance);
    }

    @HostAccess.Export
    public void unloadWorld(String name) {
        instanceManager.unregisterInstance(name);
    }

    @HostAccess.Export
    public boolean worldExists(String name) {
        return instanceManager.hasInstance(name);
    }

    private static Path resolvePath(String path) {
        Path resolved = Paths.get(path);
        if (resolved.isAbsolute()) {
            return resolved;
        }

        Path projectRoot = ProjectLoader.findProjectRoot();
        Path candidate = projectRoot.resolve(path);

        if (Files.exists(candidate)) {
            return candidate;
        }

        Path repoRoot = projectRoot.getParent().getParent();
        candidate = repoRoot.resolve(path);
        if (Files.exists(candidate)) {
            return candidate;
        }

        return Paths.get(System.getProperty("user.dir")).resolve(path);
    }
}
