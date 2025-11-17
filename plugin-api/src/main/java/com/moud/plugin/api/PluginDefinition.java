package com.moud.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares metadata for a plugin when extending the {@link Plugin} base class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginDefinition {
    String id();
    String name();
    String version() default "0.0.0";
    String description() default "";
}
