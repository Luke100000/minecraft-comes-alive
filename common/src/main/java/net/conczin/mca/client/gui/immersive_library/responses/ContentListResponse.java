package net.conczin.mca.client.gui.immersive_library.responses;

import net.conczin.mca.client.gui.immersive_library.types.Content;

public record ContentListResponse(
        Content[] contents,
        boolean has_more
) implements Response {

}
