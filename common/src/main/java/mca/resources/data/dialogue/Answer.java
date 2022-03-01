package mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import mca.client.gui.Constraint;

public class Answer {
    private final String name;
    private final List<Constraint> constraints;

    private final List<Result> results;

    public Answer(String name, List<Constraint> constraints, List<Result> results) {
        this.name = name;
        this.constraints = constraints;
        this.results = results;
    }

    public static Answer fromJson(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : "";

        List<Constraint> constraints = Constraint.fromStringList(
                json.has("constraints") ? json.get("constraints").getAsString() : ""
        );

        List<Result> results = new LinkedList<>();
        for (JsonElement e : json.getAsJsonArray("results")) {
            results.add(Result.fromJson(e.getAsJsonObject()));
        }

        return new Answer(name, constraints, results);
    }

    public String getName() {
        return name;
    }

    public List<Result> getResults() {
        return results;
    }

    public boolean isValidForConstraint(Set<Constraint> constraints) {
        return constraints.containsAll(this.constraints);
    }
}
