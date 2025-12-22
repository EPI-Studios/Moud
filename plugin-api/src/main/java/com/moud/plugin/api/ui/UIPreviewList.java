package com.moud.plugin.api.ui;

import java.lang.annotation.*;

/**
 * The IntelliJ plugin will discover and display these lists.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UIPreviewList {

    /**
     * Display name in IntelliJ HUD Preview panel.
     * If empty, the field name will be used.
     */
    String name() default "";

    /**
     * Preferred aspect ratio for this HUD (optional).
     * Examples: "16:9", "21:9".
     */
    String preferredAspect() default "";
}
