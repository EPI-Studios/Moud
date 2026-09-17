package com.meekdev.moud.script.api;

import java.util.function.Consumer;

public interface CoreGuiRef {

    record Notification(String title, String text, String icon, double duration,
                        String button1, String button2, Consumer<String> answered) {}

    boolean enabled(String name);

    void enabled(String name, boolean on);

    void notify(Notification notification);
}
