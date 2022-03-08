package mca.resources.data.analysis;

import mca.resources.data.SerializablePair;

import java.io.Serial;

public class ChanceAnalysis extends Analysis<Integer> {
    @Serial
    private static final long serialVersionUID = -2685774468194171791L;

    @Override
    public boolean isPositive(Integer v) {
        return v >= 0;
    }

    @Override
    public String asString(Integer v) {
        return String.valueOf(v);
    }

    public String getTotalAsString() {
        float positive = getSummands().stream().mapToInt(SerializablePair::getRight).filter(v -> v > 0).sum();
        float negative = -getSummands().stream().mapToInt(SerializablePair::getRight).filter(v -> v < 0).sum();
        return String.format("%d%% chance", (int)(positive / (positive + negative) * 100.0f));
    }

    @Override
    public Integer getTotal() {
        return getSummands().stream().mapToInt(SerializablePair::getRight).sum();
    }
}
