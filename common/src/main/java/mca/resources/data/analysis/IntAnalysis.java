package mca.resources.data.analysis;

import java.io.Serial;
import mca.resources.data.SerializablePair;

public class IntAnalysis extends Analysis<Integer> {
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

    @Override
    public Integer getTotal() {
        return getSummands().stream().mapToInt(SerializablePair::getRight).sum();
    }
}
