package net.mca.entity.ai.chatAI.inworldAIModules.api;

public record Session(String name, SessionCharacter[] sessionCharacters, String loadedScene) {
    public record SessionCharacter(String name, String character, String displayName, CharacterAssets characterAssets) {
        public record CharacterAssets(String avatarImg, String avatarImgOptional) {}
    }
}
