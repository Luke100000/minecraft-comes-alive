package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectedFloorScannerCachingTest {
    @Test
    void rejectedFlightCachesEveryVisitedCell() throws ReflectiveOperationException {
        SelectedFloorScanner.SurfaceCell lower = cell(0, 64, 0);
        SelectedFloorScanner.SurfaceCell upper = cell(1, 65, 0);

        Class<?> stepType = nestedType("HorizontalStep");
        Constructor<?> stepConstructor = stepType.getDeclaredConstructor(
                SelectedFloorScanner.SurfaceCell.class, BlockPos.class);
        stepConstructor.setAccessible(true);
        Object up = stepConstructor.newInstance(upper, null);
        Object down = stepConstructor.newInstance(lower, null);
        Map<BlockPos, List<?>> steps = Map.of(
                lower.feet(), List.of(up),
                upper.feet(), List.of(down)
        );

        Class<?> providerType = nestedType("StepProvider");
        AtomicInteger lookups = new AtomicInteger();
        Object provider = Proxy.newProxyInstance(
                SelectedFloorScanner.class.getClassLoader(),
                new Class<?>[]{providerType},
                (proxy, method, args) -> {
                    if (method.getName().equals("steps")) {
                        lookups.incrementAndGet();
                        SelectedFloorScanner.SurfaceCell requested =
                                (SelectedFloorScanner.SurfaceCell) args[0];
                        return steps.getOrDefault(requested.feet(), List.of());
                    }
                    return switch (method.getName()) {
                        case "toString" -> "counting step provider";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                }
        );

        Class<?> ownershipType = nestedType("StairFlightOwnership");
        Constructor<?> ownershipConstructor = ownershipType.getDeclaredConstructor(
                Level.class, providerType, int.class, int.class);
        ownershipConstructor.setAccessible(true);
        Object ownership = ownershipConstructor.newInstance(null, provider, 64, 64);
        Method owner = ownershipType.getDeclaredMethod(
                "owner", SelectedFloorScanner.SurfaceCell.class);
        owner.setAccessible(true);

        assertNull(owner.invoke(ownership, lower));
        int firstDiscoveryLookups = lookups.get();

        assertNull(owner.invoke(ownership, upper));
        assertEquals(firstDiscoveryLookups, lookups.get(),
                "a second cell in the same rejected flight must reuse the negative result");
    }

    private static SelectedFloorScanner.SurfaceCell cell(int x, int y, int z) {
        return new SelectedFloorScanner.SurfaceCell(new BlockPos(x, y, z), y, y + 3);
    }

    private static Class<?> nestedType(String simpleName) throws ClassNotFoundException {
        return Class.forName(SelectedFloorScanner.class.getName() + "$" + simpleName);
    }
}
