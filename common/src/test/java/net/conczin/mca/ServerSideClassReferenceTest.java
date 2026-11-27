package net.conczin.mca;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSideClassReferenceTest {
    private static final List<String> FORBIDDEN_CLIENT_TOKENS = List.of(
            "net/conczin/mca/MCAClient",
            "net/conczin/mca/client/",
            "net/minecraft/client/"
    );

    @Test
    void villagerEntityAndDialogueTypeHaveNoClientConstantPoolReferences() throws IOException {
        assertNoClientConstantPoolReferences("net/conczin/mca/entity/VillagerEntityMCA");
        assertNoClientConstantPoolReferences("net/conczin/mca/entity/ai/DialogueType");
    }

    private static void assertNoClientConstantPoolReferences(String internalClassName) throws IOException {
        List<String> utf8Constants = readConstantPoolUtf8Entries(internalClassName);
        List<String> violations = utf8Constants.stream()
                .filter(entry -> FORBIDDEN_CLIENT_TOKENS.stream().anyMatch(entry::contains))
                .toList();
        assertTrue(violations.isEmpty(),
                () -> internalClassName + ".class contains dedicated-server-unsafe client references: " + violations);
    }

    private static List<String> readConstantPoolUtf8Entries(String internalClassName) throws IOException {
        String resourcePath = internalClassName + ".class";
        try (InputStream raw = ServerSideClassReferenceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(raw, "Missing compiled class resource: " + resourcePath);
            DataInputStream in = new DataInputStream(raw);
            int magic = in.readInt();
            assertTrue(magic == 0xCAFEBABE, "Invalid class file magic for " + resourcePath);
            in.readUnsignedShort(); // minor_version
            in.readUnsignedShort(); // major_version
            int constantPoolCount = in.readUnsignedShort();

            List<String> utf8Entries = new ArrayList<>();
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8Entries.add(in.readUTF()); // CONSTANT_Utf8
                    case 3, 4 -> in.readInt(); // CONSTANT_Integer, CONSTANT_Float
                    case 5, 6 -> { // CONSTANT_Long, CONSTANT_Double (occupy two slots)
                        in.readLong();
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> in.readUnsignedShort(); // Class, String, MethodType, Module, Package
                    case 9, 10, 11, 12, 17, 18 -> in.readInt(); // Fieldref, Methodref, InterfaceMethodref, NameAndType, Dynamic, InvokeDynamic
                    case 15 -> { // CONSTANT_MethodHandle
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IOException("Unknown constant pool tag " + tag + " at index " + index + " in " + resourcePath);
                }
            }
            return utf8Entries;
        }
    }
}
