package net.conczin.mca.client.gui;

import net.conczin.mca.entity.interaction.Constraint;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Button is a button defined in assets/mca/api/gui/*
 * <p>
 * These buttons are dynamically attached to a Screen and include additional instruction/constraints for building
 * and processing interactions.
 */
public final class MCAButton {
    private static final int COL_WIDTH = 66;
    private static final int ROW_HEIGHT = 21;
    /**
     * The text and action to perform for this button
     */
    private final String identifier;
    /**
     * Where the text is aligned
     */
    private final String align;
    private final int x;
    private final int col;
    private final int y;
    private final int row;
    private final int width;
    private final int height;
    /**
     * whether the button press is sent to the server for processing
     */
    private final boolean notifyServer;
    /**
     * whether the button is processed by the villager or the server itself
     */
    private final boolean targetServer;
    /**
     * list of EnumConstraints separated by the pipe character |
     */
    private final String constraints;
    /**
     * Whether the button should be hidden completely when its constraints fail. The default is to simply disable it.
     */
    private final boolean hideOnFail;
    /**
     * Whether the button is an interaction that generates a response and boosts/decreases hearts
     */
    private final boolean isInteraction;

    public MCAButton(
            String identifier,     //The text and action to perform for this button
            String align, int x, int col, int y,
            int row, int width, int height,
            boolean notifyServer,  //whether the button press is sent to the server for processing
            boolean targetServer,  //whether the button is processed by the villager or the server itself
            String constraints,    //list of EnumConstraints separated by the pipe character |
            boolean hideOnFail,    //Whether the button should be hidden completely when its constraints fail. The default is to simply disable it.
            boolean isInteraction  //Whether the button is an interaction that generates a response and boosts/decreases hearts
    ) {
        this.identifier = identifier;
        this.align = align;
        this.x = x;
        this.col = col;
        this.y = y;
        this.row = row;
        this.width = width;
        this.height = height;
        this.notifyServer = notifyServer;
        this.targetServer = targetServer;
        this.constraints = constraints;
        this.hideOnFail = hideOnFail;
        this.isInteraction = isInteraction;
    }

    public String identifier() {
        return identifier;
    }

    public String align() {
        return align == null ? "top_right" : align;
    }

    public int x() {
        return x + col * COL_WIDTH;
    }

    public int y() {
        return y + row * ROW_HEIGHT;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean notifyServer() {
        return notifyServer;
    }

    public boolean targetServer() {
        return targetServer;
    }

    public String constraints() {
        return constraints;
    }

    public boolean hideOnFail() {
        return hideOnFail;
    }

    public boolean isInteraction() {
        return isInteraction;
    }

    public Stream<Constraint> getConstraints() {
        return Constraint.fromStringList(constraints).stream();
    }

    //checks if a map of given evaluated constraints apply to this button
    public boolean isValidForConstraint(Set<Constraint> constraints) {
        return getConstraints().allMatch(constraints::contains);
    }
}