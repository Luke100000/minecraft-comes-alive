package net.mca.item;

import net.mca.entity.CribEntity;
import net.mca.entity.CribWoodType;
import net.mca.entity.EntitiesMCA;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

public class CribItem extends Item
{
	private final CribWoodType wood;
	private final DyeColor color;
	
	public CribItem(Settings settings, CribWoodType wood, DyeColor color)
	{
		super(settings);
		this.wood = wood;
		this.color = color;
	}
	
	public CribWoodType getWood() { return wood; }
	public DyeColor getColor() { return color; }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        Direction direction = context.getSide();
        if (direction == Direction.DOWN) { return ActionResult.FAIL; }
        
        World world = context.getWorld();
        ItemPlacementContext itemPlacementContext = new ItemPlacementContext(context);
        BlockPos blockPos = itemPlacementContext.getBlockPos();
        ItemStack itemStack = context.getStack();
        Vec3d vec3d = Vec3d.ofBottomCenter(blockPos);
        Box box = EntitiesMCA.CRIB.get().getDimensions().getBoxAt(vec3d.getX(), vec3d.getY(), vec3d.getZ());
        
        if (!world.isSpaceEmpty(null, box) || !world.getOtherEntities(null, box).isEmpty()) { return ActionResult.FAIL; }
        
        if (world instanceof ServerWorld)
        {
        	ServerWorld serverWorld = (ServerWorld)world;
            CribEntity crib = EntitiesMCA.CRIB.get().create(serverWorld, null, null, blockPos, SpawnReason.SPAWN_EGG, true, true);
            
             crib.setWoodType(wood);
             crib.setColor(color);
            
            float f = (float)MathHelper.floor((MathHelper.wrapDegrees(context.getPlayerYaw() - 180.0f) + 22.5f) / 45.0f) * 45.0f;
            crib.refreshPositionAndAngles(crib.getX(), crib.getY(), crib.getZ(), f, 0.0f);
            serverWorld.spawnEntityAndPassengers(crib);
            
            world.playSound(null, crib.getX(), crib.getY(), crib.getZ(), SoundEvents.ENTITY_ARMOR_STAND_PLACE, SoundCategory.BLOCKS, 0.75f, 0.8f);
            crib.emitGameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
        }
        itemStack.decrement(1); 
        return ActionResult.success(world.isClient);
    }
}
