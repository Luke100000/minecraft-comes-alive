package net.conczin.mca.entity;

public interface Infectable {
    float INITIAL_INFECTION_AMOUNT = 0.001f;
    float FEVER_THRESHOLD = 0.2f;
    float BABBLING_THRESHOLD = 0.6f;

    default boolean isInfected() {
        return getInfectionProgress() > 0.0f;
    }

    default void setInfected(boolean infected) {
        setInfectionProgress(infected ? Math.max(getInfectionProgress(), INITIAL_INFECTION_AMOUNT) : 0.0f);
    }

    /**
     * A value from 0 to 1 indicating how far along the infection has progressed.
     */
    float getInfectionProgress();

    void setInfectionProgress(float progress);
}
