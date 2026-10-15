# Persistent destinations during a native jump

Only MCA1.21.1. Baseline abd5dec79; unrelated staged work is preserved.

## Cause and reproduction

Vanilla GroundPathNavigation cannot start a search during an ordinary airborne
frame. MoveToTargetSink still tries, records CANT_REACH_WALK_TARGET_SINCE and
erases WALK_TARGET. A persistent producer with a short terminal boundary can
therefore give up after landing without ever making a grounded route attempt.

Builder checkpoint58b22efe reconstructs the actual outgoing native jump before
installing the recorded Snowy407 access program. It fails at relative
(6.5,4,-3.80654). Loading only the airborne pose passes: the outgoing movement
and handoff are necessary causal conditions, and saved NBT does not preserve
that transient Brain history. The independent MCA
airborneWalkTargetWaitsForNativeSearch regression also fails before correction
at the fresh intent/failure boundary, without any Builder code.

## Correction and rejected approach

WanderOrTeleportToTargetTask defers starting a PersistentPathTarget while the
existing MCAGroundPathNavigation.canUpdatePath check denies a search. Its
existing override is public so the sink can query its owner directly. Ground,
liquid, passenger and climb eligibility remain owned by native navigation.
No new state, timer, retry, controller or Builder movement rule is introduced.

The first all-target guard passed11physical/49navigation/17producer controls
but failed failedNearbyPathRetryIsNotThrottledInsideMovementSink. Ordinary
disposable targets deliberately retain vanilla release/retry semantics. That
experiment was reverted before limiting the correction to MCA's existing
persistent-target policy boundary. Existing assertions and fixtures are intact.

Vanilla/MCA define the actual search and target lifecycle. MineColonies uses
its own asynchronous job lifecycle and always permits its navigation update;
copying that would bypass MCA's physical start contract. Baritone's executor
refuses to splice a path while falling outside liquid. These comparisons support
respecting the existing movement owner rather than adding Builder recovery.

Fixed four-lens review:390B23622099DF1F4F570D3837964C746B62F36F32517D0B1891CBFFDE7A839F.
Reuse: existing target marker and native eligibility. Quality: no parallel
state or new helper. Correctness: ordinary retry contract retained; real
landing and subsequent physical arrival asserted. Efficiency: one existing
eligibility query before a fresh persistent attempt, with no search or cache.

The refined candidate passes589commonJUnit, both loader builds,11physical
safety,12movement-sink,49navigation and17destination-producer GameTests.
The ordinary failed-path retry regression passes unchanged. Rebuilt Builder
integration remains pending.

Frozen mca-persistent-landing/ artifacts change exactly the sink and navigator
classes relative to the prior layered-snow pins, with no resource/entry changes.
NeoForge bytes equal the owning common compiled classes; Fabric is normally
remapped by its loader build.

- NeoForge:7313fc65fb4f742a273b81d4c081fded32b7ccf741f1dc0b613846db06386720.
- Fabric:77035129709406bc0623873313748f2ab721b366fdbf4475e0f24a5c67e3c5be.

Logs live in Builder's ignored postrepair-live/ evidence directory, prefixed
mca-persistent-landing. The rejected all-target artifacts under mca-landing-target/
are historical evidence and must not be used for acceptance.
