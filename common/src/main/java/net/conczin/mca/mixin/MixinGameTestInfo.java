package net.conczin.mca.mixin;

import net.conczin.mca.MCA;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Diagnostic-only probe for pending test chunks in the dedicated GameTest server. */
@Mixin(GameTestInfo.class)
abstract class MixinGameTestInfo {
    @Unique private long mca$pendingSince;
    @Unique private long mca$lastReport;
    @Unique private long mca$lastRepair;

    @Inject(method = "tick", at = @At("HEAD"))
    private void mca$reportUnreadyChunks(GameTestRunner runner, CallbackInfo ci) {
        if (!Boolean.getBoolean("mca.gametest.diagnoseChunks")
                && !Boolean.getBoolean("mca.gametest.repairChunkTickets")) return;
        GameTestInfo test = (GameTestInfo) (Object) this;
        if (test.hasStarted() || test.isDone()) return;
        long now = System.nanoTime();
        if (mca$pendingSince == 0) mca$pendingSince = now;
        if (Boolean.getBoolean("mca.gametest.repairChunkTickets")
                && now - mca$pendingSince >= 1_000_000_000L
                && now - mca$lastRepair >= 1_000_000_000L) {
            mca$lastRepair = now;
            // Consecutive 26.x GameTest batches can drop some tickets for the
            // next structure. Without these tickets its entities never reach
            // TICKING, and GameTestInfo explicitly suspends its timeout.
            test.getTestInstanceBlockEntity().getStructureBoundingBox()
                    .intersectingChunks().forEach(chunk -> {
                        if (!test.getLevel().getForceLoadedChunks().contains(chunk.pack())) {
                            test.getLevel().setChunkForced(chunk.x(), chunk.z(), true);
                            MCA.LOGGER.warn("[MCA GameTest] restored missing test chunk ticket test={} chunk={}",
                                    test.id(), chunk);
                        }
                    });
        }
        if (!Boolean.getBoolean("mca.gametest.diagnoseChunks")) return;
        if (now - mca$pendingSince < 8_000_000_000L || now - mca$lastReport < 15_000_000_000L) return;
        mca$lastReport = now;
        List<ChunkPos> unready = test.getTestInstanceBlockEntity().getStructureBoundingBox()
                .intersectingChunks()
                .filter(chunk -> !test.getLevel().areEntitiesActuallyLoadedAndTicking(chunk))
                .toList();
        MCA.LOGGER.warn("[MCA Test Chunk Wait] test={} pending={} unready={} forced={} details={}",
                test.id(), (now - mca$pendingSince) / 1_000_000_000L,
                unready, test.getLevel().getForceLoadedChunks().size(),
                unready.stream().map(chunk -> chunk + ":forced="
                        + test.getLevel().getForceLoadedChunks().contains(chunk.pack())
                        + ",entitiesLoaded=" + test.getLevel().areEntitiesLoaded(chunk.pack())
                        + ",positionTicking=" + test.getLevel().getChunkSource().isPositionTicking(chunk.pack())
                        + ",present=" + (test.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null))
                        .toList());
    }
}
