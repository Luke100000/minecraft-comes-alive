package mca.item;

import dev.architectury.registry.CreativeTabRegistry;
import mca.MCA;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;

public interface ItemGroupMCA {
    ItemGroup MCA_GROUP = CreativeTabRegistry.create(
            new Identifier(MCA.MOD_ID, "mca_tab"),
            () -> ItemsMCA.ENGAGEMENT_RING.get().getDefaultStack()
    );
}
