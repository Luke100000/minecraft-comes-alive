package net.conczin.mca.entity.ai.relationship;

public enum RelationshipType {
    STRANGER(1),
    SELF(2),
    SIBLING(2),
    SPOUSE(3),
    PARENT(3),
    CHILD(4);

    private final int proximity;

    RelationshipType(int proximity) {
        this.proximity = proximity;
    }

    public int getProximityAmplifier() {
        return proximity;
    }
}