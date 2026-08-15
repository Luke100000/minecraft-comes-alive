package net.conczin.mca.resources.data;

import com.google.common.base.Strings;
import net.conczin.mca.resources.PoolUtil;
import net.minecraft.util.RandomSource;
import java.util.Locale;

public final class NameSet {

    public static final NameSet DEFAULT = new NameSet(" ", new String[] {"unknown"}, new String[] {"names"});

    private final String separator;
    private final String[] first;
    private final String[] second;

    public String separator() {
        return separator;
    }

    public String[] first() {
        return first;
    }

    public String[] second() {
        return second;
    }

    public NameSet(String separator, String[] first, String[] second) {
        this.separator = separator;
        this.first = first;
        this.second = second;
    }

    public String toName(RandomSource rng) {
        String first = PoolUtil.pickOne(first(), null, rng);
        String second = PoolUtil.pickOne(second(), null, rng);

        if (Strings.isNullOrEmpty(separator())) {
            return toTitleCase(first + second);
        }

        return toTitleCase(first) + separator() + toTitleCase(second);
    }

    static String toTitleCase(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1);
    }
}
