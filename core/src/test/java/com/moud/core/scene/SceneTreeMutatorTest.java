package com.moud.core.scene;

import com.moud.core.NodeTypeRegistry;
import com.moud.core.builtin.CoreNodeTypesProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}

