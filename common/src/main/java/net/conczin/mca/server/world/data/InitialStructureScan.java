package net.conczin.mca.server.world.data;

public record InitialStructureScan(StructureScanner.Result structure,
                                   Village village,
                                   BuildingScanResult room,
                                   BuildingScanResult rootRoom) {
    public Building.validationResult result() {
        if (structure.result() != Building.validationResult.SUCCESS) return structure.result();
        if (room.result() != Building.validationResult.SUCCESS) return room.result();
        return rootRoom == null ? Building.validationResult.SUCCESS : rootRoom.result();
    }

    public boolean isRoomAmbiguous() {
        return room.result() == Building.validationResult.SUCCESS && room.isAmbiguous();
    }
}
