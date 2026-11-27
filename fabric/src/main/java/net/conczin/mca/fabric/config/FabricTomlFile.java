package net.conczin.mca.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class FabricTomlFile {
    private FabricTomlFile() {}

    static void write(Path destination, CommentedConfig document) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".mca-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                new TomlWriter().write(document, writer);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Atomic TOML replacement unavailable for " + destination, ex);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
