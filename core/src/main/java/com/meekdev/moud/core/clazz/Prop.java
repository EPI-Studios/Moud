package com.meekdev.moud.core.clazz;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// only for the exceptions, a plain public field is already a replicated property
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Prop {

    boolean replicated() default true;

    // worked out from something the other side already has, so sending it sends it twice
    //
    // not the same as not replicated: a property is driven only while something else is carrying
    // it, and the instance is what knows whether that is so right now. a body nobody is wearing has
    // no player behind it and every one of these goes over like anything else -- so the class says
    // which properties, and the instance says when
    boolean driven() default false;

    // the text names a file, so text that cannot name one is an error at the write rather than a
    // shrug at the read. §9.2: a range on a number clamps because an extreme is still a number, and
    // a wrong kind of value raises because it is a typo
    boolean asset() default false;

    double min() default Double.NEGATIVE_INFINITY;

    double max() default Double.POSITIVE_INFINITY;
}
