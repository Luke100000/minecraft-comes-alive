package net.mca.entity.ai;

import net.mca.Config;
import net.mca.TagsMCA;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * I know you, you know me, we're all a big happy family.
 */
public class Relationship<T extends Mob & VillagerLike<T>> implements EntityRelationship {
    public static final Predicate IS_MARRIED = (villager, player) -> villager.getRelationships().isMarriedTo(player);
    public static final Predicate IS_ENGAGED = (villager, player) -> villager.getRelationships().isEngagedWith(player);
    public static final Predicate IS_PROMISED = (villager, player) -> villager.getRelationships().isPromisedTo(player);
    public static final Predicate IS_RELATIVE = (villager, player) -> villager.getRelationships().getFamilyEntry().isRelative(player);
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
    public ServerLevel getWorld() {
        return (ServerLevel) entity.level();
    }

    @Override
    public UUID getUUID() {
        return entity.getUUID();
    }

    @NotNull
    @Override
    public FamilyTreeNode getFamilyEntry() {
        return getFamilyTree().getOrCreate(entity);
    }

    private Optional<BlockPos> placeTombstone(ServerLevel world, BlockPos entityPos) {
        int range = 2;
        for (int y = -range; y <= range; y++) {
            // prefer center
            BlockPos pos = entityPos.offset(0, y, 0);
            if (world.getBlockState(pos).isAir()) {
                world.setBlockAndUpdate(pos, BlocksMCA.CROSS_HEADSTONE.get().defaultBlockState());
                return Optional.ofNullable(pos);
            }

            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    if (x != 0 || z != 0) {
                        pos = entityPos.offset(x, y, z);
                        if (world.getBlockState(pos).isAir()) {
                            world.setBlockAndUpdate(pos, BlocksMCA.CROSS_HEADSTONE.get().defaultBlockState());
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

            ServerLevel world = (ServerLevel) entity.level();

            // look for a gravestone
            Optional<BlockPos> nearest = GraveyardManager.get(world).findNearest(entity.blockPosition(), GraveyardManager.TombstoneState.EMPTY, 10);

            // if no one was found, try to place one
            if ((beRemembered || beLoved) && nearest.isEmpty()) {
                nearest = placeTombstone(world, entity.blockPosition());
            }

            // fill it and yeet the villager into depression
            nearest.ifPresentOrElse(pos -> {
                if (entity.level().getBlockState(pos).is(TagsMCA.Blocks.TOMBSTONES) && entity.level().getBlockEntity(pos) instanceof TombstoneBlock.Data tombstone) {
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
            getFamilyTree().remove(entity.getUUID());
        }
    }

    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite) {
        // The death of a villager negatively modifies the mood of nearby strangers
        if (!entity.isHostile()) {
            WorldUtils
                    .getCloseEntities(entity.level(), entity, 32, VillagerEntityMCA.class)
                    .forEach(villager -> villager.getRelationships().onTragedy(cause, burialSite, RelationshipType.STRANGER, entity));
        }

        onTragedy(cause, burialSite, RelationshipType.SELF, entity);
    }

    @Override
    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity with) {
        if (!cause.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            int moodAffect = 5 * type.getProximityAmplifier();
            entity.level().broadcastEntityEvent(entity, Status.MCA_VILLAGER_TRAGEDY);
            entity.getVillagerBrain().modifyMoodValue(-moodAffect);

            // seen murder
            if (cause.getEntity() instanceof Player player) {
                entity.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-20);
            }
        }

        if (burialSite != null && type != RelationshipType.STRANGER) {
            entity.getVillagerBrain().setGrieving();
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(burialSite, 1, 1));
            entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(burialSite));
            entity.getBrain().setActiveActivityIfPossible(ActivityMCA.GRIEVE.get());
        }

        EntityRelationship.super.onTragedy(cause, burialSite, type, with);
    }

    public GiftSaturation getGiftSaturation() {
        return giftSaturation;
    }

    public void readFromNbt(CompoundTag nbt) {
        giftSaturation.readFromNbt(nbt.getList("giftSaturationQueue", 8));
    }

    public void writeToNbt(CompoundTag nbt) {
        nbt.put("giftSaturationQueue", giftSaturation.toNbt());
    }

    public interface Predicate extends BiPredicate<CompassionateEntity<?>, Entity> {

        boolean test(CompassionateEntity<?> villager, UUID partner);

        @Override
        default boolean test(CompassionateEntity<?> villager, Entity partner) {
            return partner != null && test(villager, partner.getUUID());
        }

        default Predicate or(Predicate b) {
            return (villager, partner) -> test(villager, partner) || b.test(villager, partner);
        }

        @Override
        default Predicate negate() {
            return (villager, partner) -> !test(villager, partner);
        }

        default BiPredicate<VillagerLike<?>, ServerPlayer> asConstraint() {
            return (villager, player) -> villager instanceof CompassionateEntity<?> && (test((CompassionateEntity<?>) villager, player));
        }
    }
}
