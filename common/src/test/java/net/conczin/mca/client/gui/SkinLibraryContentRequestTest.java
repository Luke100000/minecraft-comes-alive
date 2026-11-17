package net.conczin.mca.client.gui;

import net.conczin.mca.client.gui.immersive_library.Auth;
import net.conczin.mca.client.gui.immersive_library.responses.ContentListResponse;
import net.conczin.mca.client.gui.immersive_library.responses.ErrorResponse;
import net.conczin.mca.client.gui.immersive_library.types.Content;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class SkinLibraryContentRequestTest {
    @Test
    void staleSameQueryResponseDoesNotReplaceNewPendingRequest() throws Exception {
        SkinLibraryScreen screen = screenWithPendingQuery();
        Object pending = field("pendingQuery").get(screen);
        ContentListResponse oldResponse = new ContentListResponse(new Content[0], false);

        // Query A was requested, then B, then A again. The earlier A is no longer current.
        accept(screen, pending, 1L, 0L, Auth.getToken(), oldResponse, null);

        assertSame(pending, field("pendingQuery").get(screen));
        assertNull(field("loadedQuery").get(screen));
        assertNull(field("error").get(screen));

        // Even a late HTTP failure must not replace the newer query's error state.
        accept(screen, pending, 1L, 0L, Auth.getToken(), null, new IllegalStateException("old request"));
        assertSame(pending, field("pendingQuery").get(screen));
        assertNull(field("error").get(screen));
    }

    @Test
    void priorAccountCannotClearPendingRequestFromCurrentSession() throws Exception {
        SkinLibraryScreen screen = screenWithPendingQuery();
        Object pending = field("pendingQuery").get(screen);

        accept(screen, pending, 3L, 1L, Auth.getToken(),
                new ErrorResponse(401, "expired prior session"), null);

        assertSame(pending, field("pendingQuery").get(screen));
        assertNull(field("error").get(screen));

        // The current session's failure is still handled after both obsolete callbacks.
        accept(screen, pending, 3L, 2L, Auth.getToken(), null, new IllegalStateException("current request"));
        assertNull(field("pendingQuery").get(screen));
        assertNotNull(field("error").get(screen));
    }

    private static SkinLibraryScreen screenWithPendingQuery() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        SkinLibraryScreen screen = (SkinLibraryScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(SkinLibraryScreen.class);
        field("page").set(screen, SkinLibraryScreen.Page.LIBRARY);
        field("filteredString").set(screen, "#hair");
        Field sorting = field("sortingMode");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> sortValue = Enum.valueOf((Class) sorting.getType(), "RECOMMENDATIONS");
        sorting.set(screen, sortValue);
        Field subscription = field("subscriptionFilter");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> subscriptionValue = Enum.valueOf((Class) subscription.getType(), "LIBRARY");
        subscription.set(screen, subscriptionValue);
        field("accountGeneration").setLong(screen, 2L);
        field("contentRequestGeneration").setLong(screen, 3L);

        Method createQuery = SkinLibraryScreen.class.getDeclaredMethod("createQuery");
        createQuery.setAccessible(true);
        field("pendingQuery").set(screen, createQuery.invoke(screen));
        return screen;
    }

    private static void accept(SkinLibraryScreen screen, Object query, long generation, long session,
                               String token, Object response, Throwable failure) throws Exception {
        Class<?> responseType = Class.forName(
                "net.conczin.mca.client.gui.immersive_library.responses.Response");
        Method method = SkinLibraryScreen.class.getDeclaredMethod("acceptContents", query.getClass(),
                long.class, long.class, String.class, responseType, Throwable.class);
        method.setAccessible(true);
        method.invoke(screen, query, generation, session, token, response, failure);
    }

    private static Field field(String name) throws Exception {
        Field field = SkinLibraryScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
