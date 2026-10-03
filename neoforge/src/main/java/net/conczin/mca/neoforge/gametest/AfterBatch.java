package net.conczin.mca.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compatibility metadata for pre-26 GameTest batch teardown callbacks. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AfterBatch {
    String batch();
}
