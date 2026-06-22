package net.mca.client.model;

import net.mca.entity.CribEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;

public class CribEntityModel<T extends CribEntity> extends EntityModel<T>
{
	private final ModelPart CRIB;
	
    public CribEntityModel(ModelPart root)
    {
		this.CRIB = root.getChild("Crib");
    }
    

	public static ModelData getModelData(Dilation dilation)
	{
		ModelData modelData = new ModelData();
		ModelPartData data = modelData.getRoot();
		
		ModelPartData crib = data.addChild("Crib", ModelPartBuilder.create().uv(0, 0).cuboid(-9.0F, -5.0F, -13.0F, 19.0F, 2.0F, 25.0F, dilation), ModelTransform.pivot(-0.5F, 6.0F, 0.5F));
		
		crib.addChild("Bars", ModelPartBuilder.create()
			.uv(0, 4).cuboid(9.0F, -16.0F, -11.5F, 0.0F, 11.0F, 23.0F, dilation)
			.uv(46, 49).cuboid(-8.0F, -16.0F, 11.5F, 17.0F, 11.0F, 0.0F, dilation)
			.uv(0, 4).cuboid(-8.0F, -16.0F, -11.5F, 0.0F, 11.0F, 23.0F, dilation)
			.uv(46, 49).cuboid(-8.0F, -16.0F, -11.5F, 17.0F, 11.0F, 0.0F, dilation), ModelTransform.pivot(0.0F, 0.0F, -0.5F));

		crib.addChild("Frame", ModelPartBuilder.create()
			.uv(25, 27).cuboid(8.0F, -17.0F, -11.0F, 2.0F, 1.0F, 21.0F, dilation)
			.uv(50, 30).cuboid(-7.0F, -17.0F, 10.0F, 15.0F, 1.0F, 2.0F, dilation)
			.uv(50, 27).cuboid(-7.0F, -17.0F, -13.0F, 15.0F, 1.0F, 2.0F, dilation)
			.uv(25, 27).mirrored().cuboid(-9.0F, -17.0F, -11.0F, 2.0F, 1.0F, 21.0F, dilation).mirrored(false), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		crib.addChild("Legs", ModelPartBuilder.create()
			.uv(0, 0).cuboid(8.0F, -17.0F, -13.0F, 2.0F, 12.0F, 2.0F, dilation)
			.uv(0, 0).cuboid(8.0F, -17.0F, 10.0F, 2.0F, 12.0F, 2.0F, dilation)
			.uv(9, 0).cuboid(8.0F, -3.0F, 10.0F, 2.0F, 3.0F, 2.0F, dilation)
			.uv(9, 0).mirrored().cuboid(-9.0F, -3.0F, 10.0F, 2.0F, 3.0F, 2.0F, dilation).mirrored(false)
			.uv(9, 0).cuboid(-9.0F, -3.0F, -13.0F, 2.0F, 3.0F, 2.0F, dilation)
			.uv(9, 0).mirrored().cuboid(8.0F, -3.0F, -13.0F, 2.0F, 3.0F, 2.0F, dilation).mirrored(false)
			.uv(0, 0).mirrored().cuboid(-9.0F, -17.0F, 10.0F, 2.0F, 12.0F, 2.0F, dilation).mirrored(false)
			.uv(0, 0).mirrored().cuboid(-9.0F, -17.0F, -13.0F, 2.0F, 12.0F, 2.0F, dilation).mirrored(false), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		return modelData;
	}

	@Override
	public void render(MatrixStack stack, VertexConsumer consumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
	{
		CRIB.render(stack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
//		Frame.render(stack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
//		Legs.render(stack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
//		bb_main.render(stack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

    @Override
    public void setAngles(T entity, float f, float g, float h, float i, float j) {}
}
