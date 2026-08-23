package net.conczin.mca.network;

import net.conczin.mca.network.s2c.*;

public interface ClientHandler {
    void handleGuiRequest(OpenGuiRequest message);

    void handleChatAIContextResponse(ChatAIContextResponse message);

    void handleFamilyTreeResponse(GetFamilyTreeResponse message);

    void handleInteractDataResponse(GetInteractDataResponse message);

    void handleVillageDataResponse(GetVillageResponse message);

    void handleVillageDataFailedResponse(GetVillageFailedResponse message);

    void handleFamilyDataResponse(GetFamilyResponse message);

    void handleVillagerDataResponse(GetVillagerResponse message);

    void handleDialogueResponse(InteractionDialogueResponse message);

    void handleSkinListResponse(AnalysisResults message);

    void handleBabyNameResponse(BabyNameResponse message);

    void handleVillagerNameResponse(VillagerNameResponse message);

    void handleToastMessage(ShowToastRequest message);

    void handleFamilyTreeUUIDResponse(FamilyTreeUUIDResponse response);

    void handlePlayerDataMessage(PlayerDataMessage response);

    void handleAppearanceCatalogSync(AppearanceCatalogSync response);

    void handleDestinyGuiRequest(OpenDestinyGuiRequest request);

    void handleDialogueQuestionResponse(InteractionDialogueQuestionResponse response);

    void handleConfigResponse(ConfigResponse response);

    void handleVillagerMessage(VillagerMessage message);

    void handleCivilRegistryResponse(CivilRegistryResponse response);

    void handleBuildingPolymorph(BuildingPolymorphMessage message);
}
