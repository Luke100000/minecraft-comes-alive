package net.conczin.mca.block;

import com.mojang.serialization.MapCodec;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.util.VoxelShapeUtil;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TombstoneBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final VoxelShape GRAVELLING_SHAPE = Block.box(1, 0, 1, 15, 1, 15);
    public static final VoxelShape UPRIGHT_SHAPE = Shapes.or(
            Block.box(2, 2, 7, 14, 15, 9),
            Block.box(1, 0, 6, 15, 2, 10)
    );
    public static final MapCodec<TombstoneBlock> CODEC = simpleCodec(TombstoneBlock::new);
    public static final VoxelShape CROSS_SHAPE = Shapes.or(
            Block.box(6, 0, 2, 10, 28, 4),
            Block.box(-1, 18, 2, 17, 21, 4)
    );
    public static final VoxelShape SLANTED_SHAPE = Block.box(0, 0, 2, 16, 7, 14);
    public static final VoxelShape WALL_SHAPE = Block.box(1, 1, 0, 15, 15, 1);

    private final Map<Direction, VoxelShape> shapes;

    private final int lineWidth;
    private final int maxNameHeight;
    private final Vec3 nameplateOffset;
    private final boolean requiresSolid;
    private final float rotation;

    public TombstoneBlock(Properties properties) {
        this(properties, 1, 1, Vec3.ZERO, 0, false, UPRIGHT_SHAPE);
    }

    public TombstoneBlock(Properties properties, int lineWidth, int maxNameHeight, Vec3 nameplateOffset, float rotation, boolean requiresSolid, VoxelShape baseShape) {
        super(properties);

        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));

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

    static boolean isRemains(ItemStack stack) {
        return stack.getItem() == Items.BONE || stack.getItem() == Items.SKELETON_SKULL;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public int getMaxNameHeight() {
        return maxNameHeight;
    }

    public Vec3 getNameplateOffset() {
        return nameplateOffset;
    }

    public float getRotation() {
        return rotation;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    @Deprecated
    public VoxelShape getShape(BlockState state, BlockGetter view, BlockPos pos, CollisionContext ePos) {
        if (this == BlocksMCA.SLANTED_HEADSTONE) {
            shapes.replaceAll((i, v) -> VoxelShapeUtil.rotator(SLANTED_SHAPE).apply(i));
        }

        return shapes.getOrDefault(state.getValue(BlockStateProperties.HORIZONTAL_FACING), Shapes.block());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        updateTombstoneState(world, pos);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Data.of(world.getBlockEntity(pos)).ifPresent(data -> data.readFromStack(stack));
        updateTombstoneState(world, pos);
    }

    private void updateTombstoneState(Level world, BlockPos pos) {
        if (!world.isClientSide()) {
            GraveyardManager.get((ServerLevel) world).setTombstoneState(pos,
                    hasEntity(world, pos) ? GraveyardManager.TombstoneState.FILLED : GraveyardManager.TombstoneState.EMPTY
            );
        }
    }

    @Deprecated
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
        if (!world.isClientSide()) {
            updateNeighbors(state, world, pos);
            GraveyardManager.get((ServerLevel) world).removeTombstoneState(pos);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BlockEntityTypesMCA.TOMBSTONE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide()) {
            return null;
        }
        return (w, pos, s, data) -> ((Data) data).tick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED).add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Deprecated
    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        if (direction == Direction.DOWN && !canSurvive(state, world, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, world, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (requiresSolid) {
            pos = pos.below();
            return world.getBlockState(pos).isFaceSturdy(world, pos, Direction.UP, SupportType.FULL);
        }

        return true;
    }

    @Deprecated
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private void updateNeighbors(BlockState state, Level world, BlockPos pos) {
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)), this);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rot.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getDirectSignal(world, pos, direction);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return direction == state.getValue(BlockStateProperties.HORIZONTAL_FACING) && hasEntity(world, pos) ? 15 : 0;
    }

    protected boolean hasEntity(BlockGetter world, BlockPos pos) {
        return Data.of(world.getBlockEntity(pos)).map(Data::hasEntity).orElse(false);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
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
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> stacks = super.getDrops(state, builder);

        Optional<Data> data = Data.of(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)).filter(Data::hasEntity);

        data.flatMap(Data::getEntityName)
                .ifPresent(name -> {
                    stacks.stream().filter(TombstoneBlock::isRemains).forEach(stack -> {
                        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("block.mca.tombstone.remains", stack.getHoverName(), name));
                    });

                });
        data.ifPresent(be -> {
            stacks.stream().filter(s -> s.getItem() == asItem()).findFirst().ifPresent(be::writeToStack);
        });

        return stacks;
    }

    public static class Data extends BlockEntity {
        private Optional<EntityData> entityData = Optional.empty();

        @Nullable
        private FlowingText computedName;

        private int resurrectionProgress;
        private boolean cure;

        public Data(BlockPos pos, BlockState state) {
            super(BlockEntityTypesMCA.TOMBSTONE, pos, state);
        }

        public static Optional<Data> of(@Nullable BlockEntity be) {
            return Optional.ofNullable(be).filter(p -> p instanceof Data).map(Data.class::cast);
        }

        public boolean isResurrecting() {
            return resurrectionProgress > 0;
        }

        public void startResurrecting(boolean cure) {
            resurrectionProgress = 1;
            this.cure = cure;
            generateLightning();
            setChanged();
            sync();
        }

        public void tick() {
            if (hasEntity() && resurrectionProgress > 0) {
                resurrectionProgress++;
                setChanged();
                sync();

                if (resurrectionProgress % 30 == 0) {
                    level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), cure ? SoundEvents.BELL_BLOCK : SoundEvents.POLAR_BEAR_AMBIENT, SoundSource.BLOCKS, 1, 1);
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, worldPosition, Block.getId(getBlockState()));
                }

                if (level.random.nextInt(10) > 5 && resurrectionProgress % 20 == 0) {
                    generateLightning();
                }

                if (resurrectionProgress > 500) {
                    resurrectionProgress = 0;

                    createEntity(level, true).ifPresent(entity -> {
                        generateLightning();
                        entity.clearFire();
                        entity.setPortalCooldown();
                        entity.setPos(worldPosition.getX() + 0.5F, worldPosition.getY() + 0.5F, worldPosition.getZ() + 0.5F);
                        if (entity instanceof LivingEntity l) {
                            l.setHealth(l.getMaxHealth());
                            l.removeAllEffects();
                            l.fallDistance = 0.0f;
                            l.deathTime = 0;
                        }

                        //enforcing a dimension update
                        if (entity instanceof AgeableMob mob) {
                            mob.setAge(mob.getAge());
                        }

                        boolean alreadySpawned = false;
                        if (cure && (entity instanceof ZombieVillager zombie)) {
                            // spawnEntity is called here, so don't call it twice
                            Villager villager = zombie.convertTo(
                                    EntityType.VILLAGER,
                                    ConversionParams.single(zombie, true, true),
                                    EntitySpawnReason.CONVERSION,
                                    converted -> {
                                    }
                            );
                            if (villager != null) {
                                entity = villager;
                                alreadySpawned = true;
                            }
                        }

                        if (entity instanceof CompassionateEntity<?> compassionateEntity) {
                            compassionateEntity.getRelationships().getFamilyEntry().setDeceased(false);
                        }

                        if (entity instanceof Infectable infectable) {
                            infectable.setInfectionProgress(cure ? 0.0f : Math.max(Mth.lerp(level.random.nextFloat(), Infectable.FEVER_THRESHOLD, Infectable.BABBLING_THRESHOLD), infectable.getInfectionProgress())
                            );
                        }

                        if (!alreadySpawned) {
                            level.addFreshEntity(entity);
                        }
                    });
                }
            }
        }

        private void generateLightning() {
            level.setSkyFlashTime(10);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            bolt.setVisualOnly(true);
            bolt.snapTo(worldPosition.getX() + 0.5F, worldPosition.getY(), worldPosition.getZ() + 0.5F);
            level.addFreshEntity(bolt);
        }

        public void setEntity(@Nullable Entity entity) {
            entityData = Optional.ofNullable(entity).map(e -> new EntityData(
                    writeEntityToNbt(e),
                    e.getName().getString(),
                    EntityRelationship.of(e).map(EntityRelationship::getGender).orElse(Gender.MALE)
            ));
            computedName = null;
            setChanged();

            if (hasLevel()) {
                level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1, 1);
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, worldPosition, Block.getId(getBlockState()));
                level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(getBlockState()));
                ((TombstoneBlock) getBlockState().getBlock()).updateNeighbors(getBlockState(), level, worldPosition);

                if (!level.isClientSide()) {
                    GraveyardManager.get((ServerLevel) level).setTombstoneState(worldPosition,
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

        public Optional<String> getEntityName() {
            return entityData.map(e -> e.name);
        }

        public FlowingText getOrCreateEntityName(Function<Component, FlowingText> factory) {
            if (computedName == null) {
                computedName = factory.apply(Component.literal(getEntityName().orElse("")));
            }
            return computedName;
        }

        public Optional<Entity> createEntity(Level world, boolean remove) {
            try {
                return entityData.flatMap(data -> EntityType.create(
                        TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), data.nbt),
                        world,
                        EntitySpawnReason.TRIGGERED
                ));
            } finally {
                if (remove) {
                    setEntity(null);
                }
            }
        }

        private CompoundTag writeEntityToNbt(Entity entity) {
            TagValueOutput nbt = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
            entity.saveWithoutId(nbt);
            CompoundTag tag = nbt.buildResult();
            tag.putString("id", EntityType.getKey(entity.getType()).toString());
            return tag;
        }

        protected void sync() {
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        @Override
        protected void loadAdditional(ValueInput tag) {
            entityData = tag.read("EntityData", CompoundTag.CODEC)
                    .map(entityTag -> new EntityData(
                            entityTag,
                            tag.getStringOr("EntityName", ""),
                            Gender.byId(tag.getIntOr("EntityGender", Gender.MALE.ordinal()))
                    ));
            resurrectionProgress = tag.getIntOr("ResurrectionProgress", 0);
            cure = tag.getBooleanOr("Cure", false);
        }

        @Override
        protected void saveAdditional(ValueOutput nbt) {
            entityData.ifPresent(data -> data.writeNbt(nbt));
            nbt.putInt("ResurrectionProgress", resurrectionProgress);
            nbt.putBoolean("Cure", cure);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            TagValueOutput tag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            saveAdditional(tag);
            return tag.buildResult();
        }

        @Override
        public ClientboundBlockEntityDataPacket getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
        }

        public void readFromStack(ItemStack stack) {
            entityData = Optional.ofNullable(stack)
                    .filter(s -> s.has(DataComponents.CUSTOM_DATA))
                    .map(s -> s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())
                    .map(EntityData::new);
        }

        public void writeToStack(ItemStack stack) {
            entityData.ifPresent(data -> {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.writeNbt(tag);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            });
        }

        static final class EntityData {
            private final CompoundTag nbt;
            private final String name;
            private final Gender gender;

            public EntityData(CompoundTag nbt, String name, Gender gender) {
                this.nbt = nbt;
                this.name = name;
                this.gender = gender;
            }

            EntityData(CompoundTag nbt) {
                this(
                        nbt.getCompound("EntityData").orElseGet(CompoundTag::new),
                        nbt.getString("EntityName").orElse(""),
                        Gender.byId(nbt.getInt("EntityGender").orElse(Gender.MALE.ordinal()))
                );
            }

            void writeNbt(CompoundTag nbt) {
                nbt.put("EntityData", this.nbt);
                nbt.putString("EntityName", name);
                nbt.putInt("EntityGender", gender.ordinal());
            }

            void writeNbt(ValueOutput nbt) {
                nbt.store("EntityData", CompoundTag.CODEC, this.nbt);
                nbt.putString("EntityName", name);
                nbt.putInt("EntityGender", gender.ordinal());
            }
        }
    }
}
