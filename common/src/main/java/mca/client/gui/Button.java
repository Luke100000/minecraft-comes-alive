package mca.client.gui;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Button is a button defined in assets/mca/api/gui/*
 * <p>
 * These buttons are dynamically attached to a Screen and include additional instruction/constraints for building
 * and processing interactions.
 */
public final class Button {
    /**
     * The text and action to perform for this button
     */
    private final String identifier;

    public String identifier() {
        return identifier;
    }
    /**
     * Where the text is aligned
     */
    private final String align;

    public String align() {
        return align == null ? "top_right" : align;
    }

    private final int x;
    private final int col;
    private static final int COL_WIDTH = 66;

    public int x() {
        return x + col * COL_WIDTH;
    }

    private final int y;
    private final int row;
    private static final int ROW_HEIGHT = 21;

    public int y() {
        return y + row * ROW_HEIGHT;
    }

    private final int width;

    public int width() {
        return width;
    }

    private final int height;

    public int height() {
        return height;
    }

    /**
     * whether the button press is sent to the server for processing
     */
    private final boolean notifyServer;

    public boolean notifyServer() {
        return notifyServer;
    }

    /**
     * whether the button is processed by the villager or the server itself
     */
    private final boolean targetServer;

    public boolean targetServer() {
        return targetServer;
    }

    /**
     * list of EnumConstraints separated by the pipe character |
     */
    private final String constraints;

    public String constraints() {
        return constraints;
    }

    /**
     * Whether the button should be hidden completely when its constraints fail. The default is to simply disable it.
     */
    private final boolean hideOnFail;

    public boolean hideOnFail() {
        return hideOnFail;
    }

    /**
     * Whether the button is an interaction that generates a response and boosts/decreases hearts
     */
    private final boolean isInteraction;

    public boolean isInteraction() {
        return isInteraction;
    }

    public Button(
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

    public Stream<Constraint> getConstraints() {
        return Constraint.fromStringList(constraints).stream();
    }

    //checks if a map of given evaluated constraints apply to this button
    public boolean isValidForConstraint(Set<Constraint> constraints) {
        return getConstraints().allMatch(constraints::contains);
    }
}