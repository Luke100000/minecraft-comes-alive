package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.entity.EntityWrapper;

public interface CompassionateEntity<T extends EntityRelationship> extends EntityWrapper {
    T getRelationships();
}
