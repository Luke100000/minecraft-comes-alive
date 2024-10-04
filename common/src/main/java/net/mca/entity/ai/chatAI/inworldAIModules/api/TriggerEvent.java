package net.mca.entity.ai.chatAI.inworldAIModules.api;

public record TriggerEvent(String trigger, Parameter[] parameters) {
    public record Parameter(String name, String value){}
}
