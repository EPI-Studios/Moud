package com.meekdev.moud.mod.client;

import com.google.gson.JsonElement;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.core.ui.Frame;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.core.ui.ImageLabel;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.TextButton;
import com.meekdev.moud.core.ui.TextLabel;
import com.meekdev.moud.core.ui.VerticalAlign;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.script.api.CoreGuiRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CoreGui implements CoreGuiRef {

    public static final CoreGui INSTANCE = new CoreGui();

    private static final String PART_TABLE = "/assets/moud/gui/core-parts.json";
    private static final Map<String, Feature> PARTS = parts();

    private static final String SCREEN = "CoreNotifications";
    private static final int WIDTH = 220;
    private static final int MARGIN = 16;
    private static final int GAP = 8;
    private static final int ICON = 32;
    private static final int BUTTON_WIDTH = 74;
    private static final int BUTTON_HEIGHT = 18;
    private static final int EMPTY_HEIGHT = 16;
    private static final int TEXT_HEIGHT = 24;
    private static final int ICON_ROOM = 24;
    private static final int FADE = 1;

    private static final Color BACKGROUND = new Color(0.07f, 0.08f, 0.1f, 1f);
    private static final Color EDGE = new Color(0.22f, 0.24f, 0.29f, 1f);
    private static final Color TITLE = new Color(0.95f, 0.95f, 0.97f, 1f);
    private static final Color BODY = new Color(0.76f, 0.78f, 0.82f, 1f);
    private static final Color BUTTON = new Color(0.16f, 0.18f, 0.22f, 1f);

    private static final class Notice {
        private final Notification notification;
        private Frame frame;
        private double left;
        private boolean answered;

        private Notice(Notification notification) {
            this.notification = notification;
            this.left = notification.duration();
        }

        private void answer(@Nullable String button) {
            if (answered) return;
            answered = true;
            notification.answered().accept(button);
        }
    }

    private final List<Notice> notices = new ArrayList<>();
    private final Map<String, Boolean> asPlaced = new LinkedHashMap<>();
    private final Clock clock = new Clock();

    private @Nullable ScreenGui screen;

    private CoreGui() {}

    private static Map<String, Feature> parts() {
        Map<String, Feature> parts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> part : JsonResources.read(PART_TABLE).entrySet()) {
            parts.put(part.getKey(), Feature.valueOf(part.getValue().getAsString()));
        }
        return Map.copyOf(parts);
    }

    @Override
    public boolean enabled(String name) {
        Feature part = PARTS.get(name);
        return part != null && MoudMod.features().isOn(part);
    }

    @Override
    public void enabled(String name, boolean on) {
        Feature part = PARTS.get(name);
        if (part == null) return;
        asPlaced.putIfAbsent(name, MoudMod.features().isOn(part));
        MoudMod.features().set(part, on);
    }

    @Override
    public void notify(Notification notification) {
        notices.add(new Notice(notification));
    }

    public void reset() {
        for (Notice notice : notices) notice.answer(null);
        notices.clear();
        screen = null;
        clock.tick();
        asPlaced.forEach((name, on) -> MoudMod.features().set(PARTS.get(name), on));
        asPlaced.clear();
    }

    public void frame() {
        double delta = clock.tick();
        if (notices.isEmpty()) return;
        InstanceTree tree = ClientScene.tree();
        Instance world = ClientScene.world();
        if (tree == null || world == null) {
            reset();
            return;
        }
        if (screen == null || !screen.isAlive() || screen.tree() != tree) {
            screen = Instances.createLocal(Classes.SCREEN_GUI, world, SCREEN);
            screen.displayOrder = 1000;
            for (Notice notice : notices) notice.frame = null;
        }
        int top = MARGIN;
        Iterator<Notice> showing = notices.iterator();
        while (showing.hasNext()) {
            Notice notice = showing.next();
            if (notice.frame == null || !notice.frame.isAlive()) notice.frame = build(notice);
            notice.left -= delta;
            if (notice.left <= 0 || notice.answered) {
                Instances.destroy(notice.frame);
                notice.answer(null);
                showing.remove();
                continue;
            }
            notice.frame.position = new UDim2(1, -MARGIN, 0, top);
            notice.frame.backgroundTransparency = notice.left < FADE ? 1 - notice.left / FADE : 0;
            top += (int) notice.frame.size.yOffset() + GAP;
        }
        if (notices.isEmpty() && screen != null && screen.isAlive()) {
            Instances.destroy(screen);
            screen = null;
        }
    }

    private Frame build(Notice notice) {
        Notification notification = notice.notification;
        boolean buttons = !notification.button1().isEmpty() || !notification.button2().isEmpty();
        boolean icon = !notification.icon().isEmpty();
        boolean body = !notification.text().isEmpty();
        int height = EMPTY_HEIGHT;
        if (body) height += TEXT_HEIGHT;
        if (buttons) height += BUTTON_HEIGHT + GAP;
        if (icon) height += ICON_ROOM;
        Frame frame = Instances.createLocal(Classes.FRAME, screen, "Notification");
        frame.anchorX = 1;
        frame.size = UDim2.fromOffset(WIDTH, height);
        frame.backgroundColor = BACKGROUND;
        frame.borderSize = 1;
        frame.borderColor = EDGE;
        frame.cornerRadius = 4;
        frame.zIndex = 1;

        int left = icon ? MARGIN + ICON : 10;
        if (icon) {
            ImageLabel image = Instances.createLocal(Classes.IMAGE_LABEL, frame, "Icon");
            image.image = notification.icon();
            image.position = UDim2.fromOffset(8, 8);
            image.size = UDim2.fromOffset(ICON, ICON);
        }
        TextLabel title = Instances.createLocal(Classes.TEXT_LABEL, frame, "Title");
        title.text = notification.title();
        title.textColor = TITLE;
        title.position = UDim2.fromOffset(left, 8);
        title.size = new UDim2(1, -left - 10, 0, 12);
        title.backgroundTransparency = 1;
        title.textXAlignment = HorizontalAlign.LEFT;
        title.textYAlignment = VerticalAlign.CENTER;

        if (body) {
            TextLabel text = Instances.createLocal(Classes.TEXT_LABEL, frame, "Text");
            text.text = notification.text();
            text.textColor = BODY;
            text.textSize = 8;
            text.textWrapped = true;
            text.position = UDim2.fromOffset(left, 22);
            text.size = new UDim2(1, -left - 10, 0, 22);
            text.backgroundTransparency = 1;
            text.textXAlignment = HorizontalAlign.LEFT;
            text.textYAlignment = VerticalAlign.TOP;
        }

        int row = height - BUTTON_HEIGHT - 6;
        if (!notification.button2().isEmpty()) {
            button(notice, frame, notification.button2(), new UDim2(1, -10 - BUTTON_WIDTH, 0, row));
        }
        if (!notification.button1().isEmpty()) {
            double offset = notification.button2().isEmpty() ? -10 - BUTTON_WIDTH : -16 - BUTTON_WIDTH * 2;
            button(notice, frame, notification.button1(), new UDim2(1, offset, 0, row));
        }
        return frame;
    }

    private void button(Notice notice, Frame frame, String label, UDim2 position) {
        TextButton button = Instances.createLocal(Classes.TEXT_BUTTON, frame, label);
        button.text = label;
        button.textColor = TITLE;
        button.textSize = 8;
        button.backgroundColor = BUTTON;
        button.cornerRadius = 3;
        button.position = position;
        button.size = UDim2.fromOffset(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.activated.connect(pressed -> notice.answer(label));
    }
}
