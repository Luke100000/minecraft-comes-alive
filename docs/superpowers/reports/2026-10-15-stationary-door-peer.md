# Stationary peer in a remembered doorway

Scope: Minecraft1.21.1 only. Baseline735babac; the two changed Java owners were clean
before this slice. Unrelated dirty/staged navigation, benchmark and other work is preserved.

## Cause and correction

`SmarterOpenDoorsTask.closeDoors` preserves its own body clearance, but its peer predicate
only recognized an active PATH. A stationary sensed MCA peer partly inside an open door
therefore did not prevent closing the panel through its body. The existing peer loop now
also recognizes live body overlap with the same toggleable cell. The same-type filter and
existing nearby path condition remain; there is no new scan, state, retry or movement owner.

The regression independently confirms an open native spruce-door shape fits the peer and
the closed panel would intersect it. With no peer PATH, it checks that closure is deferred
and remembered ownership retained; after the peer moves clear, closure and ownership release
must occur. NoAI isolates the real closure decision, not sensing or physical navigation.
Owned actors and original block states are restored in finally.

## Evidence

Logs under `C:/Users/mikol/IdeaProjects/builder-addon/.superpowers/sdd/2026-10-15-access-execution/postrepair-live/`:

- `mca-stationary-door-red.log`: exact RED, “A remembered door must not close across a stationary peer without a PATH”.
- `mca-stationary-door-green.log`:587common JUnit cases, both1.21.1 loader builds, all12DoorInteractionGameTests pass.
- `mca-stationary-door-review.patch`: fixed diff SHA2568CCCAE43D6586FFAE1290404361C092E95ABAAE1AE52F48E6BBF79A893B85E18. Independent6.1Sol high four-lens review approves without findings.

Native InteractWithDoor, MCA's owner, MineColonies toggleable handling and Baritone route
opening were compared. MCA26.3 has the same old peer predicate; comparison only, no26.x edits
or validation. Existing peer-path, closure, hatch, gate, config and unloaded-chunk controls
remain intact.

Builder's old pinned jar matches the old compiled door owner exactly. The rebuilt NeoForge
jar differs in exactly one archive entry, SmarterOpenDoorsTask.class; no entries are added
or removed. New artifact SHA256695C61C21FDA6D487EA748C1FB91F40221BF96823B76B40FCBBEDAC663189317;
Fabric SHA256D1F4D99BAA6A2B33A2C3796F2A18EF5B13358E51F2D5B2BBFB172BC96D6785ED.
Builder integration verification is recorded in its current restoration report.

## Limits

Protection applies to same-type peers already present in nearby-entity memory. This test
does not prove sensor scheduling. A Builder Snowy309 capture shows a body overlapping a
closed door, but does not identify who closed it or prove this is its first causal failure.
Complete Builder construction/cleanup and repeated matrix acceptance remain separate.
