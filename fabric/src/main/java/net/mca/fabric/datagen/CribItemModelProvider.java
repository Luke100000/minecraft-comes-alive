package net.mca.fabric.datagen;

import java.util.Locale;
import java.util.Optional;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.mca.MCA;
import net.mca.entity.CribWoodType;
import net.mca.item.CribItem;
import net.mca.item.ItemsMCA;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

public class CribItemModelProvider extends FabricModelProvider {

	public CribItemModelProvider(FabricDataOutput output) { super(output); }

	@Override
	public void generateBlockStateModels(BlockModelGenerators blockStateModelGenerator) {}

	@Override
	public void generateItemModels(ItemModelGenerators itemModelGenerator)
	{
		for(CribWoodType wood : CribWoodType.values())
		{
			for(DyeColor color : DyeColor.values())
			{
				Item item = ItemsMCA.CRIBS.stream().filter(c ->
				{
					CribItem crib = (CribItem) c.get();
					return crib.getColor() == color && crib.getWood() == wood;
				}).findFirst().orElse(ItemsMCA.CRIBS.get(0)).get();

				ModelTemplate cribModel = new ModelTemplate(Optional.of(new ResourceLocation("minecraft", "item/generated")), Optional.empty(), TextureSlot.LAYER0, TextureSlot.LAYER1);

				cribModel.create(ModelLocationUtils.getModelLocation(item), TextureMapping.layered(MCA.locate("item/crib/beds/" + color.getName()),
					MCA.locate("item/crib/frames/" + wood.toString().toLowerCase(Locale.ROOT))), itemModelGenerator.output);
			}
		}
	}
}
