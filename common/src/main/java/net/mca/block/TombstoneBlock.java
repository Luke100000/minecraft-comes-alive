package net.mca.block;

import net.mca.entity.Infectable;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.GraveyardManager;
import net.mca.util.NbtHelper;
import net.mca.util.VoxelShapeUtil;
import net.mca.util.localization.FlowingText;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TombstoneBlock extends BlockWithEntity implements Waterloggable {
    public static final VoxelShape GRAVELLING_SHAPE = Block.createCuboidShape(1, 0, 1, 15, 1, 15);
    public static final VoxelShape UPRIGHT_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(2, 2, 7, 14, 15, 9),
            Block.createCuboidShape(1, 0, 6, 15, 2, 10)
    );
    public static final VoxelShape CROSS_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(6, 0, 2, 10, 28, 4),
            Block.createCuboidShape(-1, 18, 2, 17, 21, 4)
    );
    public static final VoxelShape SLANTED_SHAPE = Block.createCuboidShape(0, 0, 2, 16, 7, 14);
    public static final VoxelShape WALL_SHAPE = Block.createCuboidShape(1, 1, 0, 15, 15, 1);

    private final Map<Direction, VoxelShape> shapes;

    private final int lineWidth;
    private final int maxNameHeight;
    private final Vec3d nameplateOffset;
    private final boolean requiresSolid;
    private final float rotation;

    public TombstoneBlock(Settings properties, int lineWidth, int maxNameHeight, Vec3d nameplateOffset, float rotation, boolean requiresSolid, VoxelShape baseShape) {
        super(properties);
        setDefaultState(getDefaultState().with(Properties.WATERLOGGED, false));

        this.lineWidth = lineWidth;
        this.maxNameHeight = maxNameHeight;
        this.nameplateOffset = nameplateOffset;
        this.rotation = rotation;
        this.requiresSolid = requiresSolid;
        shapes = Arrays.stream(Direction.values())
                .filter(d -> d.getAxis() != Axis.Y)
                .collect(Collectors.toMap(
                        Function.identity(),
                        VoxelShapeUtil.rotator(baseShape))
                );
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public int getMaxNameHeight() {
        return maxNameHeight;
    }

    public Vec3d getNameplateOffset() {
        return nameplateOffset;
    }

    public float getRotation() {
        return rotation;
    }

    @Override
    public boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    // TODO: Verify (1.20)
//    @Override
//    public boolean canMobSpawnInside() {
//        return true;
//    }

    @Override
    @Deprecated
    public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext ePos) {
        if (this == BlocksMCA.SLANTED_HEADSTONE.get()) {
            shapes.replaceAll((i, v) -> VoxelShapeUtil.rotator(SLANTED_SHAPE).apply(i));
        }

        return shapes.getOrDefault(state.get(Properties.HORIZONTAL_FACING), VoxelShapes.fullCube());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        updateTombstoneState(world, pos);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Data.of(world.getBlockEntity(pos)).ifPresent(data -> data.readFromStack(stack));
        updateTombstoneState(world, pos);
    }

    private void updateTombstoneState(World world, BlockPos pos) {
        if (!world.isClient) {
            GraveyardManager.get((ServerWorld)world).setTombstoneState(pos,
                    hasEntity(world, pos) ? GraveyardManager.TombstoneState.FILLED : GraveyardManager.TombstoneState.EMPTY
            );
        }
    }

    @Deprecated
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            updateNeighbors(state, world, pos);
            GraveyardManager.get((ServerWorld)world).removeTombstoneState(pos);
        }
    }


    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return BlockEntityTypesMCA.TOMBSTONE.get().instantiate(pos, state);
    }


    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return (w, pos, s, data) -> ((Data)data).tick();
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.WATERLOGGED).add(Properties.HORIZONTAL_FACING);
    }

    @Deprecated
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(Properties.WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }

        if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }

        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (requiresSolid) {
            pos = pos.down();
            return world.getBlockState(pos).isSideSolid(world, pos, Direction.UP, SideShapeType.FULL);
        }

        return true;
    }

    @Deprecated
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    private void updateNeighbors(BlockState state, World world, BlockPos pos) {
        world.updateNeighborsAlways(pos, this);
        world.updateNeighborsAlways(pos.offset(state.get(Properties.HORIZONTAL_FACING)), this);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(Properties.HORIZONTAL_FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rot) {
        return state.with(Properties.HORIZONTAL_FACING, rot.rotate(state.get(Properties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)));
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.getStrongRedstonePower(world, pos, direction);
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return direction == state.get(Properties.HORIZONTAL_FACING) && hasEntity(world, pos) ? 15 : 0;
    }

    protected boolean hasEntity(BlockView world, BlockPos pos) {
        return Data.of(world.getBlockEntity(pos)).map(Data::hasEntity).orElse(false);
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Data.of(world.getBlockEntity(pos)).filter(Data::isResurrecting).ifPresent(data -> {
            for (int i = 0; i < random.nextInt(8) + 1; ++i) {
                world.addParticle(random.nextBoolean() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                        pos.getX() + random.nextFloat(),
                        pos.getY() + random.nextFloat(),
                        pos.getZ() + random.nextFloat(),
                        (random.nextFloat() - 0.5) / 10F,
                        0,
                        (random.nextFloat() - 0.5) / 10F
                );
            }
        });
    }

    @Deprecated
    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> stacks = super.getDroppedStacks(state, builder);

        Optional<Data> data = Data.of(builder.getOptional(LootContextParameters.BLOCK_ENTITY)).filter(Data::hasEntity);

        data
                .flatMap(Data::getEntityName)
                .ifPresent(name -> {
                    stacks.stream().filter(TombstoneBlock::isRemains).forEach(stack -> {
                        stack.removeCustomName();
                        stack.setCustomName(Text.translatable("block.mca.tombstone.remains", stack.getName(), name));
                    });

                });
        data.ifPresent(be -> {
            stacks.stream().filter(s -> s.getItem() == asItem()).findFirst().ifPresent(be::writeToStack);
        });

        return stacks;
    }

    static boolean isRemains(ItemStack stack) {
        return stack.getItem() == Items.BONE || stack.getItem() == Items.SKELETON_SKULL;
    }

    public static class Data extends BlockEntity {
        private Optional<EntityData> entityData = Optional.empty();

        @Nullable
        private FlowingText computedName;

        private int resurrectionProgress;
        private boolean cure;

        public Data(BlockPos pos, BlockState state) {
            super(BlockEntityTypesMCA.TOMBSTONE.get(), pos, state);
        }

        public boolean isResurrecting() {
            return resurrectionProgress > 0;
        }

        public void startResurrecting(boolean cure) {
            resurrectionProgress = 1;
            this.cure = cure;
            generateLightning();
            markDirty();
            sync();
        }

        public void tick() {
            if (hasEntity() && resurrectionProgress > 0) {
                resurrectionProgress++;
                markDirty();
                sync();

                if (resurrectionProgress % 30 == 0) {
                    world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), cure ? SoundEvents.BLOCK_BELL_USE : SoundEvents.ENTITY_POLAR_BEAR_AMBIENT, SoundCategory.BLOCKS, 1, 1);
                    world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(getCachedState()));
                }

                if (world.random.nextInt(10) > 5 && resurrectionProgress % 20 == 0) {
                    generateLightning();
                }

                if (resurrectionProgress > 500) {
                    resurrectionProgress = 0;

                    createEntity(world, true).ifPresent(entity -> {
                        generateLightning();
                        entity.extinguish();
                        entity.resetPortalCooldown();
                        entity.setPosition(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F);
                        if (entity instanceof LivingEntity l) {
                            l.setHealth(l.getMaxHealth());
                            l.clearStatusEffects();
                            l.fallDistance = 0.0f;
                            l.deathTime = 0;
                        }

                        //enforcing a dimension update
                        if (entity instanceof PassiveEntity mob) {
                            mob.setBreedingAge(mob.getBreedingAge());
                        }

                        boolean alreadySpawned = false;
                        if (cure && (entity instanceof ZombieVillagerEntity zombie)) {
                            // spawnEntity is called here, so don't call it twice
                            entity = zombie.convertTo(EntityType.VILLAGER, true);
                            alreadySpawned = true;
                        }

                        if (entity instanceof CompassionateEntity<?> compassionateEntity) {
                            compassionateEntity.getRelationships().getFamilyEntry().setDeceased(false);
                        }

                        if (entity instanceof Infectable infectable) {
                            infectable.setInfectionProgress(cure ? 0.0f : Math.max(MathHelper.lerp(world.random.nextFloat(), Infectable.FEVER_THRESHOLD, Infectable.BABBLING_THRESHOLD), infectable.getInfectionProgress())
                            );
                        }

                        if (!alreadySpawned) {
                            world.spawnEntity(entity);
                        }
                    });
                }
            }
        }

        private void generateLightning() {
            world.setLightningTicksLeft(10);
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            bolt.setCosmetic(true);
            bolt.updatePosition(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F);
            world.spawnEntity(bolt);
        }

        public void setEntity(@Nullable Entity entity) {
            entityData = Optional.ofNullable(entity).map(e -> new EntityData(
                    writeEntityToNbt(e),
                    e.getName(),
                    EntityRelationship.of(e).map(EntityRelationship::getGender).orElse(Gender.MALE)
            ));
            computedName = null;
            markDirty();

            if (hasWorld()) {
                world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1, 1);
                world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(getCachedState()));
                world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(getCachedState()));
                ((TombstoneBlock)getCachedState().getBlock()).updateNeighbors(getCachedState(), world, pos);

                if (!world.isClient) {
                    GraveyardManager.get((ServerWorld)world).setTombstoneState(pos,
                            hasEntity() ? GraveyardManager.TombstoneState.FILLED : GraveyardManager.TombstoneState.EMPTY
                    );
                    sync();
                }
            }
        }

        public boolean hasEntity() {
            return entityData.isPresent();
        }

        public Gender getGender() {
            return entityData.map(e -> e.gender).orElse(Gender.MALE);
        }

        public Optional<Text> getEntityName() {
            return entityData.map(e -> e.name);
        }

        public FlowingText getOrCreateEntityName(Function<Text, FlowingText> factory) {
            if (computedName == null) {
                computedName = factory.apply(getEntityName().orElse(Text.literal("")));
            }
            return computedName;
        }

        public Optional<Entity> createEntity(World world, boolean remove) {
            try {
                return entityData.flatMap(data -> EntityType.getEntityFromNbt(data.nbt, world));
            } finally {
                if (remove) {
                    setEntity(null);
                }
            }
        }

        private NbtCompound writeEntityToNbt(Entity entity) {
            NbtCompound nbt = new NbtCompound();
            entity.writeNbt(nbt);
            nbt.putString("id", EntityType.getId(entity.getType()).toString());
            return nbt;
        }

        protected void sync() {
            markDirty();
            world.updateListeners(getPos(), getCachedState(), getCachedState(), 3);
        }

        @Override
        public void readNbt(NbtCompound tag) {
            entityData = tag.contains("entityData", NbtElement.COMPOUND_TYPE) ? Optional.of(new EntityData(tag)) : Optional.empty();
            resurrectionProgress = tag.getInt("resurrectionProgress");
            cure = tag.getBoolean("cure");
        }

        @Override
        public void writeNbt(NbtCompound nbt) {
            entityData.ifPresent(data -> data.writeNbt(nbt));
            nbt.putInt("resurrectionProgress", resurrectionProgress);
            nbt.putBoolean("cure", cure);
        }

        @Override
        public NbtCompound toInitialChunkDataNbt() {
            NbtCompound tag = new NbtCompound();
            writeNbt(tag);
            return tag;
        }

        @Override
        public BlockEntityUpdateS2CPacket toUpdatePacket() {
            return BlockEntityUpdateS2CPacket.create(this, BlockEntity::toInitialChunkDataNbt);
        }

        public void readFromStack(ItemStack stack) {
            entityData = Optional.ofNullable(stack).map(s -> s.getSubNbt("entityData")).map(EntityData::new);
        }

        public void writeToStack(ItemStack stack) {
            entityData.ifPresent(data -> {
                data.writeNbt(stack.getOrCreateSubNbt("entityData"));
                getEntityName().ifPresent(name -> {
                    NbtHelper.computeIfAbsent(
                                    stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY),
                                    ItemStack.LORE_KEY, NbtElement.LIST_TYPE, NbtList::new)
                            .add(0, NbtString.of(name.getString()));
                });
            });
        }

        public static Optional<Data> of(@Nullable BlockEntity be) {
            return Optional.ofNullable(be).filter(p -> p instanceof Data).map(Data.class::cast);
        }

        static final class EntityData {
            private final NbtCompound nbt;
            private final Text name;
            private final Gender gender;

            public EntityData(NbtCompound nbt, Text name, Gender gender) {
                this.nbt = nbt;
                this.name = name == null ? Text.literal("") : Text.literal(name.getString());
                this.gender = gender;
            }

            EntityData(NbtCompound nbt) {
                this(
                        nbt.getCompound("entityData"),
                        Text.Serializer.fromJson(nbt.getString("entityName")),
                        Gender.byId(nbt.getInt("entityGender"))
                );
            }

            void writeNbt(NbtCompound nbt) {
                nbt.put("entityData", this.nbt);
                nbt.putString("entityName", Text.Serializer.toJson(name));
                nbt.putInt("entityGender", gender.ordinal());
            }
        }
    }
}
