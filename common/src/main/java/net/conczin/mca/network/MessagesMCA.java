package net.conczin.mca.network;

import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.network.s2c.*;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.network.s2c.*;

import static net.conczin.mca.cobalt.network.NetworkHandler.Direction.CLIENTBOUND;
import static net.conczin.mca.cobalt.network.NetworkHandler.Direction.SERVERBOUND;

public interface MessagesMCA {
    static void bootstrap() {
        NetworkHandler.registerMessage(InteractionVillagerMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(BabyNamingVillagerMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetFamilyRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetFamilyResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(GetVillagerResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(CallToPlayerMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetVillageRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetVillageResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(GetVillageFailedResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(OpenGuiRequest.class, CLIENTBOUND);
        NetworkHandler.registerMessage(ReportBuildingMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(ConfirmBuildingPolymorphMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(BuildingPolymorphMessage.class, CLIENTBOUND);
        NetworkHandler.registerMessage(SaveVillageMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetFamilyTreeRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetFamilyTreeResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(GetInteractDataRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetInteractDataResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(InteractionDialogueMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(InteractionDialogueResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(InteractionDialogueInitMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(GetVillagerRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(VillagerEditorSyncRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(AnalysisResults.class, CLIENTBOUND);
        NetworkHandler.registerMessage(InteractionCloseRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(ShowToastRequest.class, CLIENTBOUND);
        NetworkHandler.registerMessage(BabyNameRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(BabyNameResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(VillagerNameRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(VillagerNameResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(RenameVillageMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(FamilyTreeUUIDLookup.class, SERVERBOUND);
        NetworkHandler.registerMessage(FamilyTreeUUIDResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(DestinyMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(PlayerDataMessage.class, CLIENTBOUND);
        NetworkHandler.registerMessage(PlayerDataRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(SkinListRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(SkinListResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(CustomSkinListRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(CustomSkinListResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(OpenDestinyGuiRequest.class, CLIENTBOUND);
        NetworkHandler.registerMessage(DamageItemMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(InteractionDialogueQuestionResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(ConfigRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(ConfigResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(VillagerMessage.class, CLIENTBOUND);
        NetworkHandler.registerMessage(AddCustomClothingMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(RemoveCustomClothingMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(CustomSkinsChangedMessage.class, CLIENTBOUND);
        NetworkHandler.registerMessage(SetTargetMessage.class, SERVERBOUND);
        NetworkHandler.registerMessage(CivilRegistryPageRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(CivilRegistryResponse.class, CLIENTBOUND);
        NetworkHandler.registerMessage(ChatAIContextUpdateRequest.class, SERVERBOUND);
        NetworkHandler.registerMessage(ChatAIContextResponse.class, CLIENTBOUND);
    }
}
