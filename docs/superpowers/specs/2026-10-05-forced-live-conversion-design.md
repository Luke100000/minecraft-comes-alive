# Forced live MCA conversion

## Intent and approval boundary

The user wants conversion to preserve an existing entity's identity even when
another mod cancels the replacement's spawn event. Force applies only to a live,
registered MCA villager/zombie replacement. Fresh spawns and tombstone
resurrection remain subject to ordinary spawn-event rules.

This is a proposed design for review, not implemented behavior. Written-spec
approval precedes the implementation plan, and plan review precedes execution.
No commit, new checkout, or worktree is authorised.

## Evidence and ownership

The checkout targets Minecraft 1.21.1, Java 21, Fabric Loader 0.19.5 / Fabric API
0.116.17+1.21.1, and NeoForge 21.1.252.

`VillagerLike.convertPreservingUuid` is shared by both MCA conversion overrides.
It currently clears transferred equipment, discards the source, and ignores
`addFreshEntity`'s result. Family-tree entries use the entity UUID.

Vanilla's entity manager rejects duplicate UUIDs. Removing the source releases
its registered UUID and detaches tracking and passengers. Clearing the removal
flag alone does not restore registration.

NeoForge's matching userdev patches add `addNewEntityWithoutEvent` to the entity
manager. It retains UUID checks and native registration callbacks. The
`ServerLevel` wrapper additionally calls `onAddedToLevel` after successful
registration. Ordinary manager insertion posts `EntityJoinLevelEvent` and
returns false when cancelled. These facts were checked in local vanilla sources
and the exact 21.1.252 dependency source/patch artifacts.

## Chosen policy

Use the existing `PlatformHelper` seam for a narrow registered-entity replacement
operation. Keep preparation and conversion-specific state in common code; keep
NeoForge event handling in its adapter. Do not introduce global event listeners,
thread-local force flags, retries, configuration, or a generic transaction system.

Only allow forced replacement when the server level resolves the source UUID to
that exact source instance immediately before removal, the source is not removed,
and source/replacement are the supported MCA villager/zombie conversion pair in
the same level. Never replace an unrelated UUID occupant.

## Conversion flow

1. Create and prepare the replacement on the server thread, preserving UUID and
   conversion data. Copy equipment rather than using `copyAndClear`. Preparation
   failure leaves the source registered and its equipment/home unchanged.
2. Capture the source's vehicle and passengers for transfer or restoration.
   Recheck source identity before removing it to release the UUID.
3. On NeoForge, post the replacement's join event once at the normal pre-insertion
   stage, after source removal. Deliberately ignore its cancellation result for
   this operation, then use native no-event insertion and call `onAddedToLevel`
   only on success. Do not post a second join event or globally uncancel events.
   Check the replacement still has the expected UUID/level and is not removed
   after listeners run; ignoring cancellation does not authorise overriding these
   invariants. Do not attempt to undo independent event-handler mutations.
4. On Fabric, use ordinary native insertion. This design does not attempt to
   defeat arbitrary third-party Fabric Mixins. Both paths check insertion success
   and world registration identity, not just a non-null replacement object.
5. Once registration is confirmed, commit equipment transfer and source residency
   cleanup, and attach the replacement to the captured riding relationships where
   the normal mount APIs permit it. Return only a registered replacement.
6. If insertion fails, return no successful replacement. Remove any partially
   registered replacement belonging to this operation, clear the original's
   removal state, and attempt one checked restoration through the loader adapter.
   Restoring the previously registered original on NeoForge also bypasses the join
   veto; otherwise a rollback could be cancelled again. Keep the original's copied,
   but not yet cleared, equipment and do not perform residency cleanup. Restore
   riding relationships through normal APIs. Do not evict a different UUID occupant.

The failure path must also clean up after a registration exception before
propagating it; it must not swallow the exception or its cause. Log a registration
failure with source/replacement identity and restoration outcome. If restoration
also fails, raise an explicit failure rather than report successful conversion.
This is bounded recovery, not a promise of atomic rollback across arbitrary mod
callbacks, mount vetoes, scoreboard cleanup, or JVM failures.

## Non-live conversion

An unregistered source, including a zombie reconstructed from a tombstone, does
not qualify for force. Keep ordinary `addFreshEntity` and honour its return value.
Do not destroy that source or clear transferred equipment before successful
insertion. Retain the tombstone's existing null/registration checks and stored
remains on failure. A registered UUID owned by a different entity is a rejection,
not permission to overwrite it.

## Intended code scope

- `VillagerLike` and the two MCA conversion overrides: checked preparation and
  commit, including deferring destructive equipment/home changes.
- `PlatformHelper` and `NeoforgePlatformHelper`: the narrow replacement and
  restoration operation; Fabric uses the common native behavior.
- `mca.classtweaker`: only the exact vanilla members needed for entity-manager
  access and clearing removal state. NeoForge-only methods stay in its adapter;
  do not put them in the shared access declaration.

Keep existing unrelated local conversion/resurrection changes intact. No change
to general spawn handling, unrelated conversion/death vetoes, or structure search.

## Acceptance and verification limits

The behavior to establish is: successful live conversion retains one registered
entity with the original UUID and transferred state; a cancelled NeoForge join
event cannot block that conversion; ordinary fresh/resurrection joins still can
be cancelled; creation failure retains the original; genuine insertion failure
never reports success; equipment is not lost or duplicated; restoration and
riding outcomes are checked rather than assumed.

Respect the user's no-GameTest/game-launch boundary. During implementation,
compile common and both loader source sets, validate access wiring, and review
the focused diff. Those checks do not prove runtime event ordering or rollback.
The acceptance cases above remain explicit runtime verification gaps until an
appropriate runtime lane is authorised. Writing this spec itself needs only a
content/diff review, not compilation or a game launch.
