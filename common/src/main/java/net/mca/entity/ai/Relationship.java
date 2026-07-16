package net.mca.entity.ai;

import net.mca.Config;
import net.mca.MCA;
import net.mca.TagsMCA;
import dev.architectury.platform.Platform;
import net.mca.block.BlocksMCA;
import net.mca.block.TombstoneBlock;
import net.mca.entity.Status;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.Gender;
import net.mca.entity.ai.relationship.RelationshipType;
import net.mca.entity.interaction.gifts.GiftSaturation;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.GraveyardManager;
import net.mca.util.WorldUtils;
import net.mca.util.network.datasync.CDataManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * I know you, you know me, we're all a big happy family.
 */
public class Relationship<T extends MobEntity & VillagerLike<T>> implements EntityRelationship {
    public static final Predicate IS_MARRIED = (villager, player) -> villager.getRelationships().isMarriedTo(player);
    public static final Predicate IS_ENGAGED = (villager, player) -> villager.getRelationships().isEngagedWith(player);
    public static final Predicate IS_PROMISED = (villager, player) -> villager.getRelationships().isPromisedTo(player);
    public static final Predicate IS_RELATIVE = (villager, player) -> villager.getRelationships().getFamilyEntry().isRelative(player);
    public static final Predicate IS_ROMANTIC_PARTNER = IS_MARRIED.or(IS_ENGAGED).or(IS_PROMISED);
    public static final Predicate IS_TRUE_RELATIVE = (villager, player) -> IS_RELATIVE.test(villager, player) && !IS_ROMANTIC_PARTNER.test(villager, player);
    public static final Predicate IS_FAMILY = IS_MARRIED.or(IS_RELATIVE);
    public static final Predicate IS_PARENT = (villager, player) -> villager.getRelationships().getFamilyEntry().isParent(player);
    public static final Predicate IS_KID = (villager, player) -> FamilyTree.get(villager.getRelationships().getWorld()).getOrEmpty(player).filter(n -> n.isParent(villager.getRelationships().getUUID())).isPresent();
    public static final Predicate IS_ORPHAN = (villager, player) -> villager.getRelationships().getFamilyEntry().getParents().allMatch(FamilyTreeNode::isDeceased);

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll();
    }

    protected final T entity;

    private final GiftSaturation giftSaturation = new GiftSaturation();

    public Relationship(T entity) {
        this.entity = entity;
    }

    @Override
    public Gender getGender() {
        return entity.getGenetics().getGender();
    }

    @Override
    public ServerWorld getWorld() {
        return (ServerWorld) entity.getWorld();
    }

    @Override
    public UUID getUUID() {
        return entity.getUuid();
    }

    @NotNull
    @Override
    public FamilyTreeNode getFamilyEntry() {
        return getFamilyTree().getOrCreate(entity);
    }

    private BlockState getConfiguredTombstoneState() {
        String configuredName = Config.getInstance().defaultHeadstoneType;
        if (configuredName != null && !configuredName.contains(":")) {
            configuredName = "mca:" + configuredName;
        }

        Block block = null;
        if (configuredName != null) {
            try {
                block = Registries.BLOCK.getOrEmpty(new Identifier(configuredName)).orElse(null);
            } catch (RuntimeException ignored) {
                // Invalid configuration falls back to MCA's default cross headstone.
            }
        }

        if (block instanceof TombstoneBlock) {
            return block.getDefaultState();
        }
        return BlocksMCA.CROSS_HEADSTONE.get().getDefaultState();
    }

    private Optional<BlockPos> placeTombstone(ServerWorld world, BlockPos entityPos) {
        BlockState state = getConfiguredTombstoneState();
        int range = 2;
        for (int y = -range; y <= range; y++) {
            // prefer center
            BlockPos pos = entityPos.add(0, y, 0);
            if (world.getBlockState(pos).isAir() && state.canPlaceAt(world, pos)) {
                world.setBlockState(pos, state);
                return Optional.ofNullable(pos);
            }

            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    if (x != 0 || z != 0) {
                        pos = entityPos.add(x, y, z);
                        if (world.getBlockState(pos).isAir() && state.canPlaceAt(world, pos)) {
                            world.setBlockState(pos, state);
                            return Optional.ofNullable(pos);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public void onDeath(DamageSource cause) {
        boolean beRemembered = getFamilyEntry().willBeRemembered();
        boolean beLoved = entity.getVillagerBrain().getMemories().values().stream().anyMatch(m -> m.getHearts() > Config.getInstance().heartsRequiredToAutoSpawnGravestone);

        if (beRemembered || beLoved || !entity.isHostile()) {
            getFamilyEntry().setDeceased(true);

            ServerWorld world = (ServerWorld) entity.getWorld();

            // look for a gravestone
            Optional<BlockPos> nearest = GraveyardManager.get(world).findNearest(entity.getBlockPos(), GraveyardManager.TombstoneState.EMPTY, 10);

            // if no one was found, try to place one
            if ((beRemembered || beLoved) && nearest.isEmpty()) {
                nearest = placeTombstone(world, entity.getBlockPos());
            }

            // fill it and yeet the villager into depression
            nearest.ifPresentOrElse(pos -> {
                if (entity.getWorld().getBlockState(pos).isIn(TagsMCA.Blocks.TOMBSTONES) && entity.getWorld().getBlockEntity(pos) instanceof TombstoneBlock.Data tombstone) {
                    onTragedy(cause, pos);
                    tombstone.setEntity(entity);
                } else {
                    onTragedy(cause, null);
                }
            }, () -> {
                onTragedy(cause, null);
            });
        } else {
            onTragedy(cause, null);
        }

        // the family is too small to be remembered
        if (!beRemembered) {
            getFamilyEntry().streamParents().forEach(uuid -> {
                getFamilyTree().remove(uuid);
            });
            getFamilyTree().remove(entity.getUuid());
        }
    }

    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite) {
        // The death of a villager negatively modifies the mood of nearby strangers
        if (!entity.isHostile()) {
            WorldUtils
                    .getCloseEntities(entity.getWorld(), entity, 32, VillagerEntityMCA.class)
                    .forEach(villager -> villager.getRelationships().onTragedy(cause, burialSite, RelationshipType.STRANGER, entity));
        }

        onTragedy(cause, burialSite, RelationshipType.SELF, entity);
    }

    @Override
    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity with) {
        if (!cause.isOf(DamageTypes.OUT_OF_WORLD)) {
            int moodAffect = 5 * type.getProximityAmplifier();
            entity.getWorld().sendEntityStatus(entity, Status.MCA_VILLAGER_TRAGEDY);
            entity.getVillagerBrain().modifyMoodValue(-moodAffect);

            // seen murder
            if (cause.getAttacker() instanceof PlayerEntity player) {
                entity.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-20);
            }
        }

        if (burialSite != null && type != RelationshipType.STRANGER) {
            entity.getVillagerBrain().setGrieving();
            entity.getBrain().remember(MemoryModuleTypeMCA.MOURNING_SITE.get(), burialSite);
            entity.getBrain().forget(MemoryModuleTypeMCA.MOURNING_POSITION.get());
            if (Platform.isDevelopmentEnvironment()) {
                MCA.LOGGER.info("[MOURNING_TRACE_V3] assignment villager={} type={} grave={} position={}", entity.getName().getString(), type, burialSite, entity.getBlockPos());
            }
            entity.getBrain().forget(MemoryModuleType.PATH);
            entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
            entity.getBrain().remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(burialSite));
            entity.getBrain().doExclusively(ActivityMCA.GRIEVE.get());
        }

        EntityRelationship.super.onTragedy(cause, burialSite, type, with);
    }

    public GiftSaturation getGiftSaturation() {
        return giftSaturation;
    }

    public void readFromNbt(NbtCompound nbt) {
        giftSaturation.readFromNbt(nbt.getList("giftSaturationQueue", 8));
    }

    public void writeToNbt(NbtCompound nbt) {
        nbt.put("giftSaturationQueue", giftSaturation.toNbt());
    }

    public interface Predicate extends BiPredicate<CompassionateEntity<?>, Entity> {

        boolean test(CompassionateEntity<?> villager, UUID partner);

        @Override
        default boolean test(CompassionateEntity<?> villager, Entity partner) {
            return partner != null && test(villager, partner.getUuid());
        }

        default Predicate or(Predicate b) {
            return (villager, partner) -> test(villager, partner) || b.test(villager, partner);
        }

        @Override
        default Predicate negate() {
            return (villager, partner) -> !test(villager, partner);
        }

        default BiPredicate<VillagerLike<?>, ServerPlayerEntity> asConstraint() {
            return (villager, player) -> villager instanceof CompassionateEntity<?> && (test((CompassionateEntity<?>) villager, player));
        }
    }
}
