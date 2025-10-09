package net.conczin.mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.interaction.Constraint;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Answer {
    private final String name;
    private final int priority;
    private final List<Constraint> constraints;

    private final List<Result> results;

    public Answer(String name, List<Constraint> constraints, List<Result> results, int priority) {
        this.name = name;
        this.constraints = constraints;
        this.results = results;
        this.priority = priority;
    }

    public static Answer fromJson(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : "";
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;

        List<Constraint> constraints = Constraint.fromStringList(
                json.has("constraints") ? json.get("constraints").getAsString() : ""
        );

        List<Result> results = new LinkedList<>();
        for (JsonElement e : json.getAsJsonArray("results")) {
            results.add(Result.fromJson(e.getAsJsonObject()));
        }

        return new Answer(name, constraints, results, priority);
    }

    public String getName() {
        return name;
    }

    public List<Result> getResults() {
        return results;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isValidForConstraint(Set<Constraint> constraints) {
        return constraints.containsAll(this.constraints);
    }
}
