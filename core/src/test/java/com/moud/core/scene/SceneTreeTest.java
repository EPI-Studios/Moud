package com.moud.core.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import com.moud.core.scene.Node;
import com.moud.core.scene.SceneTree;
import org.junit.jupiter.api.Test;

class SceneTreeTest {
    @Test
    void readyCalledOnce() {
        AtomicInteger readyCount = new AtomicInteger();
        AtomicInteger processCount = new AtomicInteger();

        Node root = new Node("root") {
            @Override
            protected void onReady() {
                readyCount.incrementAndGet();
            }

            @Override
            protected void onProcess(double dtSeconds) {
                processCount.incrementAndGet();
            }
        };

        SceneTree tree = new SceneTree(root);
        tree.tick(0.016);
        tree.tick(0.016);
        tree.tick(0.016);

        assertEquals(1, readyCount.get());
        assertEquals(3, processCount.get());
    }

    @Test
    void pathIncludesAncestors() {
        Node root = new Node("root") {
        };
        Node a = new Node("a") {
        };
        Node b = new Node("b") {
        };
        root.addChild(a);
        a.addChild(b);

        new SceneTree(root);

        assertEquals("/root", root.path());
        assertEquals("/root/a", a.path());
        assertEquals("/root/a/b", b.path());
        assertEquals(b, a.findChild("b"));
    }

    @Test
    void lookupByAbsolutePath() {
        Node root = new Node("root") {
        };
        Node a = new Node("a") {
        };
        Node b = new Node("b") {
        };
        root.addChild(a);
        a.addChild(b);

        SceneTree tree = new SceneTree(root);
        assertEquals(root, tree.getNode("/root"));
        assertEquals(b, tree.getNode("/root/a/b"));
        assertEquals(null, tree.getNode("/nope"));
        assertEquals(null, tree.getNode("/root/nope"));
    }

    @Test
    void deferredAddAvoidsConcurrentModification() {
        AtomicInteger childReady = new AtomicInteger();
        AtomicInteger childProcess = new AtomicInteger();

        Node root = new Node("root") {
            private boolean added;

            @Override
            protected void onProcess(double dtSeconds) {
                if (!added) {
                    added = true;
                    addChild(new Node("late") {
                        @Override
                        protected void onReady() {
                            childReady.incrementAndGet();
                        }

                        @Override
                        protected void onProcess(double dtSeconds) {
                            childProcess.incrementAndGet();
                        }
                    });
                }
            }
        };

        SceneTree tree = new SceneTree(root);
        tree.tick(0.016);

        Node late = tree.getNode("/root/late");
        assertNotNull(late);

        tree.tick(0.016);
        assertEquals(1, childReady.get());
        assertEquals(1, childProcess.get());
    }
}
