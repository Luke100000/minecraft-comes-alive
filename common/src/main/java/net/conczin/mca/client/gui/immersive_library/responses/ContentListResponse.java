package net.conczin.mca.client.gui.immersive_library.responses;

import net.conczin.mca.client.gui.immersive_library.types.LiteContent;

public record ContentListResponse(LiteContent[] contents) implements Response {

}
