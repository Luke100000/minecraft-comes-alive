package net.conczin.mca.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compatibility metadata for GameTests ported from the pre-26 annotation API.
 * The registration bridge converts this metadata to 26.x {@code TestData}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameTest {
    int timeoutTicks() default 100;

    String batch() default "defaultBatch";

    boolean skyAccess() default false;

    int rotationSteps() default 0;

    boolean required() default true;

    boolean manualOnly() default false;

    String template() default "";

    String templateNamespace() default "minecraft";

    long setupTicks() default 0L;

    int attempts() default 1;

    int requiredSuccesses() default 1;
}
