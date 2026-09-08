# Floor / Room Single-Source Simplification

> **Spatial model superseded:** use
> `2026-09-08-exact-cell-floor-scanner-simplification-design.md` as the
> canonical source for Floor/Room membership cells, traversal, connectors, and
> persistence. This file remains historical context for the single-source and
> identity direction only.

## Canonical model

The floor system has one spatial rule and one building-identity rule:

```text
player/POI Y -> Floor
player/POI X/Z -> Room column on that Floor
```

```text
LogicalBuilding -> Main Room -> Ground Floor
```

`Structure` remains persistence/scan ownership. It is not a second player-facing building model.

## Locked invariants

- Main Room is the only persisted Ground Floor anchor. Ground Floor is always derived from the
  Main Room's `structureId/floorId`; there are no separately persisted ground IDs.
- Changing or repairing Main Room therefore re-anchors floor numbering automatically.
- A logical building with no Rooms is deleted together with its persisted Structures/Floors.
- A missing Structure never implies `structureId == logicalBuildingId`; lookup returns not-found.
- `RoomScanPlan.currentRoom` means only the Room actually selected at the interaction position.
  Target building / Structure / Floor identities are separate fields with separate meanings.
- Direct Floor selection is vertical-band based. X/Z is considered only after Y selected a Floor.
- Connectors are valid Floor/Room footprint cells. Door cells are assigned to one deterministic
  Room after Room partitioning, so exterior and interior doors use the same rule.
- The old `PHYSICAL / HORIZONTAL_CONNECTOR / VERTICAL_CONNECTOR / LANDING_HANDOFF` interaction
  priority hierarchy does not exist.
- Room ownership is column ownership. A POI anywhere in the selected Floor band belongs to the
  Room owning its X/Z column.
- POI evidence also includes immediately adjacent perimeter/wall columns. A perimeter column
  bordering multiple Rooms has exactly one deterministic owner.
- Wall/perimeter POI columns never become Room floor footprint, traversal area, or Blueprint area.
- `Update Room` rescans current physical geometry and POIs, refreshes the affected persisted Floor,
  repartitions it, and reconciles Room identities atomically. If a Room splits, the component
  containing the player keeps the old identity and other descendant components become new Rooms.
- Add Floor/Add Basement attachment is proven by real shared vertical connector columns, not an
  arbitrary maximum Y gap. Distance may break ties only after connectivity is established.
- Blueprint floor selection remains global/persistent for viewing, while every edit action is
  scoped to the logical building of the Room the player is standing in.
- Remove Floor uses that current Room's logical building plus the selected floor ordinal and is
  revalidated on the server. Remove Room never removes a Floor implicitly.

## Persistence compatibility

No building-data version bump is required. `LogicalBuilding` loading tolerates legacy
`groundStructureId/groundFloorId` tags but does not retain or re-save them. On load, the current
Main Room is authoritative and floor numbering is rebuilt from its Floor.

`StructureFloor.connectors` remains optional for old saves. Fresh scans populate connector markers.

## Tests that define the contract

The durable regression layer covers:

- Main Room re-anchors floor numbering;
- logical saves contain Main Room but no duplicate Ground Floor IDs;
- missing Structure ID returns not-found;
- no Rooms deletes the whole logical building;
- direct positions outside every Floor band do not snap to a same-column Floor;
- connector cells have exactly one deterministic Room owner;
- exterior and shared/interior connector selection use that same owner rule;
- Room POI evidence spans the Floor band, including below raised/stair surfaces;
- perimeter wall POIs stay outside footprint and shared perimeter POIs have one owner;
- Floor attachment accepts a real shared vertical connector even beyond four blocks and rejects a
  nearby disconnected Floor.

Minecraft world/collision scanning remains covered by the existing scanner integration paths; do
not introduce a large fake `Level` solely for unit testing.
