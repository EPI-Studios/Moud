package com.moud.net.protocol;

public sealed interface SceneOp permits SceneOp.CreateNode, SceneOp.QueueFree, SceneOp.Rename, SceneOp.SetProperty, SceneOp.RemoveProperty, SceneOp.Reparent {
    SceneOpType type();

    record CreateNode(long parentId, String name, String typeId) implements SceneOp {
        public CreateNode(long parentId, String name) {
            this(parentId, name, "Node");
        }

        @Override
        public SceneOpType type() {
            return SceneOpType.CREATE_NODE;
        }
    }

    record QueueFree(long nodeId) implements SceneOp {
        @Override
        public SceneOpType type() {
            return SceneOpType.QUEUE_FREE;
        }
    }

    record Rename(long nodeId, String newName) implements SceneOp {
        @Override
        public SceneOpType type() {
            return SceneOpType.RENAME;
        }
    }

    record SetProperty(long nodeId, String key, String value) implements SceneOp {
        @Override
        public SceneOpType type() {
            return SceneOpType.SET_PROPERTY;
        }
    }

    record RemoveProperty(long nodeId, String key) implements SceneOp {
        @Override
        public SceneOpType type() {
            return SceneOpType.REMOVE_PROPERTY;
        }
    }

    record Reparent(long nodeId, long newParentId, int index) implements SceneOp {
        @Override
        public SceneOpType type() {
            return SceneOpType.REPARENT;
        }
    }
}
