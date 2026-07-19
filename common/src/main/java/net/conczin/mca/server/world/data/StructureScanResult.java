package net.conczin.mca.server.world.data;

public record StructureScanResult(Building.validationResult result,
                                  StructureScanner.Result scan,
                                  Village village,
                                  int existingStructureId) {
    public boolean hasExistingStructure() {
        return existingStructureId >= 0;
    }
}
