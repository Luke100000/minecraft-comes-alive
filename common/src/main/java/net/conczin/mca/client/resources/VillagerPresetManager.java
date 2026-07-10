package net.conczin.mca.client.resources;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class VillagerPresetManager {
    private static final String EXTENSION = ".json";

    private final Path directory;

    public VillagerPresetManager(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public List<String> listNames() throws IOException {
        ensureDirectory();
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.length() > EXTENSION.length() && name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    public Optional<CompoundTag> load(String name) throws IOException {
        Path file = resolve(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return Optional.of(PresetCodec.fromJsonString(json));
    }

    public void save(String name, CompoundTag tag) throws IOException {
        ensureDirectory();
        Files.writeString(resolve(name), PresetCodec.toJsonString(tag), StandardCharsets.UTF_8);
    }

    public boolean rename(String oldName, String newName) throws IOException {
        ensureDirectory();
        Path oldFile = resolve(oldName);
        Path newFile = resolve(newName);
        if (!Files.isRegularFile(oldFile) || Files.exists(newFile)) {
            return false;
        }
        Files.move(oldFile, newFile);
        return true;
    }

    public boolean delete(String name) throws IOException {
        return Files.deleteIfExists(resolve(name));
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(directory);
    }

    private Path resolve(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()
                || normalizedName.equals(".")
                || normalizedName.equals("..")
                || normalizedName.indexOf('/') >= 0
                || normalizedName.indexOf('\\') >= 0
                || normalizedName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid villager preset name: " + name);
        }

        Path resolved = directory.resolve(normalizedName + EXTENSION).normalize();
        if (!directory.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Villager preset path escapes preset directory: " + name);
        }
        return resolved;
    }
}
