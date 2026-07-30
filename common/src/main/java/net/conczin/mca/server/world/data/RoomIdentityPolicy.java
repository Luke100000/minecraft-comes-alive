package net.conczin.mca.server.world.data;

import java.util.Arrays;
import java.util.List;

/** Assigns previous Room IDs to scanned components using interaction-stable rules. */
final class RoomIdentityPolicy {
    private RoomIdentityPolicy() {
    }

    static int[] assign(List<Integer> roomIds,
                        int currentRoomId,
                        int mainRoomId,
                        int playerComponent,
                        long[][] overlaps) {
        int[] owners = new int[overlaps.length];
        Arrays.fill(owners, -1);
        if (playerComponent < 0 || playerComponent >= overlaps.length) return owners;

        int currentRoom = roomIds.indexOf(currentRoomId);
        if (currentRoom < 0) return owners;
        int mainRoom = roomIds.indexOf(mainRoomId);

        boolean[] consumedRooms = new boolean[roomIds.size()];
        boolean[] claimedComponents = new boolean[overlaps.length];
        int mergedRooms = 0;
        for (int room = 0; room < roomIds.size(); room++) {
            if (overlaps[playerComponent][room] > 0) mergedRooms++;
        }

        boolean playerTouchesMain = mainRoom >= 0 && overlaps[playerComponent][mainRoom] > 0;
        int playerOwner = playerTouchesMain ? mainRoom : currentRoom;
        owners[playerComponent] = roomIds.get(playerOwner);
        claimedComponents[playerComponent] = true;

        if (mergedRooms > 1) {
            for (int room = 0; room < roomIds.size(); room++) {
                consumedRooms[room] = overlaps[playerComponent][room] > 0;
            }
        } else {
            consumedRooms[playerOwner] = true;
        }

        if (mainRoom >= 0 && !consumedRooms[mainRoom]) {
            claimBest(mainRoom, roomIds, overlaps, owners, consumedRooms, claimedComponents);
        }
        for (int room = 0; room < roomIds.size(); room++) {
            if (room == mainRoom || consumedRooms[room]) continue;
            claimBest(room, roomIds, overlaps, owners, consumedRooms, claimedComponents);
        }
        return owners;
    }

    private static void claimBest(int room,
                                  List<Integer> roomIds,
                                  long[][] overlaps,
                                  int[] owners,
                                  boolean[] consumedRooms,
                                  boolean[] claimedComponents) {
        int bestComponent = -1;
        long bestOverlap = 0;
        for (int component = 0; component < overlaps.length; component++) {
            long overlap = overlaps[component][room];
            if (!claimedComponents[component] && overlap > bestOverlap) {
                bestOverlap = overlap;
                bestComponent = component;
            }
        }
        consumedRooms[room] = true;
        if (bestComponent < 0) return;
        owners[bestComponent] = roomIds.get(room);
        claimedComponents[bestComponent] = true;
    }
}
