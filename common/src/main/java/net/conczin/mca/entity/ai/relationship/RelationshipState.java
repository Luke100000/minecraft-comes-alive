package net.conczin.mca.entity.ai.relationship;

public enum RelationshipState {
    /**
     * The default state. All entities when born are not married.
     */
    SINGLE("notMarried"),
    /**
     * Promised.
     */
    PROMISED("promised"),
    /**
     * Engaged.
     */
    ENGAGED("engaged"),
    /**
     * Married to another villager.
     */
    MARRIED_TO_VILLAGER("married"),
    /**
     * Married to a player.
     */
    MARRIED_TO_PLAYER("marriedToPlayer"),
    /**
     * Was once married but the spouse is dead.
     */
    WIDOW("widow");

    private static final RelationshipState[] VALUES = values();

    private final String icon;

    RelationshipState(String icon) {
        this.icon = icon;
    }

    public boolean isMarried() {
        return this == MARRIED_TO_PLAYER || this == MARRIED_TO_VILLAGER;
    }

    public RelationshipState base() {
        return this == MARRIED_TO_PLAYER ? MARRIED_TO_VILLAGER : this;
    }

    public String getIcon() {
        return icon;
    }

    public static RelationshipState byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return SINGLE;
        }
        return VALUES[id];
    }
}

