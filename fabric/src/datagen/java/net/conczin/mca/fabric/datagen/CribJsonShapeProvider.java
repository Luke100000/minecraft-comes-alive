package net.conczin.mca.fabric.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.MCA;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class CribJsonShapeProvider implements DataProvider {
    private final Path outputRoot;

    public CribJsonShapeProvider(FabricDataOutput output) {
        this.outputRoot = output.getOutputFolder();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();

        cribRecipePaths().forEach(path -> writes.add(saveRecipe(cache, path)));
        cribAdvancementPaths().forEach(path -> writes.add(saveAdvancement(cache, path)));

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> saveRecipe(CachedOutput cache, Path path) {
        JsonObject root = readJson(path);
        root.addProperty("show_notification", true);
        return DataProvider.saveStable(cache, root, path);
    }

    private CompletableFuture<?> saveAdvancement(CachedOutput cache, Path path) {
        JsonObject root = readJson(path);
        root.addProperty("sends_telemetry_event", false);
        return DataProvider.saveStable(cache, root, path);
    }

    private List<Path> cribRecipePaths() {
        return cribJsonPaths(outputRoot.resolve("data").resolve(MCA.MOD_ID).resolve("recipe"));
    }

    private List<Path> cribAdvancementPaths() {
        return cribJsonPaths(outputRoot.resolve("data").resolve(MCA.MOD_ID).resolve("advancement").resolve("recipes").resolve("decorations"));
    }

    private static List<Path> cribJsonPaths(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("_crib.json"))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to list datagen outputs in " + directory, exception);
        }
    }

    private static JsonObject readJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read datagen output " + path, exception);
        }
    }

    @Override
    public String getName() {
        return "Crib JSON shape provider";
    }
}
