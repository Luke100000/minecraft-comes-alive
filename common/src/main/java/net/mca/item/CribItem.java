package net.mca.item;

import net.mca.entity.CribEntity;
import net.mca.entity.CribWoodType;
import net.mca.entity.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CribItem extends Item
{
	private final CribWoodType wood;
	private final DyeColor color;
	
	public CribItem(Properties settings, CribWoodType wood, DyeColor color)
	{
		super(settings);
		this.wood = wood;
		this.color = color;
	}
	
	public CribWoodType getWood() { return wood; }
	public DyeColor getColor() { return color; }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Direction direction = context.getClickedFace();
        if (direction == Direction.DOWN) { return InteractionResult.FAIL; }
        
        Level world = context.getLevel();
        BlockPlaceContext itemPlacementContext = new BlockPlaceContext(context);
        BlockPos blockPos = itemPlacementContext.getClickedPos();
        ItemStack itemStack = context.getItemInHand();
        Vec3 vec3d = Vec3.atBottomCenterOf(blockPos);
        AABB box = EntitiesMCA.CRIB.get().getDimensions().makeBoundingBox(vec3d.x(), vec3d.y(), vec3d.z());
        
        if (!world.noCollision(null, box) || !world.getEntities(null, box).isEmpty()) { return InteractionResult.FAIL; }
        
        if (world instanceof ServerLevel)
        {
            ServerLevel serverWorld = (ServerLevel)world;
            CribEntity crib = EntitiesMCA.CRIB.get().create(serverWorld, null, null, blockPos, MobSpawnType.SPAWN_EGG, true, true);
            
             crib.setWoodType(wood);
             crib.setColor(color);
            
            float f = (float)Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0f) + 22.5f) / 45.0f) * 45.0f;
            crib.moveTo(crib.getX(), crib.getY(), crib.getZ(), f, 0.0f);
            serverWorld.addFreshEntityWithPassengers(crib);
            
            world.playSound(null, crib.getX(), crib.getY(), crib.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);
            crib.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
        }
        itemStack.shrink(1);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
