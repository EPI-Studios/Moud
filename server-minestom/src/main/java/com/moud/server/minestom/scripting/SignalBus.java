package com.moud.server.minestom.scripting;

import com.moud.server.minestom.util.DebugLog;

import java.util.*;

final class SignalBus {
    private static final String LOG_TAG = "signal-bus";

    private record SignalConnection(long targetNodeId, String method) {}

    private final Map<Long, Map<String, List<SignalConnection>>> connections = new HashMap<>();

    public void connect(long sourceNodeId, String signal, long targetNodeId, String method) {
        if (isInvalidString(signal) || isInvalidString(method)) return;
        if (sourceNodeId <= 0L || targetNodeId <= 0L) return;

        String signalName = signal.trim();
        String methodName = method.trim();

        List<SignalConnection> targetConnections = connections
                .computeIfAbsent(sourceNodeId, k -> new HashMap<>())
                .computeIfAbsent(signalName, k -> new ArrayList<>());

        for (SignalConnection connection : targetConnections) {
            if (connection.targetNodeId() == targetNodeId && connection.method().equals(methodName)) {
                return;
            }
        }

        targetConnections.add(new SignalConnection(targetNodeId, methodName));
    }

    public void disconnect(long sourceNodeId, String signal, long targetNodeId, String method) {
        if (isInvalidString(signal) || isInvalidString(method)) return;

        Map<String, List<SignalConnection>> nodeSignals = connections.get(sourceNodeId);
        if (nodeSignals == null) return;

        String signalName = signal.trim();
        List<SignalConnection> targetConnections = nodeSignals.get(signalName);
        if (targetConnections == null) return;

        String methodName = method.trim();
        targetConnections.removeIf(c -> c.targetNodeId() == targetNodeId && c.method().equals(methodName));

        if (targetConnections.isEmpty()) {
            nodeSignals.remove(signalName);
        }
        if (nodeSignals.isEmpty()) {
            connections.remove(sourceNodeId);
        }
    }

    public void emit(long sourceNodeId, String signal, Map<Long, ScriptObject> scriptInstances, Object... args) {
        if (isInvalidString(signal)) return;

        Map<String, List<SignalConnection>> nodeSignals = connections.get(sourceNodeId);
        if (nodeSignals == null) return;

        List<SignalConnection> targetConnections = nodeSignals.get(signal.trim());
        if (targetConnections == null || targetConnections.isEmpty()) return;

        for (SignalConnection connection : targetConnections) {
            ScriptObject targetInstance = scriptInstances.get(connection.targetNodeId());
            if (targetInstance == null) continue;

            invokeMethodSafely(targetInstance, connection, signal, args);
        }
    }

    public void removeNode(long nodeId) {
        connections.remove(nodeId);

        Iterator<Map<String, List<SignalConnection>>> nodeIterator = connections.values().iterator();

        while (nodeIterator.hasNext()) {
            Map<String, List<SignalConnection>> nodeSignals = nodeIterator.next();

            nodeSignals.values().forEach(targetConnections ->
                    targetConnections.removeIf(c -> c.targetNodeId() == nodeId)
            );

            nodeSignals.values().removeIf(List::isEmpty);

            if (nodeSignals.isEmpty()) {
                nodeIterator.remove();
            }
        }
    }

    public void clear() {
        connections.clear();
    }

    private void invokeMethodSafely(ScriptObject targetInstance, SignalConnection connection, String signalName, Object[] args) {
        try {
            if (targetInstance.hasMethod(connection.method())) {
                targetInstance.invokeMethod(connection.method(), args);
            }
        } catch (ScriptInvocationException e) {
            String errorMessage = String.format(
                    "signal='%s' target=%d method='%s' error='%s'",
                    signalName, connection.targetNodeId(), connection.method(), e.getMessage()
            );
            DebugLog.error(LOG_TAG, errorMessage, e);
        }
    }

    private boolean isInvalidString(String str) {
        return str == null || str.isBlank();
    }
}
