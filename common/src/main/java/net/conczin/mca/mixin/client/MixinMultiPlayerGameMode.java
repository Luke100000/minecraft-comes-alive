package net.conczin.mca.mixin.client;

import java.util.UUID;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.OpenPlayerInteractionRequest;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {
   @Unique
   private UUID mca$lastTargetUUID;
   @Unique
   private long mca$lastTargetTick = Long.MIN_VALUE;

   @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
   private void mca$interact(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      this.mca$openPlayerInteraction(player, entity, cir);
   }

   @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
   private void mca$interactAt(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      this.mca$openPlayerInteraction(player, entity, cir);
   }

   @Unique
   private void mca$openPlayerInteraction(Player player, Entity entity, CallbackInfoReturnable<InteractionResult> cir) {
      if (entity instanceof Player target && !target.getUUID().equals(player.getUUID())) {
         long gameTime = player.level().getGameTime();
         UUID targetUUID = target.getUUID();
         if (this.mca$lastTargetTick == gameTime && targetUUID.equals(this.mca$lastTargetUUID)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
         }

         this.mca$lastTargetTick = gameTime;
         this.mca$lastTargetUUID = targetUUID;
         Network.sendToServer(new OpenPlayerInteractionRequest(target.getUUID()));
         cir.setReturnValue(InteractionResult.SUCCESS);
      }
   }
}
