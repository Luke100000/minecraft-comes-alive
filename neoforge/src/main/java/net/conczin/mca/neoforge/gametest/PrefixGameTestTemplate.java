package net.conczin.mca.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Retains legacy template-prefix metadata for the annotation compatibility bridge. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrefixGameTestTemplate {
    boolean value();
}
