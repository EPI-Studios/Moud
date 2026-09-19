package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import java.util.List;

public interface PluginRef {

    record Mouse(Vector3 origin, Vector3 direction, Vector3 hit, Vector3 normal, Instance target, boolean over) {}

    interface Ui {

        void text(String text);

        void muted(String text);

        void heading(String text);

        boolean button(String label);

        String input(String label, String value);

        double number(String label, double value, double step);

        double slider(String label, double value, double minimum, double maximum);

        boolean checkbox(String label, boolean value);

        Color color(String label, Color value);

        String choice(String label, String value, List<String> options);

        void separator();

        void sameLine();
    }

    List<Instance> selection();

    void select(List<Instance> chosen);

    Object setting(String plugin, String key);

    void setting(String plugin, String key, Object value);

    Mouse mouse();

    void record(String label, Runnable changes);
}
