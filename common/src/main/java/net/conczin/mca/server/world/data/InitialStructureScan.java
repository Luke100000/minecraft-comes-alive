package net.conczin.mca.server.world.data;

public record InitialStructureScan(StructureScanner.Result structure,
                                   Village village,
                                   BuildingScanResult room) {
    public Building.validationResult result() {
        if (structure.result() != Building.validationResult.SUCCESS) return structure.result();
        return room.result();
    }

    public boolean isRoomAmbiguous() {
        return room.result() == Building.validationResult.SUCCESS && room.isAmbiguous();
    }
}
