package net.conczin.mca.network;

import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.network.s2c.*;
import net.mca.network.c2s.*;
import net.mca.network.s2c.*;

public interface MessagesMCA {
    static void bootstrap() {
        NetworkHandler.registerMessage(InteractionVillagerMessage.class);
        NetworkHandler.registerMessage(BabyNamingVillagerMessage.class);
        NetworkHandler.registerMessage(GetFamilyRequest.class);
        NetworkHandler.registerMessage(GetFamilyResponse.class);
        NetworkHandler.registerMessage(GetVillagerResponse.class);
        NetworkHandler.registerMessage(CallToPlayerMessage.class);
        NetworkHandler.registerMessage(GetVillageRequest.class);
        NetworkHandler.registerMessage(GetVillageResponse.class);
        NetworkHandler.registerMessage(GetVillageFailedResponse.class);
        NetworkHandler.registerMessage(OpenGuiRequest.class);
        NetworkHandler.registerMessage(ReportBuildingMessage.class);
        NetworkHandler.registerMessage(ConfirmBuildingPolymorphMessage.class);
        NetworkHandler.registerMessage(BuildingPolymorphMessage.class);
        NetworkHandler.registerMessage(SaveVillageMessage.class);
        NetworkHandler.registerMessage(GetFamilyTreeRequest.class);
        NetworkHandler.registerMessage(GetFamilyTreeResponse.class);
        NetworkHandler.registerMessage(GetInteractDataRequest.class);
        NetworkHandler.registerMessage(GetInteractDataResponse.class);
        NetworkHandler.registerMessage(InteractionDialogueMessage.class);
        NetworkHandler.registerMessage(InteractionDialogueResponse.class);
        NetworkHandler.registerMessage(InteractionDialogueInitMessage.class);
        NetworkHandler.registerMessage(GetVillagerRequest.class);
        NetworkHandler.registerMessage(VillagerEditorSyncRequest.class);
        NetworkHandler.registerMessage(AnalysisResults.class);
        NetworkHandler.registerMessage(InteractionCloseRequest.class);
        NetworkHandler.registerMessage(ShowToastRequest.class);
        NetworkHandler.registerMessage(BabyNameRequest.class);
        NetworkHandler.registerMessage(BabyNameResponse.class);
        NetworkHandler.registerMessage(VillagerNameRequest.class);
        NetworkHandler.registerMessage(VillagerNameResponse.class);
        NetworkHandler.registerMessage(RenameVillageMessage.class);
        NetworkHandler.registerMessage(FamilyTreeUUIDLookup.class);
        NetworkHandler.registerMessage(FamilyTreeUUIDResponse.class);
        NetworkHandler.registerMessage(DestinyMessage.class);
        NetworkHandler.registerMessage(PlayerDataMessage.class);
        NetworkHandler.registerMessage(PlayerDataRequest.class);
        NetworkHandler.registerMessage(SkinListRequest.class);
        NetworkHandler.registerMessage(SkinListResponse.class);
        NetworkHandler.registerMessage(CustomSkinListRequest.class);
        NetworkHandler.registerMessage(CustomSkinListResponse.class);
        NetworkHandler.registerMessage(OpenDestinyGuiRequest.class);
        NetworkHandler.registerMessage(DamageItemMessage.class);
        NetworkHandler.registerMessage(InteractionDialogueQuestionResponse.class);
        NetworkHandler.registerMessage(ConfigRequest.class);
        NetworkHandler.registerMessage(ConfigResponse.class);
        NetworkHandler.registerMessage(VillagerMessage.class);
        NetworkHandler.registerMessage(AddCustomClothingMessage.class);
        NetworkHandler.registerMessage(RemoveCustomClothingMessage.class);
        NetworkHandler.registerMessage(CustomSkinsChangedMessage.class);
        NetworkHandler.registerMessage(SetTargetMessage.class);
        NetworkHandler.registerMessage(CivilRegistryPageRequest.class);
        NetworkHandler.registerMessage(CivilRegistryResponse.class);
        NetworkHandler.registerMessage(ChatAIContextUpdateRequest.class);
        NetworkHandler.registerMessage(ChatAIContextResponse.class);
    }
}
