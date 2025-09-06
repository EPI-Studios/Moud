package com.moud.client.api.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class UIService {

    public UIElement createElement(String type) {
        return new UIElement(type);
    }

    public static final class UIElement {
        private final String type;
        private final Map<String, Object> properties = new ConcurrentHashMap<>();

        public UIElement(String type) {
            this.type = type;
        }

        public UIElement setProperty(String key, Object value) {
            properties.put(key, value);
            return this;
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public String getType() {
            return type;
        }
    }
}