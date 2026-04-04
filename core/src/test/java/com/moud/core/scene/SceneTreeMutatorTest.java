package com.moud.core.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.moud.core.NodeTypeRegistry;
import com.moud.core.builtin.CoreNodeTypesProvider;

import java.util.List;
import java.util.Map;

final class SceneTreeMutatorTest {
    @Test
    void replacesRootChildrenAndPreservesIds() {
        PlainNode root = new PlainNode("root");
        NodeTypeRegistry types = new NodeTypeRegistry();
        new CoreNodeTypesProvider().register(types);
        SceneTree tree = new SceneTree(root);

        assertNull(tree.getNode(100L));

        List<SceneTreeMutator.NodeSpec> specs = List.of(
                new SceneTreeMutator.NodeSpec(100L, 0L, "Box", "CSGBlock", Map.of(
                        "x", "1",
                        "y", "41",
                        "z", "5",
                        "sx", "2",
                        "sy", "3",
                        "sz", "4",
                        "block", "minecraft:stone"
                )),
                new SceneTreeMutator.NodeSpec(101L, 100L, "Child", "Node", Map.of(
                        "foo", "bar"
                ))
        );

        SceneTreeMutator.replaceRootChildren(tree, specs, types);

        Node box = tree.getNode(100L);
        assertNotNull(box);
        assertEquals("Box", box.name());
        assertEquals("CSGBlock", types.typeIdFor(box));
        assertEquals("2", box.getProperty("sx"));

        Node child = tree.getNode(101L);
        assertNotNull(child);
        assertEquals(box, child.parent());
        assertEquals("bar", child.getProperty("foo"));
    }

    @Test
    void preservesIdsAndAdvancesNextNodeId() {
        PlainNode root = new PlainNode("root");
        NodeTypeRegistry types = new NodeTypeRegistry();
        new CoreNodeTypesProvider().register(types);
        SceneTree tree = new SceneTree(root);

        List<SceneTreeMutator.NodeSpec> specs = List.of(
                new SceneTreeMutator.NodeSpec(2L, 0L, "A", "Node", Map.of()),
                new SceneTreeMutator.NodeSpec(3L, 2L, "B", "Node", Map.of())
        );
        SceneTreeMutator.replaceRootChildren(tree, specs, types);

        Node a = tree.getNode(2L);
        assertNotNull(a);
        assertEquals("A", a.name());

        Node b = tree.getNode(3L);
        assertNotNull(b);
        assertEquals("B", b.name());
        assertEquals(a, b.parent());

        PlainNode c = new PlainNode("C");
        root.addChild(c);
        assertNotNull(tree.getNode(c.nodeId()));
        assertEquals("C", tree.getNode(c.nodeId()).name());
        // If nextNodeId isn't advanced when loading explicit ids, this will collide with existing nodes.
        assertEquals(4L, c.nodeId());
        assertEquals("A", tree.getNode(2L).name());
    }
}
