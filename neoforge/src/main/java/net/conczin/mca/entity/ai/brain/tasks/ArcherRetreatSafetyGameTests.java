package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class ArcherRetreatSafetyGameTests {
    private static final String BATCH = "archer_retreat_safety";
    private static final int RADIUS = 24;
    private static final List<Arena> ARENAS = new ArrayList<>();

    private ArcherRetreatSafetyGameTests() {
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        List.copyOf(ARENAS).forEach(Arena::close);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void rangedEvasionKeepsAvailableFiringRange(GameTestHelper helper) {
        Arena arena = fixture(helper, 0, true, false, 7);
        arena.tick(() -> {
            var walk = arena.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (RangedCombatState.current(arena.archer).orElse(null) == RangedCombatState.KITE
                    && walk != null && walk.getTarget() instanceof CombatEscapePositionTracker) {
                Vec3 destination = walk.getTarget().currentPosition();
                helper.assertTrue(destination.distanceToSqr(arena.skeleton.position()) <= 225.0D,
                        "ranged evasion selected an out-of-range destination despite open firing ground: "
                                + destination + arena.describe());
                if (arena.sawEvasionPath && arena.archer.position().distanceToSqr(arena.startPosition) > 0.25D) {
                    arena.succeed();
                }
            }
            if (helper.getTick() == 100) {
                helper.fail("never observed native ranged evasion movement" + arena.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void elevatedActiveSkeletonProducesEvasionMovement(GameTestHelper helper) {
        Arena arena = fixture(helper, 3, true, false, 7);
        arena.tick(() -> {
            if (arena.sawEvasionPath && arena.archer.position().distanceToSqr(arena.startPosition) > 0.25D) {
                arena.succeed();
            } else if (helper.getTick() == 70) {
                helper.fail("elevated active ranged attacker produced no native evasion movement" + arena.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void elevatedPassiveSkeletonDoesNotForceEvasion(GameTestHelper helper) {
        Arena arena = fixture(helper, 3, false, false);
        arena.tick(() -> {
            helper.assertTrue(arena.skeleton.getTarget() == null, "passive control acquired an attacker");
            RangedCombatState state = RangedCombatState.current(arena.archer).orElse(null);
            helper.assertTrue(state != RangedCombatState.KITE && state != RangedCombatState.EMERGENCY_FLEE,
                    "passive elevated skeleton forced evasion" + arena.describe());
            // End before the guard's first shot can make this passive actor retaliate.
            if (helper.getTick() == 16) arena.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void recentRangedPressurePreventsPrematureCalm(GameTestHelper helper) {
        Arena arena = criticalFixture(helper);
        arena.tick(() -> {
            if (helper.getTick() == 85) {
                arena.assertCriticalPressure();
                helper.assertTrue(arena.sawPanic && arena.sawNativePath,
                        "critical guard never entered panic with native navigation" + arena.describe());
                helper.assertTrue(arena.archer.getBrain().isActive(Activity.PANIC),
                        "critical guard calmed while a recent ranged attacker still targeted it beyond melee range"
                                + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 190)
    public static void criticalPanicRetainsCombatEquipmentPastRefreshCooldown(GameTestHelper helper) {
        Arena arena = criticalFixture(helper);
        arena.tick(() -> {
            if (helper.getTick() == 100) arena.arrowHit();
            if (helper.getTick() == 150) {
                // Native pursuit can close slightly inside six blocks. Gear must
                // remain equipped there too; the miss-only gap cases stay beyond six.
                arena.assertCriticalPressure(false);
                helper.assertTrue(arena.sawPanic && arena.sawNativePath && arena.sawTargetCleared,
                        "fixture did not exercise native critical-health combat handoff" + arena.describe());
                helper.assertTrue(RangedWeaponHelper.isHoldingSupportedWeapon(arena.archer)
                                && arena.archer.getBrain().hasMemoryValue(MemoryModuleTypeMCA.WEARS_ARMOR),
                        "REST equipment refresh removed combat gear during recent ranged pressure" + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void criticalGuardCalmsAfterRangedAttackerLeaves(GameTestHelper helper) {
        Arena arena = criticalFixture(helper);
        arena.tick(() -> {
            if (helper.getTick() == 60) {
                helper.assertTrue(arena.sawPanic, "fixture never established critical-health panic" + arena.describe());
                arena.discardProjectiles();
                arena.skeleton.discard();
            }
            if (helper.getTick() == 170) {
                helper.assertTrue(arena.archer.isAlive()
                                && arena.archer.getHealth() < arena.archer.getMaxHealth() * 0.25F,
                        "safe control stopped being a living critical-health guard");
                helper.assertTrue(!arena.archer.getBrain().isActive(Activity.PANIC),
                        "low health alone kept a safe guard panicking" + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 220)
    public static void rangedDangerOutlastsNativeHurtExpiryWhenShotsMiss(GameTestHelper helper) {
        Arena arena = missedShotsFixture(helper);
        observeLongHitGap(helper, arena);
    }

    private static void observeLongHitGap(GameTestHelper helper, Arena arena) {
        arena.tick(() -> {
            if (arena.injurySource != null && arena.injuryAge() >= 125) {
                arena.assertInjuryGap();
                arena.assertCriticalPressure();
                helper.assertTrue(arena.archer.getBrain().isActive(Activity.PANIC),
                        "critical guard forgot its still-active ranged attacker after the native hurt reference expired"
                                + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void combatGearSurvivesLongHitGapWhileRangedAttackerRemainsActive(GameTestHelper helper) {
        Arena arena = missedShotsFixture(helper);
        arena.tick(() -> {
            if (arena.injurySource != null && arena.injuryAge() >= 160) {
                arena.assertInjuryGap();
                arena.assertCriticalPressure();
                helper.assertTrue(arena.sawTargetCleared,
                        "fixture did not exercise critical-health target removal" + arena.describe());
                helper.assertTrue(RangedWeaponHelper.isHoldingSupportedWeapon(arena.archer)
                                && arena.archer.getBrain().hasMemoryValue(MemoryModuleTypeMCA.WEARS_ARMOR),
                        "REST cleared combat gear after a long hit gap despite the visible remembered attacker"
                                + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 280)
    public static void rememberedRangedDangerEndsWhenAttackerDisengagesAfterHitGap(GameTestHelper helper) {
        Arena arena = missedShotsFixture(helper);
        boolean[] disengaged = {false};
        arena.tick(() -> {
            if (arena.injurySource == null) return;
            if (!disengaged[0] && arena.injuryAge() >= 125) {
                arena.assertInjuryGap();
                arena.assertCriticalPressure();
                arena.requireContinuousPressure = false;
                arena.skeleton.setTarget(null);
                disengaged[0] = true;
            }
            if (arena.injuryAge() >= 180) {
                helper.assertTrue(arena.skeleton.getTarget() == null, "disengaged control resumed attacking");
                helper.assertTrue(!arena.archer.getBrain().isActive(Activity.PANIC),
                        "stale injury memory kept a safe guard panicking after its attacker disengaged" + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 110)
    public static void activeRangedDriverOnFiveBlockShelfDoesNotNeedMeleeRoute(GameTestHelper helper) {
        elevatedRangedDriver(helper, 5, 5);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 110)
    public static void activeRangedDriverOnEightBlockShelfKeepsClearShot(GameTestHelper helper) {
        Arena arena = fixture(helper, 8, true, false, 10);
        arena.tick(() -> {
            helper.assertTrue(RangedCombatState.current(arena.archer).orElse(null) != RangedCombatState.KITE,
                    "distant elevated attacker forced unnecessary retreat" + arena.describe());
            if (helper.getTick() == 60) {
                helper.assertTrue(arena.skeleton.getHealth() < arena.skeleton.getMaxHealth(),
                        "guard could not shoot the exposed elevated attacker" + arena.describe());
                arena.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void strafeProbeRejectsWallAndUnsupportedEdgeWhileOpenSideStaysUsable(GameTestHelper helper) {
        Arena arena = fixture(helper, 0, false, false);
        arena.checked(() -> {
            // Probe from an off-center position just before crossing a cell boundary,
            // as in the single one-frame live cancellation. This tests geometry only.
            arena.archer.absMoveTo(arena.center.getX() + 0.75D, arena.center.getY(), arena.center.getZ() + 0.5D);
            arena.archer.setYRot(0.0F);
            helper.assertTrue(RangedCombatPositioning.findSafeStrafeLanding(arena.archer, 1.0F, List.of()) != null,
                    "open-ground control rejected the next lateral step");
            helper.getLevel().setBlock(arena.center.east(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(RangedCombatPositioning.findSafeStrafeLanding(arena.archer, 1.0F, List.of()) == null,
                    "strafe probe accepted an AABB-blocked step");
            helper.getLevel().setBlock(arena.center.east(), Blocks.AIR.defaultBlockState(), 3);
            // Put the whole probe footprint beyond the old floor, with a real
            // two-block landing below it. A one-block drop is now intentional.
            arena.archer.absMoveTo(arena.center.getX() + 0.85D, arena.center.getY(), arena.center.getZ() + 0.5D);
            for (int depth = 1; depth <= 3; depth++) {
                BlockPos block = arena.center.east().below(depth);
                arena.originalBlocks.putIfAbsent(block, helper.getLevel().getBlockState(block));
                helper.getLevel().setBlock(block, (depth == 3 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            helper.assertTrue(RangedCombatPositioning.findSafeStrafeLanding(arena.archer, 1.0F, List.of()) == null,
                    "strafe probe accepted an unsupported next cell");
            helper.assertTrue(RangedCombatPositioning.findSafeStrafeLanding(arena.archer, -1.0F, List.of()) != null,
                    "a blocked side incorrectly made the supported opposite side unusable");
            arena.succeed();
        });
    }

    private static Arena missedShotsFixture(GameTestHelper helper) {
        Arena arena = criticalFixture(helper);
        // The skeleton keeps its native targeting, bow AI and locomotion. Remove
        // owned projectiles every tick so the single initial injury cannot refresh.
        arena.suppressFurtherHits = true;
        // These cases test danger memory and equipment, with native locomotion
        // retained at a low positive speed so the threat stays in range throughout.
        arena.requireContinuousPressure = true;
        arena.archer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.005D);
        arena.skeletonSpeed = 0.005D;
        arena.skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(arena.skeletonSpeed);
        // Observe the same positions/LOS that calm-down sees, before any ordinary
        // behavior starts. Declining startup makes this probe an observer only.
        arena.archer.getBrain().addActivity(Activity.CORE, ImmutableList.of(Pair.of(-1,
                new Behavior<Villager>(Map.of()) {
                    @Override
                    protected boolean checkExtraStartConditions(ServerLevel level, Villager entity) {
                        arena.observeContinuousPressure();
                        return false;
                    }
                })));
        return arena;
    }

    private static void elevatedRangedDriver(GameTestHelper helper, int height, int horizontalDistance) {
        Arena arena = fixture(helper, height, true, false, horizontalDistance);
        arena.checked(() -> {
            helper.assertTrue(arena.skeleton.getNavigation().getPath() == null,
                    "driver fixture must start without a melee pursuit route");
            helper.assertTrue(RangedCombatPositioning.collectThreatContext(arena.archer, arena.skeleton)
                            .escapeThreats().contains(arena.skeleton),
                    "active ranged attacker was excluded solely for its " + height + "-block shelf" + arena.describe());
        });
        arena.tick(() -> {
            if (arena.sawEvasionPath && arena.archer.position().distanceToSqr(arena.startPosition) > 0.25D) {
                arena.succeed();
            } else if (helper.getTick() == 80) {
                helper.fail("elevated ranged driver never produced native evasion" + arena.describe());
            }
        });
    }

    private static Arena criticalFixture(GameTestHelper helper) {
        Arena arena = fixture(helper, 0, false, true);
        helper.runAfterDelay(20, () -> arena.checked(() -> {
            helper.assertTrue(arena.archer.getBrain().hasMemoryValue(MemoryModuleTypeMCA.WEARS_ARMOR)
                            && RangedWeaponHelper.isHoldingSupportedWeapon(arena.archer),
                    "healthy REST guard did not equip before the injury" + arena.describe());
            arena.archer.setHealth(200.0F);
            arena.skeleton.setNoAi(false);
            arena.skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(arena.skeletonSpeed);
            arena.skeleton.setTarget(arena.archer);
            helper.assertTrue(arena.arrowHit(), "initial controlled arrow impact was not accepted");
        }));
        return arena;
    }

    private static Arena fixture(GameTestHelper helper, int elevation, boolean active, boolean rest) {
        return fixture(helper, elevation, active, rest, 13);
    }

    private static Arena fixture(GameTestHelper helper, int elevation, boolean active, boolean rest, int horizontalDistance) {
        Arena arena = new Arena(helper);
        try {
            arena.prepare();
            BlockPos start = arena.center;
            // The target's shelf is disconnected from the guard's floor. Only a ranged
            // attacker should bypass the melee topology gate in this fixture.
            BlockPos target = start.east(horizontalDistance).above(elevation);
            for (int y = 0; y < elevation; y++) {
                helper.getLevel().setBlock(target.below(y + 1), Blocks.STONE.defaultBlockState(), 3);
            }
            arena.archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                    .withProfession(ProfessionsMCA.ARCHER).withPosition(Vec3.atBottomCenterOf(start))
                    .withName("Retreat Safety Archer").spawn(MobSpawnType.STRUCTURE);
            arena.actors.add(arena.archer);
            arena.archer.getTraits().removeTrait(Traits.DWARFISM);
            arena.archer.getGenetics().setGene(Genetics.SIZE, 0.5F);
            arena.archer.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            arena.archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
            arena.archer.setHealth(1000.0F);
            arena.archer.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            arena.archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            arena.archer.getVillagerBrain().setArmorWear(false);
            arena.archer.refreshBrain(helper.getLevel());
            arena.archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule())
                    .changeActivityAt(0, rest ? Activity.REST : Activity.IDLE).build());
            if (rest) {
                arena.archer.getBrain().setMemory(MemoryModuleType.HOME,
                        GlobalPos.of(helper.getLevel().dimension(), start));
            }
            arena.archer.setOnGround(true);
            arena.archer.setYRot(270.0F);
            arena.startPosition = arena.archer.position();

            arena.skeleton = EntityType.SKELETON.create(helper.getLevel());
            helper.assertTrue(arena.skeleton != null, "could not create ranged attacker");
            arena.actors.add(arena.skeleton);
            arena.skeleton.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            arena.skeleton.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
            arena.skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
            arena.skeleton.setHealth(1000.0F);
            arena.skeleton.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            arena.skeletonSpeed = arena.skeleton.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            // Hold the firing anchor still for destination tests; its native bow AI
            // remains enabled when active. Panic tests restore its ordinary movement.
            arena.skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            arena.skeleton.setNoAi(!active);
            arena.skeleton.moveTo(Vec3.atBottomCenterOf(target));
            arena.skeleton.setOnGround(true);
            helper.getLevel().addFreshEntity(arena.skeleton);
            if (active) arena.skeleton.setTarget(arena.archer);
            arena.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, arena.skeleton);
            helper.assertTrue(arena.archer.hasLineOfSight(arena.skeleton), "fixture has no initial firing lane");
            helper.assertTrue(!arena.archer.isNoAi(), "guard Brain must tick naturally");
            return arena;
        } catch (RuntimeException | Error failure) {
            arena.close();
            throw failure;
        }
    }

    private static final class Arena {
        private final GameTestHelper helper;
        private final BlockPos center;
        private final AABB footprint;
        private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        private final List<ChunkPos> addedChunks = new ArrayList<>();
        private final List<Entity> actors = new ArrayList<>();
        private VillagerEntityMCA archer;
        private Skeleton skeleton;
        private Vec3 startPosition;
        private double skeletonSpeed;
        private boolean sawNativePath;
        private boolean sawEvasionPath;
        private boolean sawPanic;
        private boolean sawTargetCleared;
        private boolean sawInjuryMemory;
        private boolean suppressFurtherHits;
        private boolean requireContinuousPressure;
        private String pressureInterruption;
        private DamageSource injurySource;
        private int injuryTick;
        private boolean closed;

        private Arena(GameTestHelper helper) {
            this.helper = helper;
            center = helper.absolutePos(new BlockPos(56, 3, 56));
            footprint = AABB.encapsulatingFullBlocks(center.offset(-RADIUS, -1, -RADIUS), center.offset(RADIUS, 8, RADIUS));
            AABB bounds = helper.getBounds();
            // The template reserves 112 x 112 columns but is only four blocks tall.
            // Snapshot and restore our taller shelf/wall volume in those same columns.
            helper.assertTrue(footprint.minX >= bounds.minX && footprint.maxX <= bounds.maxX
                            && footprint.minZ >= bounds.minZ && footprint.maxZ <= bounds.maxZ,
                    "retreat fixture must fit inside its allocated arena columns");
            for (Arena other : ARENAS) {
                helper.assertTrue(!footprint.inflate(48.0D).intersects(other.footprint),
                        "retreat fixtures must not share terrain or guard sensor ranges");
            }
            ARENAS.add(this);
        }

        private void prepare() {
            ChunkPos min = new ChunkPos(center.offset(-RADIUS, 0, -RADIUS));
            ChunkPos max = new ChunkPos(center.offset(RADIUS, 0, RADIUS));
            for (int x = min.x; x <= max.x; x++) {
                for (int z = min.z; z <= max.z; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) addedChunks.add(chunk);
                }
            }
            for (BlockPos position : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS),
                    center.offset(RADIUS, 8, RADIUS))) {
                originalBlocks.put(position.immutable(), helper.getLevel().getBlockState(position));
            }
            GameTestTerrain.prepareFlatArea(helper, center, RADIUS, 9);
            for (int offset = -RADIUS; offset <= RADIUS; offset++) {
                for (int y = 0; y < 4; y++) {
                    helper.getLevel().setBlock(center.offset(offset, y, -RADIUS), Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(center.offset(offset, y, RADIUS), Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(center.offset(-RADIUS, y, offset), Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(center.offset(RADIUS, y, offset), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        private void tick(Runnable observation) {
            helper.onEachTick(() -> checked(() -> {
                if (suppressFurtherHits) {
                    discardProjectiles();
                    DamageSource source = archer.getLastDamageSource();
                    helper.assertTrue(source == null || source == injurySource,
                            "miss-only fixture accepted another hit; long-hit-gap proof is invalid" + describe());
                }
                boolean nativePath = archer.getNavigation().getPath() != null && !archer.getNavigation().isDone()
                        && archer.getBrain().getMemory(MemoryModuleType.PATH).orElse(null) == archer.getNavigation().getPath();
                sawNativePath |= nativePath;
                var walk = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                sawEvasionPath |= nativePath && walk != null && walk.getTarget() instanceof CombatEscapePositionTracker
                        && RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE;
                sawPanic |= archer.getBrain().isActive(Activity.PANIC);
                sawTargetCleared |= archer.getHealth() < archer.getMaxHealth() * 0.25F
                        && !archer.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
                sawInjuryMemory |= archer.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null) == skeleton;
                if (requireContinuousPressure && injurySource != null) assertContinuousPressure();
                observation.run();
            }));
        }

        private void checked(Runnable action) {
            if (closed) return;
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private boolean arrowHit() {
            Arrow arrow = new Arrow(helper.getLevel(), skeleton, Items.ARROW.getDefaultInstance(),
                    skeleton.getMainHandItem());
            try {
                // Supply an actual arrow damage source; sensors, panic, target loss,
                // equipment and movement then run through the unmodified Brain.
                DamageSource source = helper.getLevel().damageSources().arrow(arrow, skeleton);
                boolean accepted = archer.hurt(source, 1.0F);
                if (accepted) {
                    injurySource = source;
                    injuryTick = archer.tickCount;
                }
                return accepted;
            } finally {
                arrow.discard();
            }
        }

        private void assertCriticalPressure() {
            assertCriticalPressure(true);
        }

        private void assertCriticalPressure(boolean beyondMeleeDistance) {
            helper.assertTrue(archer.isAlive() && archer.getHealth() < archer.getMaxHealth() * 0.25F,
                    "fixture did not retain a living critical-health guard" + describe());
            helper.assertTrue(skeleton.isAlive() && skeleton.getTarget() == archer
                            && (!beyondMeleeDistance || archer.distanceToSqr(skeleton) > 36.0D)
                            && archer.distanceToSqr(skeleton) <= 256.0D
                            && skeleton.hasLineOfSight(archer),
                    "fixture no longer has active ranged pressure"
                            + (beyondMeleeDistance ? " beyond melee distance" : "") + describe());
        }

        private int injuryAge() {
            return archer.tickCount - injuryTick;
        }

        private void assertInjuryGap() {
            helper.assertTrue(injuryAge() > 100 && archer.getLastHurtByMob() == null
                            && archer.getLastDamageSource() == null,
                    "fixture has not outlasted the native hurt references" + describe());
            helper.assertTrue(sawInjuryMemory && sawPanic && sawNativePath,
                    "fixture did not establish actual injury memory and native panic movement" + describe());
        }

        private void assertContinuousPressure() {
            assertCriticalPressure();
            helper.assertTrue(archer.getSensing().hasLineOfSight(skeleton),
                    "continuity fixture lost the guard's view of its attacker" + describe());
            helper.assertTrue(pressureInterruption == null,
                    "continuity fixture temporarily lost pressure before its endpoint" + describe());
        }

        private void observeContinuousPressure() {
            if (!requireContinuousPressure || closed || injurySource == null || pressureInterruption != null) return;
            double distanceSquared = archer.distanceToSqr(skeleton);
            boolean targeting = skeleton.getTarget() == archer;
            boolean visible = archer.getSensing().hasLineOfSight(skeleton);
            if (!RangedWeaponHelper.isValidAttackTarget(archer, skeleton) || skeleton.isRemoved()
                    || !targeting || distanceSquared <= 36.0D || distanceSquared > 256.0D || !visible) {
                // Record from entity ticking; assert later in the GameTest callback.
                pressureInterruption = "tick=" + archer.tickCount + ", distanceSquared=" + distanceSquared
                        + ", targeting=" + targeting + ", visible=" + visible;
            }
        }

        private String describe() {
            return "; tick=" + helper.getTick()
                    + ", pos=" + archer.position() + ", enemy=" + skeleton.position()
                    + ", health=" + archer.getHealth() + ", activity=" + archer.getBrain().getActiveNonCoreActivity()
                    + ", state=" + RangedCombatState.current(archer)
                    + ", target=" + archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                    + ", hurtBy=" + archer.getBrain().getMemory(MemoryModuleType.HURT_BY)
                    + ", hurtByEntity=" + archer.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY)
                    + ", injuryAge=" + injuryAge()
                    + ", path=" + archer.getNavigation().getPath() + ", sawNativePath=" + sawNativePath
                    + ", pressureInterruption=" + pressureInterruption;
        }

        private void succeed() {
            close();
            helper.succeed();
        }

        private void discardProjectiles() {
            List<Entity> projectiles = new ArrayList<>();
            for (Entity entity : helper.getLevel().getAllEntities()) {
                if (entity instanceof Projectile projectile && actors.contains(projectile.getOwner())) projectiles.add(entity);
            }
            projectiles.forEach(Entity::discard);
        }

        private void close() {
            if (closed) return;
            closed = true;
            discardProjectiles();
            actors.forEach(Entity::discard);
            originalBlocks.forEach((position, state) -> helper.getLevel().setBlock(position, state, 3));
            addedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
            ARENAS.remove(this);
        }
    }
}
