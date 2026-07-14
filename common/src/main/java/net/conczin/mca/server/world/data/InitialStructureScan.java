package net.conczin.mca.server.world.data;

public record InitialStructureScan(BuildingScanResult root, BuildingScanResult room) {
    public Building.validationResult result() {
        return root.result() != Building.validationResult.SUCCESS ? root.result() : room.result();
    }

    public boolean isRoomAmbiguous() {
        return room.result() == Building.validationResult.SUCCESS && room.isAmbiguous();
    }
}
