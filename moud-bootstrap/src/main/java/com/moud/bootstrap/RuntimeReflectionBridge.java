package com.moud.bootstrap;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import java.lang.reflect.Method;
import java.nio.file.Path;

final class RuntimeReflectionBridge {

    private RuntimeReflectionBridge() {
    }

    static void injectIntoTargetClassPath(Path jar) {
        FabricLauncherBase.getLauncher().addToClassPath(jar);
    }

    static void invokeStaticNoArg(String className, String methodName) throws Exception {
        Class<?> type;
        try {
            type = FabricLauncherBase.getLauncher().loadIntoTarget(className);
        } catch (ClassNotFoundException primary) {
            type = FabricLauncherBase.getClass(className);
        }
        Method method = type.getMethod(methodName);
        method.invoke(null);
    }
}
