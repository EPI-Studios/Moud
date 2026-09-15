package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.InstanceRef;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Arrange {

    public enum Edge { MIN, CENTRE, MAX }

    private record Item(Instance instance, CFrame world, double centre, double half) {
        double min() {
            return centre - half;
        }

        double max() {
            return centre + half;
        }
    }

    private Arrange() {}

    public static void align(SceneDocument document, int axis, Edge edge) {
        List<Item> items = items(document, axis);
        if (items.size() < 2) return;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (Item item : items) {
            low = Math.min(low, item.min());
            high = Math.max(high, item.max());
        }
        List<Edit> edits = new ArrayList<>();
        for (Item item : items) {
            double shift = switch (edge) {
                case MIN -> low - item.min();
                case MAX -> high - item.max();
                case CENTRE -> (low + high) * 0.5 - item.centre();
            };
            edits.addAll(move(document, item, axis, shift));
        }
        document.history().execute(new Batch("Align", edits));
    }

    public static void distribute(SceneDocument document, int axis) {
        List<Item> items = items(document, axis);
        if (items.size() < 3) return;
        items.sort(Comparator.comparingDouble(Item::centre));
        double sizes = 0;
        for (Item item : items) sizes += item.half() * 2.0;
        double span = items.getLast().max() - items.getFirst().min();
        double gap = (span - sizes) / (items.size() - 1);
        double cursor = items.getFirst().min();
        List<Edit> edits = new ArrayList<>();
        for (Item item : items) {
            edits.addAll(move(document, item, axis, cursor - item.min()));
            cursor += item.half() * 2.0 + gap;
        }
        document.history().execute(new Batch("Distribute", edits));
    }

    private static List<Item> items(SceneDocument document, int axis) {
        Vector3 direction = axis(axis);
        List<Item> items = new ArrayList<>();
        for (InstanceRef root : document.selectedRoots()) {
            Instance instance = document.find(root);
            if (!(instance instanceof Spatial)) continue;
            CFrame world = Transforms.world(instance);
            double half = instance instanceof Part part ? Frames.extentAlong(part, direction) : 0.0;
            items.add(new Item(instance, world, world.position().dot(direction), half));
        }
        return items;
    }

    private static List<Edit> move(SceneDocument document, Item item, int axis, double shift) {
        if (Math.abs(shift) < 1.0e-9) return List.of();
        return Frames.worldWrites(document, item.instance(), item.world().withPosition(item.world().position().add(axis(axis).mul(shift))), null);
    }

    private static Vector3 axis(int axis) {
        return switch (axis) {
            case 0 -> new Vector3(1, 0, 0);
            case 1 -> new Vector3(0, 1, 0);
            default -> new Vector3(0, 0, 1);
        };
    }
}
